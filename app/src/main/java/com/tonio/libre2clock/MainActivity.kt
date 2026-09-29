package com.tonio.libre2clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.tonio.libre2clock.ui.navigation.NavGraph
import com.tonio.libre2clock.ui.theme.Libre2ClockTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.service.GlucoseForegroundService
import com.tonio.libre2clock.util.EventLogManager
import com.tonio.libre2clock.util.LogLevel

class MainActivity : ComponentActivity() {

    // Inicialización inmediata en lugar de lazy para evitar problemas con ProGuard
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var repositoryImpl: GlucoseRepositoryImpl
    private lateinit var repository: GlucoseRepository
    private lateinit var eventLogger: EventLogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar componentes inmediatamente
        try {
            preferenceManager = AppContainer.providePreferenceManager(applicationContext)
            repositoryImpl = AppContainer.provideGlucoseRepository(applicationContext)
            repository = repositoryImpl
            eventLogger = AppContainer.provideEventLogManager(applicationContext)
        } catch (e: Exception) {
            // Fallback si algo falla
            android.util.Log.e("MainActivity", "Error initializing components", e)
            finish()
            return
        }

        // Inicializar exception handler
        setupUncaughtExceptionHandler()

        enableEdgeToEdge()

        setContent {
            Libre2ClockTheme {
                var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Handle result if needed
                }

                LaunchedEffect(Unit) {
                    // Pedir permisos primero
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    // Inicialización en background - todo dentro de LaunchedEffect
                    try {
                        AppContainer.provideCloudSyncManager(this@MainActivity)
                        repositoryImpl.initialize()

                        val token = preferenceManager.authToken.first()
                        if (token != null) {
                            isLoggedIn = true
                            startForegroundService(Intent(this@MainActivity, GlucoseForegroundService::class.java))
                        } else {
                            isLoggedIn = false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error during initialization", e)
                        isLoggedIn = false
                    }
                }

                val currentIsLoggedIn = isLoggedIn
                if (currentIsLoggedIn != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavGraph(
                            repository = repository,
                            preferenceManager = preferenceManager,
                            isLoggedIn = currentIsLoggedIn
                        )
                    }
                }
            }
        }
    }

    /**
     * Extraído a función separada para mejor legibilidad y rendimiento
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (::eventLogger.isInitialized) {
                    eventLogger.log(
                        level = LogLevel.ERROR,
                        tag = "UncaughtException",
                        message = "${throwable.javaClass.simpleName}: ${throwable.message}",
                        detail = throwable.stackTraceToString()
                    )
                }
            } catch (e: Exception) {
                // Ignore if logging fails
            }
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }
    }
}
