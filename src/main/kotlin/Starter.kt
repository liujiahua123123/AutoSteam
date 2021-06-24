import kotlinx.coroutines.runBlocking
import java.net.Authenticator
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Starter {
    companion object{
        @JvmStatic
        fun main(args: Array<String>){
            runBlocking {
                //headers: referer
                System.setProperty("sun.net.http.allowRestrictedHeaders", "true")


                //proxy
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
                System.setProperty("jdk.http.auth.proxying.disabledSchemes", "")
                //Authenticator.setDefault(ProxyAuthenticator)

                //ssl
                System.setProperty("https.protocols", "TLSv1.2")
                System.setProperty("jdk.tls.client.protocols", "TLSv1.2")

                val context: SSLContext = SSLContext.getInstance("TLS")
                val trustManagerArray: Array<TrustManager> = arrayOf(object : X509TrustManager {
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                    }

                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                })
                context.init(null, trustManagerArray, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
                HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

                main()
            }
        }
    }
}