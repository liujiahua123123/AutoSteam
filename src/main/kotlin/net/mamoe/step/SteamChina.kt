package net.mamoe.step

import decode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.time.delay
import kotlinx.serialization.encodeToString
import ksoupJson
import net.mamoe.data
import net.mamoe.email.MyMailServer
import net.mamoe.sms.ReceiveCodeTimeoutException
import net.mamoe.sms.SMSService
import net.mamoe.steam.*
import net.mamoe.steamPasswordRSA
import java.time.Duration


object PhoneNumber:StringComponentKey
object PhoneCode:StringComponentKey
object AgreementToken:StringComponentKey



object VerifyPhone:SteamStep(){
    override val name: String get() = "Verify Phone"
    override val process: suspend StepExecutor.() -> Unit
        get() ={
            log("Waiting for WanMei Captcha")
            val wanmeiCaptcha = WanmeiCapQueue.receive()
            val phone = SMSService.DEFAULT.getPhone()
            log("Use WMCaptcha " + wanmeiCaptcha.capTicket + " and " + phone.number)

            val x = client.get("https://rnr.steamchina.com/securityCode"){
                data("mobilePhone",phone.number)
                data("graphCode", "[]")
                data("capTicket",wanmeiCaptcha.capTicket)
                data("secCode",wanmeiCaptcha.secCode)
                data("reason","1")
            }.decode<CNRegisterResponse>()

            if(x.code != 0 || x.description!="Success"){
                phone.block()
                worker.log("Phone Number Used")
                throw RerunStepException()
            }
            worker.log("Waiting For Code")

            val code = try {
                phone.waitCode()
            }catch (e:ReceiveCodeTimeoutException){
                phone.block()
                worker.log("Could not receive a code, blocking phone and restarting..")
                throw RerunStepException()
            }

            if(!code.received) {
                phone.block()
                println("No code received")
                throw RerunStepException()
            }

            component[PhoneCode] = code.code!!
            component[PhoneNumber] = phone.number
        }
}

object StartCNAuth:SteamStep() {
    override val name: String get() = "Start CN Auth"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val d = client.post("https://store.steamchina.com/login/getrsakey/") {
                data(
                    GetRsaKeyRequest(
                        username = component[Username]
                    )
                )
            }.decode<GetRsaKeyResponse>()

            val ps = steamPasswordRSA(d.publickey_mod, d.publickey_exp, component[Password])
            debug(ps)

            val r = client.post("https://store.steamchina.com/login/dologin/") {
                data(
                    LoginRequest(
                        username = component[Username],
                        password = ps,
                        rsatimestamp = d.timestamp,
                    )
                )
            }.decode<LoginResponse>()

            if (r.agreement_session_url == null) {
                error("no need to CN anth")
            }
            val agreementToken = r.agreement_session_url.substringAfter("token=").trim()

            client.get(r.agreement_session_url)
            client.get("https://store.steamchina.com/agreements/startidverification?token=$agreementToken&redir=https://store.steamchina.com/login/?agreementsource=2")
            client.get("https://rnr.steamchina.com/register.html?token=$agreementToken&newUser=false")

            component[AgreementToken] = agreementToken
        }
}

object CompleteCNAuth:SteamStep(){
    override val name: String = "Complete CN Auth"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val id = MyMailServer.randomId()

            val resp = client.post("https://rnr.steamchina.com/register?token=${component[AgreementToken]}"){
                header("Content-Type", "application/json")
                this.requestBody(
                    ksoupJson.encodeToString(CNRegisterRequest(
                        mobilePhone = component[PhoneNumber],
                        realName = id.name,
                        residentId = id.credentialsValue,
                        securityCode = component[PhoneCode]
                )))
            }.decode<CNRegisterResponse>()

            if(resp.description != "Success"){
                println("Reg Failed, changing identity")
                throw RerunStepException()
            }

            delay(Duration.ofMillis(1000))
            val finalStep = client.post("https://store.steamchina.com/agreements/ajaxcompleteagreement"){
                data("token",component[AgreementToken])
            }.body()

            val saved = client.get("https://store.steamchina.com/login/?agreementsource=2"){
                header("referer","https://store.steamchina.com/agreements/startidverification?token=${component[AgreementToken]}&redir=https://store.steamchina.com/login/?agreementsource=2")
            }.body()

            val d1 = client.post("https://store.steamchina.com/login/getrsakey/"){
                data(GetRsaKeyRequest(
                    username = component[Username]
                ))
            }.decode<GetRsaKeyResponse>()

            val ps = steamPasswordRSA(d1.publickey_mod, d1.publickey_exp, component[Password])
            debug(ps)

            val r1 = client.post("https://store.steamchina.com/login/dologin/"){
                data(LoginRequest(
                    username = component[Username],
                    password = ps,
                    rsatimestamp = d1.timestamp,
                ))
            }.decode<LoginResponse>()

            if(!r1.login_complete){
                error("Login Incomplete!!")
            }

            component[CnAuthStatus] = true
        }
}




data class WanmeiCaptcha(
    val capTicket:String,
    val secCode:String
)

val WanmeiCapQueue = Channel<WanmeiCaptcha>(capacity = Channel.UNLIMITED)
