package net.mamoe.step

import Profile
import SteamAccount
import accountjar.RemoteJar
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.time.delay
import kotlinx.serialization.encodeToString
import ksoupJson
import net.mamoe.data
import net.mamoe.decode
import net.mamoe.email.MailService
import net.mamoe.steam.*
import net.mamoe.steamPasswordRSA
import tempAccounts
import java.io.File
import java.time.Duration
import kotlin.random.Random


object Email:StringComponentKey
object RegisterSession:StringComponentKey
object Username:StringComponentKey
object Password:StringComponentKey

object ProfileStatus:ComponentKey<Profile>
object CnAuthStatus:ComponentKey<Boolean>

object Steam64Id:StringComponentKey
object Email2FA:StringComponentKey


object StoreAccountReferer:ComponentKey<SteamAccount>

object VerifyMail:SteamStep(){
    override val name: String = "Verify Email"

    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val email = component[Email]
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
            var accountName = component[Email].substringBefore("@")

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
            component[Username] = accountName
        }
}

object TestPassword:SteamStep(){
    override val name: String = "Test Password"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val accountName = component[Username]
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
            component[Password] = password
        }
}

object CompleteRegister:SteamStep(){
    override val name: String = "Complete Register"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val response = client.steamAjax("https://store.steampowered.com/join/createaccount") {
                data(
                    CreateAccountRequest(
                        component[Username],
                        component[Password],
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
            val account = component.getOrNull(StoreAccountReferer)
            if(account!=null) {
                tempAccounts.remove(account)//remove temp, store online
            }

            RemoteJar.pushAccount(SteamAccount(
                -1,component[Username],component[Password],component[Email],component.getOrNull(ProfileStatus)?:Profile.NO_PROFILE,component.getOrNull(CnAuthStatus)?:false
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

object Login: SteamStep() {
    override val name: String
        get() = "Login"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val username = component[Username]
            val d = client.post("https://store.steampowered.com/login/getrsakey/"){
                data(
                    GetRsaKeyRequest(
                        username = username
                    )
                )
            }.decode<GetRsaKeyResponse>()

            val ps = steamPasswordRSA(d.publickey_mod,d.publickey_exp,component[Password])
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
                component[Email2FA] = MailService.DEFAULT.get2FA(component[Email])
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

            client.get("https://steamcommunity.com/profiles/$steamId/edit/info")


            debug("Showing Login Success cookies: ")
            client.cookies.forEach { (t, u) ->
                debug("Cookies for $t")
                u.forEach { (t, u) ->
                    debug("$t => $u")
                }
            }
            debug("=======")
        }
}


object SetPrivacy:SteamStep() {
    override val name: String = "Set Privacy"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val sessionId = client.cookies["steamcommunity.com"]?.get("sessionid")
                ?: error("Failed to find sessionId for steam community")
            val steamId = component[Steam64Id]

            val r = client.post("https://steamcommunity.com/profiles/${steamId}/ajaxsetprivacy/") {
                data(
                    SetPrivacyRequest(
                        eCommentPermission = 1,
                        sessionid = sessionId,
                        Privacy = "{\"PrivacyProfile\":3,\"PrivacyInventory\":3,\"PrivacyInventoryGifts\":1,\"PrivacyOwnedGames\":3,\"PrivacyPlaytime\":3,\"PrivacyFriendsList\":3}"
                    )
                )
            }.decode<SetPrivacyResponse>()
            if (r.success != 1) {
                error("Failed to set privacy")
            }
        }
}

object SetProfile:SteamStep() {
    override val name: String = "Set Profile"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val communitySessionId = client.cookies["steamcommunity.com"]?.get("sessionid")
                ?: error("Failed to find sessionId for steam community")

            val steamId = component[Steam64Id]

            val editResponse = client.post("https://steamcommunity.com/profiles/$steamId/edit/") {
                data(
                    EditProfileRequest(
                        sessionID = communitySessionId,
                        personaName = "上海黑手维克托",
                        summary = "上海黑手维克托老师专用账号"
                    )
                )
            }.decode<EditProfileResponse>()

            if (editResponse.success != 1) {
                error("profile failed")
            }

            client.post("https://steamcommunity.com/groups/Anti-Player-RE") {
                data(
                    JoinGroupRequest(
                        sessionID = communitySessionId
                    )
                )
            }

        }
}

object SetAvatar:SteamStep(){
    override val name: String = "Set Avatar"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val communitySessionId = client.cookies["steamcommunity.com"]?.get("sessionid")
                ?: error("Failed to find sessionId for steam community")

            val steamId = component[Steam64Id]

            val upload = client.post("https://steamcommunity.com/actions/FileUploader"){
                data(UploadAvatarRequest(
                    sessionid = communitySessionId,
                    sId = steamId
                ))
                data("avatar","MyAva.jpg", File(System.getProperty("user.dir") + "/BlackHandVector.jpg").readBytes().inputStream())
                maxBodySize(1024*10)
                timeout(120000)
            }.decode<UploadAvatarResponse>()

            if(!upload.success){
                throw RerunStepException(changeProxy = true)
            }
            component[ProfileStatus] = Profile.VECTOR_PROFILE
        }
}


fun createRegisterComponent(email:String, sessionId:String):Component{
    return Component().apply {
        this[Email] = email
        this[RegisterSession] = sessionId
    }
}

fun createProfileComponent(email:String, username:String, password:String):Component {
    return Component().apply {
        this[Email] = email
        this[Password] = password
        this[Username] = username
    }
}

fun SteamAccount.toComponent():Component{
    return Component().also {
        it[Email] = this.email
        it[Password] = this.password
        it[Username] = this.username
        it[ProfileStatus] = this.profiled
        it[CnAuthStatus] = this.chinaAuthed
        it[StoreAccountReferer] = this
    }
}



//put compo directly
val GoogleCapQueue = Channel<Component>(Channel.UNLIMITED)