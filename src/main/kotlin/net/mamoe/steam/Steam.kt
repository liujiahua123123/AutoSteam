package net.mamoe.steam

import kotlinx.serialization.Serializable


/**
 * https://store.steampowered.com/join/refreshcaptcha/
 */
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
//Request, Form Data,
//email =
//captchagid =
//captcha_text =
//elang = 6

@Serializable
data class AjaxVerifyEmailResponse(
    val details: String,//""
    val sessionid: String,//5787350384381888941
    val success: Int//1
)


/**
 * https://store.steampowered.com/join/ajaxcheckemailverified
 */

//Request, Form Data
//creationid = //5787350384381888941

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
)


/**
 * https://store.steampowered.com/join/checkavail/
 */

//Request, Form Data
//accountname = aaaaaaa

@Serializable
data class CheckUsernameResponse(
    val bAvailable: Boolean,
    val rgSuggestions: List<String>
)


/**
 * https://store.steampowered.com/join/checkpasswordavail/
 */

//Request, Form Data
//password =
//accountname = aaaaaa

@Serializable
data class CheckPasswordResponse(
    val bAvailable: Boolean
)


/**
 * https://store.steampowered.com/join/createaccount
 */

//Request, Form Data
//accountname=aaaaaaa12312333333&password=Kim321232123&count=20&lt=0&creation_sessionid=5787350384381888941&embedded_appid=0



