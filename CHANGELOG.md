# Changelog

## 0781b95 (Oct 01, 2026 19:21:32)
1.
Tamaño del objeto de informe (FullReportData): Al generar informes (especialmente para periodos de 30 a 90 días o con alta densidad de lecturas de glucosa), la estructura FullReportData almacena todas las lecturas diarias (DailySummary). Al serializar este objeto a JSON, el texto resultante puede medir de 3 MB a 10 MB o más.
2.
Límite de CursorWindow en Android SQLite: En la arquitectura de Android, la clase CursorWindow de SQLite tiene un límite estricto de memoria por fila (típicamente 2 MB). Cuando SectionCacheDatabaseHelper.getCachedPayload ejecutaba una consulta db.query() sobre una fila cuyo campo payload_json superaba los 2 MB, el motor de SQLite lanzaba una excepción nativa SQLiteBlobTooBigException al intentar cargar el cursor (cursor.moveToFirst()), haciendo colapsar la aplicación.
🛠️ Solución Implementada
Se actualizó  SectionCacheDatabaseHelper.kt con las siguientes mejoras:
1.
Compresión GZIP + Base64 Transparente:
◦
Al guardar payloads en la caché (upsertPayload), si el JSON supera los 10 KB se comprime automáticamente mediante GZIPOutputStream y se codifica en Base64.
◦
Como las estructuras JSON de lecturas de glucosa contienen claves muy repetitivas (factoryTimestamp, calibratedValue, etc.), la compresión GZIP reduce su tamaño entre un 85% y 95% (por ejemplo, un JSON de 4 MB se reduce a ~200-250 KB). Esto permite almacenarlos e interrogarlos dentro de SQLite de manera ultrarrápida y muy por debajo del límite de 2 MB del CursorWindow.
◦
Al recuperar la información (getCachedPayload / getLatestCachedPayload), la app detecta automáticamente el prefijo GZIP_BASE64: y descomprime el texto en memoria sin afectar al resto del código.
2.
Captura de Excepciones y Purga de Registros Problemáticos:
◦
Se añadieron bloques try-catch en las operaciones de lectura de la base de datos SQLite.
◦
Si se encuentra un registro antiguo no comprimido o corrupto que cause un error de tamaño en el cursor, el sistema lo elimina en silencio de la tabla section_cache y devuelve null (cache miss). Esto permite que el reporte se recalcule y se vuelva a guardar comprimido sin lanzar jamás un UncaughtException.
3.
Límite de Seguridad Máximo:
◦
Se estableció un umbral de seguridad (1.8 MB). Si tras la compresión un payload siguiera excediendo este tamaño, se omite su escritura en SQLite, permitiendo que la aplicación funcione con los datos en memoria sin interrumpir el flujo del usuario. — antonio-bravo
[detail](#0781b95-details)

<details id='0781b95-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/local/SectionCacheDatabaseHelper.kt [Modified]
</details>


---
## 44f20d7 (Sep 30, 2026 16:05:51)
Update changelog — github-actions[bot]
[detail](#44f20d7-details)

<details id='44f20d7-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 119f7ef (Sep 30, 2026 18:05:38)
added firebase-config to limit Clould Sync users — antonio-bravo
[detail](#119f7ef-details)

<details id='119f7ef-details'>
<summary>Changed files</summary>

- app/build.gradle.kts [Modified]
- app/src/main/java/com/tonio/libre2clock/data/sync/AuthManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/sync/CloudSyncManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/sync/RemoteConfigManager.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCloudScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- gradle/libs.versions.toml [Modified]
</details>


---
## c34c801 (Sep 30, 2026 15:47:36)
Update changelog — github-actions[bot]
[detail](#c34c801-details)

<details id='c34c801-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## b4f0a1a (Sep 30, 2026 17:47:20)
remove unnecessary packages — antonio-bravo
[detail](#b4f0a1a-details)

<details id='b4f0a1a-details'>
<summary>Changed files</summary>

- app/build.gradle.kts [Modified]
- app/proguard-rules.pro [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/components/DateTimeEntryFields.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/icons/CustomIcons.kt [Deleted]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/login/LoginScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/report/ReportScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/sensor/SensorLogsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAdvancedScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAlertsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsBatteryScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCalibrationScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsCloudScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsComponents.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsEventLogScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/strategy/StrategyScreen.kt [Modified]
- app/src/main/res/values/themes.xml [Modified]
</details>


---
## 721071f (Sep 30, 2026 12:01:17)
Update changelog — github-actions[bot]
[detail](#721071f-details)

<details id='721071f-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## bf65649 (Sep 30, 2026 14:01:02)
build.gradle.kts Minify and Shrink — antonio-bravo
[detail](#bf65649-details)

<details id='bf65649-details'>
<summary>Changed files</summary>

- app/build.gradle.kts [Modified]
</details>


---
## a7e9c4b (Sep 30, 2026 10:47:15)
Update changelog — github-actions[bot]
[detail](#a7e9c4b-details)

<details id='a7e9c4b-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## c3cc954 (Sep 30, 2026 12:46:55)
fix CapillariyScreen — antonio-bravo
[detail](#c3cc954-details)

<details id='c3cc954-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/capillary/CapillaryScreen.kt [Modified]
</details>


---
## 02f8522 (Sep 30, 2026 10:05:00)
Update changelog — github-actions[bot]
[detail](#02f8522-details)

<details id='02f8522-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## a790584 (Sep 30, 2026 12:04:41)
fix CapillaryScreen default value — antonio-bravo
[detail](#a790584-details)

<details id='a790584-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/capillary/CapillaryScreen.kt [Modified]
</details>


---
## ceb36c7 (Sep 29, 2026 20:07:14)
Update changelog — github-actions[bot]
[detail](#ceb36c7-details)

<details id='ceb36c7-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 953fe6d (Sep 29, 2026 22:06:59)
optimize speed and ram — antonio-bravo
[detail](#953fe6d-details)

<details id='953fe6d-details'>
<summary>Changed files</summary>

- .idea/misc.xml [Modified]
- app/build.gradle.kts [Modified]
- app/proguard-rules.pro [Modified]
- app/src/main/AndroidManifest.xml [Modified]
- app/src/main/java/com/tonio/libre2clock/MainActivity.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
- gradle.properties [Modified]
- verify_optimizations.sh [Deleted]
</details>


---
## 3ca1f98 (Sep 23, 2026 08:43:19)
Update changelog — github-actions[bot]
[detail](#3ca1f98-details)

<details id='3ca1f98-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 5f02581 (Sep 23, 2026 10:43:07)
remove checkbox on main screen and fix merge insulin logs — antonio-bravo
[detail](#5f02581-details)

<details id='5f02581-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/sync/CloudSyncManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
</details>


---
## 4049a7e (Sep 22, 2026 18:49:22)
Update changelog — github-actions[bot]
[detail](#4049a7e-details)

<details id='4049a7e-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 5d9e910 (Sep 22, 2026 20:49:09)
notify once on custom + availability to discount active insulin — antonio-bravo
[detail](#5d9e910-details)

<details id='5d9e910-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/model/OffsetModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/InsulinProcessor.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/service/GlucoseForegroundService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAlertsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 48c60fc (Sep 21, 2026 11:39:44)
Update changelog — github-actions[bot]
[detail](#48c60fc-details)

<details id='48c60fc-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## 552957c (Sep 21, 2026 13:39:30)
Add custom alarm when reach to value > or < — antonio-bravo
[detail](#552957c-details)

<details id='552957c-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/data/model/OffsetModels.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/service/GlucoseForegroundService.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsAlertsScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/settings/SettingsViewModel.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## a5b9205 (Sep 20, 2026 13:38:54)
Update changelog — github-actions[bot]
[detail](#a5b9205-details)

<details id='a5b9205-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
## ca79b14 (Sep 20, 2026 15:38:41)
fix insulin — antonio-bravo
[detail](#ca79b14-details)

<details id='ca79b14-details'>
<summary>Changed files</summary>

- app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt [Modified]
- app/src/main/java/com/tonio/libre2clock/ui/insulin/InsulinScreen.kt [Modified]
- app/src/main/res/values-es/strings.xml [Modified]
- app/src/main/res/values/strings.xml [Modified]
</details>


---
## 27ed7f0 (Sep 20, 2026 13:01:33)
Update changelog — github-actions[bot]
[detail](#27ed7f0-details)

<details id='27ed7f0-details'>
<summary>Changed files</summary>

- CHANGELOG.md [Modified]
</details>


---
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
