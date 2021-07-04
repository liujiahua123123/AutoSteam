@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalContracts::class)

package net.mamoe

import MockChromeClient
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.jsoup.Connection
import org.jsoup.Connection.Method.*
import org.jsoup.Jsoup
import java.math.BigInteger
import java.net.*
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.*
import javax.crypto.Cipher


interface FormData

@Suppress("UNSAFE_CAST")
fun Connection.data(formData: FormData){
    formData::class.memberProperties.forEach {
        data(it.name, (it as KProperty1<FormData, *>).get(formData).toString())
    }
}

@OptIn(InternalAPI::class)
fun steamPasswordRSA(mod:String, exp:String, password: String):String{

    val keyFactory = KeyFactory.getInstance("RSA")
    var key = keyFactory.generatePublic(
        RSAPublicKeySpec(
        BigInteger(mod,16),
        BigInteger(exp,16),//exp
    )
    )

    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val cipherData = cipher.doFinal(password.toByteArray())

    val b64 = cipherData.encodeBase64()
    return b64

    //return RSA(mod,exp).encrypt(password)
}

val SteamJson = Json {
    this.ignoreUnknownKeys = true
    this.isLenient = true
    this.encodeDefaults = true
    this.ignoreUnknownKeys = true
}

inline fun <reified T : Any> String.deserialize(): T = SteamJson.decodeFromString(this)


@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> T.serialize(format: StringFormat, serializer: KSerializer<T> = format.serializersModule.serializer()): String {
    return format.encodeToString(serializer, this)
}


inline fun <reified T:Any> Connection.Response.decode():T{
    return body().deserialize()
}

/**
 * HTTP协议的扩充 - Him188JumpServer
 *
 */


/**
 * Steam特制的Client, 增加了steam独有的ajax参数
 */
open class SteamClient: MockChromeClient(){
    internal val ajaxCounter = AtomicInteger(1)

    @PublishedApi
    internal fun nextAjaxCount():String = "" + ajaxCounter.addAndGet(1)

    @OptIn(ExperimentalContracts::class)//BUG
    suspend inline fun ajax(url: String, block: Connection.() -> Unit = {}): Connection.Response {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return this.post(url){
            data("count",nextAjaxCount())
            header("X-Requested-With","XMLHttpRequest")
            header("X-Prototype-Version","1.7")
            header("accept","text/javascript, text/html, application/xml, text/xml, */*")
            header("Accept-Encoding","gzip, deflate, br")
            header("Accept-Language","zh-CN,zh;q=0.9")
            header("Cache-Control","no-cache")
            block.invoke(this)
        }
    }
}


/**
 * Steam Store 特制的Client, 自动添加headers
 */
open class SteamStoreClient: SteamClient(){

    var referer = "https://store.steampowered.com"

    init {
        addIntrinsic{
            it.header("Origin","https://store.steampowered.com")
            it.header("Referer",referer)
        }
    }
}


/*
/**
 * Code from Github, Java Steam Account Generator
 * Add random to replace constant 0x01
 *
 */
internal class RSA(modHex: String?, expHex: String?) {
    private val modulus: BigInteger
    private val exponent: BigInteger
    private val charset = Charset.forName("ISO-8859-1")

    @OptIn(InternalAPI::class)
    fun encrypt(password: String): String {
        val data = pkcs1pad2(password.toByteArray(charset), modulus.bitLength() + 7 shr 3)
        val d2 = data.modPow(exponent, modulus)
        var dataHex = d2.toString(16)
        if (dataHex.length and 1 == 1) {
            dataHex = "0$dataHex"
        }
        val encrypted = hexStringToByteArray(dataHex)
        return encrypted.encodeBase64()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun pkcs1pad2(data: ByteArray, n: Int): BigInteger {
        var n = n
        val bytes = ByteArray(n)
        var i = data.size - 1
        while (i >= 0 && n > 11) {
            bytes[--n] = data[i--]
        }
        bytes[--n] = 0
        while (n > 2) {
            bytes[--n] = Random.Default.nextBytes(1)[0]
        }
        bytes[--n] = 0x2
        bytes[--n] = 0
        return BigInteger(bytes)
    }

    init {
        modulus = BigInteger(modHex, 16)
        exponent = BigInteger(expHex, 16)
    }
}

 */


