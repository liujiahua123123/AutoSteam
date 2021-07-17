package net.mamoe.step

import MockChromeClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
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

interface proxyProvider{
    operator fun invoke():(Connection) -> Unit
}

class StepExecutor(val worker: Worker, val component: Component, val client: MockChromeClient, val provider: proxyProvider){

    private var provided = provider()

    init {
        client.addIntrinsic{
            provided(it)
        }
    }

    fun log(message: String){
        worker.log(message)
    }

    fun debug(message: String){
        worker.log(message)
    }

    private suspend fun executeStep(
        step: suspend StepExecutor.() -> Unit
    ){
        var maxRetry = 15

        while (true) {
            try {
                step.invoke(this)
            } catch (e: RerunStepException) {
                if(e.changeProxy){
                    provided = provider()
                }
                if(maxRetry -- > 0) {
                    continue
                }else{
                    error("Too many Retry")
                }
            }
        }
    }

    suspend fun executeSteps(vararg step:Step){
        step.forEach {
            try {
                worker.log("Start step ${it.name}")
                executeStep(it.process)
                worker.log("Complete step ${it.name}")
            }catch (e:Exception){
                worker.log("Terminated!")
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
}

private class WorkerImpl(val name:String):Worker{
    override fun log(message: String) {
        println("[$name] $message")
    }

    override fun debug(message: String) {
        println("[$name] $message")
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


suspend fun main(){

}
