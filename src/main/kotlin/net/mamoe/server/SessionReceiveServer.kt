package net.mamoe.server

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
import kotlinx.serialization.Serializable
import net.mamoe.email.MailService
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import registerSimple

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
        }
        module {
            routing {
               get("/new"){
                   val address = call.parameters.getOrFail("address")
                   val session = call.parameters.getOrFail("session")
                    GlobalScope.launch {
                        registerSimple(session,address)
                    }
                    call.respond("Ok")
                }
                get("/email"){
                    call.respond(MailService.DEFAULT.nextMailAddress())
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