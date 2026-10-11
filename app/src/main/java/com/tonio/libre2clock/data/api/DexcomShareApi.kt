package com.tonio.libre2clock.data.api

import com.tonio.libre2clock.data.model.DexcomAuthRequest
import com.tonio.libre2clock.data.model.DexcomGlucoseReading
import com.tonio.libre2clock.data.model.DexcomLoginByIdRequest
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface DexcomShareApi {

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("General/AuthenticatePublisherAccount")
    suspend fun authenticate(
        @Body request: DexcomAuthRequest
    ): String

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("General/LoginPublisherAccountById")
    suspend fun loginById(
        @Body request: DexcomLoginByIdRequest
    ): String

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("Publisher/ReadPublisherLatestGlucoseValues")
    suspend fun readLatestGlucoseValues(
        @Query("sessionId") sessionId: String,
        @Query("minutes") minutes: Int = 1440,
        @Query("maxCount") maxCount: Int = 288
    ): List<DexcomGlucoseReading>
}
