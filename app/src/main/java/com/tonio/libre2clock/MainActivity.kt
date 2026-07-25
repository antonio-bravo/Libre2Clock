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
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.ui.navigation.NavGraph
import com.tonio.libre2clock.ui.theme.Libre2ClockTheme
import kotlinx.coroutines.flow.first

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tonio.libre2clock.service.GlucoseForegroundService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferenceManager = PreferenceManager(applicationContext)
        val repository = GlucoseRepositoryImpl(preferenceManager)
        
        setContent {
            Libre2ClockTheme {
                var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }
                
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Handle result if needed
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    
                    repository.initialize()
                    val token = preferenceManager.authToken.first()
                    if (token != null) {
                        isLoggedIn = true
                        startForegroundService(Intent(this@MainActivity, GlucoseForegroundService::class.java))
                    } else {
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
}
