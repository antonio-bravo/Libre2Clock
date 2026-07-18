# Implementation Plan - Final Fix for Graph Visualization and Backup Integrity

Address the "empty graph" issue by fixing the path drawing logic, improving data sorting, and ensuring the backup restoration process is fully compatible with all data formats.

## Proposed Changes

### Data Layer

#### [MODIFY] [PreferenceManager.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt)
- **Lenient JSON**: Force use of the custom `json` instance (with `ignoreUnknownKeys = true`) in all DataStore read/write operations.
- **Robust Keying**: Ensure the `mergeHistoricalMeasurements` logic handles potentially malformed timestamps gracefully during restoration.

### UI & Visualization

#### [MODIFY] [TrendGraph.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/TrendGraph.kt)
- **Data Sanitization**: Filter the `measurements` list to remove any entries with invalid/unparseable timestamps BEFORE sorting or drawing.
- **Strict Chronological Sorting**: Ensure the list is sorted ASCENDING (oldest to newest).
- **Path Logic Fix**: Use a boolean flag `isFirstPoint` instead of `index == 0` to trigger the initial `moveTo` call. This ensures that even if the first few items in the original list were filtered out, the drawing still starts correctly.
- **X-Axis Optimization**:
    - Format dates as `dd-MM`.
    - Ensure Y-axis labels are drawn at reasonable intervals to avoid overlapping during scroll.
- **Infinite Loop Guard**: Add safety checks to the grid drawing loops.

## Verification Plan

### Manual Verification
1.  **Restore Backup**:
    *   Load `libre2clock_history_backup (1).json`.
    *   Verify that the graph immediately populates with data from yesterday.
2.  **Navigation**:
    *   Scroll left to see yesterday's data.
    *   Verify the line is continuous (no gaps unless they exist in the data).
3.  **Metrics**:
    *   Check that "Avg Glucose" for yesterday still shows the correct values.
