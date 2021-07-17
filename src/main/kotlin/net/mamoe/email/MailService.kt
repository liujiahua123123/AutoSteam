package net.mamoe.email

import Ksoup
import kotlinx.coroutines.time.delay
import kotlinx.serialization.Serializable
import net.mamoe.decode
import networkRetry
import java.time.Duration

interface MailService {

    suspend fun verifyRegister(address:String):String//return verification link

    suspend fun nextMailAddress():String

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
}
