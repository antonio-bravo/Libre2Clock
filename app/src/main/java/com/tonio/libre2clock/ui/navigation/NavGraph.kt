package com.tonio.libre2clock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.ui.dashboard.DashboardScreen
import com.tonio.libre2clock.ui.dashboard.DashboardViewModel
import com.tonio.libre2clock.ui.login.LoginScreen
import com.tonio.libre2clock.ui.login.LoginViewModel
import com.tonio.libre2clock.ui.settings.SettingsScreen
import com.tonio.libre2clock.ui.settings.SettingsViewModel
import com.tonio.libre2clock.service.GlucoseForegroundService
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@Composable
fun NavGraph(
    repository: GlucoseRepository,
    preferenceManager: PreferenceManager,
    isLoggedIn: Boolean
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(
        if (isLoggedIn) Destination.Dashboard else Destination.Login
    )

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
        },
        entryProvider = entryProvider {
            entry<Destination.Login> {
                val loginViewModel: LoginViewModel = viewModel { LoginViewModel(repository) }
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(Destination.Dashboard)
                    }
                )
            }
            entry<Destination.Dashboard> {
                val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(repository) }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToSettings = {
                        backStack.add(Destination.Settings)
                    }
                )
            }
            entry<Destination.Settings> {
                val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(preferenceManager, repository) }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    onTestNotification = {
                        val intent = Intent(context, GlucoseForegroundService::class.java).apply {
                            action = "TEST_NOTIFICATION"
                        }
                        context.startService(intent)
                    }
                )
            }
        }
    )
}
