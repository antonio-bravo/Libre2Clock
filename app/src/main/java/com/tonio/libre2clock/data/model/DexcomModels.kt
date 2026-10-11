package com.tonio.libre2clock.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class GlucoseSourceProvider {
    LIBRE_LINK_UP,
    DEXCOM_SHARE
}

enum class DexcomServerRegion(val displayName: String, val baseUrl: String) {
    OUS("Europa e Internacional (OUS)", "https://shareous1.dexcom.com/ShareWebServices/Services/"),
    US("Estados Unidos (US)", "https://share2.dexcom.com/ShareWebServices/Services/")
}

@JsonClass(generateAdapter = false)
data class DexcomAuthRequest(
    @param:Json(name = "accountName") val accountName: String,
    @param:Json(name = "password") val password: String,
    @param:Json(name = "applicationId") val applicationId: String = DEXCOM_APPLICATION_ID
)

@JsonClass(generateAdapter = false)
data class DexcomLoginByIdRequest(
    @param:Json(name = "accountId") val accountId: String,
    @param:Json(name = "password") val password: String,
    @param:Json(name = "applicationId") val applicationId: String = DEXCOM_APPLICATION_ID
)

@JsonClass(generateAdapter = false)
data class DexcomGlucoseReading(
    @param:Json(name = "WT") val wt: String? = null,
    @param:Json(name = "ST") val st: String? = null,
    @param:Json(name = "DT") val dt: String? = null,
    @param:Json(name = "Value") val value: Int = 0,
    @param:Json(name = "Trend") val trend: String? = null,
    @param:Json(name = "Epoch") val epoch: Long? = null
)

const val DEXCOM_APPLICATION_ID = "d86868a4-1909-418b-a812-366d288127cd"
