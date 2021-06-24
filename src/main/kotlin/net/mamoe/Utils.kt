@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalContracts::class)
package net.mamoe

import io.ktor.http.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import org.jsoup.Connection
import org.jsoup.Connection.Method.*
import org.jsoup.Jsoup
import java.net.*
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

open class Ksoup(

){
    internal val intrinsics = mutableListOf<IntrinsicHandler>()
    internal val responseHandler = mutableListOf<ResponseHandler>()

    fun interface IntrinsicHandler {
        operator fun Ksoup.invoke(p2: Connection)
    }

    fun interface ResponseHandler {
        operator fun Ksoup.invoke(p2: Connection.Response)
    }

    fun addIntrinsic(block: IntrinsicHandler) {
        intrinsics.add(block)
    }

    fun addResponseHandler(block: ResponseHandler){
        responseHandler.add(block)
    }

    @PublishedApi
    internal fun Connection.applyIntrinsics(): Connection {
        intrinsics.forEach { it.run { this@Ksoup(this@applyIntrinsics) } }
        return this
    }

    @PublishedApi
    internal fun Connection.Response.applyHandlers(): Connection.Response{
        responseHandler.forEach { it.run { this@Ksoup(this@applyHandlers) } }
        return this
    }

    init {
        intrinsics.add { conn ->
            conn.ignoreContentType(true)
            conn.ignoreHttpErrors(true)
        }
    }

    suspend inline fun get(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(GET) }.applyIntrinsics().apply(block).executeSuspend(threadPool)
    }

    suspend inline fun request(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).applyIntrinsics().apply(block).executeSuspend(threadPool)
    }

    suspend inline fun post(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(POST) }.applyIntrinsics().apply(block).executeSuspend(threadPool)
    }

    suspend inline fun put(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(PUT) }.applyIntrinsics().apply(block).executeSuspend(threadPool)
    }

    suspend inline fun delete(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(DELETE) }.applyIntrinsics().apply(block).executeSuspend(threadPool)
    }

    suspend inline fun options(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(OPTIONS) }.applyIntrinsics().apply(block).executeSuspend(threadPool)
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
    open val cookies = mutableMapOf<String,String>()

    init {
        addResponseHandler{
            cookies.putAll(it.cookies())
        }
        addIntrinsic{
            it.cookies(cookies)
            it.userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36")
            it.header("sec-ch-ua","\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"91\", \"Chromium\";v=\"91\"")
            it.header("sec-ch-ua-mobile:","?0")
            it.header("Sec-Fetch-Dest","empty")
            it.header("Sec-Fetch-Mode","cors")
            it.header("Sec-Fetch-Site","same-origin")
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

    suspend inline fun ajax(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return Jsoup.connect(url).apply { method(POST) }.apply {
            data("count",nextAjaxCount())
            header("X-Requested-With","XMLHttpRequest")
            header("X-Prototype-Version","1.7")
            header("accept","text/javascript, text/html, application/xml, text/xml, */*")
            header("Accept-Encoding","gzip, deflate, br")
            header("Accept-Language","zh-CN,zh;q=0.9")
            header("Cache-Control","no-cache")
        }.applyIntrinsics().apply(block).executeSuspend(threadPool)
    }
}


/**
 * Steam Store 特制的Client, 自动添加headers
 */
open class SteamStoreClient: SteamClient(){

    val referer = "https://store.steampowered.com"

    init {
        addIntrinsic{
            it.header("Host","store.steampowered.com")
            it.header("Origin","https://store.steampowered.com")
            it.header("Referer",referer)
        }
    }
}
