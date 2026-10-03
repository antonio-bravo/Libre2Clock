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
import kotlinx.coroutines.async
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

    private val eventLogger = com.tonio.libre2clock.di.AppContainer.provideEventLogManager(context)

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
    suspend fun getFreshCellularLocation(timeoutMillis: Long = 10000L): EmergencyLocation? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val gpsEnabled = isGpsSatelliteEnabled()
        val networkEnabled = isLocationEnabled()

        eventLogger.log(
            LogLevel.INFO,
            "LocationDiagnostics",
            "Iniciando captura de Red Móvil (Antenas). Permisos: FINE=$hasFine, COARSE=$hasCoarse. Estado: GPS=$gpsEnabled, NetworkProvider=$networkEnabled"
        )

        if (!hasFine && !hasCoarse) {
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "Fallo Red Móvil: Sin permiso FINE ni COARSE")
            val lastKnown = getBestNetworkLastKnownLocation()
            return@withContext lastKnown?.toEmergencyLocation("last_known_no_perm")
        }

        val loc = withTimeoutOrNull(timeoutMillis) {
            val fusedDeferred = async { 
                fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, "FusedBalanced") 
            }
            val netDeferred = async { 
                fetchFreshNetworkLocation("SystemNetworkProvider") 
            }
            
            fusedDeferred.await() ?: netDeferred.await()
        } ?: getBestNetworkLastKnownLocation().also {
            if (it != null) eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "Red Móvil: Usando última ubicación conocida (accuracy=${it.accuracy}m, provider=${it.provider})")
            else eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "Red Móvil: La captura en tiempo real agotó tiempo ($timeoutMillis ms) y no hay ubicación previa en caché.")
        }

        val result = loc?.toEmergencyLocation("Red Móvil (Antenas)")
        if (result != null) {
            eventLogger.log(
                LogLevel.INFO,
                "LocationDiagnostics",
                "✅ Éxito Red Móvil: Lat=${result.latitude}, Lng=${result.longitude}, Precisión=${result.accuracy}m, Provider=${result.provider}"
            )
        } else {
            eventLogger.log(
                LogLevel.ERROR,
                "LocationDiagnostics",
                "❌ Fallo Red Móvil: Imposible obtener coordenadas de la red celular."
            )
        }

        return@withContext result
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshWifiLocation(timeoutMillis: Long = 10000L): EmergencyLocation? = withContext(Dispatchers.IO) {
        eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "Iniciando captura de Wi-Fi/Red Balanceada.")
        if (!hasLocationPermission()) {
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "Fallo Wi-Fi: Permisos denegados")
            return@withContext getBestNetworkLastKnownLocation()?.toEmergencyLocation("last_known_wifi")
        }
        
        val loc = withTimeoutOrNull(timeoutMillis) {
            fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, "FusedWifi")
        } ?: getBestNetworkLastKnownLocation()

        val result = loc?.toEmergencyLocation("Wi-Fi / Red Balanceada")
        if (result != null) {
            eventLogger.log(
                LogLevel.INFO,
                "LocationDiagnostics",
                "✅ Éxito Wi-Fi: Lat=${result.latitude}, Lng=${result.longitude}, Precisión=${result.accuracy}m"
            )
        } else {
            eventLogger.log(LogLevel.ERROR, "LocationDiagnostics", "❌ Fallo Wi-Fi: Imposible obtener coordenadas por Wi-Fi.")
        }

        return@withContext result
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        timeoutMillis: Long = 11000L
    ): EmergencyLocation? = withContext(Dispatchers.IO) {
        eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "Iniciando captura completa de ubicación (Pipeline Completo)")
        if (!hasLocationPermission()) {
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "Fallo Pipeline: Sin permisos de ubicación")
            return@withContext getBestFreshLastKnownLocation()?.toEmergencyLocation("last_known_no_perm")
        }

        return@withContext try {
            val freshLocation = withTimeoutOrNull(timeoutMillis) {
                var location: Location? = null

                if (isGpsSatelliteEnabled()) {
                    eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "Pipeline: Probando GPS Satelital (Alta Precisión)")
                    location = withTimeoutOrNull(5000L) {
                        fetchFusedLocation(Priority.PRIORITY_HIGH_ACCURACY, "FusedGPS") 
                            ?: fetchFreshGpsLocation("SystemGPS")
                    }
                }

                if (location == null) {
                    eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "Pipeline: GPS nulo o inactivo. Probando Red Celular/Wi-Fi")
                    location = withTimeoutOrNull(5000L) {
                        fetchFusedLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, "FusedBalanced") 
                            ?: fetchFreshNetworkLocation("SystemNetwork")
                    }
                }

                if (location == null) {
                    eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "Pipeline: Probando ubicación previa guardada en memoria")
                    location = getBestFreshLastKnownLocation()
                }

                location
            }

            val finalLoc = freshLocation ?: getBestFreshLastKnownLocation() ?: getBestNetworkLastKnownLocation()
            val result = finalLoc?.toEmergencyLocation(finalLoc.provider ?: "network_cell_fallback")
            if (result != null) {
                eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "✅ Éxito Pipeline Completo: Lat=${result.latitude}, Lng=${result.longitude}, Precisión=${result.accuracy}m, Provider=${result.provider}")
            } else {
                eventLogger.log(LogLevel.ERROR, "LocationDiagnostics", "❌ Fallo Pipeline Completo: No se obtuvieron coordenadas por ninguna vía.")
            }
            result
        } catch (e: Exception) {
            eventLogger.log(LogLevel.ERROR, "LocationDiagnostics", "Excepción en Pipeline: ${e.message}")
            getBestNetworkLastKnownLocation()?.toEmergencyLocation("network_cell_fallback")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFusedLocation(priority: Int, label: String): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationSource = CancellationTokenSource()
        
        return try {
            val loc = fusedClient.getCurrentLocation(priority, cancellationSource.token).await()
            if (loc != null) {
                eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "[$label] FusedLocation devolvió: Acc=${loc.accuracy}m, Lat=${loc.latitude}, Lng=${loc.longitude}")
            } else {
                eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] FusedLocation devolvió NULL")
            }
            loc
        } catch (e: CancellationException) {
            cancellationSource.cancel()
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] FusedLocation Cancelado")
            throw e
        } catch (e: Exception) {
            cancellationSource.cancel()
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] Error en FusedLocation: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFreshNetworkLocation(label: String = "SystemNetwork"): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] LocationManager.NETWORK_PROVIDER está DESACTIVADO en el sistema.")
            return null
        }

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
                        if (loc != null) {
                            eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "[$label] NetworkProvider devolvió: Acc=${loc.accuracy}m, Lat=${loc.latitude}, Lng=${loc.longitude}")
                        } else {
                            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] NetworkProvider devolvió NULL")
                        }
                        if (continuation.isActive) continuation.resume(loc)
                    }
                } catch (e: Exception) {
                    eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] Error en NetworkProvider: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } else {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "[$label] NetworkProvider listener devolvió: Acc=${loc.accuracy}m")
                        if (continuation.isActive) continuation.resume(loc)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] NetworkProvider fue desactivado")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation {
                    try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                }
                try {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, context.mainLooper)
                } catch (e: Exception) {
                    eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "[$label] Error al solicitar single update: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFreshGpsLocation(label: String = "SystemGPS"): Location? {
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
        val maxAgeMs = 15 * 60 * 1000L

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val fusedLast = withTimeoutOrNull(2000) { fusedClient.lastLocation.await() }
            if (fusedLast != null && isRecentLocation(fusedLast, maxAgeMs)) {
                eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "LastKnown: Fused valid (age=${(System.currentTimeMillis()-fusedLast.time)/1000}s, acc=${fusedLast.accuracy}m)")
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
                    eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "LastKnown: $provider valid (age=${(System.currentTimeMillis()-l.time)/1000}s, acc=${l.accuracy}m)")
                }
            }
        }
        return bestLocation
    }

    @SuppressLint("MissingPermission")
    private fun getBestNetworkLastKnownLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val networkLoc = try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
        val passiveLoc = try { locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null }
        
        val res = when {
            networkLoc != null && passiveLoc != null -> if (networkLoc.time >= passiveLoc.time) networkLoc else passiveLoc
            networkLoc != null -> networkLoc
            else -> passiveLoc
        }
        if (res != null) {
            eventLogger.log(LogLevel.INFO, "LocationDiagnostics", "BestNetworkLastKnown: Prov=${res.provider}, Acc=${res.accuracy}m, Age=${(System.currentTimeMillis()-res.time)/1000}s")
        } else {
            eventLogger.log(LogLevel.WARNING, "LocationDiagnostics", "BestNetworkLastKnown: Sin registros en NETWORK_PROVIDER ni PASSIVE_PROVIDER")
        }
        return res
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
