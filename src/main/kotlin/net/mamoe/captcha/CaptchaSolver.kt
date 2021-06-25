package net.mamoe.captcha

import APIKEY_2CAP
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.mamoe.Ksoup
import net.mamoe.SteamStoreClient
import net.mamoe.decode

interface CaptchaSolver{

    suspend fun solve(req: CaptchaSolveRequest):CaptchaSolveResponse

    companion object{
        val DEFAULT = TwoCaptchaSolver
    }
}


data class CaptchaSolveRequest(
    val sitekey:String,
    val s:String,
    val userAgent:String,
    val url:String,
    val cookie: Map<String,String>
)

data class CaptchaSolveResponse(
    val token:String
)


object TwoCaptchaSolver: CaptchaSolver{
    val client = Ksoup().apply {
        addIntrinsic{
            it.data("key",APIKEY_2CAP)
            println("[2CAP] -> Connect " + it.request().url())
            println("[2CAP] ->    Send " + it.request().requestBody())
        }
        addResponseHandler{
            println("[2CAP] <-  Status " + it.statusCode() + " " + it.statusMessage())
            println("[2CAP] <- Receive " + it.body())
        }
    }

    override suspend fun solve(req: CaptchaSolveRequest): CaptchaSolveResponse {
        val r = client.get("http://2captcha.com/in.php"){
            data("method","userrecaptcha")
            data("enterprise","1")
            data("googlekey",req.sitekey)
            data("pageurl",req.url)
            data("data-s", req.s)
            data("userAgent", req.userAgent)
            data("cookies",req.cookie.map { it.key + ":" + it.value }.joinToString(";"))
            data("json","1")
            proxy("127.0.0.1",8888)
        }.decode<CapResponse>()

        if(r.status != 1){
            error("Error in Cap Service, stop")
        }

        val requestId = r.request!!
        println("Solving Recaptcha, workID = $requestId")
        while (true){
            delay(5000)
            val wait = client.get("https://2captcha.com/res.php"){
                data("action","get")
                data("id",requestId)
                data("json","1")
            }.decode<CapResponse>()

            if(wait.status == 1){
                println("Captcha is ready")
                if(wait.request==null){
                    error("Token Not Found")
                }
                return CaptchaSolveResponse(wait.request)
            }
            println("Waiting for captcha to be solved...")
        }

    }


    @Serializable
    data class CapResponse(
        val status: Int,
        val request:String?//token
    )


}
