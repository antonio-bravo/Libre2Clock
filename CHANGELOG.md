# Changelog

## 59ffbf9 (Nov 26, 2026 23:06:13)
purge-releases just manual, it does on the main build — antonio-bravo
[detail](#59ffbf9-details)

<details id='59ffbf9-details'>
<summary>Changed files</summary>

- .github/workflows/purgar-releases.yml [Modified]
</details>


---
## 404d464 (Nov 26, 2026 22:03:40)
Update changelog — github-actions[bot]
[detail](#404d464-details)

<details id='404d464-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 195db06 (Nov 26, 2026 23:03:25)
explain MARD/Bias — antonio-bravo
[detail](#195db06-details)

<details id='195db06-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/components/SensorQualityHelpDialog.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/sensor/SensorLogsScreen.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 0f037b9 (Nov 26, 2026 21:50:43)
Update changelog — github-actions[bot]
[detail](#0f037b9-details)

<details id='0f037b9-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 8314233 (Nov 26, 2026 22:50:28)
restore VERSION_CODE yyyyMMddHH — antonio-bravo
[detail](#8314233-details)

<details id='8314233-details'>
<summary>Changed files</summary>

- app/build.gradle.kts [Modified]
</details>


---
## 1ea522b (Nov 26, 2026 21:05:51)
Update changelog — github-actions[bot]
[detail](#1ea522b-details)

<details id='1ea522b-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 2b7e81a (Nov 26, 2026 22:05:35)
improvements — antonio-bravo
[detail](#2b7e81a-details)

<details id='2b7e81a-details'>
<summary>Changed files</summary>

- .idea/misc.xml [Modified]
- app/build.gradle.kts [Modified]
- app/src/main/AndroidManifest.xml [Modified]
- app/src/main/java/com/tonio/libre2clock/MainActivity.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/api/DexcomService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/api/LibreService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/model/OffsetModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/InsulinProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/SecureCredentialStore.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/sync/CloudSyncManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/di/AppContainer.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/service/GlucoseAlarmReceiver.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/service/GlucoseForegroundService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/components/ShareDialog.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/AgpChartComponent.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/PdfViewerDialog.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/sensor/SensorLogsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAdvancedScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCloudScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsDeviceScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsEmergencyScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsNightscoutScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsVoiceScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsWidgetScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/AlertSnoozeHelper.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/util/ClinicalExporter.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/util/EmergencyAlertDispatcher.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/EmergencyLocationManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/EventLogManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/GlucoseUnitConverter.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/util/GlucoseVoiceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/HealthConnectManager.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/util/NightscoutManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/PdfReportGenerator.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/ReportCalculator.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/SensorErrorSummary.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/widget/GlucoseWidgetProvider.kt [Modified]
- app/src/main/res/drawable/placeholder.png [Added]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/colors.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- app/src/main/res/xml/glucose_widget_info.xml [Modified]
- app/src/test/java/com/tonio/libre2clock/AlertSnoozeHelperTest.kt [Deleted]
- app/src/test/java/com/tonio/libre2clock/ClinicalExporterTest.kt [Deleted]
- app/src/test/java/com/tonio/libre2clock/DexcomServiceTest.kt [Modified]
- app/src/test/java/com/tonio/libre2clock/GlucoseAndInsulinProcessorTest.kt [Deleted]
- app/src/test/java/com/tonio/libre2clock/GlucoseUnitConverterTest.kt [Deleted]
- app/src/test/java/com/tonio/libre2clock/HealthConnectTest.kt [Deleted]
- app/src/test/java/com/tonio/libre2clock/ReportCalculatorTest.kt [Deleted]
- app/src/test/java/com/tonio/libre2clock/SensorQualityTest.kt [Deleted]
- gradle/libs.versions.toml [Modified]
</details>


---
## 6f1be67 (Nov 26, 2026 14:19:13)
Update changelog — github-actions[bot]
[detail](#6f1be67-details)

<details id='6f1be67-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 07a32e3 (Nov 26, 2026 15:18:58)
adjust intelligency blood samples per sensor — antonio-bravo
[detail](#07a32e3-details)

<details id='07a32e3-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/model/OffsetModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCalibrationScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsSectionCacheRepository.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- app/src/test/java/com/tonio/libre2clock/ExampleUnitTest.kt [Modified]
</details>


---
## 6db25a9 (Nov 26, 2026 12:38:35)
Update changelog — github-actions[bot]
[detail](#6db25a9-details)

<details id='6db25a9-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 0792a10 (Nov 26, 2026 13:38:22)
update porcentaje dinámico:
Ajuste automático activo: +16% — antonio-bravo
[detail](#0792a10-details)

<details id='0792a10-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 26cc680 (Nov 26, 2026 12:12:25)
Update changelog — github-actions[bot]
[detail](#26cc680-details)

<details id='26cc680-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## edb8dad (Nov 26, 2026 13:12:11)
fix intelligence adjustment using % — antonio-bravo
[detail](#edb8dad-details)

<details id='edb8dad-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseProcessor.kt [Modified]
- app/src/test/java/com/tonio/libre2clock/ExampleUnitTest.kt [Modified]
</details>


---
## 1f9fe74 (Nov 26, 2026 11:15:35)
Update changelog — github-actions[bot]
[detail](#1f9fe74-details)

<details id='1f9fe74-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 6e2b588 (Nov 26, 2026 12:15:18)
fix correction intelligence apply to ranges — antonio-bravo
[detail](#6e2b588-details)

<details id='6e2b588-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/components/ShareDialog.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/login/LoginScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsBatteryScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCloudScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsEmergencyScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsNightscoutScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsVoiceScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsWidgetScreen.kt [Modified]
- app/src/test/java/com/tonio/libre2clock/ExampleUnitTest.kt [Modified]
</details>


---
## ae11612 (Nov 26, 2026 10:26:27)
Update changelog — github-actions[bot]
[detail](#ae11612-details)

<details id='ae11612-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## a426c19 (Nov 26, 2026 11:25:40)
update packages

# Conflicts:
#	gradle/libs.versions.toml

diff --git c/app/build.gradle.kts i/app/build.gradle.kts
index 542c9bd..3add69a 100644
--- c/app/build.gradle.kts
+++ i/app/build.gradle.kts
@@ -166,4 +166,8 @@ dependencies {
     androidTestImplementation(libs.androidx.runner)
     debugImplementation(libs.androidx.compose.ui.test.manifest)
     debugImplementation(libs.androidx.compose.ui.tooling)
+}
+
+tasks.matching { it.name.contains("AarMetadata") }.configureEach {
+    enabled = false
 }
\ No newline at end of file
diff --git c/gradle/libs.versions.toml i/gradle/libs.versions.toml
index 7debfba..6a9d85a 100644
--- c/gradle/libs.versions.toml
+++ i/gradle/libs.versions.toml
@@ -9,7 +9,7 @@ lifecycleViewmodelCompose = "2.11.1"
 lifecycleRuntimeCompose = "2.11.1"
 activityCompose = "1.13.0"
 kotlin = "2.4.21"
-composeBom = "2025.12.01"
+composeBom = "2026.11.01"
 googleDevtoolsKsp = "2.3.14"
 jetbrainsKotlinPluginSerialization = "2.4.21"
 kotlinxSerializationCore = "1.13.0" — antonio-bravo
[detail](#a426c19-details)

<details id='a426c19-details'>
<summary>Changed files</summary>

- app/build.gradle.kts [Modified]
- gradle/libs.versions.toml [Modified]
</details>


---
## b565fa7 (Nov 26, 2026 10:10:43)
Update changelog — github-actions[bot]
[detail](#b565fa7-details)

<details id='b565fa7-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## f0c015e (Nov 26, 2026 11:10:32)
Merge pull request #87 from antonio-bravo/renovate/jetpack-compose-and-androidx-ui

Update Jetpack Compose & AndroidX UI — antonio-bravo
[detail](#f0c015e-details)

<details id='f0c015e-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## 3804c57 (Nov 26, 2026 10:03:31)
Update Jetpack Compose & AndroidX UI — renovate[bot]
[detail](#3804c57-details)

<details id='3804c57-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## 569ffbe (Nov 26, 2026 10:03:06)
Update changelog — github-actions[bot]
[detail](#569ffbe-details)

<details id='569ffbe-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 19a650c (Nov 26, 2026 11:02:50)
dependandbot by groups — antonio-bravo
[detail](#19a650c-details)

<details id='19a650c-details'>
<summary>Changed files</summary>

- .github/dependanbot.yml [Modified]
- .github/renovate.json [Modified]
- gradle/libs.versions.toml [Modified]
</details>


---
## 68cd1bb (Nov 26, 2026 09:58:28)
Update changelog — github-actions[bot]
[detail](#68cd1bb-details)

<details id='68cd1bb-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 08a0583 (Nov 26, 2026 10:58:11)
fix update — antonio-bravo
[detail](#08a0583-details)

<details id='08a0583-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsNightscoutScreen.kt [Modified]
- gradle/libs.versions.toml [Modified]
</details>


---
## f6fee88 (Nov 26, 2026 09:52:00)
Update changelog — github-actions[bot]
[detail](#f6fee88-details)

<details id='f6fee88-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 607a348 (Nov 26, 2026 10:51:49)
Merge pull request #84 from antonio-bravo/renovate/major-composebom

Update dependency androidx.compose:compose-bom to v2026 — antonio-bravo
[detail](#607a348-details)

<details id='607a348-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## 43d7de6 (Nov 26, 2026 09:46:27)
Update changelog — github-actions[bot]
[detail](#43d7de6-details)

<details id='43d7de6-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 2a66472 (Nov 26, 2026 10:46:17)
Merge pull request #82 from antonio-bravo/renovate/activitycompose

Update dependency androidx.activity:activity-compose to v1.13.0 — antonio-bravo
[detail](#2a66472-details)

<details id='2a66472-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## fb6df16 (Nov 26, 2026 09:43:50)
Update dependency androidx.compose:compose-bom to v2026 — renovate[bot]
[detail](#fb6df16-details)

<details id='fb6df16-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## 694b50a (Nov 26, 2026 09:43:37)
Update dependency androidx.activity:activity-compose to v1.13.0 — renovate[bot]
[detail](#694b50a-details)

<details id='694b50a-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## 706325f (Nov 26, 2026 09:42:56)
Update changelog — github-actions[bot]
[detail](#706325f-details)

<details id='706325f-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 71172e9 (Nov 26, 2026 10:42:21)
simplify UI for intelligence per sensor offset on ranges — antonio-bravo
[detail](#71172e9-details)

<details id='71172e9-details'>
<summary>Changed files</summary>

- .idea/misc.xml [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCalibrationScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCloudScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- gradle/libs.versions.toml [Modified]
</details>


---
## c1f33c4 (Nov 26, 2026 08:56:59)
Update changelog — github-actions[bot]
[detail](#c1f33c4-details)

<details id='c1f33c4-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## cbe4145 (Nov 26, 2026 09:56:47)
Merge pull request #78 from antonio-bravo/renovate/composebom

Update dependency androidx.compose:compose-bom to v2026.11.01 — antonio-bravo
[detail](#cbe4145-details)

<details id='cbe4145-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## c893a31 (Nov 26, 2026 08:56:34)
Update changelog — github-actions[bot]
[detail](#c893a31-details)

<details id='c893a31-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 71cea1c (Nov 26, 2026 09:56:21)
Merge pull request #81 from antonio-bravo/renovate/actions-checkout-7.x

Update actions/checkout action to v7 — antonio-bravo
[detail](#71cea1c-details)

<details id='71cea1c-details'>
<summary>Changed files</summary>

- .github/workflows/purgar-releases.yml [Modified]
</details>


---
## f0582cd (Nov 26, 2026 04:29:40)
Update dependency androidx.compose:compose-bom to v2026.11.01 — renovate[bot]
[detail](#f0582cd-details)

<details id='f0582cd-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## e5a5dd5 (Nov 26, 2026 00:50:37)
Update changelog — github-actions[bot]
[detail](#e5a5dd5-details)

<details id='e5a5dd5-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## e34b7b8 (Nov 26, 2026 00:50:25)
Update dependency com.android.application to v9.5.3 (#80)

Co-authored-by: renovate[bot] <29139614+renovate[bot]@users.noreply.github.com> — renovate[bot]
[detail](#e34b7b8-details)

<details id='e34b7b8-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## 7e617ab (Nov 23, 2026 23:01:56)
Update changelog — github-actions[bot]
[detail](#7e617ab-details)

<details id='7e617ab-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 9196331 (Nov 23, 2026 23:01:44)
Update dependency androidx.camera:camera-view to v1.7.0 (#71)

Co-authored-by: renovate[bot] <29139614+renovate[bot]@users.noreply.github.com> — renovate[bot]
[detail](#9196331-details)

<details id='9196331-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## bbbf10f (Nov 23, 2026 12:11:19)
Update changelog — github-actions[bot]
[detail](#bbbf10f-details)

<details id='bbbf10f-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 0423803 (Nov 23, 2026 12:11:00)
Update dependency androidx.camera:camera-lifecycle to v1.7.0 (#70)

Co-authored-by: renovate[bot] <29139614+renovate[bot]@users.noreply.github.com> — renovate[bot]
[detail](#0423803-details)

<details id='0423803-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## feadf6b (Nov 23, 2026 00:42:11)
Update changelog — github-actions[bot]
[detail](#feadf6b-details)

<details id='feadf6b-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 395df48 (Nov 23, 2026 00:41:58)
Update dependency androidx.camera:camera-core to v1.7.0 (#69)

Co-authored-by: renovate[bot] <29139614+renovate[bot]@users.noreply.github.com> — renovate[bot]
[detail](#395df48-details)

<details id='395df48-details'>
<summary>Changed files</summary>

- gradle/libs.versions.toml [Modified]
</details>


---
## d3c717b (Nov 22, 2026 22:02:51)
Update changelog — github-actions[bot]
[detail](#d3c717b-details)

<details id='d3c717b-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## c540a5f (Nov 22, 2026 23:02:38)
add renovate — antonio-bravo
[detail](#c540a5f-details)

<details id='c540a5f-details'>
<summary>Changed files</summary>

- renovate.json [Deleted]
</details>


---
## 7e03604 (Nov 22, 2026 21:40:03)
Update actions/checkout action to v7 — renovate[bot]
[detail](#7e03604-details)

<details id='7e03604-details'>
<summary>Changed files</summary>

- .github/workflows/purgar-releases.yml [Modified]
</details>


---
## 8f99dc9 (Nov 22, 2026 21:39:20)
Update changelog — github-actions[bot]
[detail](#8f99dc9-details)

<details id='8f99dc9-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## e682eb0 (Nov 22, 2026 22:39:07)
workflow — antonio-bravo
[detail](#e682eb0-details)

<details id='e682eb0-details'>
<summary>Changed files</summary>

- .github/workflows/build-release.yml [Modified]
- .github/workflows/purgar-releases.yml [Modified]
</details>


---
