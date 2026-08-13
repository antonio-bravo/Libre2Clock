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

import com.tonio.libre2clock.ui.capillary.CapillaryScreen
import com.tonio.libre2clock.ui.sensor.SensorLogsScreen
import com.tonio.libre2clock.ui.insulin.InsulinHubScreen
import com.tonio.libre2clock.ui.insulin.InsulinLogsScreen
import com.tonio.libre2clock.ui.strategy.StrategyScreen

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
    
    // Shared ViewModels for persistent state and better performance
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(preferenceManager, repository, context.applicationContext) }
    val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(repository, preferenceManager, context.applicationContext) }
    val loginViewModel: LoginViewModel = viewModel { LoginViewModel(repository) }
    val reportViewModel: com.tonio.libre2clock.ui.report.ReportViewModel = viewModel { 
        com.tonio.libre2clock.ui.report.ReportViewModel(repository, preferenceManager, context.applicationContext) 
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
        },
        entryProvider = entryProvider {
            entry<Destination.Login> {
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(Destination.Dashboard)
                    }
                )
            }
            entry<Destination.Dashboard> {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToSettings = {
                        backStack.add(Destination.Settings)
                    },
                    onNavigateToStrategy = {
                        backStack.add(Destination.Strategy)
                    },
                    onNavigateToCapillary = {
                        backStack.add(Destination.Capillary)
                    },
                    onNavigateToSensorLogs = {
                        backStack.add(Destination.SensorLogs)
                    },
                    onNavigateToInsulinHub = {
                        backStack.add(Destination.InsulinHub)
                    },
                    onNavigateToReports = {
                        backStack.add(Destination.Reports)
                    },
                    onAddDose = { dose ->
                        dashboardViewModel.addInsulinDose(dose)
                    }
                )
            }
            entry<Destination.Settings> {
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
            entry<Destination.Strategy> {
                StrategyScreen(
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.Capillary> {
                CapillaryScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.SensorLogs> {
                SensorLogsScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.InsulinHub> {
                InsulinHubScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    onNavigateToLogs = { backStack.add(Destination.InsulinLogs) }
                )
            }
            entry<Destination.InsulinLogs> {
                InsulinLogsScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.Reports> {
                com.tonio.libre2clock.ui.report.ReportScreen(
                    viewModel = reportViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
        }
    )
}
