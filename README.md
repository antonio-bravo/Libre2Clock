# Libre2Clock

Libre2Clock is an advanced Android application that bridges LibreLinkUp & Dexcom glucose data with your smartwatch and home screen, offering comprehensive diabetes management tools. It focuses on precision, clarity, and user control, featuring dual-value glucose displays, customizable home screen widgets, emergency SOS alerts, sophisticated insulin tracking, and clinical AGP report generation.

The app is fully localized in **English** and **Spanish**, respecting your device's system settings.

---

## 📱 Screenshots & Visual Layouts

| Dashboard | Insulin Management | Settings & Calibration |
|:---:|:---:|:---:|
| <img width="380" alt="Dashboard" src="https://github.com/user-attachments/assets/d67c55ca-a00b-47f5-bf94-996c229af84b" /> | <img width="382" alt="Insulin Hub" src="https://github.com/user-attachments/assets/6e09c842-a53e-43e9-b46b-500142a59082" /> | <img width="380" alt="Settings" src="https://github.com/user-attachments/assets/cdf71cc1-4058-4ae0-a879-7a85626f94f4" /> |

### 🖼️ Home Screen Resizable Widget Layouts

```
┌─────────────────────────────────────────────────────────────┐
│                 EXPANDED WIDGET (4x2 / 2x2)                 │
├─────────────────────────────────────────────────────────────┤
│                         110(118) ↗                          │
│                            12:34                            │
│ ─────────────────────────────────────────────────────────── │
│          Rápida: 1.5 U   Lenta: 12.0 U   FS: 45             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────┐   ┌───────────────────┐
│        COMPACT WIDGET (2x1)             │   │  MINI TILE (1x1)  │
├─────────────────────────────────────────┤   ├───────────────────┤
│                 110 ↗                   │   │       110 ↗       │
│         12:34 | R:1.5U | FS:45          │   │      R: 1.5U      │
└─────────────────────────────────────────┘   └───────────────────┘
```

---

## 🚀 Key Features

### 🧩 Home Screen Widget & IP Widget Settings
- **Flexible Resizing**: Shrinks down to a minimalist **1x1** or **2x1** tile or expands up to **4x2** celdas on your home screen launcher.
- **IP Widget Style Customization**: Live interactive preview screen (*Settings -> Widget Settings*) to customize your widget style before adding it to your home screen.
- **Background Opacity / Glass Style**: Adjustable transparency slider from **0%** (fully transparent IP Widget glass style) to **100%** (fully opaque).
- **Target Range Color Coding**: Automatically changes card background based on glucose level (**Red** = Low, **Orange** = High, **Green** = In Range) or uses a neutral dark glass card.
- **Selectable Display Fields**: Toggle individual visibility for:
  - 🕒 Date & Time of last reading (works in both compact and expanded view)
  - 💉 Active Rapid Insulin IOB (`Rápida`)
  - 💉 Active Slow Insulin IOB (`Lenta`)
  - ⚡ Insulin Sensitivity Factor (`FS` / `ISF`)
- **Color-Coded Trend Arrows**: Directional trend arrows colored by rate of change (`↑`/`↓` Red, `↘` Orange, `↗` Yellow, `→` Green/White).
- **One-Tap App Launch**: Tapping anywhere on the widget instantly opens Libre2Clock.

### 🩸 Glucose Monitoring & Calibration
- **Dual Display**: See your raw sensor value and your calibrated value side-by-side: `raw(calibrated)` mg/dL (e.g., `110(118)`).
- **Multi-Source Support**: Connect seamlessly to **LibreLinkUp** or **Dexcom Share** (US & OUS server regions).
- **Range-Based Offsets**: Define specific calibration rules for different glucose ranges with smart offset estimation based on current sensor readings or historical averages.
- **Sensor Audit System**: Specialized diagnostic tool calculating hardware drift percentage, mean sensor vs. mean capillary values, and sample validation.
- **Dynamic Capillary Adjustment**: Automatically applies deviation from your latest fingerstick test (within 6 hours) to current glucose.

### 🚨 Emergency SOS Alerts
- **Multi-Channel Hypoglycemia SOS**: Automatically alerts your emergency contacts when glucose drops below a critical threshold.
- **WhatsApp Zero-Touch Sending**: Automatic background WhatsApp message dispatching via CallMeBot API or Accessibility Service.
- **Telegram Bot Integration**: Direct, silent background Telegram notifications to family chats or groups.
- **SMS & Webhooks**: Direct SMS alerts and customizable Webhook POST requests (compatible with WASender, IFTTT, Zapier, Make).
- **GPS Location Sharing**: Captures real-time GPS coordinates and embeds a direct Google Maps link in the emergency message.

### 💉 Advanced Diabetes Calculator & Insulin Hub
- **4-Stage IOB Decay**: Realistic active insulin (Insulin On Board) decay calculation for rapid-acting insulin.
- **Bolus Helper**:
  - Built on the **Rule of 450** (I:C Ratio) and **Rule of 1800** (ISF).
  - Calculates suggested dosages for both `Real` and `Calibrated` values.
  - **Basal Expiration Warning**: Recommends a +20% bolus adjustment if basal insulin is within 2 hours of expiring.
- **Insulin Stats**: Detailed 7-day and 30-day averages with rapid vs. slow insulin breakdown.

### 📊 AGP & Analytics PDF Reports
- **Ambulatory Glucose Profile (AGP)**: Standardized clinical 24-hour glucose profile curve generator.
- **Time In Range (TIR)**: Measures Time In Range (70–180 mg/dL), Time Below Range (TBR < 70), and Time Above Range (TAR > 180).
- **Glycemic Variability (CV%)**: Calculates Coefficient of Variation (CV%) with clinical stability targets (< 36%).
- **PDF Export & Sharing**: Generate, view, and share comprehensive PDF reports directly from the app.

### ☁️ Cloud Sync & Smartwatch Integration
- **Firebase Cloud Sync**: Keep your settings, historical readings, and logs synchronized across devices using your Google account.
- **Smartwatch Mirroring**: Sends glucose readings and trend arrows as standard Android notifications (compatible with Galaxy Watch, Pixel Watch, Garmin, Amazfit Bip S via Zepp Life).
- **Scheduled Push Modes**: Configure periodic notifications, schedule-based pushes, or hybrid modes.

---

## 📚 Detailed Documentation

For in-depth logic and technical details, refer to:

- [Diabetes Calculator Logic](file:///DIABETES_CALCULATOR.md)
- [Insulin IOB Algorithm](file:///INSULIN_IOB_ALGORITHM.md)
- [Calibration & Offset System](file:///CALIBRATION_SYSTEM.md)
- [Software Architecture](file:///ARCHITECTURE.md)
- [Backup & Restore Guide](file:///BACKUP_AND_RESTORE.md)

---

## 🛠️ Getting Started

1. Clone the repository.
2. Open in **Android Studio**.
3. Sync Gradle and run on an Android 9.0+ device.
4. Sign in with your **LibreLinkUp** or **Dexcom** credentials.

## Build APKs Locally

```bash
./gradlew assembleRelease # Release APK
./gradlew assembleDebug   # Debug APK
```

Output folders: `app/build/outputs/apk/release/` and `app/build/outputs/apk/debug/`.

---

## ⚖️ Medical Disclaimer

This software is not a medical device and does not replace professional medical advice, diagnosis, or treatment. Always follow guidance from qualified healthcare professionals.

## License

No license file is currently included. Contact the author for permissions.
