package accountjar

import Ksoup
import Profile
import SteamAccount
import decode
import kotlinx.serialization.encodeToString
import ksoupJson
import networkRetry


object RemoteJar {
    const val BASE = "http://107.172.156.153:9612"
    val client = Ksoup().apply {
        addIntrinsic {
            it.data("from", "AutoSteam")
            it.header("APIKEY","bf60d285-2d6b-4827-b79a-403a1baa97ac")
            it.networkRetry(10)
        }
    }

    suspend fun popAccount(profile: Profile? = null,chinaAuth:Boolean? = null):SteamAccount{
        val x = client.get("$BASE/pop"){
            if(profile!=null){
                data("profile",profile.name)
            }
            if(chinaAuth!=null){
                data("chinaAuth","" + chinaAuth)
            }
        }.decode<TemplateResponse<SteamAccount>>()
        if(!x.success){
            error(x.message)
        }
        return x.data!!
    }



    suspend fun pushAccount(account: SteamAccount){
        val x = client.post(BASE + "/push"){
            requestBody(ksoupJson.encodeToString(account))
            header("Content-Type", "application/json")
        }.decode<TemplateResponse<String>>()
        if(!x.success){
            error(x.message)
        }
    }
}





