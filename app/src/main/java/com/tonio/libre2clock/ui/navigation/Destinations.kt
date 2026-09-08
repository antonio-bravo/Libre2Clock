package com.tonio.libre2clock.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Login : Destination

    @Serializable
    data object Dashboard : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object SettingsAlerts : Destination

    @Serializable
    data object SettingsCalibration : Destination

    @Serializable
    data object SettingsBattery : Destination

    @Serializable
    data object SettingsDevice : Destination

    @Serializable
    data object SettingsData : Destination

    @Serializable
    data object SettingsAdvanced : Destination

    @Serializable
    data object SettingsCloud : Destination

    @Serializable
    data object Strategy : Destination

    @Serializable
    data object Capillary : Destination

    @Serializable
    data object InsulinHub : Destination

    @Serializable
    data object InsulinLogs : Destination

    @Serializable
    data object SensorLogs : Destination

    @Serializable
    data object Reports : Destination
}
