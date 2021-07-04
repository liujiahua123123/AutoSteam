package myproxy

import JUMPSERVER_HEADER
import Ksoup
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.AccessController
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

                            val ksoup = Ksoup()

                            kotlin.runCatching {
                                ksoup.request(target){
                                    request.cookies.rawCookies.forEach { (key, value_) ->
                                        val value = decodeCookieValue(value_,CookieEncoding.URI_ENCODING)
                                        //println("Cookie $key = $value")
                                        this.cookie(key,value)
                                    }
                                    request.headers.forEach { headerKey, headerSplitValue ->
                                        if(
                                            headerKey != JUMPSERVER_HEADER &&
                                            !headerKey.equals("content-length",ignoreCase = true) &&
                                            !headerKey.equals("host",ignoreCase = true)
                                        ) {
                                            //println("Header " +headerKey + " = " +  headerSplitValue.joinToString(" "))
                                            this.header(headerKey, headerSplitValue.joinToString(" "))
                                        }
                                    }

                                    val body = withContext(Dispatchers.IO) {
                                        val text = String(call.receiveStream().readBytes(), Charsets.UTF_8)
                                        ksoupJson.decodeFromString<PortableRequest>(text)
                                    }

                                    this.request().applyPortable(body)
                                }

                            }.fold(
                                onSuccess = { result ->
                                    result.cookies().forEach { (k, v) ->
                                        //println("Return Cookie " + k + " = " + v)
                                        call.response.cookies.append(
                                            Cookie(k,v)
                                        )
                                    }
                                    result.headers().forEach { k, v ->
                                        if(!k.equals("content-length",ignoreCase = true) &&
                                            !k.equals("content-type",ignoreCase = true) &&
                                            !k.equals("Content-Encoding",ignoreCase = true)
                                        ){
                                            //println("Return Header " + k + " = " + v)
                                            call.response.header(k, v)
                                        }
                                    }
                                    call.respond(
                                        object : OutgoingContent.ByteArrayContent() {
                                            override val contentType: ContentType get() = ContentType.parse(result.contentType())
                                            override val status: HttpStatusCode get() = HttpStatusCode(result.statusCode(),result.statusMessage())
                                            override fun bytes() = result.bodyAsBytes()
                                        }
                                    )
                                },
                                onFailure = { e ->
                                    call.respond(
                                        HttpStatusCode(881, "Request error"),
                                        e.toString()
                                    )
                                    e.printStackTrace()
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