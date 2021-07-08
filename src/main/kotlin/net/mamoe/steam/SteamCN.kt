package net.mamoe.steam

import kotlinx.serialization.Serializable

//https://rn




//https://rnr.steamchina.com/register

@Serializable
data class CNRegisterRequest(
    val capTicket: String = "",
    val mobilePhone: String,
    val realName: String,
    val residentId: String,
    val secCode: String = "",
    val securityCode: String
)

@Serializable
data class CNRegisterResponse(
    val code:Int,
    val description:String
)


