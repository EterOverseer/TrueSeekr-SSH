# PocketSSH — Third-Party Dependencies & Licenses

All dependency versions are pinned in `gradle/libs.versions.toml` and verified for Android compatibility (`minSdk 24`, `compileSdk 36.1`).

| Component | Artifact / Coordinates | Version | License | Purpose |
|---|---|---|---|---|
| **ConnectBot SSH (`cbssh`)** | `org.connectbot.sshlib:sshlib` | `0.6.9` | Apache License 2.0 | Pure-Kotlin coroutine SSHv2 transport, Ed25519/RSA-SHA2/Curve25519/ML-KEM KEX, PTY shell, SFTP v3 |
| **ConnectBot Terminal (`termlib`)** | `org.connectbot:termlib` | `0.1.0` | Apache License 2.0 (`libvterm` C core: MIT) | Jetpack Compose terminal renderer backed by native `libvterm` via JNI |
| **AndroidX Biometric** | `androidx.biometric:biometric` | `1.1.0` | Apache License 2.0 | BiometricPrompt (Fingerprint / Face) local vault lock |
| **AndroidX Room** | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` | `2.7.0` | Apache License 2.0 | Local SQLite persistence for profiles, groups, recent history, known hosts, snippets |
| **AndroidX DataStore** | `androidx.datastore:datastore-preferences` | `1.1.7` | Apache License 2.0 | Local preferences (App Lock, auto-lock timeout, screenshot protection, terminal defaults) |
| **Jetpack Compose & Material 3** | `androidx.compose:compose-bom` | `2026.05.01` | Apache License 2.0 | Declarative UI & Material 3 dark charcoal theme |
| **Navigation Compose** | `androidx.navigation:navigation-compose` | `2.8.9` | Apache License 2.0 | Type-safe `@Serializable` navigation |
| **Kotlinx Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `core` | `1.10.2` | Apache License 2.0 | Asynchronous SSH I/O and reactive Flows |
| **Kotlinx Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.8.0` | Apache License 2.0 | Type-safe nav keys, encrypted credential envelopes, and backup JSON |
| **Robolectric & JUnit** | `org.robolectric:robolectric`, `junit:junit` | `4.16.1` / `4.13.2` | MIT / EPL 1.0 | Local JVM & Android unit testing |

## Evaluation Notes on `cbssh` and `termlib`

- **`org.connectbot.sshlib:sshlib:0.6.9`** (`connectbot/cbssh`): Active modern Kotlin rewrite replacing legacy Trilead SSH2. Built on Kaitai Struct + KStateMachine + Ktor sockets. Provides `SshClient`, `SshClientConfig`, `CryptoPolicyPreset.DEFAULT`, `HostKeyVerifier`, `SshSession`, and `SftpClient` cleanly on Android `minSdk 24+`.
- **`org.connectbot:termlib:0.1.0`** (`connectbot/termlib`): Targets `minCompileSdk=36`, bundling `libvterm` JNI libraries and exposing Jetpack Compose `Terminal(...)`, `TerminalEmulatorFactory`, `ModifierManager`, `SelectionController`, and `VTermKey`. (Newer `0.2.x`/`0.3.x` artifacts require `compileSdk 37`.)
