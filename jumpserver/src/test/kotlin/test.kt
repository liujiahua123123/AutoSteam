import kotlinx.coroutines.runBlocking
import myproxy.Main
import kotlin.concurrent.thread

fun main() {
    thread {
        Thread.sleep(3000)
        runBlocking {
            println(
                Ksoup()
                .get("https://mirai.mamoe.net") {
                }.body()
            )
        }
    }

    Main.main(emptyArray())
}
