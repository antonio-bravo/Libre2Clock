# Implementation Plan - Notification Calibration, Scrollable Graph, and Sensor Refresh

Address calibration issues in notifications, improve graph readability/navigation with scrolling (12h viewport), and add a manual refresh for sensor data.

## User Review Required

> [!IMPORTANT]
> The **glucose notifications** will now be fully reactive. Any change to your offset settings will reflect immediately in the background notification.
>
> The **Trend Graph** will now allow you to scroll back in time to see all your stored history. By default, it will show a **12-hour window** on the screen, which makes the points much easier to read than the previous compressed view.

## Proposed Changes

### Background Service & Calibration Centralization

#### [MODIFY] [GlucoseForegroundService.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/service/GlucoseForegroundService.kt)
- Add observers for all calibration-related preferences: `glucoseOffset`, `glucoseOffsetRanges`, `autoAdjustEnabled`, and `capillaryReadings`.
- Store these values in the service to ensure that every background update uses the latest user settings.
- Use `GlucoseProcessor.process` in `updateNotification`, `sendWatchAlertNotification`, and `sendThresholdAlarmNotification` to ensure the "dual value" `raw(calibrated)` is always accurate.

### UI & UX Enhancements

#### [MODIFY] [TrendGraph.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt)
- **Date Format**: Change X-axis labels to `dd-MM` and `HH:mm`.
- **Horizontal Scrolling**:
    - Wrap the graph in a `HorizontalScroll`.
    - Implement a fixed scaling factor: `12 hours = Screen Width`.
    - The graph will automatically expand its width based on the amount of data available in `historicalData`.
    - Keep the Y-axis labels (50, 100...) visible or fixed if possible (or just draw them frequently).
- **Initial Position**: Automatically scroll to the end (most recent data) when the graph loads.

#### [MODIFY] [DashboardScreen.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt)
- **SensorHealthCard**: Add a `Refresh` icon button next to the `Copy` button.
- This button will trigger `viewModel.refresh()`, forcing an immediate update of sensor status and glucose data from the LibreLinkUp API.

## Verification Plan

### Manual Verification
1.  **Notification Calibration**:
    *   Change the Manual Offset in Settings.
    *   Check the persistent notification; it should update the `(calibrated)` value immediately.
2.  **Scrollable Graph**:
    *   Verify that the graph is no longer "squashed" and you can scroll horizontally.
    *   Check that the date format is `DD-MM`.
3.  **Sensor Refresh**:
    *   Tap the new refresh button in the **Sensor Health** card.
    *   Verify that the "last sync" time or sensor info updates.
