# TrueSeekrSSH

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="TrueSeekrSSH app icon" width="128" />
</p>

<h1 align="center">TrueSeekrSSH</h1>

<p align="center">
  <strong>A native Android SSH client for managing remote servers.</strong><br />
  Saved server profiles · Interactive terminal · SFTP file manager · Encrypted credentials
</p>

<p align="center">
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img alt="Kotlin 2.4.21" src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img alt="Security focused" src="https://img.shields.io/badge/Security-focused-16A085?style=for-the-badge&logo=letsencrypt&logoColor=white" />
</p>

<p align="center">
  <a href="#features">Features</a> ·
  <a href="#security">Security</a> ·
  <a href="#download">Download</a> ·
  <a href="#build-from-source">Build from source</a> ·
  <a href="#technology-stack">Technology stack</a> ·
  <a href="#documentation">Documentation</a>
</p>

---

## Overview

TrueSeekrSSH is a native Android application for connecting to SSH servers, working in a real terminal, and managing remote files over SFTP. It keeps server profiles organized, protects saved credentials with Android Keystore-backed encryption, and provides a configurable interface for mobile use.

The app is built with Kotlin and Jetpack Compose. Its interface includes Thai and English labels.

## Features

### Server profiles
- Save and organize SSH connections with groups and favorites.
- Quickly revisit recent connections and see active sessions.
- Authenticate with a password, a private key, or a private key with a passphrase.
- Search and sort profiles by name, host/IP, last connection, or usage.
- Import and export profile metadata as JSON. Exports can be encrypted with a passphrase; credentials are not included.

### Interactive terminal
- Native terminal interface powered by ConnectBot's terminal library.
- Multiple terminal session tabs.
- Search within terminal output.
- Function-key row and additional terminal key toolbar.
- Saved command snippets with a confirmation step before sending commands.
- Five terminal themes: Obsidian Aurora, Charcoal Lime, Teal Matrix, Solarized Dark, and High Contrast.
- Configurable font, font size, cursor style, scrollback, haptic feedback, keep-alive, connection timeout, and reconnect prompt.

### Server dashboard
View information reported by the connected server without leaving the app:
- Operating system, kernel, and hostname
- Uptime and load average
- CPU and memory information
- Disk mounts and network interfaces

The exact information available depends on the remote server and its installed utilities.

### SFTP file manager
- Browse remote directories in list or grid layout.
- Create directories, rename files, and delete remote items.
- Upload and download files using the Android document picker.
- Uses Android's Storage Access Framework rather than requesting broad storage access.

### Personalization
- Dark, light, or system theme.
- Four accent presets: Cyan Aurora, Electric Lime, Ice Signal, and Cyber Violet.
- JetBrains Mono, Space Grotesk, and Noto Sans Thai bundled with the app.
- UI labels available in Thai and English.

## Security

Security-sensitive data is handled locally and protected by Android platform features.

| Area | Implementation |
|---|---|
| Saved credentials | Passwords and private keys are encrypted with AES-256-GCM using a master key held by Android Keystore. |
| Local storage | Encrypted credentials are stored in app-private, no-backup storage. |
| SSH host verification | New hosts require explicit fingerprint approval. A changed host key is rejected and reported to the user. |
| App lock | Optional biometric authentication and PIN-based app lock with configurable auto-lock. |
| Key passphrases | Kept in memory and cleared during lock and disconnect flows. |
| SSH transport | Modern key exchange, host-key, and cipher policies; no silent downgrade to legacy algorithms. |
| Backups | Android backup is disabled and extraction rules exclude app data. |
| Screen privacy | Optional secure-screen setting to block screenshots and the recent-app preview. |
| Diagnostic output | Passwords, passphrases, and private-key PEM blocks are redacted from displayed error text. |

### Android permissions

The application requests only:
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `USE_BIOMETRIC`

See the [Security Architecture](docs/SECURITY_ARCHITECTURE.md) document for implementation details.

## Download

The latest automated debug build is published on the GitHub Releases page.

**[Download the latest TrueSeekrSSH APK](https://github.com/EterOverseer/TrueSeekr-SSH/releases/tag/latest-build)**

To download an APK produced by a build on another branch, open that run in **Actions** and download the `app-debug` artifact.

> **Important — debug build signing:** GitHub Actions generates a temporary debug keystore for each run. APKs from different workflow runs therefore have different signatures. Uninstall the previously installed CI build before installing a new CI build. These debug APKs are intended for testing, not as a stable upgrade channel.

## Build from source

### Requirements

- JDK 17
- Android SDK Platform 36.1
- Gradle 9.3.1
- Android SDK configured for your environment

The repository includes Gradle wrapper configuration but does not currently include the `gradlew` launcher scripts. Use an installed Gradle 9.3.1 executable, or generate wrapper scripts locally if preferred.

### Clone the repository

```bash
git clone https://github.com/EterOverseer/TrueSeekr-SSH.git
cd TrueSeekr-SSH
```

### Create the local debug keystore

The debug signing configuration expects `debug.keystore` in the repository root. This file is ignored by Git.

```bash
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

### Build a debug APK

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run tests and Android Lint

```bash
gradle :app:testDebugUnitTest
gradle :app:lintDebug
```

### Build with GitHub Actions

The workflow is defined in [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).

- Every push and manual workflow run builds a debug APK.
- Every run uploads an `app-debug` artifact, including runs from feature branches.
- A push to `main` refreshes the rolling `latest-build` pre-release.
- Pushing a version tag such as `v1.0.0` creates a regular GitHub Release.

To build manually, open **Actions → Build APK → Run workflow** and select the `main` branch.

### Create a signed release build

Release signing is configured through environment variables. Do not commit keystores or passwords to the repository.

The configured key alias is `upload`.

```bash
export KEYSTORE_PATH=/secure/path/to/your-release-key.jks
export STORE_PASSWORD="<keystore-password>"
export KEY_PASSWORD="<key-password>"

# Release APK
gradle :app:assembleRelease

# Android App Bundle for Google Play
gradle :app:bundleRelease
```

Before publishing, increment `versionCode` and `versionName` in `app/build.gradle.kts`, verify the signing configuration, and run the tests and Android Lint checks.

See [Release Build Instructions](docs/RELEASE_BUILD.md) for the release checklist.

## Technology stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| SSH / SFTP | [ConnectBot sshlib](https://github.com/connectbot/cbssh) |
| Terminal | [ConnectBot termlib](https://github.com/connectbot/termlib) |
| Local database | Room |
| Preferences | DataStore |
| Credential protection | Android Keystore, AES-256-GCM, AndroidX Biometric |
| Asynchronous work | Kotlin Coroutines and Flow |
| Serialization | kotlinx.serialization |
| Build | Gradle Kotlin DSL, version catalog, KSP |
| Tests | JUnit 4, Robolectric, Compose UI tests |

## Project structure

The Android source is organized by responsibility:

```text
app/src/main/java/com/example/
├── core/       Shared utilities and network monitoring
├── data/       Room database, entities, DAOs, repositories
├── domain/     Domain models and enums
├── profiles/   Server profile presentation and state
├── security/   Credential vault, encryption, app lock, backups
├── settings/   App preferences and settings state
├── sftp/       SFTP operations and file management
├── ssh/        SSH sessions and host-key verification
├── terminal/   Terminal integration and session state
└── ui/         Compose screens, components, and theme
```

## Documentation

| Document | Description |
|---|---|
| [Security Architecture](docs/SECURITY_ARCHITECTURE.md) | Credential storage, host-key verification, cryptography, and device security |
| [Release Build Instructions](docs/RELEASE_BUILD.md) | Debug and release builds, signing, and pre-release checklist |
| [Dependencies and Licenses](docs/DEPENDENCIES_AND_LICENSES.md) | Dependency versions, licenses, and evaluation notes |

---

<p align="center">
  Built with Kotlin and Jetpack Compose.
</p>
