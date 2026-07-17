package com.tonio.libre2clock.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @param:Json(name = "email") val email: String,
    @param:Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @param:Json(name = "status") val status: Int,
    @param:Json(name = "data") val data: LoginData?
)

@JsonClass(generateAdapter = true)
data class LoginData(
    @param:Json(name = "user") val user: User,
    @param:Json(name = "authTicket") val authTicket: AuthTicket
)

@JsonClass(generateAdapter = true)
data class User(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "email") val email: String
)

@JsonClass(generateAdapter = true)
data class AuthTicket(
    @param:Json(name = "token") val token: String,
    @param:Json(name = "expires") val expires: Long
)

@JsonClass(generateAdapter = true)
data class ConnectionsResponse(
    @param:Json(name = "status") val status: Int,
    @param:Json(name = "data") val data: List<Connection>?
)

@JsonClass(generateAdapter = true)
data class Connection(
    @param:Json(name = "patientId") val patientId: String,
    @param:Json(name = "firstName") val firstName: String,
    @param:Json(name = "lastName") val lastName: String,
    @param:Json(name = "glucoseMeasurement") val glucoseMeasurement: GlucoseMeasurement?
)

@JsonClass(generateAdapter = true)
data class GlucoseResponse(
    @param:Json(name = "status") val status: Int,
    @param:Json(name = "data") val data: GlucoseData?
)

@JsonClass(generateAdapter = true)
data class GlucoseData(
    @param:Json(name = "connection") val connection: Connection,
    @param:Json(name = "activeSensors") val activeSensors: List<ActiveSensor>?,
    @param:Json(name = "graphData") val graphData: List<GlucoseMeasurement>
)

@JsonClass(generateAdapter = true)
data class ActiveSensor(
    @param:Json(name = "sensor") val sensor: Sensor
)

@JsonClass(generateAdapter = true)
data class Sensor(
    @param:Json(name = "sn") val serialNumber: String,
    @param:Json(name = "a") val activationTimestamp: Long, // Unix epoch seconds
    @param:Json(name = "w") val warmupMinutes: Int
)

data class SensorStatus(
    val daysRemaining: Int,
    val expiryDate: String,
    val serialNumber: String
)

@JsonClass(generateAdapter = true)
@Serializable
data class GlucoseMeasurement(
    @param:Json(name = "FactoryTimestamp") val factoryTimestamp: String,
    @param:Json(name = "Timestamp") val timestamp: String,
    @param:Json(name = "type") val type: Int,
    @param:Json(name = "ValueInMgPerDl") val valueInMgPerDl: Int,
    @param:Json(name = "TrendArrow") val trendArrow: Int?,
    @param:Json(name = "MeasurementColor") val measurementColor: Int?,
    @param:Json(name = "Value") val value: Int,
    val calibratedValue: Int = value
)
