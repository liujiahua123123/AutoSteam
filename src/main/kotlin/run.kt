
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import net.mamoe.SteamStoreClient
import net.mamoe.data
import net.mamoe.decode
import net.mamoe.email.MailService
import net.mamoe.server.SessionReceiveServer
import net.mamoe.steam.*
import java.io.File
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

val client = SteamStoreClient().apply {
    referer = "https://store.steampowered.com/join/"
    addIntrinsic{
        println("[NETWORK] -> Connect " + it.request().url())
    }
    addResponseHandler{
        println("[NETWORK] <-  Status " + it.statusCode() + " " + it.statusMessage())
        if(it.body().contains("<!DOCTYPE html>")){
            println("[NETWORK] <- Receive HTML")
        }else {
            println("[NETWORK] <- Receive " + it.body())
        }
    }
}

val regDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

val file = File(System.getProperty("user.dir") + "/accounts.txt").apply {
    createNewFile()
}
suspend fun main(){
    fixJava()

    SessionReceiveServer.start(true)
}

suspend fun registerSimple(sessionID:String, email:String){
    withContext(regDispatcher) {
        client.ajaxCounter.addAndGet(2)//for default

        client.get(MailService.DEFAULT.verifyRegister(email).apply {
            println("Mail Verify Link: $this")
        })
        println("Mail Verified")

        while (true) {
            val emailResponse = client.ajax("https://store.steampowered.com/join/ajaxcheckemailverified") {
                data(
                    AjaxEmailVerifiedRequest(
                        creationid = sessionID
                    )
                )
            }.decode<AjaxEmailVerifiedResponse>()

            when (emailResponse.status()) {
                AjaxEmailVerifiedResponse.Companion.Status.SUCCESS -> {
                    println("Email Verified")
                    break
                }
                AjaxEmailVerifiedResponse.Companion.Status.WAITING -> {
                    println("Waiting")
                    delay(Duration.ofMillis(3000))
                    continue
                }
                else -> {
                    error("Error in email verification, probably a domain ban")
                }
            }
        }


        println("Password = KIM321321232")
        var accountName = email.substringBefore("@")

        while (true) {
            val res = client.ajax("https://store.steampowered.com/join/checkavail/") {
                data(
                    CheckUsernameRequest(
                        accountName
                    )
                )
            }.decode<CheckUsernameResponse>()
            if (!res.bAvailable) {
                if(res.rgSuggestions.isEmpty()) {
                    accountName += Random.nextInt(0, 9)
                }else{
                    accountName = res.rgSuggestions[0]
                }
                delay(Duration.ofMillis(500))
                continue
            }
            break;
        }

        var password = "KIManti" + Random.Default.nextInt(10000,99999) + "a"

        val response = client.ajax("https://store.steampowered.com/join/createaccount") {
            data(
                CreateAccountRequest(
                    accountName,
                    password,
                    creation_sessionid = sessionID
                )
            )
        }.decode<CreateAccountResponse>()


        if(response.bSuccess) {
            println("Register Complete, account saved")
            println("$accountName:$password")
            file.appendText("$accountName:$password\n")
        }else{
            error("Error in registration")
        }
    }
}


fun fixJava(){
    //headers: referer
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true")


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

suspend fun test(){
    /*
    val captchaResponse = client.ajax("https://store.steampowered.com/join/refreshcaptcha/"){
        data(RefreshCaptchaRequest)
    }.decode<RefreshCaptchaResponse>()



    println("Received Captcha Response")



    val capAnswer = CaptchaSolver.DEFAULT.solve(CaptchaSolveRequest(
        sitekey = captchaResponse.sitekey,
        s = captchaResponse.s,
        url = "https://store.steampowered.com/join/",
        userAgent = client.userAgent,
        cookie = client.cookies
    ))

    /*
    val capAnswer = CaptchaSolveResponse(
        token = "03AGdBq27ny2HyUu8bodEqg80IzGJi_Mw3ismMAuGvtKfyxeQHl60P-P4TTP_0XSnQGwURI42MH76-O7caEEHg2271TPu2DXwiJ2GzD2Sd3mxU1rPfQqMeAezX1vzo6GgfCmRtfC5vUoEZjWtpuZEgjHu_wpFWLRZJaGVNh20sLY3_YH4B8MtCBVD5yqaKmu-6upS_Sv1svKJ9NRJXGFUyiySh2vka-r2HO3H8jfrRsiFLvPB5KhD1t-gi8lNrcDHMGNKACAvXur9NGQ71dmEgz5wdWHpc9T8uVosgmcKE5WF1JaXJnvIORjpRCIMQD-9ewNZ_hzyRYWIU2LKUHZivjjCa5zB_anWWmFumuAX5aivlvskY9N8Fs6FijhoUnUI7iTBNEZ2kka5reX7To8aXBwnHJVwoOEYGWTwNtQcDX7J1sP_wubs4b4D31FGx7ev4uiHzIhyfeYalIWKNL9uso-uYp8zW4idUncBqtwfuY7fPMwtYC-u-Migk8mBjWkZLRnAAJUXpbYWpYlMPOpQbD9rbu85nVO2Fg2fqhHHtxh0YRCEokKvx23Fq8x_RzrhvaE_9sNf5KFPWB6PrmXhuCgTYXUOOZ8OhvCFxmH0mtTj8RliBs8-DtDTUFkphagreBRzekI1fFT_UfA_bTDguE0S_FI5u36sAOVC9CI9UjVCcxE9BWgEPeSvSNdToxToe1x9SFbVMX806HEG2LOp227HqK1YT0XNYVVEv8QRnP5SV_ZzErOPQ6Z9GAtGthWCbJtdVWVo06EvQ1vEEYyhOoRpg0hkK9PQAJLTZ3IX66dFVM2MNzuL51UND2gMLIAajEFBHEbSQaeuDeRbUEMZ6w33ZMYNGs9u775VCQEcxQOP8eM4INfpqvFco_hIInZ4lYUAIKbItAjNbVMXycTgF4EerHLSEN-6rGotIupHg8McO2A_DoP6yv3v9UT8J0uGScv3MORiWpY3rSDGOqCDdbwJmG-VEfX0-A381P8mvTeVzNB5Lx2voesGs5m2aZm-ZP-Pqf3-QCPD3ADfBf5mhvKki6wk1bHaFPP7V1nno2lRyhWGMmWP7LngdZQGBRC86xgE1wRQLyPKU0W0j6SOS4GyxO4Kp6V4lJa70LakVWRFIync80OA9UBSiGIQJ-7qerxx5MS-a9mDXudnXnNYibjK4VNQr-QRoSOqOfpvjC8p0kQ-eIUIf2cub49BNylF8MK5cbKk0K5r6sCjv9xKw8R3UkGyNC43ydT6ruPhCJQH39WAIw0nNTV6-X0jjUXFRnh4FpvcILQi6T8vDWgCGbXaIuTsN1DqXeoBICGiy2D0JDQLNk_MvhgTYd3Isf4befXSb4LVzWYIz7gH3xpc0GwMtpGaYpSwSa2pUnSgzTCtkKg4pRAJaK-vZ9mqzvnZi3VixTPW1ep9D_3XlY5lBqi4l6SIWzLGjPZxJvvpO7DGpPhaHrE6uZJoJO66JUovL8qjH00_8Uielr6Ho-sfEJy7MQW8kIIzSunbLwWqrbqaCpDThPS8ItYorx3LA8kdFjAvgV0hfm08X"
     */


    println("CapAnswer is $capAnswer")
    val email = "testtest1@antiplayer.club"


    val sendEmailResponse = client.ajax("https://store.steampowered.com/join/ajaxverifyemail"){
        proxy("127.0.0.1",8888)
        cookie("browserid","2492157143329006409")
        cookie("app_impressions","1151640@1_4_4__129_1|1410710@1_4_4__43_1|1147560@1_4_4__139_4|495420@1_4_4__139_4|548430@1_4_4__139_4|35140:200260:208650:367480@1_4_4__139_3|1356670@1_4_4__139_3|374320:442010@1_4_4__139_3|1567800@1_4_4__128_1|578080@1_4_4__129_1|1410710@1_4_4__43_1|1147560@1_4_4__139_4|1217060@1_4_4__139_4|851850@1_4_4__139_4|1263850@1_4_4__139_3|374320:442010@1_4_4__139_3|1172620@1_4_4__139_3|374320:442010@1_4_4__129_1|1410710@1_4_4__43_1|1217060@1_4_4__139_4|1147560@1_4_4__139_4|35140:200260:208650:367480@1_4_4__139_4|107410@1_4_4__139_3|1151640@1_4_4__139_3|374320:442010@1_4_4__139_3")

        data(AjaxVerifyEmailRequest(
            email = email,
            captcha_text = capAnswer.token,
            captchagid = "3989608458749455593",
            elang = 6
        ))
    }.decode<AjaxVerifyEmailResponse>()

    if(sendEmailResponse.success!=1){
        error("Failed to send email, blocked by shield" )
    }

     */
}

