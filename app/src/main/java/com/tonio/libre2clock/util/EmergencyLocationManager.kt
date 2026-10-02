package com.tonio.libre2clock.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class EmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val mapUrl: String = "https://maps.google.com/?q=$latitude,$longitude"
)

class EmergencyLocationManager(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return finePerm || coarsePerm
    }

    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMillis: Long = 10000L): EmergencyLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null

        return@withContext try {
            withTimeoutOrNull(timeoutMillis) {
                // 1. Intentar con FusedLocationProviderClient (Google Play Services)
                val fusedLocation = try {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    val cancellationSource = CancellationTokenSource()
                    
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationSource.token
                    ).await() ?: fusedClient.lastLocation.await()
                } catch (_: Exception) {
                    null
                }

                // 2. Fallback a LocationManager tradicional si FusedLocation devuele null
                val finalLocation = fusedLocation ?: getBestLocationFromSystemManager()

                finalLocation?.let {
                    EmergencyLocation(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracy = it.accuracy
                    )
                }
            }
        } catch (_: Exception) {
            getBestLocationFromSystemManager()?.let {
                EmergencyLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracy = it.accuracy
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLocationFromSystemManager(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null

        for (provider in providers) {
            val l = try { locationManager.getLastKnownLocation(provider) } catch (_: Exception) { null } ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        return bestLocation
    }
}
