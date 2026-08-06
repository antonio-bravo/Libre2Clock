# Libre2Clock

Libre2Clock is an advanced Android application that bridges LibreLinkUp glucose data with your smartwatch and provides comprehensive diabetes management tools. It focuses on precision, clarity, and user control, offering dual-value glucose displays, sophisticated insulin tracking, and customizable notification systems.

The app is fully localized in **English** and **Spanish**, respecting your device's system settings.

## 📱 Screenshots (English)

| Dashboard | Insulin Management | Settings & Calibration |
|:---:|:---:|:---:|
| ![Dashboard Placeholder](https://via.placeholder.com/300x600?text=Dashboard+English) | ![Insulin Placeholder](https://via.placeholder.com/300x600?text=Insulin+Hub+English) | ![Settings Placeholder](https://via.placeholder.com/300x600?text=Settings+Audit+English) |

---

## 🚀 Key Features

### 🩸 Glucose Monitoring & Calibration
- **Dual Display**: See your raw sensor value and your calibrated value side-by-side: `raw(calibrated)` mg/dL.
- **Range-Based Offsets**: Define specific calibration rules for different glucose ranges (e.g., more correction at high values).
- **Sensor Audit System**: A specialized tool to **prove sensor inaccuracy**. For each range, the app calculates:
    - **Bias (Error %)**: Technical percentage of how much the hardware drifts from your capillary readings.
    - **Averages Comparison**: Directly compare mean sensor values vs. mean capillary values.
    - **Sample Validation**: Track how many tests support the error claim.
- **Capillary Auto-Adjust**: Automatically applies the latest capillary deviation to current readings for up to 6 hours.

### 💉 Advanced Insulin Hub
- **IOB (Insulin On Board)**: Uses a realistic 4-stage decay model for rapid-acting insulin.
- **Bolus Helper**: 
    - Based on the **Rule of 450** (I:C Ratio) and **Rule of 1800** (ISF).
    - Suggests dosages for both `Real` and `Offset` values.
    - **Basal Expiration Warning**: Suggests a +20% bolus increase if your basal insulin is within 2 hours of expiring.
- **Insulin Stats**: Detailed 7-day and 30-day averages with rapid vs. slow insulin breakdown.

### ⌚ Smartwatch Integration
- **Watch Notifications**: Mirrors readable glucose updates to your watch via standard Android notifications.
- **Flexible Modes**:
    - **Off**: No watch updates.
    - **Periodic**: Sends updates at a fixed interval (e.g., every 30 mins) regardless of rules.
    - **Schedules**: Only sends updates during active time windows (e.g., "Work hours").
    - **All**: Combines periodic updates with specific schedules.
- **Zepp Life / Bip S Optimized**: Designed for clear legibility on limited-screen devices.

### 🔋 Reliability & Safety
- **Battery Optimization**: 
    - Automatically disables fast refresh (60s) below a configurable threshold.
    - Reduces polling to 15m on critical battery to prevent phone shutdown.
- **Data Protection**: 
    - Automatic **Google Cloud Backup** integration.
    - Manual **JSON Export/Import** for local history management.

## 📚 Detailed Documentation

For in-depth logic and technical details, please refer to:

- [Diabetes Calculator Logic](file:///DIABETES_CALCULATOR.md)
- [Insulin IOB Algorithm](file:///INSULIN_IOB_ALGORITHM.md)
- [Calibration & Offset System](file:///CALIBRATION_SYSTEM.md)
- [Software Architecture](file:///ARCHITECTURE.md)
- [Backup & Restore Guide](file:///BACKUP_AND_RESTORE.md)

## 🛠️ Getting Started

1. Clone the repository.
2. Open in **Android Studio**.
3. Sync Gradle and run on an Android 9.0+ device.
4. Sign in with your **LibreLinkUp** credentials.

## Build APKs Locally

```bash
./gradlew assembleRelease # Release APK
./gradlew assembleDebug   # Debug APK
```

Output folders: `app/build/outputs/apk/release/` and `app/build/outputs/apk/debug/`.

## ⚖️ Medical Disclaimer

This software is not a medical device and does not replace professional medical advice, diagnosis, or treatment. Always follow guidance from qualified healthcare professionals.

## License

No license file is currently included. Contact the author for permissions.
