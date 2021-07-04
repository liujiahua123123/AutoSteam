package myproxy

import JUMPFOR_HEADER
import JUMPSERVER_HEADER
import Ksoup
import PortableRequest
import applyPortable
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import ksoupJson
import java.io.File
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

                            println("Received a request for" + target)

                            doReqKtor(target, request)
                        }
                    }
                }
                install(CallLogging)
            }
        }) {
        }.start(true)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.doReqKsoup(
        target: String,
        request: ApplicationRequest
    ) {
        kotlin.runCatching {
            val ksoup = Ksoup()
            ksoup.request(target) {
                request.cookies.rawCookies.forEach { (key, value_) ->
                    val value = decodeCookieValue(value_, CookieEncoding.URI_ENCODING)
                    println("Cookie $key = $value")
                    this.cookie(key, value)
                }
                request.headers.forEach { headerKey, headerSplitValue ->
                    if (
                        !headerKey.equals(JUMPSERVER_HEADER, ignoreCase = true) &&
                        !headerKey.equals("content-length", ignoreCase = true) &&
                        !headerKey.equals("host", ignoreCase = true)
                    ) {
                        println("Header " + headerKey + " = " + headerSplitValue.joinToString(" "))
                        this.header(headerKey, headerSplitValue.joinToString(" "))
                    }
                }

                val body = withContext(Dispatchers.IO) {
                    val text = String(call.receiveStream().readBytes(), Charsets.UTF_8)
                    ksoupJson.decodeFromString<PortableRequest>(text)
                }

                this.applyPortable(body)
                if (target == "https://steamcommunity.com/actions/FileUploader") {
                    println("I write addtional")
                    data(
                        "avatar",
                        "MyAva.jpg",
                        File(System.getProperty("user.dir") + "/BlackHandVector.jpg").readBytes()
                            .inputStream()
                    )
                }
                maxBodySize(1200000)

                println("Sending Request")
                this.request().data().forEach {
                    println("key " + it.key())
                    println("v " + it.value())
                    println("a " + it.inputStream())
                }
            }
        }.fold(
            onSuccess = { result ->
                println("success result")
                result.cookies().forEach { (k, v) ->
                    println("Return Cookie " + k + " = " + v)
                    call.response.cookies.append(
                        Cookie(k, v)
                    )
                }
                result.headers().forEach { (k, v) ->
                    if (!k.equals("content-length", ignoreCase = true) &&
                        !k.equals("content-type", ignoreCase = true) &&
                        !k.equals("Content-Encoding", ignoreCase = true)
                    ) {
                        println("Return Header " + k + " = " + v)
                        call.response.header(k, v)
                    }
                }
                call.response.header(JUMPFOR_HEADER, target)
                call.respond(
                    object : OutgoingContent.ByteArrayContent() {
                        override val contentType: ContentType get() = ContentType.parse(result.contentType())
                        override val status: HttpStatusCode
                            get() = HttpStatusCode(
                                result.statusCode(),
                                result.statusMessage()
                            )

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

    private suspend fun PipelineContext<Unit, ApplicationCall>.doReqKtor(
        target: String,
        request: ApplicationRequest
    ) {
        kotlin.runCatching {

            val cookies = AcceptAllCookiesStorage()
            HttpClient(OkHttp) {
                install(HttpCookies) {
                    storage = cookies
                }
            }.use { client ->
                val url = Url(target)

                client.request<HttpResponse>(url) {
                    headers.appendAll(request.headers)
                    request.cookies.rawCookies.forEach { (t, u) ->
                        cookie(t, decodeCookieValue(u, CookieEncoding.URI_ENCODING))
                    }

                    val portableRequest = withContext(Dispatchers.IO) {
                        val text = String(call.receiveStream().readBytes(), Charsets.UTF_8)
                        ksoupJson.decodeFromString<PortableRequest>(text)
                    }

                    if (portableRequest.requestBody != null) {
                        body = TextContent(portableRequest.requestBody!!, ContentType.Any)
                    } else if (portableRequest.requestData.isNotEmpty()) {
                        body = MultiPartFormDataContent(portableRequest.requestData.map {
                            if (it.inputStream != null) {
                                val stream = it.inputStream!!.inputStream().asInput()
                                if (it.value != null) {
                                    PartData.FileItem(
                                        { stream },
                                        { stream.close() },
                                        Headers.build {
                                            append(
                                                HttpHeaders.ContentDisposition,
                                                ContentDisposition.File.withParameter(
                                                    ContentDisposition.Parameters.FileName,
                                                    it.value!!
                                                )
                                            )
                                        })
                                } else {
                                    PartData.BinaryItem(
                                        { stream },
                                        { stream.close() },
                                        Headers.Empty
                                    )
                                }
                            } else {
                                PartData.FormItem(it.value!!, {}, Headers.Empty)
                            }
                        })
                    }
                }
            }
        }.fold(
            onSuccess = { result ->
                println("success result")
                result.setCookie().forEach { (k, v) ->
                    println("Return Cookie $k = $v")
                    call.response.cookies.append(Cookie(k, v))
                }
                result.headers.forEach { k, v ->
                    if (!k.equals("content-length", ignoreCase = true) &&
                        !k.equals("content-type", ignoreCase = true) &&
                        !k.equals("Content-Encoding", ignoreCase = true)
                    ) {
                        println("Return Header " + k + " = " + v)
                        v.forEach {
                            call.response.header(k, it)
                        }
                    }
                }
                call.response.header(JUMPFOR_HEADER, target)
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentType: ContentType get() = result.contentType() ?: ContentType.Any
                        override val status: HttpStatusCode
                            get() = result.status

                        override fun readFrom(): ByteReadChannel = result.content
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