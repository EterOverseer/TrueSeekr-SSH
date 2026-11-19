# PocketSSH — Build & Release Instructions

## Prerequisites

- JDK 17+ (configured for Java 11 source/target compatibility in Gradle)
- Android SDK Platform 36 (`compileSdk 36.1`, `minSdk 24`, `targetSdk 36`)
- Gradle 9.3.1 (via `gradle` command in CI/container or standard Android Studio Gradle sync)

## 1. Debug APK & Verification

```bash
# Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# Build Debug APK
gradle :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Run Android Lint
gradle :app:lintDebug
```

## 2. Release APK / AAB Signing

Do **not** commit private release keystores or passwords into source control. Configure signing via environment variables before running the release build:

```bash
export KEYSTORE_PATH=/secure/path/to/pocketssh-release.jks
export STORE_PASSWORD="<keystore-password>"
export KEY_PASSWORD="<key-password>"

gradle :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Or Android App Bundle for Google Play:
gradle :app:bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

## 3. Pre-Release Checklist

1. Verify `app/build.gradle.kts` `versionCode` and `versionName` are incremented.
2. Confirm `android:allowBackup="false"` and `backup_rules.xml` / `data_extraction_rules.xml` exclusions remain intact.
3. Confirm all unit/Robolectric tests and `lintDebug` pass with zero security errors.
