package net.mamoe.sms

import APIKEY_SMS
import JOBID_SMS
import Ksoup
import PASSWORD_SMS
import kotlinx.coroutines.time.delay
import java.net.SocketException
import java.time.Duration
import java.util.regex.Pattern

interface SMSService{
    suspend fun getPhone():Phone
    companion object{
       val DEFAULT:SMSService = WhiteHorseSMSService
    }
}

data class Code(
    val code:String?,
    val received:Boolean,
)

interface Phone{
    val number:String
    suspend fun release()
    suspend fun block()
    suspend fun getCode():Code

    suspend fun waitCode():Code
}


object WhiteHorseSMSService:SMSService{
    val base = "http://uewttlc.cn:81/api/do.php"

    val client = Ksoup().apply {
        addIntrinsic{
            println("[WH] -> Connect " + it.request().url())
            it.timeout(50000)
        }
        addResponseHandler{
            println("[WH] <-  Status " + it.statusCode() + " " + it.statusMessage())
            if(it.body().contains("<!DOCTYPE html>")){
                println("[WH] <- Receive HTML")
            }else {
                println("[WH] <- Receive " + it.body())
            }
        }
    }

    private var token:String? = null


    override suspend fun getPhone(): Phone {
        if(token == null){
            while (true) {
                try {
                    token = client.get(base) {
                        data("action", "loginIn")
                        data("password", PASSWORD_SMS)
                        data("name", APIKEY_SMS)
                    }.body().split("|").run {
                        if (this.size != 2) {
                            error("Failed to login")
                        }
                        if (this[0].trim() != "1") {
                            error("Error with API key SMS")
                        }
                        this[1].trim()
                    }

                    client.get(base) {
                        data("action", "cancelAllRecv")
                        data("token", token)
                    }
                    break
                }catch (e:SocketException){
                    delay(Duration.ofMillis(3000))
                }
            }
        }

        while (true) {
            try {
                client.get(base) {
                    data("action", "getPhone")
                    data("token", token)
                    data("sid", JOBID_SMS)
                }.body().split("|").run {
                    if (this[0].trim() != "1") {
                        error(this[1])
                    }
                    return WhiteHorsePhone(this[1])
                }
            }catch (e:SocketException){
                delay(Duration.ofMillis(3000))
            }
        }
    }

    class WhiteHorsePhone(override val number:String):Phone {
        override suspend fun release() {
            TODO("Not yet implemented")
        }

        override suspend fun block() {
            TODO("Not yet implemented")
        }

        override suspend fun getCode(): Code {
            while (true) {
                try {
                    client.get(base) {
                        data("action", "getMessage")
                        data("token", token)
                        data("sid", JOBID_SMS)
                        data("phone", number)
                    }.body().split("|").run {
                        if (this.size != 2) {
                            error("Failed to parse")
                        }
                        if (this[0].trim() != "1") {
                            return Code(null, false)
                        }
                        val r = Regex("\\d{6}")
                        val item = this[1]
                        val re = r.find(item) ?: error("No Code Found")
                        if (re.groups.isEmpty()) {
                            error("No Code Found[1]")
                        }
                        return Code(re.groups[0]!!.value, true)
                    }
                }catch (e:SocketException){
                    delay(Duration.ofMillis(3000))
                }
            }
        }


        override suspend fun waitCode(): Code {
            while (true){
                val code = getCode()
                if(!code.received){
                    delay(Duration.ofMillis(3000))
                    continue
                }
                return code
            }
        }

    }
}

suspend fun main(){


    val pattern = Regex("\\d{6}")
    val matches = pattern.find("aaaaaaaa000000bbbbb")
    if(matches!=null){
        println(matches.groups[0]!!.value)
    }

}