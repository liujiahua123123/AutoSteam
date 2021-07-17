package net.mamoe.email

import Ksoup
import kotlinx.coroutines.time.delay
import kotlinx.serialization.Serializable
import net.mamoe.decode
import net.mamoe.step.Email2FA
import networkRetry
import java.time.Duration

interface MailService {

    suspend fun verifyRegister(address:String):String//return verification link

    suspend fun nextMailAddress():String

    suspend fun get2FA(address: String):String

    companion object{
        val DEFAULT: MailService = MyMailServer
    }

}




object MyMailServer: MailService{

    suspend fun randomId():Identity = client.get("$BASE/identity?country=CN").decode<Identity>()

    @Serializable
    data class StorableEmail(
        val id: String,//uuid
        val title: String,
        val from: String,
        val to: List<String>,//this include (cc + bcc + to)
        val content:String,
        val remoteAddress: String,
    )

    @Serializable
    data class GetEmailResponse(
        val success: Boolean,
        val email: StorableEmail?
    )

    @Serializable
    enum class Gender{
        FEMALE,
        MALE,
        OTHER,
        UNKNOWN
    }

    @Serializable
    data class Identity(
        val name: String,
        val gender: Gender,
        val country: String,
        val age: Int,
        val credentialsName: String,
        val credentialsValue: String,
        val tempEmailAddress: String
    )




    const val BASE = "http://107.172.156.153:9661"
    val client = Ksoup().apply {
        addIntrinsic{
            it.data("from","AutoSteam")
            println("[MAIL] -> Connect " + it.request().url())
            //println("[MAIL] ->    Send " + it.request().requestBody())
            it.networkRetry(10)
        }
        addResponseHandler{
            //println("[MAIL] <-  Status " + it.statusCode() + " " + it.statusMessage())
        }
    }


    override suspend fun verifyRegister(address: String):String {
        while (true) {
            val response = client
                .get("$BASE/receive?address=$address")
                .decode<GetEmailResponse>()
            if(!response.success || response.email == null){
                delay(Duration.ofMillis(3000))
                continue
            }
            val content = response.email.content
            val par = content
                .substringAfter("<a href=\"https://store.steampowered.com/account/newaccountverification?")
                .substringBefore("\" target=\"_blank\"")

            if(par.isEmpty()){
                error("Failed to identify stoken in the email")
            }

            return "https://store.steampowered.com/account/newaccountverification?$par"
        }
    }

    override suspend fun nextMailAddress(): String {
        return client.get("$BASE/identity").decode<Identity>().tempEmailAddress
    }

    override suspend fun get2FA(address: String): String {
        while (true) {
            val response = client
                .get("$BASE/receive?address=$address")
                .decode<GetEmailResponse>()
            if(!response.success || response.email == null){
                delay(Duration.ofMillis(3000))
                continue
            }
            val content = response.email.content
            return content.substringAfter("<td class=\"title-48 c-blue1 fw-b a-center\" style=\"font-size:48px; line-height:52px; font-family:'Motiva Sans', Helvetica, Arial, sans-serif; color:#3a9aed; font-weight:bold; text-align:center;\">").substringBefore("</td>").trim()
        }
    }
}


suspend fun main(){
    val x = MailService.DEFAULT.get2FA("zcx19850505@loveloli.store")
    println(x)
}