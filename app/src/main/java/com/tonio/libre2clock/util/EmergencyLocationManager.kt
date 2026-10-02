package com.tonio.libre2clock.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class EmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String = "unknown",
    val mapUrl: String = "https://maps.google.com/?q=$latitude,$longitude"
)

class EmergencyLocationManager(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return finePerm || coarsePerm
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun isGpsSatelliteEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun promptEnableLocation() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshCellularLocation(timeoutMillis: Long = 5000L): EmergencyLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        val loc = withTimeoutOrNull(timeoutMillis) {
            fetchFreshNetworkLocation()
        } ?: getBestFreshLastKnownLocation()
        return@withContext loc?.toEmergencyLocation("Red Móvil (Antenas)")
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshWifiLocation(timeoutMillis: Long = 5000L): EmergencyLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        val loc = withTimeoutOrNull(timeoutMillis) {
            fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        } ?: getBestFreshLastKnownLocation()
        return@withContext loc?.toEmergencyLocation("Wi-Fi / Red Balanceada")
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        timeoutMillis: Long = 11000L
    ): EmergencyLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext null
        }

        return@withContext try {
            withTimeoutOrNull(timeoutMillis) {
                var location: Location? = null

                // =========================================================================
                // PRIORIDAD 1: Satélites GPS (Alta Precisión) si el GPS está activo (hasta 5s)
                // =========================================================================
                if (isGpsSatelliteEnabled()) {
                    location = withTimeoutOrNull(5000L) {
                        fetchFusedLocation(Priority.PRIORITY_HIGH_ACCURACY) ?: fetchFreshGpsLocation()
                    }
                }

                // =========================================================================
                // PRIORIDAD 2: Antenas Móviles / Wi-Fi si el GPS no está activo o falló (hasta 5s)
                // =========================================================================
                if (location == null) {
                    location = withTimeoutOrNull(5000L) {
                        fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY) ?: fetchFreshNetworkLocation()
                    }
                }

                // =========================================================================
                // PRIORIDAD 3: Respaldo de última ubicación conocida reciente (< 15 min)
                // =========================================================================
                if (location == null) {
                    location = getBestFreshLastKnownLocation()
                }

                // Si se obtuvo ubicación en cualquiera de las prioridades, retornarla
                location?.toEmergencyLocation(location.provider ?: "network_cell_fallback")
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFusedLocation(priority: Int): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationSource = CancellationTokenSource()
        
        return try {
            fusedClient.getCurrentLocation(priority, cancellationSource.token).await()
        } catch (e: CancellationException) {
            cancellationSource.cancel()
            throw e
        } catch (_: Exception) {
            cancellationSource.cancel()
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFreshNetworkLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(
                        LocationManager.NETWORK_PROVIDER,
                        signal,
                        context.mainExecutor
                    ) { loc ->
                        if (continuation.isActive) continuation.resume(loc)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } else {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(loc)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation {
                    try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                }
                try {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, context.mainLooper)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFreshGpsLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(
                        LocationManager.GPS_PROVIDER,
                        signal,
                        context.mainExecutor
                    ) { loc ->
                        if (continuation.isActive) continuation.resume(loc)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } else {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(loc)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation {
                    try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                }
                try {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, context.mainLooper)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getBestFreshLastKnownLocation(): Location? {
        var bestLocation: Location? = null
        val maxAgeMs = 15 * 60 * 1000L // Máximo 15 minutos de antigüedad

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val fusedLast = withTimeoutOrNull(2000) { fusedClient.lastLocation.await() }
            if (fusedLast != null && isRecentLocation(fusedLast, maxAgeMs)) {
                bestLocation = fusedLast
            }
        } catch (_: Exception) { }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val l = try { locationManager.getLastKnownLocation(provider) } catch (_: Exception) { null } ?: continue
                if (!isRecentLocation(l, maxAgeMs)) continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
        }
        return bestLocation
    }

    private fun isRecentLocation(location: Location, maxAgeMs: Long): Boolean {
        val ageMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
            val locationAgeNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            locationAgeNanos / 1_000_000L
        } else {
            System.currentTimeMillis() - location.time
        }
        return ageMs in 0..maxAgeMs
    }

    private fun Location.toEmergencyLocation(providerName: String): EmergencyLocation {
        return EmergencyLocation(
            latitude = this.latitude,
            longitude = this.longitude,
            accuracy = this.accuracy,
            provider = providerName,
            mapUrl = "https://maps.google.com/?q=${this.latitude},${this.longitude}"
        )
    }
}
