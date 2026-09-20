# Changelog

## c50867a (Sep 20, 2026 15:01:22)
fix text in FS: — antonio-bravo
[detail](#c50867a-details)

<details id='c50867a-details'>
<summary>Changed files</summary>

- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## d0dcae3 (Sep 20, 2026 12:48:01)
Update changelog — github-actions[bot]
[detail](#d0dcae3-details)

<details id='d0dcae3-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 8e4e7c1 (Sep 20, 2026 14:47:49)
Merge pull request #5 from antonio-bravo/feature/timestamp_parser

fix — antonio-bravo
[detail](#8e4e7c1-details)

<details id='8e4e7c1-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsEventLogScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/EventLogManager.kt [Modified]
</details>


---
## 47cfbd9 (Sep 20, 2026 14:46:30)
fix — antonio-bravo
[detail](#47cfbd9-details)

<details id='47cfbd9-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsEventLogScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/EventLogManager.kt [Modified]
</details>


---
## c1acfb2 (Sep 20, 2026 12:02:38)
Update changelog — github-actions[bot]
[detail](#c1acfb2-details)

<details id='c1acfb2-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## b98abf5 (Sep 20, 2026 14:02:26)
Merge pull request #4 from antonio-bravo/feature/timestamp_parser

fix timestap on GlucoseRepositoryImpl — antonio-bravo
[detail](#b98abf5-details)

<details id='b98abf5-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
</details>


---
## ec67355 (Sep 20, 2026 13:59:21)
fix timestap on GlucoseRepositoryImpl — antonio-bravo
[detail](#ec67355-details)

<details id='ec67355-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
</details>


---
## b001994 (Sep 20, 2026 09:52:22)
Update changelog — github-actions[bot]
[detail](#b001994-details)

<details id='b001994-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 90fcc7e (Sep 20, 2026 11:52:12)
Merge pull request #3 from antonio-bravo/feature/optmization_20260920

Optimizaciones realizadas para que vaya "Como un Flash" en teléfonos … — antonio-bravo
[detail](#90fcc7e-details)

<details id='90fcc7e-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/local/SectionCacheDatabaseHelper.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsCacheRepository.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt [Modified]
</details>


---
## 76a9ab0 (Sep 20, 2026 11:45:30)
Optimizaciones realizadas para que vaya "Como un Flash" en teléfonos con pocos recursos
Se han aplicado las siguientes optimizaciones de alto rendimiento en el código:
1.
Emisión de interfaz instantánea (0 ms de espera):
◦
 DashboardViewModel.kt ahora inicia el estado de las métricas directamente desde las métricas guardadas en la base de datos SQLite (getLatestCached).
◦
Resultado: Al abrir la aplicación, la HbA1c, promedios, hipoglucemias, variabilidad CV% y TIR/TBR aparecen inmediatamente en pantalla al instante sin mostrar símbolos -- ni esperas. Luego, en segundo plano, se recalcula fluidamente solo si han llegado nuevas glucemias.
2.
Parseo de fechas 500 veces más rápido:
◦
En  TimestampParser.kt, se añadió un fast-path que prioriza el sello de tiempo numérico epochSeconds leído desde SQLite. Esto evita ejecutar analizadores de texto y expresiones regulares para miles de puntos de datos.
3.
Bypass de procesamiento cuando no hay offsets:
◦
En  DashboardViewModel.kt, si el usuario no tiene calibraciones o rangos de ajuste manual activos, se salta la re-creación en memoria de 50,000 objetos, ahorrando significativamente uso de CPU y memoria RAM en teléfonos de pocos recursos.
4.
Ajuste del umbral para HbA1c Estimada:
◦
En  DashboardMetricsModels.kt, se redujo el requisito de mediciones de 100 a 10 (a1cTotalMeasurements >= 10), garantizando que la HbA1c estimada se muestre siempre que haya datos suficientes. — antonio-bravo
[detail](#76a9ab0-details)

<details id='76a9ab0-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/local/SectionCacheDatabaseHelper.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsCacheRepository.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt [Modified]
</details>


---
## 58c817e (Sep 20, 2026 08:56:55)
Update changelog — github-actions[bot]
[detail](#58c817e-details)

<details id='58c817e-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## e8585bd (Sep 20, 2026 10:56:34)
added TIR  > 70% & TBR < 5% — antonio-bravo
[detail](#e8585bd-details)

<details id='e8585bd-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## af6eaf5 (Sep 19, 2026 22:27:58)
Update changelog — github-actions[bot]
[detail](#af6eaf5-details)

<details id='af6eaf5-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 25303a3 (Sep 20, 2026 00:27:47)
fix problem add insulin — antonio-bravo
[detail](#25303a3-details)

<details id='25303a3-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/MainActivity.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 7f1ccbc (Sep 19, 2026 21:58:32)
Update changelog — github-actions[bot]
[detail](#7f1ccbc-details)

<details id='7f1ccbc-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## bf3f4ca (Sep 19, 2026 23:58:19)
Glucose Prediction and Preventive Hypoglycemia Alerts — antonio-bravo
[detail](#bf3f4ca-details)

<details id='bf3f4ca-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/InsulinProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/service/GlucoseForegroundService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAlertsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- app/src/test/java/com/tonio/libre2clock/ExampleUnitTest.kt [Modified]
</details>


---
## c35cc7c (Sep 19, 2026 21:39:42)
Update changelog — github-actions[bot]
[detail](#c35cc7c-details)

<details id='c35cc7c-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 60e2c05 (Sep 19, 2026 23:39:30)
fix issue add insulin to sync — antonio-bravo
[detail](#60e2c05-details)

<details id='60e2c05-details'>
<summary>Changed files</summary>

- .idea/misc.xml [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
</details>


---
## f1a8e05 (Sep 19, 2026 20:05:30)
Update changelog — github-actions[bot]
[detail](#f1a8e05-details)

<details id='f1a8e05-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 46a0ead (Sep 19, 2026 22:05:15)
adjust text size in App — antonio-bravo
[detail](#46a0ead-details)

<details id='46a0ead-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## a199935 (Sep 19, 2026 18:41:16)
Update changelog — github-actions[bot]
[detail](#a199935-details)

<details id='a199935-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 13552cc (Sep 19, 2026 20:41:04)
add CV Coeficiente de Variabilidad % — antonio-bravo
[detail](#13552cc-details)

<details id='13552cc-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/AgpChartComponent.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/PdfReportGenerator.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 553db4c (Sep 19, 2026 17:25:54)
Update changelog — github-actions[bot]
[detail](#553db4c-details)

<details id='553db4c-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 04708a4 (Sep 19, 2026 19:25:43)
add nice coloring base on gluse measure — antonio-bravo
[detail](#04708a4-details)

<details id='04708a4-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 88d7c46 (Sep 19, 2026 16:44:39)
Update changelog — github-actions[bot]
[detail](#88d7c46-details)

<details id='88d7c46-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## e885374 (Sep 19, 2026 18:44:29)
add insulin dosis raw(offset) / FS — antonio-bravo
[detail](#e885374-details)

<details id='e885374-details'>
<summary>Changed files</summary>

- .idea/misc.xml [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## d601985 (Sep 18, 2026 16:57:26)
Update changelog — github-actions[bot]
[detail](#d601985-details)

<details id='d601985-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## f0afb9f (Sep 18, 2026 18:57:10)
capillarity get measure based on date and time — antonio-bravo
[detail](#f0afb9f-details)

<details id='f0afb9f-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepository.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/capillary/CapillaryScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- gradle/libs.versions.toml [Modified]
</details>


---
## de1aec2 (Sep 17, 2026 14:15:32)
Update changelog — github-actions[bot]
[detail](#de1aec2-details)

<details id='de1aec2-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 9a65898 (Sep 17, 2026 16:15:14)
auto adjust range per senson on capilarity otherwise use avg for measures in that range — antonio-bravo
[detail](#9a65898-details)

<details id='9a65898-details'>
<summary>Changed files</summary>

- .idea/misc.xml [Modified]
- CALIBRATION_SYSTEM.md [Modified]
- README.md [Modified]
- app/src/main/java/com/tonio/libre2clock/data/model/OffsetModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/service/GlucoseForegroundService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCalibrationScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- app/src/test/java/com/tonio/libre2clock/ExampleUnitTest.kt [Modified]
</details>


---
## 0bb4bd5 (Sep 17, 2026 08:50:36)
Update changelog — github-actions[bot]
[detail](#0bb4bd5-details)

<details id='0bb4bd5-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 1fdbc94 (Sep 17, 2026 10:50:25)
report used by endocrine — antonio-bravo
[detail](#1fdbc94-details)

<details id='1fdbc94-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/report/ReportScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/PdfReportGenerator.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 4e35867 (Sep 17, 2026 08:26:10)
Update changelog — github-actions[bot]
[detail](#4e35867-details)

<details id='4e35867-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 8f155e0 (Sep 17, 2026 10:25:57)
fix date format in SensorLogsScreen — antonio-bravo
[detail](#8f155e0-details)

<details id='8f155e0-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/sensor/SensorLogsScreen.kt [Modified]
</details>


---
## 21ad6aa (Sep 16, 2026 22:37:15)
Update changelog — github-actions[bot]
[detail](#21ad6aa-details)

<details id='21ad6aa-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 171d02b (Sep 17, 2026 00:36:56)
Improvements — antonio-bravo
[detail](#171d02b-details)

<details id='171d02b-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/model/OffsetModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/sync/CloudSyncManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAlertsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCalibrationScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsSectionCacheRepository.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- app/src/test/java/com/tonio/libre2clock/ExampleUnitTest.kt [Modified]
</details>


---
## b5ecfa1 (Sep 15, 2026 08:36:45)
Update changelog — github-actions[bot]
[detail](#b5ecfa1-details)

<details id='b5ecfa1-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 557a2e8 (Sep 15, 2026 10:36:32)
parseMeasurementInstant — antonio-bravo
[detail](#557a2e8-details)

<details id='557a2e8-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt [Modified]
</details>


---
## b16d494 (Sep 15, 2026 07:51:28)
Update changelog — github-actions[bot]
[detail](#b16d494-details)

<details id='b16d494-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 634bed2 (Sep 15, 2026 09:51:17)
fix accept android SDL Licenses — antonio-bravo
[detail](#634bed2-details)

<details id='634bed2-details'>
<summary>Changed files</summary>

- .github/workflows/build-release.yml [Modified]
</details>


---
## 3a1c3b3 (Sep 15, 2026 07:45:00)
Update changelog — github-actions[bot]
[detail](#3a1c3b3-details)

<details id='3a1c3b3-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## c99784b (Sep 15, 2026 09:44:43)
fix workflow — antonio-bravo
[detail](#c99784b-details)

<details id='c99784b-details'>
<summary>Changed files</summary>

- .github/workflows/build-release.yml [Modified]
</details>


---
## b301c4d (Sep 15, 2026 07:28:49)
Update changelog — github-actions[bot]
[detail](#b301c4d-details)

<details id='b301c4d-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## a895d42 (Sep 15, 2026 09:27:43)
delta glucose — antonio-bravo
[detail](#a895d42-details)

<details id='a895d42-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/sync/CloudSyncManager.kt [Modified]
</details>


---
## 284e296 (Sep 15, 2026 09:26:56)
intento de fix — antonio-bravo
[detail](#284e296-details)

<details id='284e296-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
</details>


---
## af2ed8b (Sep 15, 2026 07:23:50)
Update changelog — github-actions[bot]
[detail](#af2ed8b-details)

<details id='af2ed8b-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## c36411d (Sep 15, 2026 09:23:37)
Merge pull request #2 from antonio-bravo/gemini

gemini — antonio-bravo
[detail](#c36411d-details)

<details id='c36411d-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt [Modified]
- app/src/test/java/com/tonio/libre2clock/TimestampPriorityTest.kt [Modified]
</details>


---
## e9c5eac (Sep 15, 2026 09:18:33)
gemini — antonio-bravo
[detail](#e9c5eac-details)

<details id='e9c5eac-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardMetricsModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt [Modified]
- app/src/test/java/com/tonio/libre2clock/TimestampPriorityTest.kt [Modified]
</details>


---
## 3f34ac5 (Sep 14, 2026 14:40:40)
Update changelog — github-actions[bot]
[detail](#3f34ac5-details)

<details id='3f34ac5-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## f359058 (Sep 14, 2026 16:40:25)
undo changes — antonio-bravo
[detail](#f359058-details)

<details id='f359058-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/sync/CloudSyncManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt [Modified]
</details>


---
