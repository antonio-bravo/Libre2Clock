# Project Plan

Libre2Clock: Add capillary logging, Room database persistence, and an auto-calibration engine to suggest/apply glucose offsets.

## Project Brief

# Project Brief: Libre2Clock

Libre2Clock
 is an advanced Android monitoring utility for FreeStyle Libre 2 sensors, designed to bridge the gap between raw sensor data and real
-world accuracy. By integrating finger-prick (capillary) logging with an automated calibration engine, it ensures users
 receive the most accurate glucose readings on their phone and wearable devices.

### Features
*   **Intelligent Dual-Value
 Dashboard**: Displays real-time glucose in an `original(calibrated)` format, paired with precise sensor expiration tracking (days remaining and
 exact expiration timestamp).
*   **Capillary Logging & Auto-Calibration**: A dedicated log for recording finger-prick
 readings; the app compares these against sensor data to automatically suggest or calculate optimal offsets for the calibration table.
*   **Multi-
Range Offset Management**: A scrollable, interactive settings interface that allows users to define custom glucose intervals (Min, Max)
 and manually or automatically assign calibration offsets.
*   **Wearable Sync Diagnostic**: A persistent notification system optimized for mirroring to smart
watches (e.g., Amazfit Bip S), including a "Test Notification" button to verify connectivity with companion
 apps like Mi Fit.

### High-Level Tech Stack
*   **Language**: Kotlin
*   **UI
 Framework**: Jetpack Compose with Material 3.
*   **Navigation**: **Jetpack Navigation 3** (
State-driven).
*   **Adaptive Strategy**: **Compose Material Adaptive** library for seamless transitions between handsets, foldables, and
 large screens.
*   **Concurrency**: Kotlin Coroutines and Flow for handling real-time data streams and background calculations
.
*   **Persistence**: **Room Database** for storing capillary logs and **DataStore** for persistent calibration settings.

*   **Background Services**: Android Foreground Services to ensure reliable notification delivery for wearable devices.

## Implementation Steps
**Total Duration:** 29m 13s

### Task_1_DataLayer_and_API: Implement the data layer including LibreLinkUp API models, Retrofit service, and a Repository to manage glucose data fetching and authentication.
- **Status:** COMPLETED
- **Updates:** Implemented data models for LibreLinkUp, Retrofit service with OkHttp and custom interceptors for authentication, and a GlucoseRepository using DataStore for persistence. The repository provides a Flow of glucose readings. Verified that the project builds successfully.
- **Acceptance Criteria:**
  - Data models for LibreLinkUp API defined
  - Retrofit service configured with OkHttp
  - Repository provides a Flow of glucose readings
  - Build passes
- **Duration:** 3m 26s

### Task_2_CoreUI_and_Navigation: Develop the main Dashboard UI with glucose readings and trend arrows, integrate an interactive Trend Graph, and set up Navigation 3 with Adaptive Layout support.
- **Status:** COMPLETED
- **Updates:** Implemented Navigation 3 state-driven architecture. Created Login and Dashboard screens. Developed a custom interactive trend graph using Canvas. Integrated GlucoseRepository into ViewModels. Applied Material 3 styling and ensured edge-to-edge support. Verified build.
- **Acceptance Criteria:**
  - Dashboard displays current glucose and trend
  - Interactive graph renders historical data
  - Navigation 3 state-driven architecture implemented
  - UI adapts to different screen sizes (phones/tablets)
  - Material 3 styling used
- **Duration:** 6m 27s

### Task_3_ForegroundService_and_Notifications: Implement a Foreground Service to periodically fetch glucose data and show a persistent notification with the current reading.
- **Status:** COMPLETED
- **Updates:** Implemented a Foreground Service of type 'dataSync' to periodically fetch glucose data. Created a persistent notification displaying the current glucose level and trend. Handled Android 14 notification and foreground service permissions. Integrated service start/stop logic with authentication status. Verified build.
- **Acceptance Criteria:**
  - Foreground service fetches data in background
  - Persistent notification shows current glucose level
  - App handles necessary permissions (notifications)
- **Duration:** 1m 33s

### Task_4_FinalPolish_and_Verification: Apply Material 3 color schemes (light/dark), implement edge-to-edge display, create an adaptive app icon, and perform final verification.
- **Status:** COMPLETED
- **Updates:** Applied Material 3 vibrant color scheme (light/dark). Implemented full edge-to-edge support. Created a custom adaptive app icon representing the glucose monitoring and clock concept. Critic agent performed final verification and confirmed application stability, requirement alignment, and high-quality UI/UX. No crashes or missing features found.
- **Acceptance Criteria:**
  - Material 3 vibrant color scheme implemented
  - Adaptive app icon created
  - Full edge-to-edge display working
  - App does not crash
  - Build passes
  - Critic agent verifies application stability and requirement alignment
- **Duration:** 5m 6s

### Task_5_Offset_and_Test_Utility: Implement the Glucose Offset configuration in settings and the Notification Test Utility to trigger mock notifications.
- **Status:** COMPLETED
- **Updates:** Implemented a Settings screen to configure a manual glucose offset. The offset is persisted in DataStore and applied to all glucose readings in both the UI and notifications. Added a 'Test Notification' button in settings that triggers a mock glucose notification for verifying watch mirroring. Updated the foreground service to handle test intents and apply the manual offset. Verified build.
- **Acceptance Criteria:**
  - Glucose offset can be configured in UI and is persisted in DataStore
  - Displayed glucose values in UI and notifications apply the configured offset
  - Notification test utility button triggers a mock glucose notification
  - Build passes
- **Duration:** 1m 43s

### Task_6_Final_Verification_Updated: Run and Verify the updated application to ensure the new offset and test notification features work correctly and the app remains stable.
- **Status:** COMPLETED
- **Updates:** Run and Verify the updated application to ensure the new offset and test notification features work correctly and the app remains stable. Critic agent verified the implementation of Settings screen, manual offset persistence in DataStore, and the Test Notification utility. The app remains stable and follows Material 3 guidelines. No regressions found.
- **Acceptance Criteria:**
  - App does not crash
  - Build passes
  - Offset functionality verified (UI and Notification)
  - Test notification functionality verified
  - Critic agent verifies application stability and requirement alignment
- **Duration:** 2m 46s

### Task_7_RangeOffsetTable_and_DualValueUI: Implement the editable range-based offset table in Settings and update UI/Notifications to display dual glucose values (original(calibrated)).
- **Status:** COMPLETED
- **Updates:** Implemented an interactive, editable table in the Settings screen for defining glucose offset ranges (Min, Max, Offset). The table data is serialized to JSON and persisted in DataStore. Updated the GlucoseProcessor to apply these range-based offsets. Modified the Dashboard and Notifications to display glucose readings in the 'original(calibrated)' format. Verified the build passes.
- **Acceptance Criteria:**
  - Interactive table in Settings to define Min, Max, and Offset for multiple ranges
  - Offset table data persisted in and retrieved from DataStore
  - Dashboard and Notifications show glucose in 'original(calibrated)' format
  - Correct range-based offset applied to all glucose readings
  - Build passes
- **Duration:** 4m 2s

### Task_8_SettingsUI_Refinement_and_Mirroring: Improve Settings UI with a scrollable layout and 'Add Range' button. Ensure Test Notification is optimized for watch mirroring and uses the dual-value format.
- **Status:** COMPLETED
- **Updates:** Improved Settings UI with a scrollable LazyColumn layout. Added an 'Add Range' button to the offset table. Optimized the Test Notification with high-priority flags and 'original(calibrated)' format for better watch mirroring. Ensured consistent dual-value display across the app. Verified build.
- **Acceptance Criteria:**
  - Settings screen uses a scrollable layout (Column/LazyColumn)
  - 'Add Range' button successfully adds new entries to the offset table
  - Test Notification triggers high-priority alert with 'original(calibrated)' format
  - Dashboard and Notifications consistently show 'original(calibrated)' values
  - Build passes
- **Duration:** 1m 1s

### Task_9_SensorHealthMonitoring: Implement Sensor Health Monitoring on the Dashboard, including days remaining and expiry date/time display.
- **Status:** COMPLETED
- **Updates:** Implemented sensor health monitoring on the Dashboard. Updated API models to parse sensor activation timestamps and calculated the 14-day expiration window. Added a 'Sensor Health' card to the Dashboard displaying days remaining and the specific expiry date/time. Verified the build passes.
- **Acceptance Criteria:**
  - Sensor expiration data fetched or calculated from API response
  - Dashboard displays 'Days Remaining' and 'Expiry Date/Time'
  - UI follows Material 3 design and is adaptive
  - Build passes
- **Duration:** 3m 9s

### Task_10_FinalVerification_Phase1: Final verification of the Dashboard improvements, including sensor health and trend graph visibility.
- **Status:** COMPLETED
- **Updates:** Verified the visibility of the trend graph and sensor health monitoring components. Ensured that demo data correctly populates the 12-hour history for the graph. The app shows stable glucose readings and accurate expiration tracking.
- **Acceptance Criteria:**
  - App does not crash
  - Build passes
  - Trend Graph is visible and populates correctly
  - Sensor health data is accurate
  - Critic agent verifies stability

### Task_11_CapillaryLogging_and_AutoCalibration: Implement Room database for capillary logs, a UI for finger-prick recording, and an auto-calibration engine to suggest/apply offsets.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Room database stores capillary readings with timestamps
  - UI allows users to log finger-prick glucose values
  - Auto-calibration engine calculates suggested offsets by comparing logs with sensor data
  - Users can apply suggested offsets to the range table
  - Build passes
- **StartTime:** 2026-07-14 16:25:12 CEST

### Task_12_FinalRunAndVerify_Calibration: Final run and verification of capillary logging, auto-calibration accuracy, and general application stability.
- **Status:** PENDING
- **Acceptance Criteria:**
  - App does not crash
  - Build passes
  - Capillary logging and offset suggestion verified
  - Dual-value dashboard reflects auto-calibrated readings
  - Critic agent confirms UI polish and functionality

