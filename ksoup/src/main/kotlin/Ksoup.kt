@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalContracts::class)

import io.ktor.http.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.helper.HttpConnection
import java.io.File
import java.io.InputStream
import java.net.SocketException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.crypto.dsig.keyinfo.KeyValue
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
const val JUMPFOR_HEADER = "jumpserver-for"
const val JUMPSERVER_ERROR_STATUS_CODE = 881


fun Connection.Request.jumpServer(ip:String, port:Short) {
    header(JUMPSERVER_HEADER, "jumpserver://$ip:$port")
}

fun Connection.jumpServer(ip:String, port:Short) {
    header(JUMPSERVER_HEADER, "jumpserver://$ip:$port")
}

fun Connection.jumpServer(spec:String){
    if(!spec.startsWith("jumpserver://")){
        throw IllegalArgumentException("jumpserver address should start with jumpserver://")
    }
    header(JUMPSERVER_HEADER, spec)
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
                val request = conn.request()
                conn.header(JUMPSERVER_HEADER,original)

                //redirect to jump server
                request.url(URL(applyJumpServerAddress.replace("jumpserver://","http://")))
                val jumpData = ksoupJson.encodeToString(request.toPortable())

                //changed to a jump server body
                request.data().clear()
                request.method(Connection.Method.POST)
                request.requestBody(jumpData)
            }
        }
        responseHandlers.add { resp ->
            if (resp.statusCode() == JUMPSERVER_ERROR_STATUS_CODE) {
                throw SocketException("Jump Server Error \n" + resp.body())
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
        fun fixJava(){
            System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection,content-length,expect,host,upgrade")

            //proxy
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
            System.setProperty("jdk.http.auth.proxying.disabledSchemes", "")
            //Authenticator.setDefault(ProxyAuthenticator)

            //ssl
            System.setProperty("https.protocols", "TLSv1.2")
            System.setProperty("jdk.tls.client.protocols", "TLSv1.2")

            val context: SSLContext = SSLContext.getInstance("TLS")
            val trustManagerArray: Array<TrustManager> = arrayOf(object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {}

                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {}

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            })
            context.init(null, trustManagerArray, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        }

        init {
            fixJava()
        }

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
            val uri:String = if(it.header(JUMPFOR_HEADER)!=null) {
                val dis = it.header(JUMPFOR_HEADER)
                println(dis)
                URL(dis).host
            }else{
                it.url().host
            }
            cookies.putIfAbsent(uri, mutableMapOf())
            cookies[uri]!!.putAll(it.cookies())
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

@Serializable
data class PortableRequest(
    val requestBody:String?,
    val requestData:List<PortableKeyVal>,
    val postCharset:String,
    val maxBodySize:Int,
    val method:Connection.Method,
    val timeout:Int
){
    companion object{
        @Serializable
        data class PortableKeyVal(
            val key: String,
            val value: String?,
            val inputStream: ByteArray?,
            val contentType: String?
        )
    }
}

fun Connection.Request.toPortable():PortableRequest{
    return PortableRequest(
        requestBody = this.requestBody(),
        maxBodySize = this.maxBodySize(),
        postCharset = this.postDataCharset(),
        requestData = this.data().map {
            PortableRequest.Companion.PortableKeyVal(it.key(),it.value(), it.inputStream()?.readAllBytes(),it.contentType())
        },
        method = this.method(),
        timeout = this.timeout()
    )
}

fun Connection.applyPortable(portableRequest: PortableRequest){
    this.maxBodySize(portableRequest.maxBodySize)
    if(portableRequest.requestBody!=null) {
        println("The request body " + portableRequest.requestBody)
        this.requestBody(portableRequest.requestBody)
    }
    this.postDataCharset(portableRequest.postCharset)
    this.method(portableRequest.method)
    this.timeout(portableRequest.timeout)

    portableRequest.requestData.forEach {
        if(it.inputStream!=null) {
            println("Write InputStream Body")
            data(it.key, it.value,it.inputStream.inputStream())
            //data("avatar","MyAva.jpg",File(System.getProperty("user.dir") + "/BlackHandVector.jpg").readBytes().inputStream())
        }else{
            this.data(it.key, it.value)
        }
    }
}
