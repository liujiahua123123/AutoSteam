package net.mamoe.steam

import kotlinx.html.currentTimeMillis
import kotlinx.serialization.Serializable
import net.mamoe.FormData


@Serializable
data class Account(
    val username: String,
    val password: String,
    val email:String,
    val profiled: Boolean = false,
    val chinaAuthed: Boolean = false,
)


/**
 * https://store.steampowered.com/join/refreshcaptcha/
 */
@Serializable
object RefreshCaptchaRequest:FormData

@Serializable
data class RefreshCaptchaResponse(
    val gid: String,
    val s: String,
    val sitekey: String,
    val type: Int
)

/**
 * https://store.steampowered.com/join/ajaxverifyemail
 */
@Serializable
data class AjaxVerifyEmailRequest(
    val email:String,
    val captchagid:String,
    val captcha_text:String,
    val elang:Int = 6,
):FormData

@Serializable
data class AjaxVerifyEmailResponse(
    val details: String,//""
    val sessionid: String,//5787350384381888941
    val success: Int//1
)


/**
 * https://store.steampowered.com/join/ajaxcheckemailverified
 */

@Serializable
data class AjaxEmailVerifiedRequest(
    val creationid:String
):FormData

@Serializable
data class AjaxEmailVerifiedResponse(
    val global_account: Int,
    val has_existing_account: Int,
    val pw_account: Int,
    val steam_china_account: Int,
    val success: Int
    //36, 10 -> debug("Waiting for Email verification...[36]")
    //42, 49 -> throw RuntimeException("Unknown error in email verification[42/49]")
    //27 -> throw RuntimeException("Time out in email verification")
    //1 -> email verified
) {

    fun status(): Status {
        return when (this.success) {
            36, 10 -> Status.WAITING
            42, 49 -> Status.ERROR
            27 -> Status.TIMEOUT
            1 -> Status.SUCCESS
            else -> Status.UNKNOWN
        }
    }

    companion object {
        enum class Status(val info: String) {
            WAITING("Waiting for Email verification...[36/10]"),
            SUCCESS("Email Verified [1]"),
            TIMEOUT("Time out in email verification"),
            ERROR("Unknown error in email verification[42/49]"),
            UNKNOWN("Unknown success code")
        }
    }
}


/**
 * https://store.steampowered.com/join/checkavail/
 */

@Serializable
data class CheckUsernameRequest(
    val accountname:String
):FormData

@Serializable
data class CheckUsernameResponse(
    val bAvailable: Boolean,
    val rgSuggestions: List<String>
)


/**
 * https://store.steampowered.com/join/checkpasswordavail/
 */

@Serializable
data class CheckPasswordRequest(
    val accountname:String,
    val password: String
):FormData

@Serializable
data class CheckPasswordResponse(
    val bAvailable: Boolean
)


/**
 * https://store.steampowered.com/join/createaccount
 */

//Request, Form Data
//accountname=aaaaaaa12312333333&password=Kim321232123&count=20&lt=0&creation_sessionid=5787350384381888941&embedded_appid=0

@Serializable
data class CreateAccountRequest(
    val accountname:String,
    val password:String,
    val creation_sessionid:String,
    val lt:String = "0",
    val embedded_appid:String = "0"
):FormData


@Serializable
data class CreateAccountResponse(
    val bSuccess:Boolean,
    val bInSteamClient: Boolean,
    val eresult:Int
)


//https://store.steampowered.com/login/getrsakey/

@Serializable
data class GetRsaKeyRequest(
    val donotcache:Long = currentTimeMillis(),
    val username:String
):FormData

@Serializable
data class GetRsaKeyResponse(
    val publickey_exp: String,
    val publickey_mod: String,
    val success: Boolean,
    val timestamp: String,
    val token_gid: String
)

//https://store.steampowered.com/login/dologin/

@Serializable
data class LoginRequest(
    val donotcache:Long = currentTimeMillis(),
    val username:String,
    val password:String,
    val twofactorcode:String = "",
    val emailauth:String = "",
    val loginfriendlyname:String = "",
    val captchagid:Int = -1,
    val captcha_text:String = "",
    val emailsteamid:String = "",
    val rsatimestamp:String,
    val remember_login:Boolean = true
):FormData

@Serializable
data class LoginResponse(
    val captcha_gid: Int,
    val captcha_needed: Boolean,
    val message: String,
    val requires_twofactor: Boolean,
    val success: Boolean,
    val emailauth_needed: Boolean
)




