# Libre2Clock

Libre2Clock is an Android app that connects to LibreLinkUp, fetches glucose data, and mirrors readable glucose updates to a smartwatch via Android notifications. It also provides advanced insulin tracking and a bolus calculator to help manage diabetes more effectively.

The app focuses on:
- **Clear glucose display** in dual format: `raw(offset-adjusted)` mg/dL + trend arrow.
- **Continuous background sync** with a foreground service.
- **Advanced Insulin Tracking**: Manage rapid and slow/basal doses with a realistic IOB decay model.
- **Diabetes Calculator**: Bolus helper based on the Rule of 450/1800 with dual suggestions.
- **Flexible Calibration**: Manual offset, range-based offsets, and capillary auto-adjust.
- **Data Safety**: Local JSON backups and integrated Google Cloud backup.

## Main Features

### Glucose & Sensor
- LibreLinkUp login and token persistence.
- Demo mode with mock glucose and sensor data.
- Dashboard with:
  - Current glucose card (Dual format).
  - Sensor health card with **real-time countdown**.
  - Historical trend graph.

### Insulin Management (Insulin Hub)
- **IOB Tracking**: Real-time Insulin On Board with a 4-stage realistic decay curve for rapid insulin.
- **Bolus Calculator**:
  - Rule of 450 (I:C Ratio) and Rule of 1800 (ISF) calculation.
  - Dual suggestions: `Real(Offset)` dosage for informed decisions.
  - Target glucose adjustment (default 80 mg/dL).
  - Warning for expiring basal (suggests +20% bolus increase).
- **Log Management**: Full history with edit/delete capabilities.
- **Stats**: Today's totals and 7d/30d averages with Rapid/Slow breakdown.

### Calibration & Alarms
- Global manual offset and range-based offsets.
- Optional capillary-based auto-adjust.
- Configurable watch push notifications (5 to 180 mins).
- Independent low and high glucose alarm toggles (OFF by default).

## Documentación Detallada

Para profundizar en el funcionamiento de cada sistema, consulta los siguientes archivos:

### Algoritmos y Lógica Médica
- [Calculadora de Diabetes](file:///DIABETES_CALCULATOR.md): Detalles de la Regla de 450/1800 y bolos de corrección.
- [Algoritmo de Insulina (IOB)](file:///INSULIN_IOB_ALGORITHM.md): Detalle de la curva de decaimiento de 4 tramos.
- [Sistema de Calibración](file:///CALIBRATION_SYSTEM.md): Cómo funcionan los offsets y el auto-ajuste.

### Guía Técnica
- [Arquitectura del Software](file:///ARCHITECTURE.md): Tecnologías usadas y estructura del código.
- [Respaldo y Seguridad](file:///BACKUP_AND_RESTORE.md): Gestión de backups locales y en la nube.
- [Solución de Problemas (ADB)](file:///EMULATOR_FIX.md): Pasos para arreglar errores del emulador.

## Tech Stack

- Kotlin & Jetpack Compose (Material 3)
- Coroutines + Flow
- DataStore Preferences
- Retrofit + OkHttp + Moshi
- Android Foreground Service for continuous sync

## Getting Started

1. Clone the repository.
2. Open in Android Studio.
3. Sync Gradle.
4. Run the app on a device with internet access.
5. Sign in with LibreLinkUp credentials, or start Demo Mode.

## Build APKs Locally

```bash
./gradlew assembleRelease # Release APK
./gradlew assembleDebug   # Debug APK
```

Output folders: `app/build/outputs/apk/release/` and `app/build/outputs/apk/debug/`.

## Medical Disclaimer

This software is not a medical device and does not replace professional medical advice, diagnosis, or treatment. Always follow guidance from qualified healthcare professionals.

## License

No license file is currently included. Contact the author for permissions.
