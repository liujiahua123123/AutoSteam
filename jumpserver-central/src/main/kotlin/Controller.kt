import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClientFactory
import java.io.File
import java.nio.charset.Charset
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.writeBytes

@Serializable
data class JumpServer(
    val ip: String,
    val password: String
){
    fun createSSH():JumpServerSSHClient{
        val client = SshClient.setUpDefaultClient()
        client.start()

        client.connect("root", ip, 22)
            .verify()
            .session.run {
                addPasswordIdentity(password)
                auth().verify()
                return JumpServerSSHClient(this,this@JumpServer)
            }
    }


    suspend fun test():TestResult{
        return try {
            val r = Ksoup().get("https://api.ipify.org/") {
                jumpServer(ip, 8188)
                networkRetry(5)
            }.body()
            TestResult(true,r)
        }catch (e:Exception){
            TestResult(false,e.message?:e.localizedMessage)
        }

    }

    data class TestResult(
        val connectable:Boolean,
        val message:String
    )
}

class JumpServerSSHClient(
    private val session: ClientSession,
    val server: JumpServer
) {

    val sftp = SftpClientFactory.instance().createSftpFileSystem(session)

    fun execute(cmd: String) {
        println("[" + server.ip + "]: Executing $cmd")
        kotlin.runCatching { session.executeRemoteCommand(cmd, System.out, System.err, Charset.defaultCharset()) }
            .onFailure {
                println(it.message)
            }
    }

    companion object {
        init {
            Logger.getLogger("io.netty").level = Level.OFF
            Logger.getGlobal().level = Level.SEVERE
            Logger.getLogger("org.apache.ftpserver.listener.nio.FtpLoggingFilter").level = Level.SEVERE
            Logger.getLogger("io.netty.handler.logging.LoggingHandler").level = Level.SEVERE
            org.slf4j.LoggerFactory.getLogger("io.netty")
        }
    }

    fun uploadFile(
        remotePath: String,
        localFile: File
    ) {
        println("[" + server.ip + "]: Uploading file $localFile to $remotePath")

        // SftpClientFactory.instance().createSftpClient(session).openRemoteFileChannel(remotePath).writePacket {
        //     writeFully(localFile.readBytes())
        // }

        // Paths.get(URI("sftp://root:$password@104.168.102.103/home/chiasense.zip"))
        //     .writeBytes(localFile.readBytes())


        var int = 5

        while (true) {
            try {
                sftp.run {
                    getPath(remotePath).writeBytes(localFile.readBytes())
                    // localFile.inputStream().withOut(getPath(remotePath).outputStream()) { out ->
                    //     this.copyTo(out)
                    // }
                }
                break
            }catch (e:Exception){
                if(int-- < 0){
                    throw e
                }
                continue
            }
        }

    }


}


fun JumpServerSSHClient.installJumpServer(){

    execute("yum update -y")
    execute("yum install -y zip")
    execute("yum install -y unzip")
    execute("yum install -y java-11-openjdk")
    execute("yum install -y tmux")
    execute("systemctl stop firewalld.service")
    execute("systemctl disable firewalld.service")

    execute("cd /root")
    execute("mkdir /root/jumpserver")
    execute("cd /root/jumpserver")
    execute("ls jumpserver")

    val sessionName = "jumpserver"

    execute("tmux kill-session -t $sessionName")

    uploadFile("/root/jumpserver/jumpserver.jar",File(System.getProperty("user.dir") + "/jumpserver/build/libs/jumpserver-1.0-SNAPSHOT-all.jar"))
    execute("ls jumpserver")

    execute("chmod 777 /root/jumpserver/jumpserver.jar")

    execute("""tmux new -d -s $sessionName""")
    execute("""tmux send-keys -t $sessionName "java -jar /root/jumpserver/jumpserver.jar" ENTER""")

    println(">> JumpServer is not running on " + server.ip + ":8188")
}



suspend fun main(){
//broken
    /**
     *
    23.94.22.104-NMm5rJwM73q477PNqj
    172.245.112.70-Cv6wdj2RGFbZ6358nE


    192.227.131.113-n6BFpS4A20yYyVp3z4
     */


    val cjh = """
        107.174.146.144-2Nm6Uq28KZ3q8gGdNr
        23.95.213.143-pFU89Tr0n5RZ6yhpT1
        23.94.182.111-7lWlp4U59zvX8IVjJ8
        23.94.190.104-7IQCaymwENg432Z8k7
        172.245.6.145-Bwp8E893gxJG2L4qkJ
        172.245.156.111-QqA1ti25B76CH8Mkmr
        107.173.24.129-kImXLusX06S62Lfh29
        104.168.96.118-2VezE9N9Z9aBpUgn06
        107.175.87.113-9BncENr1W7fR7tV1l5
        107.172.156.106-6Qadz27rF67m5ZVHfI
        104.168.46.119-UoA902hPbeJT3p6iI5
    """.trimIndent().split("\n").map {
        JumpServer(it.split("-")[0],it.split("-")[1])
    }


    cjh.forEach {
        GlobalScope.launch {
            val x = it.test()
            if(x.connectable) {
                println("jumpserver://" +it.ip + ":8188")
            }else{
                println(x)
            }
        }
    }

    delay(99999999999)


}