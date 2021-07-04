package myproxy

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
import kotlinx.coroutines.runBlocking
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

                            println(target)
                            request.headers.forEach { s, list ->
                                println(s)
                                println(list)
                            }

                            kotlin.runCatching {
                                /*
                                client.request<HttpResponse> {
                                    url(target)
                                    method = request.httpMethod
                                    headers.appendAll(request.headers.filter { s, s2 ->
                                        //!s.equals("Host", ignoreCase = true) &&
                                        !s.equals("jumpserver-target", ignoreCase = true) &&
                                        !s.equals("Content-Type",ignoreCase = true)
                                    })
                                    body = object : OutgoingContent.ReadChannelContent() {
                                        override fun readFrom(): ByteReadChannel = request.receiveChannel()
                                        override val contentType: ContentType get() = request.contentType()
                                    }
                                }

                                 */

                                val client =  HttpClient.newHttpClient()
                                val x = client.send<String>(HttpRequest
                                    .newBuilder(URI(target))
                                    .method(request.httpMethod.value,HttpRequest.BodyPublishers.ofInputStream {
                                        request.receiveChannel().toInputStream()
                                    })
                                    .apply {
                                        request.headers.forEach{ s, s2 ->
                                            //!s.equals("Host", ignoreCase = true) &&
                                            if(
                                                !s.equals("jumpserver-target", ignoreCase = true) &&
                                                !s.equals("Content-Type",ignoreCase = true)
                                            ){
                                                header(s,s2.joinToString(" "))
                                            }
                                        }
                                    }
                                    .build()
                                    ,HttpResponse.BodyHandlers.ofString())
                                println(x)

                            }.fold(
                                onSuccess = { result ->
                                    /*
                                    call.respond(
                                        object : OutgoingContent.ReadChannelContent() {
                                            override fun readFrom(): ByteReadChannel = result.content
                                            override val contentType: ContentType? get() = result.contentType()
                                            override val status: HttpStatusCode get() = result.status
                                        }
                                    )

                                     */
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