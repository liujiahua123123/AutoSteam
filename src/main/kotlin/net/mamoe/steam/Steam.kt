package net.mamoe.steam

import kotlinx.html.currentTimeMillis
import kotlinx.serialization.Serializable
import net.mamoe.FormData
import java.io.InputStream


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
    val captcha_gid: Int = -1,
    val captcha_needed: Boolean = false,
    val message: String = "",
    val requires_twofactor: Boolean = false,
    val success: Boolean = false,
    val emailauth_needed: Boolean = false,
    val login_complete: Boolean = false,
    val transfer_urls: List<String> = emptyList(),
    val transfer_parameters: Map<String,String> = emptyMap(),
    val agreement_session_url:String? = null//For CN Only
)

/**
 * https://store.steampowered.com/twofactor/manage_action
 */
data class ManageActionRequest(
    val action: String,
    val sessionid: String
):FormData


/**
 * https://steamcommunity.com/profiles/{steamid}/edit/
 */

data class EditProfileRequest(
    val sessionID: String,
    val type:String = "profileSave",
    val weblink_1_title:String = "",
    val weblink_1_url:String = "",
    val weblink_2_title:String = "",
    val weblink_2_url:String = "",
    val weblink_3_title:String = "",
    val weblink_3_url:String = "",
    val personaName:String = "",//nick name
    val real_name:String = "",
    val customURL:String = "",
    val country:String = "",
    val state:String = "",
    val city:String = "",
    val summary:String = "",//information
    val hide_profile_awards:Int = 0,
    val json:Int = 1
):FormData


@Serializable
data class EditProfileResponse(
    val success:Int,
    val errmsg:String = ""
)

/**
 * https://steamcommunity.com/actions/FileUploader
 */
data class UploadAvatarRequest(
    //val avatar: InputStream// use .data()
    val type:String = "player_avatar_image",
    val sId:String,//steam id
    val sessionid:String,
    val doSub:Int = 1,
    val json:Int = 1
):FormData


@Serializable
data class UploadAvatarResponse(
    val images: Images? = null,
    val message: String = "",
    val success: Boolean
){
    @Serializable
    data class Images(
        val `0`: String = "",
        val full: String = "",
        val medium: String = ""
    )
}


/**
 *  https://steamcommunity.com/groups/{groupName} //Anti-Player-RE
 */
data class JoinGroupRequest(
    val sessionID:String,
    val action:String = "join"
):FormData

//302 response -> HTML


/**
 * https://steamcommunity.com/profiles/76561199190833821/ajaxsetprivacy/
 */

data class SetPrivacyRequest(
    val sessionid:String,
    val Privacy:String,
    val eCommentPermission:Int
):FormData

@Serializable
data class SetPrivacyResponse(
    val success:Int
)