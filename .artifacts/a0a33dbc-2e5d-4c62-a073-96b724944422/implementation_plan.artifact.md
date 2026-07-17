# Implementation Plan - Fix Data Display and Enable Cross-Day History

The goal is to fix the empty dashboard by correctly fetching, processing, and storing historical glucose data (`graphData`) from the LibreLinkUp API. This data will be used to populate the trend graph and calculate metrics like Average Glucose and HbA1c.

## User Review Required

> [!IMPORTANT]
> To support cross-IDE usage (VS Code/Android Studio) and avoid regional login issues, we will implement an automatic server redirection. The app will also start caching historical data locally to ensure metrics are available even when offline.

## Proposed Changes

### Data Layer

#### [MODIFY] [LibreModels.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/model/LibreModels.kt)
- Update `LoginResponse` and `LoginData` to handle `redirect: true` and the `region` field.

#### [MODIFY] [LibreService.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/api/LibreService.kt)
- Add support for changing the `BASE_URL` dynamically based on regional redirects.
- Fix `Account-Id` header generation to match the SHA-256 requirement precisely.

#### [MODIFY] [GlucoseRepositoryImpl.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/data/repository/GlucoseRepositoryImpl.kt)
- **Login Redirect**: Implement retry logic if a redirect is required.
- **Data Persistence**: Ensure `graphData` is merged into the local `historicalGlucoseArchive` every time it is fetched.
- **Metric Readiness**: Ensure the local cache is initialized correctly so the `DashboardViewModel` can calculate averages.

### UI & Logic

#### [MODIFY] [DashboardScreen.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/ui/dashboard/DashboardScreen.kt)
- Verify `calculateDashboardMetrics` logic to ensure it correctly filters data for Today, Yesterday, Week, and Month based on the new stored history.

#### [MODIFY] [TimestampParser.kt](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/app/src/main/java/com/tonio/libre2clock/util/TimestampParser.kt)
- Update formatters to be `Locale`-independent (using `Locale.US`) to prevent parsing errors on devices with different language settings.

## Verification Plan

### Automated Tests
- Test regional redirect logic by mocking a redirect response.
- Test `TimestampParser` with timestamps from `graph_response.json`.

### Manual Verification
- Perform a fresh login and verify the "Avg Glucose" and "Estimated HbA1c" values.
- Verify the central panel (Yesterday/Week/Month) correctly displays data.
- Verify the trend graph shows historical data from the start of the day.
- Run a local export and check that the generated JSON contains all historical measurements.
