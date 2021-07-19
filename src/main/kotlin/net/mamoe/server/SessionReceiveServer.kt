package net.mamoe.server

import JumpServerProxyProvider
import MockChromeClient
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.mamoe.email.MailService
import net.mamoe.step.*
import networkRetry
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

object SessionReceiveServer {


    private val server: NettyApplicationEngine = embeddedServer(Netty, environment = applicationEngineEnvironment {
        // this.parentCoroutineContext
        this.connector {
            host = "0.0.0.0"
            port = 9989
            println("Session Receive Server running on http://localhost:9989")
        }
        this.log = LoggerFactory.getLogger("Server")
        module {
            install(CallLogging) {
                level = Level.INFO
            }
            install(CORS) {
                // Access-Control-Allow-Origin
                anyHost()
                method(HttpMethod.Get)
                method(HttpMethod.Put)
                method(HttpMethod.Post)
                method(HttpMethod.Delete)
                method(HttpMethod.Patch)
                allowSameOrigin = true
                for (header in "X-Requested-With,userToken,Content-type,Accept,Version,Timestamp,Platform,Sign".split(',')) {
                    header(header)
                }
            }
        }
        module {
            routing {
               get("newJoinSession/v1"){
                   val add = call.parameters.getOrFail("address")
                   val sessionid = call.parameters.getOrFail("session")
                    GlobalScope.launch {
                        GoogleCapQueue.send(createRegisterComponent(add,sessionid))
                    }
                    call.respond("OK")
                }
                get("/email"){
                    call.respond(MailService.DEFAULT.nextMailAddress())
                }
                get("/cn"){
                    val capTicket = call.parameters.getOrFail("capTicket")
                    val sucCode = call.parameters.getOrFail("secCode")
                    GlobalScope.launch {
                        WanmeiCapQueue.send(WanmeiCaptcha(capTicket,sucCode))
                    }
                }
            }
        }
    })

    var started:Boolean = false

    @Synchronized
    fun start(wait: Boolean = false): NettyApplicationEngine {
        if (started) return server
        started = true
        server.start(wait)
        return server
    }

}