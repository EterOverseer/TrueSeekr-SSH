<p align="center">
  <img src="app/src/main/res/drawable/pocketssh_icon_1795059769535.jpg" alt="PocketSSH logo" width="140" />
</p>

<h1 align="center">PocketSSH</h1>

<p align="center">
  <b>A secure, native Android SSH client</b><br/>
  Persistent profiles · real interactive terminal · SFTP file manager · Keystore-protected credentials
</p>

<p align="center">
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-24D8E7?style=for-the-badge&logo=android&logoColor=white&labelColor=151A20" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-B6F36A?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=151A20" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-24D8E7?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=151A20" />
  <img alt="Security first" src="https://img.shields.io/badge/Security-first-B6F36A?style=for-the-badge&logo=letsencrypt&logoColor=white&labelColor=151A20" />
</p>

<p align="center">
  <a href="#-features">Features</a> ·
  <a href="#-security">Security</a> ·
  <a href="#-getting-started">Getting started</a> ·
  <a href="#-project-structure">Structure</a> ·
  <a href="#-documentation">Docs</a>
</p>

<!-- TODO: add screenshots here, e.g. a row of 3-4 images (servers list, terminal, SFTP, settings) -->

---

## ✨ Features

### 🖥️ Server profiles
- Save SSH profiles with **groups**, **favorites**, **recent connections** and a live view of **active sessions**
- Three authentication methods: **password**, **private key**, and **private key + passphrase**
- Search and sort by name, last connected, host / IP, or most used
- Export and import profile metadata as JSON, optionally **encrypted with a passphrase**. Credentials are never included in exports

### ⌨️ Real interactive terminal
- Jetpack Compose terminal powered by ConnectBot's `termlib` (native `libvterm` core)
- **Multiple session tabs**, in-terminal **search**, a function-key bar and an extra key toolbar
- Saved **command snippets** in a quick-access sheet, with a confirmation step before anything is sent to the server
- Five terminal themes: *Obsidian Aurora*, *Charcoal Lime*, *Teal Matrix*, *Solarized Dark*, *High Contrast*
- Configurable font (JetBrains Mono or system monospace), font size, cursor style (block / underline / bar), scrollback, haptic key feedback, keep-alive, connection timeout and reconnect prompt

### 📊 Server dashboard
Peek at a connected server without leaving the app: OS and kernel, hostname, uptime, load average, CPU, memory, disk mounts and network interfaces (whatever the server exposes).

### 📁 SFTP file manager
- Browse remote files in **list or grid** view
- Create folders, rename and delete
- Upload and download through the Android file picker (Storage Access Framework), so no broad storage permission is needed

### 🎨 Look and feel
- Dark (*Obsidian*), Light, or follow the system theme
- Four accent presets: *Cyan Aurora*, *Electric Lime*, *Ice Signal*, *Cyber Violet*
- Bundled **JetBrains Mono**, **Space Grotesk** and **Noto Sans Thai**, with UI labels in both Thai and English

---

## 🔐 Security

PocketSSH is built around "never trust, never store plaintext":

| Area | How it works |
|---|---|
| **Credentials** | Passwords and private keys are encrypted with **AES-256-GCM** using an **Android Keystore** master key, stored in app-private no-backup storage |
| **Host keys** | Unknown hosts pause the connection and show the SHA-256 fingerprint for explicit approval. A **changed key is rejected** and the user is warned about a possible MITM |
| **App lock** | Biometric prompt plus PIN (PBKDF2-HMAC-SHA256) with configurable auto-lock |
| **Passphrases** | Kept only in memory and cleared on lock and disconnect |
| **Transport** | Modern key exchange, host key and cipher policy with no silent downgrade to legacy algorithms |
| **Backups** | `allowBackup="false"`, with data-extraction rules excluding app data |
| **Screen privacy** | Optional `FLAG_SECURE` to block screenshots and the recents preview |
| **Logs** | Passwords, passphrases and PEM blocks are redacted from error text |

Permissions requested: `INTERNET`, `ACCESS_NETWORK_STATE`, `USE_BIOMETRIC`. Nothing else.

Full details: [`docs/SECURITY_ARCHITECTURE.md`](docs/SECURITY_ARCHITECTURE.md)

---

## 🧰 Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| SSH / SFTP | [ConnectBot `sshlib`](https://github.com/connectbot/cbssh) (pure-Kotlin SSHv2 over coroutines) |
| Terminal | [ConnectBot `termlib`](https://github.com/connectbot/termlib) |
| Storage | Room (profiles, groups, known hosts, snippets), DataStore (settings) |
| Security | Android Keystore, AndroidX Biometric |
| Async / serialization | Kotlin Coroutines and Flow, kotlinx.serialization |
| Build | Gradle (Kotlin DSL), KSP, version catalog in `gradle/libs.versions.toml` |
| Tests | JUnit 4, Robolectric, Compose UI tests |

Dependency versions and licenses: [`docs/DEPENDENCIES_AND_LICENSES.md`](docs/DEPENDENCIES_AND_LICENSES.md)

---

## 🚀 Getting started

### Requirements

- JDK 17 or newer
- Android SDK Platform **36.1** (`compileSdk 36.1`, `minSdk 24`, `targetSdk 36`)
- Gradle **9.3.1** (this repo does not ship a Gradle wrapper; run `gradle wrapper --gradle-version 9.3.1` once if you want one)

### Build a debug APK

```bash
git clone https://github.com/EterOverseer/TrueSeekr-SSH.git
cd TrueSeekr-SSH

# The debug signing config expects debug.keystore at the repo root.
# The file is git-ignored, so create it once:
keytool -genkeypair -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Or just open the project in **Android Studio** and press **Run**, after creating `debug.keystore` as above.

### Run the checks

```bash
gradle :app:testDebugUnitTest   # unit + Robolectric tests
gradle :app:lintDebug           # Android Lint
```

### Build with GitHub Actions

`.github/workflows/build-apk.yml` builds a debug APK on every push. Open the run in the **Actions** tab and download the **`app-debug`** artifact. The workflow creates the debug keystore for you.

> Each CI run generates a fresh debug key, so APKs from different runs have different signatures. Uninstall the previous build before installing a new one.

### Release build

Signing is configured through environment variables, so keys never live in the repo:

```bash
export KEYSTORE_PATH=/secure/path/to/pocketssh-release.jks   # key alias: upload
export STORE_PASSWORD="<keystore-password>"
export KEY_PASSWORD="<key-password>"

gradle :app:assembleRelease   # APK
gradle :app:bundleRelease     # AAB for Google Play
```

Never commit keystores or passwords. See [`docs/RELEASE_BUILD.md`](docs/RELEASE_BUILD.md) for the pre-release checklist.

---

## 🗂️ Project structure

```text
app/src/main/java/com/example/
├── MainActivity.kt, PocketSshApplication.kt
├── core/        formatters, host-fingerprint utils, network monitor
├── data/        Room database, entities, DAOs, repositories
├── domain/      models and enums
├── profiles/    server list view model
├── security/    Keystore crypto, credential vault, biometric + PIN lock, encrypted backups
├── settings/    DataStore preferences and settings view model
├── sftp/        SFTP manager and view model
├── ssh/         session manager, host key verifier
├── terminal/    terminal bridge and view model
└── ui/          Compose screens, reusable components, theme and design tokens
```

---

## 📚 Documentation

| Document | What's inside |
|---|---|
| [`docs/SECURITY_ARCHITECTURE.md`](docs/SECURITY_ARCHITECTURE.md) | Data storage, host-key verification, crypto policy, device hardening |
| [`docs/RELEASE_BUILD.md`](docs/RELEASE_BUILD.md) | Debug and release builds, signing, pre-release checklist |
| [`docs/DEPENDENCIES_AND_LICENSES.md`](docs/DEPENDENCIES_AND_LICENSES.md) | Pinned dependencies, licenses, evaluation notes |

---

<p align="center">
  Made with Kotlin and Jetpack Compose 🛡️
</p>
