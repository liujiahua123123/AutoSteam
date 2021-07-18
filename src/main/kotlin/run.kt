
import accountjar.RemoteJar
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotlinx.serialization.encodeToString
import net.mamoe.*
import net.mamoe.email.MailService
import net.mamoe.email.MyMailServer
import net.mamoe.server.SessionReceiveServer
import net.mamoe.sms.Code
import net.mamoe.sms.Phone
import net.mamoe.sms.SMSService
import net.mamoe.steam.*
import net.mamoe.step.*
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.io.File
import java.net.*
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.Executors
import javax.net.ssl.*
import kotlin.random.Random
import kotlin.system.exitProcess

object JumpServerProxyProvider: ProxyProvider {
    val list = """
        jumpserver://172.245.156.111:8188
        jumpserver://23.95.213.143:8188
        jumpserver://23.94.182.111:8188
        jumpserver://104.168.96.118:8188
        jumpserver://107.173.24.129:8188
        jumpserver://107.172.156.106:8188
        jumpserver://23.94.190.104:8188
        jumpserver://172.245.6.145:8188
        jumpserver://107.175.87.113:8188
        jumpserver://104.168.46.119:8188
    """.trimIndent().split("\n")

    override fun invoke(): Pair<(Connection) -> Unit,String>{
        with(list.random()) {
            return Pair({
                it.jumpServer(this)
            },this)
        }
    }
}
object NoProxyProvide:ProxyProvider{
    override fun invoke(): Pair<(Connection) -> Unit, String> {
        return Pair({},"localhost")
    }
}



val client = SteamStoreClient().apply {
    referer = "https://store.steampowered.com/join/"
    addIntrinsic{
        println("[NETWORK] -> Connect " + it.request().url())
        it.timeout(10000)
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

val cnclient = MockChromeClient().apply {
    addIntrinsic{
        println("[NETWORK] -> Connect " + it.request().url())
        it.header("Origin","https://store.steamchina.com")
        it.header("Referer","https://store.steamchina.com/login/?redir=&redir_ssl=1&snr=1_4_4__global-header")
        it.timeout(5000)
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

val rnrClient = MockChromeClient().apply {
    addIntrinsic{
        println("[NETWORK] -> Connect " + it.request().url())
        it.header("Origin","http://rnr.steamchina.com")
        it.header("Referer","https://rnr.steamchina.com/register.html")
        it.timeout(5000)
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

val file = File(System.getProperty("user.dir") + "/accounts.json").apply {
    createNewFile()
    if(this.readText().isEmpty()){
        this.writeText("[]")
    }
}
suspend fun main(){
    //fixJava()

    //client.post("https://steamcommunity.com/login/transfer"){
        //requestBody("steamid")
   // }

   // exitProcess(1)

    /*
    while (true) {
        val accounts = file.readText().deserialize<MutableList<Account>>()
        val unprofiled = accounts.filter { !it.profiled }
        println("There are  $unprofiled accounts need to be handled")

        val next = unprofiled.firstOrNull()?: error("No account to done")

        println("start handle: $next")
        doProfile(next.username, next.password)

        accounts.remove(next)
        accounts.add(next.copy(profiled = true))

        file.writeText(SteamJson.encodeToString(accounts))
        println("finish handle: $next")
        client.cookies.clear()
        delay(Duration.ofMillis(10000))
    }

     */

   // SessionReceiveServer.start()

   // GlobalScope.launch {
        //SessionReceiveServer.start()
   // }



    /*

    val account = RemoteJar.popAccount(chinaAuth = false)

    val worker = WorkerImpl("W " + account.id)
    val client = MockChromeClient().apply {
        addIntrinsic{
            it.networkRetry(8)
        }
    }

    val executor = StepExecutor(worker,account.toComponent(),client,NoProxyProvide)

    executor.executeSteps(VerifyPhone,StartCNAuth,CompleteCNAuth,StoreAccount)

     */


    repeat(21) {
        val account = RemoteJar.popAccount(profile = Profile.NO_PROFILE)

        val worker = WorkerImpl("W $it")

        val client = MockChromeClient().apply {
            addIntrinsic {
                it.networkRetry(8)
            }
        }

        val executor = StepExecutor(worker, account.toComponent(), client, JumpServerProxyProvider)

        executor.executeSteps(Login,SetPrivacy,SetProfile,SetAvatar,StoreAccount)
    }

}


