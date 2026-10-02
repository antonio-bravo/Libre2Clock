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

    /**
     * Abre la pantalla de configuración de ubicación para que el usuario active la ubicación del sistema si lo desea.
     */
    fun promptEnableLocation() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        timeoutMillis: Long = 6000L,
        forceHighAccuracy: Boolean = false
    ): EmergencyLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext getBestLastKnownLocation()?.toEmergencyLocation("last_known_no_perm")
        }

        return@withContext try {
            val freshLocation = withTimeoutOrNull(timeoutMillis) {
                var location: Location? = null

                // A) Probar con Fused Location (Google Play Services) - Torres Móviles + Wi-Fi + GPS
                location = if (forceHighAccuracy) {
                    fetchFusedLocation(Priority.PRIORITY_HIGH_ACCURACY) 
                        ?: fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                } else {
                    fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY) 
                        ?: fetchFusedLocation(Priority.PRIORITY_HIGH_ACCURACY)
                }

                // B) Si Fused Location devuelve nulo, SOLICITAR ACTIVAMENTE A LA RED DE TORRES MÓVILES (NETWORK_PROVIDER)
                if (location == null) {
                    location = fetchFreshNetworkLocation()
                }

                // C) Si sigue siendo nulo, SOLICITAR ACTIVAMENTE AL GPS DE SATÉLITES (GPS_PROVIDER)
                if (location == null) {
                    location = fetchFreshGpsLocation()
                }

                location
            }

            // D) Si la captura en tiempo real fue nula o expiró por tiempo, usar la mejor ubicación previa conocida
            val finalLoc = freshLocation ?: getBestLastKnownLocation()
            finalLoc?.toEmergencyLocation(finalLoc.provider ?: "network_cell_fallback")
        } catch (_: Exception) {
            getBestLastKnownLocation()?.toEmergencyLocation("last_known_fallback")
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
    private suspend fun getBestLastKnownLocation(): Location? {
        var bestLocation: Location? = null
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            bestLocation = withTimeoutOrNull(2000) { fusedClient.lastLocation.await() }
        } catch (_: Exception) { }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val l = try { locationManager.getLastKnownLocation(provider) } catch (_: Exception) { null } ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
        }
        return bestLocation
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
