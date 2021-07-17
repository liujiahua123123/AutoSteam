package accountjar

import SteamAccount
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ksoupJson
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import java.io.File
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap

private val APIKEY = listOf("NaturalHG390831","cc551021asd13579asd","bf60d285-2d6b-4827-b79a-403a1baa97ac","d2c8c15b-9ec1-4162-8456-5bb59e1431e1")

class UserError(override val message: String): Exception()


fun ApplicationCall.checkAPIKey(){

    if (System.getProperty("user.dir").contains("jiahua")) {
        return
    }

    fun handle(): Boolean {
        val key = this.request.header("APIKEY") ?: this.parameters["APIKEY"] ?: return false
        return APIKEY.contains(key)
    }

    if (!handle()) {
        throw UserError("Permission Denied")
    }
}

fun ApplicationCall.readRequirement(): SqlExpressionBuilder.() -> Op<Boolean> {
    try {
        val profileRequirement = this.parameters["profile"]?.run { Profile.valueOf(this) }

        val cnAuthRequirement = this.parameters["chinaAuth"]?.toBoolean()


        if (profileRequirement == null && cnAuthRequirement == null) {
            return { Op.TRUE }
        }
        if (profileRequirement == null && cnAuthRequirement != null) {
            return {
                DBAccounts.cnAuthed eq cnAuthRequirement
            }
        }
        if (profileRequirement != null && cnAuthRequirement == null) {
            return {
                DBAccounts.profile eq profileRequirement
            }
        }
        return {
            DBAccounts.profile eq profileRequirement!!
            DBAccounts.cnAuthed eq cnAuthRequirement!!
        }
    } catch (e: IllegalArgumentException) {
        throw UserError("Illegal Profile Argument")
    }
}

private val countCache = ConcurrentHashMap<String,Long>()

fun Routing.userScope(path: String, method: HttpMethod, body: PipelineInterceptor<Unit, ApplicationCall>){
    this.route(path,method) {
        handle {
            try {
                body.invoke(this,this.subject)
            } catch (e: Exception) {
                if (e is UserError) {
                    call.templateResponse(e.message, false, null)
                }else {
                    e.printStackTrace()
                    call.templateResponse(e.message ?: e::class.toString(), false, null)
                }
            }
        }
    }
}
fun start(){
    embeddedServer(Netty, environment = applicationEngineEnvironment {
        connector {
            port = 9612
        }
        module {
            routing {
                userScope("/get", HttpMethod.Get){
                    call.checkAPIKey()
                    val req = call.readRequirement()
                    val account = Jar.getAccount(req)
                    if (account != null) {
                        call.templateResponse("",true, account)
                    } else {
                        call.templateResponse("No Available Account", false, null)
                    }
                }
                userScope("/pop", HttpMethod.Get){
                    call.checkAPIKey()
                    val req = call.readRequirement()
                    val account = Jar.popAccount(req)
                    if (account != null) {
                        countCache.clear()
                        call.templateResponse("", true, account)
                    } else {
                        call.templateResponse("No Available Account", false, null)
                    }
                }
                userScope("/push", HttpMethod.Post){
                    call.checkAPIKey()

                    val data = call.receivePostData<SteamAccount>()
                    Jar.pushAccount(data)
                    call.templateResponse("", true, "OK")
                    countCache.clear()
                }

                userScope("/count", HttpMethod.Get){
                    call.checkAPIKey()
                    val countReq = call.parameters["profile"]?:"NULL"  + "-" + (call.parameters["chinaAuth"]?:"NULL")
                    val cache = countCache[countReq]
                    if(cache != null){
                        call.templateResponse("Retrieved from cache, key=$countReq",true,cache)
                    }else {
                        val req = call.readRequirement()
                        val count = Jar.countAccounts(req)
                        countCache[countReq] = count
                        call.templateResponse("", true, count)
                    }
                }

                get("/"){
                    call.respondFile(File(System.getProperty("user.dir") + "index.html"))
                }
            }
            install(CallLogging)
        }
    }) {
    }.start(true)
}




suspend inline fun <reified T:Any> ApplicationCall.templateResponse(message: String, success:Boolean, data:T?){
    if(data!=null) {
        this.respond(HttpStatusCode.OK, ksoupJson.encodeToString(TemplateResponse(message, success, data)))
    }else{
        this.respond(HttpStatusCode.OK, ksoupJson.encodeToString(EmptyDataResponse(message, success)))
    }
}


@Serializable
data class TemplateResponse<T:Any>(val message: String, val success:Boolean, val data:T?)

@Serializable
data class EmptyDataResponse(val message: String, val success:Boolean, val data:String? = null)

suspend inline fun <reified T : Any> ApplicationCall.receivePostData(): T {
    return ksoupJson.decodeFromString(this.receiveText())
}
