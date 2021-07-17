package net.mamoe.step

import net.mamoe.data
import net.mamoe.decode
import net.mamoe.email.MailService
import net.mamoe.steam.GetRsaKeyRequest
import net.mamoe.steam.GetRsaKeyResponse
import net.mamoe.steam.LoginRequest
import net.mamoe.steam.LoginResponse
import net.mamoe.steamPasswordRSA

object LoginEmail:StringComponentKey
object LoginPassword:StringComponentKey
object LoginUsername:StringComponentKey
object Steam64Id:StringComponentKey
object Email2FA:StringComponentKey


object Login: SteamStep() {
    override val name: String
        get() = "Login"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val username = component[LoginUsername]
            val d = client.post("https://store.steampowered.com/login/getrsakey/"){
                data(
                    GetRsaKeyRequest(
                        username = username
                    )
                )
            }.decode<GetRsaKeyResponse>()

            val ps = steamPasswordRSA(d.publickey_mod,d.publickey_exp,component[LoginPassword])
            debug("Encode Password $ps")

            val r = client.post("https://store.steampowered.com/login/dologin/"){
                data(
                    LoginRequest(
                        username = username,
                        password = ps,
                        rsatimestamp = d.timestamp,
                        remember_login = true,
                        emailauth = component.getOrNull(Email2FA)?:""
                    )
                )
            }.decode<LoginResponse>()

            if(r.emailauth_needed && component.getOrNull(Email2FA) == null){
                debug("Need Email Auth")
                component[Email2FA] = MailService.DEFAULT.get2FA(component[LoginEmail])
                throw RerunStepException()
            }

            if(!r.login_complete){
                debug(r.message)
                error("Failed to complete login")
            }

            val steamId = r.transfer_parameters.map { it }.firstOrNull { it.key == "steamid" }?.value
                ?: error("Failed to retrieve steamid from transfer parms")

            component[Steam64Id] = steamId

            r.transfer_urls.forEach {
                val host =  it.substringAfter("https://").substringBefore("/")
                val referer = "https://store.steampowered.com/"
                client.post(it){
                    data(r.transfer_parameters)
                    header("host",host)
                    header("referer",referer)
                    timeout(120_000)
                }
                debug("Redirecting Login to $it")
            }
        }
}


object Terminated

