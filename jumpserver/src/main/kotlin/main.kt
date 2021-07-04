package myproxy

import JUMPFOR_HEADER
import JUMPSERVER_HEADER
import Ksoup
import MockChromeClient
import PortableRequest
import applyPortable
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import ksoupJson
import okhttp3.Dispatcher
import org.jsoup.Connection
import toPortable
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.AccessController
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        fixJava()
        //val client = HttpClient()



        embeddedServer(Netty, environment = applicationEngineEnvironment {
            connector {
                port = 8188
            }
            module {
                routing {
                    route("/") {
                        handle {
                            val target = call.request.headers["jumpserver-target"] ?: kotlin.run {
                                call.respond(HttpStatusCode.BadRequest)
                                return@handle
                            }

                            val request = call.request

                            println("Received a request for" + target )
                            val ksoup = SteamStoreClient().apply {
                                referer = "https://store.steampowered.com/join/"
                                addIntrinsic {
                                    println("[NETWORK] -> Connect " + it.request().url())
                                }
                                addResponseHandler {
                                    println("[NETWORK] <-  Status " + it.statusCode() + " " + it.statusMessage())
                                    if (it.body().contains("<!DOCTYPE html>")) {
                                        println("[NETWORK] <- Receive HTML")
                                    } else {
                                        println("[NETWORK] <- Receive " + it.body())
                                    }
                                }
                            }


                            kotlin.runCatching {
                                try {
                                    ksoup.request(target) {
                                        request.cookies.rawCookies.forEach { (key, value_) ->
                                            val value = decodeCookieValue(value_, CookieEncoding.URI_ENCODING)
                                            println("Cookie $key = $value")
                                            this.cookie(key, value)
                                        }
                                        request.headers.forEach { headerKey, headerSplitValue ->
                                            if (
                                                !headerKey.equals(JUMPSERVER_HEADER,ignoreCase = true) &&
                                                !headerKey.equals("content-length", ignoreCase = true) &&
                                                !headerKey.equals("host", ignoreCase = true)
                                            ) {
                                                println("Header " +headerKey + " = " +  headerSplitValue.joinToString(" "))
                                                this.header(headerKey, headerSplitValue.joinToString(" "))
                                            }
                                        }

                                        val body = withContext(Dispatchers.IO) {
                                            val text = String(call.receiveStream().readBytes(), Charsets.UTF_8)
                                            ksoupJson.decodeFromString<PortableRequest>(text)
                                        }

                                        this.applyPortable(body)

                                        if(body.method!=Connection.Method.GET) {
                                            data(
                                                "avatar",
                                                "MyAva.jpg",
                                                File(System.getProperty("user.dir") + "/BlackHandVector.jpg").readBytes()
                                                    .inputStream()
                                            )
                                        }

                                        println("From: " + body)
                                        println("To: " + this.request().toPortable())


                                        println("Sending Request")
                                        this.request().data().forEach {
                                            println("key " + it.key())
                                            println("v " + it.value())
                                            println("a " + it.inputStream())
                                        }
                                    }
                                }catch (e:Exception){
                                    e.printStackTrace()
                                    error(e)
                                }
                            }.fold(
                                onSuccess = { result ->
                                    println("success result")
                                    result.cookies().forEach { (k, v) ->
                                        println("Return Cookie " + k + " = " + v)
                                        call.response.cookies.append(
                                            Cookie(k,v)
                                        )
                                    }
                                    result.headers().forEach { (k, v) ->
                                        if(!k.equals("content-length",ignoreCase = true) &&
                                            !k.equals("content-type",ignoreCase = true) &&
                                            !k.equals("Content-Encoding",ignoreCase = true)
                                        ){
                                            println("Return Header " + k + " = " + v)
                                            call.response.header(k, v)
                                        }
                                    }
                                    call.response.header(JUMPFOR_HEADER,target)
                                    call.respond(
                                        object : OutgoingContent.ByteArrayContent() {
                                            override val contentType: ContentType get() = ContentType.parse(result.contentType())
                                            override val status: HttpStatusCode get() = HttpStatusCode(result.statusCode(),result.statusMessage())
                                            override fun bytes() = result.bodyAsBytes()
                                        }
                                    )
                                },
                                onFailure = { e ->
                                    println("error result")
                                    e.printStackTrace()
                                    call.respond(
                                        HttpStatusCode(881, "Request error"),
                                        e.toString()
                                    )
                                }
                            )
                        }
                    }
                }
                install(CallLogging)
            }
        }) {
        }.start(true)
    }
}

fun fixJava() {
    //headers: referer
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
        override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    })
    context.init(null, trustManagerArray, SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
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
            it.header("Origin","https://store.steampowered.com")
            it.header("Referer",referer)
        }
    }
}

