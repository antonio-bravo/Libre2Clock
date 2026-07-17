# Implementation Plan - Enhanced Sensor Health Visibility

Add more detailed sensor information to the dashboard and implement a "copy to clipboard" feature for easy sharing or recording of sensor status.

## Proposed Changes

### Data Layer

#### [MODIFY] [LibreModels.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/model/LibreModels.kt)
- Update `SensorStatus` data class to include `startDate` as a `String`.

#### [MODIFY] [GlucoseRepositoryImpl.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt)
- Update the mapping logic to extract and format the sensor's activation timestamp into a readable `startDate` string.

### UI Layer

#### [MODIFY] [DashboardScreen.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt)
- **SensorHealthCard Improvements**:
    - Display the `startDate` alongside the `expiryDate`.
    - Add a "Copy" icon button to the top-right of the card.
    - Implement the copy logic using `LocalClipboardManager` to export:
        - Serial Number
        - Start Date
        - Expiration Date
        - Days Remaining
- **Format**:
    - The copied text will look like:
      ```text
      Sensor SN: [SN]
      Started: [Date]
      Expires: [Date]
      Remaining: [Days] days
      ```

## Verification Plan

### Manual Verification
1. Open the **Dashboard**.
2. Verify that the **Sensor Health** card now shows "Started: [Date]".
3. Click the **Copy** button.
4. Paste the content into another app (e.g., Keep, WhatsApp) and verify that all four requested fields are present and formatted correctly.
