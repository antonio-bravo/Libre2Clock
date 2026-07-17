# Implementation Plan - UI Metrics and Graph Fixes

The user is experiencing issues with the dashboard:
1.  **Missing Values**: Top and central metrics (HbA1c, Averages, Hypos) are not showing values.
2.  **Graph Issue**: "Glucose Trend" graph is not painting as expected.
3.  **Formatting Requirement**: Averages should be displayed as `valor_app(valor_con_offset) ± oscillation`.

## Proposed Changes

### [Dashboard Metrics]

#### [MODIFY] [DashboardScreen.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt)
- Update `DisplayMetric` to store `avgRaw`, `avgCalibrated`, and `oscillation`.
- Update `buildDisplayMetric` to calculate these values and format the output string according to the new requirement: `avgRaw(avgCalibrated) ± max(plus, minus)`.
- Use the `±` symbol.
- Ensure `calculateDashboardMetrics` doesn't filter out data too aggressively.
- Refactor `MetricCell` and `CornerMetric` to accommodate the new display format if necessary.

### [Glucose Trend Graph]

#### [MODIFY] [TrendGraph.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt)
- Update `InteractiveTrendGraph` to use a time-based X-axis instead of an index-based one. This ensures points are correctly spaced according to their timestamps.
- Fix potential division by zero or small list issues.
- Improve the `Canvas` drawing logic to ensure paths are visible and correctly aligned with time labels.
- Add horizontal lines for target ranges (70-180) more clearly.

### [Data Processing]

#### [MODIFY] [GlucoseRepositoryImpl.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt)
- Review `mergeAndPruneHistory` to ensure it doesn't drop valid data.
- Ensure `historicalGlucose` is always sorted ASCENDING by time.

## Verification Plan

### Automated Tests
- I will check if there are unit tests for `calculateDashboardMetrics` or `GlucoseProcessor` and update them if needed.

### Manual Verification
- I will ask the user to verify the UI changes.
- I'll check the logs if I can run the app, but since I can't see the screen directly without `take_screenshot`, I will rely on code analysis and user feedback.
- I will use `render_compose_preview` for `DashboardScreen` and `InteractiveTrendGraph` to verify the layout.
