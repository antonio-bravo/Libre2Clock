# Implementation Plan - Reactive Glucose Calibration and UI Enhancements

Enable real-time, retroactive glucose calibration and improve the dashboard UI for better data visualization.

## Proposed Changes

### Data & Architecture

#### [MODIFY] [GlucoseRepositoryImpl.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt)
- Stop applying calibration offsets during data ingestion. The repository will now store "pure" raw data from the API.
- Update `measurementKey` to not depend on `calibratedValue` for merging, ensuring consistency.

#### [MODIFY] [DashboardViewModel.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardViewModel.kt)
- Inject `PreferenceManager`.
- Implement reactive transformation: combine `historicalGlucose` and `currentGlucose` with offset preferences (`glucoseOffset`, `glucoseOffsetRanges`, `autoAdjustEnabled`, `capillaryReadings`).
- Use `GlucoseProcessor.process` inside the `combine` block to update the entire dataset whenever any setting changes.

### UI Enhancements

#### [MODIFY] [DashboardScreen.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt)
- **Metrics Calculation**: Update `calculateDashboardMetrics` to produce dual-line metrics for Average Glucose.
    - Line 1: Raw average with `±` (Max/Min deviation).
    - Line 2: Calibrated average with `±` (Max/Min deviation).
- **Metric Display**: Update `CornerMetric` and `GlucoseCard` to support the new multi-line format.

#### [MODIFY] [TrendGraph.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt)
- **Grid Lines**: Increase visibility of horizontal grid lines at 50 mg/dL intervals.
- **Labels**: Add text labels for the 50, 100, 150... mg/dL levels on the Y-axis.

### Navigation

#### [MODIFY] [NavGraph.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/navigation/NavGraph.kt)
- Update `DashboardViewModel` instantiation to include `preferenceManager`.

## Verification Plan

### Automated Tests
- Verify that changing a preference (e.g., `glucoseOffset`) in `PreferenceManager` triggers an update in the `DashboardViewModel`'s `historicalData` Flow.

### Manual Verification
1. Open the **Dashboard**.
2. Go to **Settings** and change the Manual Offset.
3. Return to the **Dashboard** and verify that the graph and all average values update **instantly** without needing a manual refresh.
4. Verify the new layout of the "Avg Glucose" metric (top-right).
5. Verify that the trend graph now has clearly visible horizontal lines and Y-axis labels.
