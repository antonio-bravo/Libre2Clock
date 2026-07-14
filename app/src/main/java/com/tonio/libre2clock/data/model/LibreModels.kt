package com.tonio.libre2clock.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "status") val status: Int,
    @Json(name = "data") val data: LoginData?
)

@JsonClass(generateAdapter = true)
data class LoginData(
    @Json(name = "user") val user: User,
    @Json(name = "authTicket") val authTicket: AuthTicket
)

@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String
)

@JsonClass(generateAdapter = true)
data class AuthTicket(
    @Json(name = "token") val token: String,
    @Json(name = "expires") val expires: Long
)

@JsonClass(generateAdapter = true)
data class ConnectionsResponse(
    @Json(name = "status") val status: Int,
    @Json(name = "data") val data: List<Connection>?
)

@JsonClass(generateAdapter = true)
data class Connection(
    @Json(name = "patientId") val patientId: String,
    @Json(name = "firstName") val firstName: String,
    @Json(name = "lastName") val lastName: String,
    @Json(name = "glucoseMeasurement") val glucoseMeasurement: GlucoseMeasurement?
)

@JsonClass(generateAdapter = true)
data class GlucoseResponse(
    @Json(name = "status") val status: Int,
    @Json(name = "data") val data: GlucoseData?
)

@JsonClass(generateAdapter = true)
data class GlucoseData(
    @Json(name = "connection") val connection: Connection,
    @Json(name = "activeSensors") val activeSensors: List<ActiveSensor>?,
    @Json(name = "graphData") val graphData: List<GlucoseMeasurement>
)

@JsonClass(generateAdapter = true)
data class ActiveSensor(
    @Json(name = "sensor") val sensor: Sensor
)

@JsonClass(generateAdapter = true)
data class Sensor(
    @Json(name = "sn") val serialNumber: String,
    @Json(name = "a") val activationTimestamp: Long, // Unix epoch seconds
    @Json(name = "w") val warmupMinutes: Int
)

data class SensorStatus(
    val daysRemaining: Int,
    val expiryDate: String,
    val serialNumber: String
)

@JsonClass(generateAdapter = true)
data class GlucoseMeasurement(
    @Json(name = "FactoryTimestamp") val factoryTimestamp: String,
    @Json(name = "Timestamp") val timestamp: String,
    @Json(name = "type") val type: Int,
    @Json(name = "ValueInMgPerDl") val valueInMgPerDl: Int,
    @Json(name = "TrendArrow") val trendArrow: Int?,
    @Json(name = "MeasurementColor") val measurementColor: Int?,
    @Json(name = "Value") val value: Int,
    val calibratedValue: Int = value
)
