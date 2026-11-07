package com.tonio.libre2clock.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

enum class AppRole {
    PATIENT,
    FOLLOWER
}

@Keep
@Serializable
data class ShareInvite(
    @PropertyName("code") val code: String = "",
    @PropertyName("ownerUid") val ownerUid: String = "",
    @PropertyName("patientId") val patientId: String = "",
    @PropertyName("label") val label: String = "",
    @PropertyName("createdAtMs") val createdAtMs: Long = System.currentTimeMillis(),
    @PropertyName("expiresAtMs") val expiresAtMs: Long? = null,
    @get:PropertyName("isUsed") @set:PropertyName("isUsed") var isUsed: Boolean = false,
    @get:PropertyName("active") @set:PropertyName("active") var active: Boolean = true
)

@Keep
@Serializable
data class FollowerInfo(
    @PropertyName("followerUid") val followerUid: String = "",
    @PropertyName("label") val label: String = "",
    @get:PropertyName("active") @set:PropertyName("active") var active: Boolean = true,
    @PropertyName("createdAtMs") val createdAtMs: Long = System.currentTimeMillis(),
    @PropertyName("expiresAtMs") val expiresAtMs: Long? = null
)

@Keep
@Serializable
data class FollowedPatient(
    val patientUid: String = "",
    val patientId: String = "",
    val label: String = "",
    val addedAtMs: Long = System.currentTimeMillis()
)
