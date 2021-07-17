package net.mamoe.step

import SteamAccount
import accountjar.RemoteJar
import kotlinx.coroutines.time.delay
import kotlinx.serialization.encodeToString
import ksoupJson
import net.mamoe.data
import net.mamoe.decode
import net.mamoe.email.MailService
import net.mamoe.steam.*
import java.io.File
import java.time.Duration
import kotlin.random.Random


object RegisterEmail:StringComponentKey
object RegisterSession:StringComponentKey
object RegisterUsername:StringComponentKey
object RegisterPassword:StringComponentKey

object VerifyMail:SteamStep(){
    override val name: String = "Verify Email"

    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val email = component[RegisterEmail]
            val session = component[RegisterSession]

            client.get(MailService.DEFAULT.verifyRegister(email))

            while (true) {
                val emailResponse = client.steamAjax("https://store.steampowered.com/join/ajaxcheckemailverified") {
                    data(
                        AjaxEmailVerifiedRequest(
                            creationid = session
                        )
                    )
                }.decode<AjaxEmailVerifiedResponse>()

                when (emailResponse.status()) {
                    AjaxEmailVerifiedResponse.Companion.Status.SUCCESS -> {
                        log("Email Verified")
                        break
                    }
                    AjaxEmailVerifiedResponse.Companion.Status.WAITING -> {
                        log("Waiting Email Verify Pass")
                        delay(Duration.ofMillis(3000))
                        continue
                    }
                    else -> {
                        error("Error in email verification, probably a domain ban")
                    }
                }
            }
        }
}

object TestUsername:SteamStep(){
    override val name: String = "Test Username"

    override val process: suspend StepExecutor.() -> Unit
        get() = {
            var accountName = component[RegisterEmail].substringBefore("@")

            while (true) {
                worker.debug("Test Username = $accountName")
                val res = client.steamAjax("https://store.steampowered.com/join/checkavail/") {
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
                break
            }

            worker.debug("Select Username = $accountName")
            component[RegisterUsername] = accountName
        }
}

object TestPassword:SteamStep(){
    override val name: String = "Test Password"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val accountName = component[RegisterUsername]
            val password = "KIManti" + Random.Default.nextInt(10000,999999) + "a"
            worker.debug("Test Password = $password")
            val res = client.steamAjax("https://store.steampowered.com/join/checkpasswordavail/"){
                data(
                    CheckPasswordRequest(
                        accountName,password
                    )
                )
            }.decode<CheckPasswordResponse>()

            if(!res.bAvailable){
                throw RerunStepException()
            }
            worker.debug("Select Password = $password")
            component[RegisterPassword] = password
        }
}

object CompleteRegister:SteamStep(){
    override val name: String = "Complete Register"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val response = client.steamAjax("https://store.steampowered.com/join/createaccount") {
                data(
                    CreateAccountRequest(
                        component[RegisterUsername],
                        component[RegisterPassword],
                        creation_sessionid = component[RegisterSession]
                    )
                )
            }.decode<CreateAccountResponse>()
            if(!response.bSuccess){
                error("Register Failed " + response.eresult + " " + response)
            }
        }
}

object StoreAccount:SteamStep(){
    override val name: String = "Store Account"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            RemoteJar.pushAccount(SteamAccount(
                -1,component[RegisterUsername],component[RegisterPassword],component[RegisterEmail],Profile.NO_PROFILE,false
            ).also {
                debug("Uploading Accounts to Remote Jar $it")
                File(System.getProperty("user.dir") + "/doubleStore.txt").apply {
                    if(!this.exists()){
                        this.createNewFile()
                    }
                }.appendText(ksoupJson.encodeToString(it) + "\n")
            })
        }
}

fun createRegisterComponent(email:String, sessionId:String):Component{
    return Component().apply {
        this[RegisterEmail] = email
        this[RegisterSession] = sessionId
    }
}

