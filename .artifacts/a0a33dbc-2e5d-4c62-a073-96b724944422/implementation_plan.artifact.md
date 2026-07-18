# Implementation Plan - Fix History Restore and Metric Reactivity

The user reported that after restoring a backup, historical data (specifically "Yesterday") does not appear in the dashboard metrics or the graph. This is due to an architectural gap where the `GlucoseRepository` does not observe changes in the local storage after a restore operation, and the merging logic for backups is inconsistent with the main data ingestion flow.

## Proposed Changes

### Data & Logic Refinement

#### [MODIFY] [PreferenceManager.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/PreferenceManager.kt)
- **Fix Merge Logic**: Update `mergeHistoricalMeasurements` to use a robust key (Parsed Instant + Raw Value) instead of the fragile string-based key.
- **Improve Sorting**: Ensure merged data is sorted by time (Instant) instead of alphabetical timestamp strings.
- **Consistency**: Use the same pruning logic as the repository to respect `historyRetentionDays`.

#### [MODIFY] [GlucoseRepositoryImpl.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt)
- **Reactive Observation**: Change `historicalGlucose` and `currentGlucose` to be derived flows from `PreferenceManager`. Instead of holding independent `MutableStateFlow`s, the repository will now actively observe the DataStore.
- This ensures that if a user restores a backup, the UI updates **instantly** without needing a restart.

### Verification Plan

### Manual Verification
1.  **Backup/Restore**:
    *   Note the current data in the dashboard.
    *   Perform a Restore from the provided backup file.
    *   Verify that the graph and "Yesterday" metrics populate **immediately** without closing the app.
2.  **Metrics Check**:
    *   Verify that "Estimated HbA1c" values change after the restore, reflecting the larger dataset.
3.  **Graph Check**:
    *   Scroll back in the trend graph to verify that data points from yesterday are visible.
