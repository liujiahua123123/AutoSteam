package myproxy

import JUMPFOR_HEADER
import JUMPSERVER_HEADER
import Ksoup
import PortableRequest
import applyPortable
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
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
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import ksoupJson
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
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
                            //println("Received a request for" + target)

                            doReqKsoup(target, request)
//                            doReqKtor(target, request)
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
                val cookie = mutableMapOf<String,String>()
                request.cookies.rawCookies.forEach { (key, value_) ->
                    val value = decodeCookieValue(value_, CookieEncoding.URI_ENCODING)
                    //println("Cookie $key = $value")
                    this.cookie(key, value)
                    cookie.put(key,value)
                }
                val body = withContext(Dispatchers.IO) {
                    val text = String(call.receiveStream().readBytes(), Charsets.UTF_8)
                    ksoupJson.decodeFromString<PortableRequest>(text)
                }
                this.applyPortable(body)

                request.headers.forEach { headerKey, headerSplitValue ->
                    if (
                        !headerKey.equals(JUMPSERVER_HEADER, ignoreCase = true) &&
                        !headerKey.equals("content-length", ignoreCase = true) &&
                        !headerKey.equals("host", ignoreCase = true)
                    ) {
                       // println("Header " + headerKey + " = " + headerSplitValue.joinToString(" "))
                        if(headerKey.equals("content-type", ignoreCase = true) && headerSplitValue.joinToString(" ").contains("x-www-form-urlencoded")){
                            //need to be removed for building the form properly
                        }else{
                            this.header(headerKey, headerSplitValue.joinToString(" "))
                        }
                    }
                }

               // println("Sending Request")
               // this.request().data().forEach {
                    //println("key " + it.key())
                    //println("v " + it.value())
                   // println("a " + it.inputStream())
                //}
            }
        }.fold(
            onSuccess = { result ->
                //println("success result")
                println("successfully jumped request for $target")
                result.cookies().forEach { (k, v) ->
                    //println("Return Cookie " + k + " = " + v)
                    call.response.cookies.append(
                        Cookie(k, v)
                    )
                }
                result.headers().forEach { (k, v) ->
                    if (!k.equals("content-length", ignoreCase = true) &&
                        !k.equals("content-type", ignoreCase = true) &&
                        !k.equals("Content-Encoding", ignoreCase = true)
                    ) {
                        //println("Return Header " + k + " = " + v)
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
               // println("error result")
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
                engine {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 7890))
                }
                install(HttpCookies) {
                    storage = cookies
                }
            }.use { client ->
                val url = Url(target)

                client.request<HttpResponse>(url) {

                    request.headers.forEach { headerKey, headerSplitValue ->
                        if (
                            !headerKey.equals(JUMPSERVER_HEADER, ignoreCase = true) &&
                            !headerKey.equals("content-length", ignoreCase = true) &&
                            !headerKey.equals("host", ignoreCase = true) &&
                            !headerKey.equals("content-type", ignoreCase = true)
                        ) {
                            println("Header " + headerKey + " = " + headerSplitValue.joinToString(" "))
                            header(headerKey, headerSplitValue.joinToString(" "))
                        }
                    }

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
                                                ).withParameter(
                                                    ContentDisposition.Parameters.Name,
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
                kotlin.runCatching {
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
                    val resultBody = result.receive<String>()
                    println(resultBody)

                    call.respond(
                        HttpStatusCode.OK,
                        resultBody
//                    object : OutgoingContent.ReadChannelContent() {
//                        override val contentType: ContentType get() = result.contentType() ?: ContentType.Any
//                        override val status: HttpStatusCode
//                            get() = result.status
//
//                        override fun readFrom(): ByteReadChannel = result.content
//                    }
                    )
                }.onFailure {
                    println("Failed to give result back")
                    it.printStackTrace()
                }
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