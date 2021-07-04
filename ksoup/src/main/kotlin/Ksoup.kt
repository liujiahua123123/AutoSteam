@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalContracts::class)

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.SocketException
import java.net.URL
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

val ksoupJson = Json {
    this.ignoreUnknownKeys = true
    this.isLenient = true
    this.encodeDefaults = true
    this.ignoreUnknownKeys = true
}

inline fun <reified T : Any> String.deserialize(): T = ksoupJson.decodeFromString(this)


@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> T.serialize(format: StringFormat, serializer: KSerializer<T> = format.serializersModule.serializer()): String {
    return format.encodeToString(serializer, this)
}


inline fun <reified T:Any> Connection.Response.decode():T{
    return body().deserialize()
}

/**
 * HTTP协议的扩充 - Him188JumpServer
 *
 */

/**
 * Apply a jump server
 */
const val JUMPSERVER_HEADER = "jumpserver-target"
const val JUMPSERVER_ERROR_STATUS_CODE = 881


fun Connection.Request.jumpServer(ip:String, port:Short) {
    header(JUMPSERVER_HEADER, "jumpserver://$ip:$port")
}

fun Connection.jumpServer(ip:String, port:Short) {
    header(JUMPSERVER_HEADER, "jumpserver://$ip:$port")
}


open class Ksoup(

){
    internal val intrinsics = mutableListOf<IntrinsicHandler>()
    internal val responseHandlers = mutableListOf<ResponseHandler>()
    internal val requestOverriders = mutableListOf<RequestOverrider>()

    fun interface IntrinsicHandler {
        operator fun Ksoup.invoke(p2: Connection)
    }

    fun interface ResponseHandler {
        operator fun Ksoup.invoke(p2: Connection.Response)
    }

    fun interface RequestOverrider {
        operator fun Ksoup.invoke(p2: Connection)
    }

    fun addIntrinsic(block: IntrinsicHandler) {
        intrinsics.add(block)
    }

    fun addResponseHandler(block: ResponseHandler){
        responseHandlers.add(block)
    }

    @PublishedApi
    internal fun Connection.applyIntrinsics(): Connection {
        intrinsics.forEach { it.run { this@Ksoup(this@applyIntrinsics) } }
        return this
    }

    @PublishedApi
    internal fun Connection.Response.applyHandlers(): Connection.Response{
        responseHandlers.forEach { it.run { this@Ksoup(this@applyHandlers) } }
        return this
    }

    @PublishedApi
    internal fun Connection.applyOverrides(): Connection {
        requestOverriders.forEach { it.run { this@Ksoup(this@applyOverrides) } }
        return this
    }

    init {
        intrinsics.add { conn ->
            conn.ignoreContentType(true)
            conn.ignoreHttpErrors(true)
        }
        requestOverriders.add{ conn ->
            val applyJumpServerAddress = conn.request().header(JUMPSERVER_HEADER)

            if(applyJumpServerAddress != null && applyJumpServerAddress.isNotEmpty()){
                val original = conn.request().url().toString()
                conn.header(JUMPSERVER_HEADER,original)
                conn.request().url(URL(applyJumpServerAddress.replace("jumpserver://","http://")))
            }
        }
        responseHandlers.add { resp ->
            if (resp.statusCode() == JUMPSERVER_ERROR_STATUS_CODE) {
                throw SocketException("Jump Server Error" + resp.body())
            }
        }
    }

    suspend inline fun get(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(Connection.Method.GET) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun request(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun post(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(Connection.Method.POST) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun put(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(Connection.Method.PUT) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun delete(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(Connection.Method.DELETE) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun options(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(Connection.Method.OPTIONS) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    companion object {
        private const val HEADER_WITH_LOG = "WITH_LOG"

        fun Connection.withLog(): Connection {
            return header(HEADER_WITH_LOG, "true")
        }

        @PublishedApi
        internal val threadPool = ThreadPoolExecutor(10, 1000, 60L, TimeUnit.SECONDS, SynchronousQueue())
    }

    @PublishedApi
    internal suspend fun Connection.executeSuspend(pool: ThreadPoolExecutor = threadPool, log: Boolean = true): Connection.Response {
        val connection = this

        fun Map<String, String>.headersToString(): String {
            return entries.joinToString("\n") { "${it.key}=${it.value}" }
        }

        if (log) {
            /*
            logger.info {
                "${request.method().name.toUpperCase()} ${request.url()}\n\n${request.headers().headersToString()}\n${
                    request.cookies().headersToString()
                }\n\n${request.requestBody()}"
            }
             */
        }

        val resp: Connection.Response = suspendCancellableCoroutine { cont ->
            val future = pool.submit {
                cont.resumeWith(kotlin.runCatching { connection.execute() })
            }
            cont.invokeOnCancellation {
                kotlin.runCatching {
                    future.cancel(true)
                }.exceptionOrNull()?.printStackTrace()
            }
        }


        if (log) {
            /*
            logger.info {
                "RESPONSE RAW: ${resp.body()}\n" //+
                //  "\n" +
                //  resp.headers().headersToString()
            }
            */
        }
        return resp.applyHandlers()
    }
}

/**
 * 简单模拟为Chrome访问
 * 增加了userAgents 自动记忆cookies
 */
open class MockChromeClient : Ksoup() {
    open val cookies = mutableMapOf<String,MutableMap<String,String>>()

    /**
     * Site => Cookies
     */

    open val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36"

    init {
        addResponseHandler{
            cookies.putIfAbsent(it.url().host, mutableMapOf())
            cookies[it.url().host]!!.putAll(it.cookies())
        }
        addIntrinsic{
            it.cookies(cookies.getOrDefault(it.request().url().host, emptyMap<String,String>()))
            it.userAgent(userAgent)
            it.header("sec-ch-ua","\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"91\", \"Chromium\";v=\"91\"")
            it.header("sec-ch-ua-mobile","?0")
            it.header("Sec-Fetch-Dest","empty")
            it.header("Sec-Fetch-Mode","cors")
            it.header("Sec-Fetch-Site","same-origin")
        }
    }
}


