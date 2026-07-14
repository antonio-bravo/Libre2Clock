package com.tonio.libre2clock.data.api

import com.tonio.libre2clock.data.model.ConnectionsResponse
import com.tonio.libre2clock.data.model.GlucoseResponse
import com.tonio.libre2clock.data.model.LoginRequest
import com.tonio.libre2clock.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface LibreLinkUpApi {
    @POST("llu/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @GET("llu/connections")
    suspend fun getConnections(): ConnectionsResponse

    @GET("llu/connections/{patientId}/graph")
    suspend fun getGlucoseGraph(
        @Path("patientId") patientId: String
    ): GlucoseResponse
}
