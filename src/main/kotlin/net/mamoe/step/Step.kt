package net.mamoe.step

import MockChromeClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.currentTimeMillis
import org.jsoup.Connection
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.random.Random
import kotlin.reflect.jvm.jvmName


class MissingComponentException(name:String):Exception("Missing Component $name")
class Component{
    private val map = ConcurrentHashMap<ComponentKey<*>,Any>()

    fun has(key: ComponentKey<*>) = map.contains(key)

    operator fun <T:Any> get(key: ComponentKey<T>):T = getOrNull(key)?:throw MissingComponentException(key::class.simpleName?:key::class.jvmName)

    operator fun <T:Any> set(key: ComponentKey<T>, value:T) {
        map[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T:Any> getOrNull(key: ComponentKey<T>):T? = map[key].run {
        if(this == null){
            this
        }else{
            this as T
        }
    }

}

interface ComponentKey<T>
interface StringComponentKey:ComponentKey<String>


class RerunStepException(val changeProxy:Boolean = false):Exception()

interface ProxyProvider{
    operator fun invoke():Pair<(Connection) -> Unit,String>
}

class StepExecutor(val worker: Worker, val component: Component, val client: MockChromeClient, val provider: ProxyProvider){

    private var provided = provider()

    init {
        client.addIntrinsic{
            provided.first(it)
            worker.debug("Proxy: " + provided.second)

            it.data("from","AutoSteam")
            debug(" -> Connect " + it.request().url())
            debug(" ->    Send " + it.request().requestBody())
        }
        client.addResponseHandler{
            debug(" <-  Status " + it.statusCode() + " " + it.statusMessage())
            debug(" <-  Body " + if(it.body().contains("HTML")){"HTML"}else{it.body()})
        }
    }

    fun log(message: String){
        worker.log(message)
    }

    fun debug(message: String){
        worker.debug(message)
    }

    private suspend fun executeStep(
        step: suspend StepExecutor.() -> Unit
    ){
        var maxRetry = 15
        var networkRetry = 5

        while (true) {
            try {
                step.invoke(this)
                break
            } catch (e: RerunStepException) {
                if(e.changeProxy){
                    worker.debug("Changed Proxy ")
                    provided = provider()
                }
                if(maxRetry -- > 0) {
                    continue
                }else{
                    error("Too many Retry")
                }
            }catch (e: Exception){
                if(e is SocketException || e is TimeoutException || e is IOException){
                    if(networkRetry-- > 0) {
                        provided = provider()
                        continue
                    }
                }
                throw e;
            }
        }
    }

    suspend fun executeSteps(vararg step:Step){
        var counter = 1
        step.forEach {
            try {
                worker.log(Colors.ANSI_BRIGHT_BLUE + "Starting ${it.name} (" + counter++ + "/" + step.size + ")" + Colors.ANSI_RESET)
                val startTime = currentTimeMillis()
                executeStep(it.process)
                worker.log(Colors.ANSI_BRIGHT_GREEN + "Complete ${it.name} (" + (currentTimeMillis() - startTime) + "ms)" + Colors.ANSI_RESET)
            }catch (e:Exception){
                worker.log(Colors.ANSI_BRIGHT_RED + "Terminated ${it.name}"+ Colors.ANSI_RESET)
                val logFile = File(System.getProperty("user.dir") + "/logs/" + Random.nextInt(99999) + ".log").apply {
                    createNewFile()
                }
                worker.log(Colors.ANSI_BRIGHT_RED + "Saving Logs to ${logFile.path}"+ Colors.ANSI_RESET)
                logFile.writeText(worker.flushLogs())
                logFile.appendText("\n\n\n Exception: ")
                logFile.appendText(e.stackTraceToString())
                throw e
            }
        }
    }


}

interface Step{
    val name:String
    val process:suspend StepExecutor.() -> Unit
}


abstract class SteamStep:Step{
    val ajaxCounter = AtomicInteger()

    @OptIn(ExperimentalContracts::class)//BUG
    suspend fun MockChromeClient.steamAjax(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        return this.post(url) {
            data("count", "" + ajaxCounter.getAndAdd(1))
            header("X-Requested-With", "XMLHttpRequest")
            header("X-Prototype-Version", "1.7")
            header("accept", "text/javascript, text/html, application/xml, text/xml, */*")
            header("Accept-Encoding", "gzip, deflate, br")
            header("Accept-Language", "zh-CN,zh;q=0.9")
            header("Cache-Control", "no-cache")
            block.invoke(this)
        }
    }

}

interface Worker{
    fun log(message:String)

    fun debug(message: String)

    fun flushLogs():String
}

class WorkerImpl(val name:String, val debugMode:Boolean = false):Worker{
    private val builder = StringBuilder()

    override fun log(message: String) {
        println("[$name]  $message")
        builder.append("[LOG]").append(message).append("\n")
    }

    override fun debug(message: String) {
        if(debugMode){
            println("[$name]  $message")
        }
        builder.append("[DEBUG]").append(message).append("\n")
    }

    override fun flushLogs(): String {
        return builder.toString()
    }
}

object WorkerManager{

    private const val MAX_CONCURRENCY = 3;

    private val queue = Channel<Worker>(MAX_CONCURRENCY)

    init {
        repeat(MAX_CONCURRENCY){
            queue.trySend(WorkerImpl("Worker$it"))
        }
    }

    suspend fun <T:Any> withWorker(
        job: suspend Worker.() -> T
    ):T{
        val it = queue.receive()
        return try {
            job.invoke(it)
        }catch (e:Throwable){
            throw e
        }finally {
            queue.send(it)
        }
    }
}


internal object Colors {
    const val ANSI_RESET = "\u001B[0m"
    const val ANSI_BLACK = "\u001B[30m"
    const val ANSI_RED = "\u001B[31m"
    const val ANSI_GREEN = "\u001B[32m"
    const val ANSI_YELLOW = "\u001B[33m"
    const val ANSI_BLUE = "\u001B[34m"
    const val ANSI_PURPLE = "\u001B[35m"
    const val ANSI_CYAN = "\u001B[36m"
    const val ANSI_WHITE = "\u001B[37m"
    const val ANSI_GRAY = "\u001B[90m"
    const val ANSI_BRIGHT_RED = "\u001B[91m"
    const val ANSI_BRIGHT_GREEN = "\u001B[92m"
    const val ANSI_BRIGHT_YELLOW = "\u001B[93m"
    const val ANSI_BRIGHT_BLUE = "\u001B[94m"
    const val ANSI_BRIGHT_MAGENTA = "\u001B[95m"
    const val ANSI_BRIGHT_CYAN = "\u001B[96m"
    const val ANSI_BRIGHT_WHITE = "\u001B[97m"
    const val ANSI_NONE = ""
}


