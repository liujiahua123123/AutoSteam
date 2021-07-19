
import accountjar.RemoteJar
import io.ktor.http.*
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotlinx.serialization.decodeFromString
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlin.concurrent.thread
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

//local storage
val tempAccounts = Collections.synchronizedSet(mutableSetOf<SteamAccount>())

suspend fun main(){
    Runtime.getRuntime().addShutdownHook(thread(start = false){
        println("Store Local Account back to RemoteJar before exit")
            tempAccounts.forEach {
                runBlocking {
                    println(">$it")
                    RemoteJar.pushAccount(it)
                }
            }
    })


    println("请选择你要做什么, 并输入对应序号(1/2/3)")
    println("[1]: 注册新账户 [不需要挂梯子] 需要使用Chrome解谷歌验证码 如果验证码被ban需要为Chrome设置额外的梯子")
    println("[2]: 设置身份头像 [需要挂梯子] 无需额外操作")
    println("[3]: 国服认证 [不需要挂梯子] 需要使用Chrome解完美验证码")
    println("=======================")

    GlobalScope.launch {
        SessionReceiveServer.start()
        println("+============+")
        println("Session Server Started, ready to receive>>>")
    }




    val taskTodoRaw = readLine()
    val taskTodo =  when(taskTodoRaw){
        "1" -> 1
        "2" -> 2
        "3" -> 3
        else -> {
            println("重新开启并输入对应序号(1/2/3)")
            exitProcess(1)
        }
    }



    when(taskTodo){
        1 -> {
            println(">> 开始解验证码即可, 并发数=5 任务数量=~")
            repeat(5){
                GlobalScope.launch {
                    println("Worker Reg$it is ready...")
                    while (isActive) {
                        val worker = WorkerImpl("Reg$it")
                        try {
                            val client = MockChromeClient().apply {
                                addIntrinsic {
                                    it.networkRetry(8)
                                }
                            }
                            val a = GoogleCapQueue.receive()
                            StepExecutor(
                                worker,a, client,JumpServerProxyProvider
                            ).executeSteps(
                                VerifyMail,TestUsername,TestPassword,CompleteRegister,StoreAccount
                            )
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            println("Exception Happened, account dropped!")
                        }
                    }
                }
            }
        }
        2 -> {
            println(">> 开始自动处理名字头像组, 并发数=3 任务数量=~")
            repeat(3) {
                println("Start Worker$it")
                GlobalScope.launch {
                    while (isActive) {
                        val worker = WorkerImpl("Worker$it")
                        var account:SteamAccount? = null
                        try {
                            account = try {
                                RemoteJar.popAccount(profile = Profile.NO_PROFILE)
                            }catch (e:RemoteJar.NoAvailableAccountException){
                                println("没有需要处理的账号!!")
                                break
                            }

                            worker.log("Handle Account: $account")

                            val client = MockChromeClient().apply {
                                addIntrinsic {
                                    it.networkRetry(8)
                                }
                            }

                            val executor = StepExecutor(worker, account!!.toComponent(), client, JumpServerProxyProvider)
                            executor.executeSteps(Login,SetPrivacy,SetProfile,SetAvatar,StoreAccount)
                            worker.log("Successfully Handled Account: $account wait 30 second to protect IP")
                            delay(1000 * 30)

                        } catch (e: Throwable) {
                            e.printStackTrace()

                            worker.log("Exception Happened, store the origin account back, wait 60 sec to protect IP")
                            if(account!=null) {
                                RemoteJar.pushAccount(account)
                            }
                            delay(1000 * 60)
                        }
                    }
                }
            }
        }

        3 -> {
            println(">> 开始解验证码即可, 并发数=3 任务数量=~")
            repeat(3) {
                println("Start CN$it")
                GlobalScope.launch {
                    while (isActive) {
                        val worker = WorkerImpl("CN$it")
                        var account2:SteamAccount? = null

                        try {
                            try {
                                account2 = RemoteJar.popAccount(chinaAuth = false)
                            }catch (e:RemoteJar.NoAvailableAccountException){
                                println("没有需要处理的账号!!")
                                break
                            }

                            worker.log("开始认证: $account2")

                            val client = MockChromeClient().apply {
                                addIntrinsic {
                                    it.networkRetry(5)
                                }
                            }

                            val executor = StepExecutor(worker, account2.toComponent(), client, NoProxyProvide)

                            executor.executeSteps(VerifyPhone,StartCNAuth,CompleteCNAuth,StoreAccount)
                            worker.log("完成认证Account: $account2,")

                        } catch (e: Throwable) {
                            e.printStackTrace()

                            worker.log("Exception Happened, store the origin account back, wait 60 sec to protect IP")
                            if(account2!=null) {
                                RemoteJar.pushAccount(account2)
                            }
                            delay(1000 * 60)
                        }
                    }
                }
            }

        }
        else -> error("?")
    }


    delay(999999999999999)
    /*

    repeat(8) {
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
    */

}


