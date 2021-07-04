@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalContracts::class)

package net.mamoe

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.jsoup.Connection
import org.jsoup.Connection.Method.*
import org.jsoup.Jsoup
import java.math.BigInteger
import java.net.*
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.*
import javax.crypto.Cipher


interface FormData

@Suppress("UNSAFE_CAST")
fun Connection.data(formData: FormData){
    formData::class.memberProperties.forEach {
        data(it.name, (it as KProperty1<FormData, *>).get(formData).toString())
    }
}

@OptIn(InternalAPI::class)
fun steamPasswordRSA(mod:String, exp:String, password: String):String{

    val keyFactory = KeyFactory.getInstance("RSA")
    var key = keyFactory.generatePublic(
        RSAPublicKeySpec(
        BigInteger(mod,16),
        BigInteger(exp,16),//exp
    )
    )

    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val cipherData = cipher.doFinal(password.toByteArray())

    val b64 = cipherData.encodeBase64()
    return b64

    //return RSA(mod,exp).encrypt(password)
}

val SteamJson = Json {
    this.ignoreUnknownKeys = true
    this.isLenient = true
    this.encodeDefaults = true
    this.ignoreUnknownKeys = true
}

inline fun <reified T : Any> String.deserialize(): T = SteamJson.decodeFromString(this)


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
    header(JUMPSERVER_HEADER, "jumpserver://$ip:$port/request")
}

fun Connection.jumpServer(ip:String, port:Short) {
    header(JUMPSERVER_HEADER, "jumpserver://$ip:$port/request")
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
    internal fun Connection.applyOverrides():Connection{
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
        return Jsoup.connect(url).apply { method(GET) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun request(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun post(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(POST) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun put(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(PUT) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun delete(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(DELETE) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
    }

    suspend inline fun options(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(OPTIONS) }.applyIntrinsics().apply(block).applyOverrides().executeSuspend(threadPool)
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

        val resp:Connection.Response = suspendCancellableCoroutine { cont ->
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
            //it.header("sec-ch-ua","\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"91\", \"Chromium\";v=\"91\"")
            //it.header("sec-ch-ua-mobile","?0")
            //it.header("Sec-Fetch-Dest","empty")
            //it.header("Sec-Fetch-Mode","cors")
            //it.header("Sec-Fetch-Site","same-origin")
        }
    }
}


/**
 * Steam特制的Client, 增加了steam独有的ajax参数
 */
open class SteamClient: MockChromeClient(){
    internal val ajaxCounter = AtomicInteger(1)

    @PublishedApi
    internal fun nextAjaxCount():String = "" + ajaxCounter.addAndGet(1)

    @OptIn(ExperimentalContracts::class)//BUG
    suspend inline fun ajax(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return this.post(url){
            data("count",nextAjaxCount())
            header("X-Requested-With","XMLHttpRequest")
            header("X-Prototype-Version","1.7")
            header("accept","text/javascript, text/html, application/xml, text/xml, */*")
            header("Accept-Encoding","gzip, deflate, br")
            header("Accept-Language","zh-CN,zh;q=0.9")
            header("Cache-Control","no-cache")
            block.invoke(this)
        }
    }
}


/**
 * Steam Store 特制的Client, 自动添加headers
 */
open class SteamStoreClient: SteamClient(){

    var referer = "https://store.steampowered.com"

    init {
        addIntrinsic{
            //it.header("Origin","https://store.steampowered.com")
            //it.header("Referer",referer)
        }
    }
}


/*
/**
 * Code from Github, Java Steam Account Generator
 * Add random to replace constant 0x01
 *
 */
internal class RSA(modHex: String?, expHex: String?) {
    private val modulus: BigInteger
    private val exponent: BigInteger
    private val charset = Charset.forName("ISO-8859-1")

    @OptIn(InternalAPI::class)
    fun encrypt(password: String): String {
        val data = pkcs1pad2(password.toByteArray(charset), modulus.bitLength() + 7 shr 3)
        val d2 = data.modPow(exponent, modulus)
        var dataHex = d2.toString(16)
        if (dataHex.length and 1 == 1) {
            dataHex = "0$dataHex"
        }
        val encrypted = hexStringToByteArray(dataHex)
        return encrypted.encodeBase64()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun pkcs1pad2(data: ByteArray, n: Int): BigInteger {
        var n = n
        val bytes = ByteArray(n)
        var i = data.size - 1
        while (i >= 0 && n > 11) {
            bytes[--n] = data[i--]
        }
        bytes[--n] = 0
        while (n > 2) {
            bytes[--n] = Random.Default.nextBytes(1)[0]
        }
        bytes[--n] = 0x2
        bytes[--n] = 0
        return BigInteger(bytes)
    }

    init {
        modulus = BigInteger(modHex, 16)
        exponent = BigInteger(expHex, 16)
    }
}

 */


