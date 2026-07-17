# Implementation Plan - Fix Missing `jlink` Executable Error

The project build is failing because Gradle is using a JRE from a VS Code extension that lacks the `jlink` executable. AGP (Android Gradle Plugin) requires `jlink` to create custom JDK images for projects with `compileSdk` 30 or higher.

## User Review Required

> [!IMPORTANT]
> This plan will hardcode the Gradle JDK path to the Android Studio bundled JDK in your `gradle.properties`. This path is specific to macOS. If you share this project with developers on other operating systems, they may need to adjust this setting or you may want to remove it before committing to a shared repository.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle.properties](file:///Users/antonio-bravo/AndroidStudioProjects/Libre2Clock/gradle.properties)

Add `org.gradle.java.home` pointing to the Android Studio bundled JDK.

```properties
org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify that the project builds successfully without the `jlink` error.

### Manual Verification
- Verify in Android Studio that the Gradle sync completes successfully.
