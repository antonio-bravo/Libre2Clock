# Libre2Clock

Libre2Clock is an Android app that connects to LibreLinkUp, fetches glucose data, and mirrors readable glucose updates to a smartwatch via Android notifications (for example, Amazfit Bip S through Zepp Life notification mirroring).

The app focuses on:
- Clear glucose display in dual format: raw(offset-adjusted) mg/dL + trend arrow.
- Continuous background sync with a foreground service.
- Configurable periodic watch push notifications.
- Optional high/low glucose alarms (disabled by default unless explicitly enabled).
- Flexible calibration tools (manual offset, range-based offsets, optional capillary auto-adjust).

## Main Features

- LibreLinkUp login and token persistence.
- Demo mode with mock glucose and sensor data.
- Dashboard with:
  - Current glucose card.
  - Trend arrow.
  - Sensor health card (remaining days, expiry, serial).
  - Historical trend graph.
- Calibration system:
  - Global manual offset.
  - Range-based offsets.
  - Optional capillary-based auto-adjust.
- Watch notifications:
  - Periodic push at user-defined interval (5 to 180 minutes).
  - Message format optimized for watch readability.
  - Test notification from Settings.
- Alarm controls:
  - Independent low and high glucose alarm toggles.
  - Both are OFF by default.
  - Cooldown protection to avoid repeated alarm spam.

## Glucose Display Format

The app standard display is:

raw_value(calibrated_value) mg/dL + trend_arrow

Example:

135(155) mg/dL ↗

This same format is used consistently in app notifications intended for watch mirroring.

## Watch Behavior

Libre2Clock supports two independent notification channels in behavior:

1. Periodic watch push:
- Sends glucose + trend at your configured interval.
- Controlled by "Enable periodic watch push".

2. Threshold alarms:
- Low glucose alarm when calibrated value < 70 mg/dL.
- High glucose alarm when calibrated value > 180 mg/dL.
- Controlled independently by dedicated toggles in Settings.
- Disabled by default.

This means you can keep periodic watch updates ON while keeping alarms OFF.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Coroutines + Flow
- DataStore Preferences
- Retrofit + OkHttp + Moshi
- KSP (Moshi codegen)
- Android foreground service for continuous sync

## Requirements

- Android Studio (latest stable recommended)
- JDK 17 or newer (project uses modern Android Gradle Plugin)
- Android SDK configured
- For watch mirroring: a smartwatch app that mirrors Android notifications (such as Zepp Life)

## Getting Started

1. Clone the repository.
2. Open in Android Studio.
3. Sync Gradle.
4. Run the app on a device with internet access.
5. Sign in with LibreLinkUp credentials, or start Demo Mode.

## Build APKs Locally

Release APK:

./gradlew assembleRelease

Debug APK:

./gradlew assembleDebug

Output folders:
- app/build/outputs/apk/release/
- app/build/outputs/apk/debug/

## GitHub Actions Release Pipeline

The project includes a manual workflow in:

.github/workflows/build-release.yml

What it does:
- Accepts optional version input.
- If version is empty, generates: 1.YYYYMMDD.HHmmss (UTC).
- Builds both release and debug APKs.
- Publishes a GitHub Release with both files:
  - libre2clock-vVERSION.apk
  - libre2clock-vVERSION_debug.apk

## Project Structure (Simplified)

- app/src/main/java/com/tonio/libre2clock/
  - data/
    - api/ (LibreLinkUp networking)
    - model/ (DTOs and domain models)
    - repository/ (sync, calibration, preferences)
  - service/
    - GlucoseForegroundService.kt
  - ui/
    - login/
    - dashboard/
    - settings/
    - navigation/

## Security and Privacy Notes

- Authentication tokens are stored in DataStore preferences on-device.
- The app communicates with LibreView endpoints over HTTPS.
- Notification mirroring may expose glucose values on paired devices; configure lockscreen and watch privacy settings as needed.

## Medical Disclaimer

This software is not a medical device and does not replace professional medical advice, diagnosis, or treatment. Always follow guidance from qualified healthcare professionals.

## Development Environment: Oh My Posh (macOS + zsh)

If you want the same terminal prompt style while working on this project, use your existing Oh My Posh config from this repository.

### 1) Install Oh My Posh

brew install jandedobbeleer/oh-my-posh/oh-my-posh

### 2) Install a Nerd Font (recommended)

brew tap homebrew/cask-fonts
brew install --cask font-meslo-lg-nerd-font

Then set your terminal font to "MesloLGS NF".

### 3) Use your current prompt config

This repository uses:

.oh-my-posh/libre2clock.omp.json

Add this line to your ~/.zshrc:

eval "$(oh-my-posh init zsh --config $HOME/oh-my-posh/emodipt-extend.omp.json)"

If your preferred file has a different name, replace libre2clock.omp.json with your own file name.

Then reload your shell:

source ~/.zshrc

### 4) Optional quality-of-life aliases

Add to ~/.zshrc:

alias gs='git status -sb'
alias ga='git add'
alias gc='git commit'
alias gp='git push'
alias ll='ls -lah'

## Troubleshooting

- No watch updates:
  - Ensure periodic watch push is enabled in Settings.
  - Verify notification permission is granted.
  - Confirm your watch app is mirroring this app notifications.
- Too many sounds/alerts:
  - Disable low/high glucose alarm toggles.
  - Keep periodic push enabled if you only want passive updates.
- Login issues:
  - Recheck LibreLinkUp credentials.
  - Verify network connectivity.

## License

No license file is currently included in this repository. If you plan to publish or accept contributions, consider adding a LICENSE file.
