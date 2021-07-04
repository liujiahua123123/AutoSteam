import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.get
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import myproxy.Main
import java.util.*
import kotlin.concurrent.thread

fun main() {
    thread {
        Thread.sleep(3000)
        runBlocking {
            val client = HttpClient()
            client.get<HttpResponse>("http://localhost:8188") {
                header("jumpserver-target", String(Base64.getEncoder().encode("https://mirai.mamoe.net".toByteArray())))

            }.let {
                println(it)
            }
        }
    }

    Main.main(emptyArray())
}
