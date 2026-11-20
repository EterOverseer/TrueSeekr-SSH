# TrueSeekrSSH — Security Architecture

## 1. Separation of Concerns

| Data Domain | Storage Mechanism | Backup Excluded? | Plaintext Secrets? |
|---|---|---|---|
| **SSH Profile Metadata** | Room (`ssh_profiles`, `profile_groups`, `recent_connections`) | Yes (`allowBackup="false"`, `backup_rules.xml`, `data_extraction_rules.xml`) | **Never** (stores only `credentialRefId` UUID) |
| **Passwords & Private Keys** | `CredentialVault` inside `noBackupFilesDir/pocketssh_vault/*.enc` | Yes | **Never** (AES-256-GCM encrypted with AndroidKeyStore master key) |
| **Key Passphrases** | Ephemeral in-memory `CharArray` map only | N/A | Cleared on lock, disconnect, and vault cleanup; **never persisted to disk by default** |
| **Host Trust (`known_hosts`)** | Room `known_hosts` indexed by `(host, port, keyType)` | Yes | Public keys & SHA-256 fingerprints only |
| **App Lock PIN & Settings** | Jetpack DataStore (`pocketssh_settings`) | Yes | PIN stored as PBKDF2-HMAC-SHA256 (`100,000` iterations, 16-byte random salt) |

## 2. Host Key Verification (Zero Trust by Default)

- `PocketSshHostKeyVerifier` implements `org.connectbot.sshlib.HostKeyVerifier`.
- On every SSH handshake, SHA-256 (`SHA256:<base64>` and colon-hex) is computed from the server's encoded public key.
- **Unknown host:** Connection pauses on a `CompletableDeferred<Boolean>` and presents the fingerprint dialog (`UnknownHostKeyDialog`). Connection proceeds **only** after explicit user approval.
- **Known host match:** Verified and `lastVerifiedAt` timestamp updated.
- **Key mismatch:** Strictly rejected (`verify()` returns `false`), connection aborts, and `HostKeyMismatchDialog` alerts the user of potential MITM or host key regeneration.
- Deleting a profile or importing a backup **never** silently alters or trusts `known_hosts`.

## 3. Cryptography & Transport Policy

- `SshCryptoPolicy.resolve(CryptoPolicyPreset.DEFAULT)` enforces modern KEX (including Curve25519 / PQ hybrid where supported by peer), Ed25519 / ECDSA / RSA-SHA2 host keys, and AEAD / EtM ciphers. Weak/legacy algorithms are rejected without silent downgrade.

## 4. Device-Level Hardening

- **BiometricPrompt + PIN App Lock:** Protects local vault access with configurable auto-lock timeout (`0s`, `30s`, `1m`, `5m`, `Never`). Does not replace remote SSH authentication.
- **Screenshot & Recents Protection:** Opt-in `FLAG_SECURE` toggle in Settings (`protectScreenshots`, disabled by default for remote display/emulator compatibility and configurable anytime).
- **Log Redaction:** `Formatters.redactDiagnosticText()` strips password/passphrase/PEM patterns before displaying transport errors.
- **Storage Access Framework (SAF):** File and private-key picking uses `OpenDocument` / `CreateDocument` without broad storage permissions.

## 5. External Tailscale VPN Boundary & Ownership Protection

- **Zero Embedded VPN Code:** TrueSeekrSSH does not embed Tailscale, WireGuard, or `VpnService`. It delegates VPN operation solely to the official `com.tailscale.ipn` Android package via explicit Intents to `com.tailscale.ipn.AutomationActivity`.
- **Least-Privilege Manifest Visibility:** Uses `<queries><package android:name="com.tailscale.ipn" /></queries>` rather than `QUERY_ALL_PACKAGES`.
- **Zero-Interference Ownership Rules:**
  - Any VPN active before TrueSeekrSSH initiates `CONNECT_VPN` is classified as `EXTERNAL`.
  - Cross-process restarts while a VPN remains active downgrade ownership to `UNKNOWN`.
  - Auto-disconnect (`com.tailscale.ipn.DISCONNECT_VPN`) is blocked unless `ownership == APP_REQUESTED` within the active process lifetime, zero active/pending Tailscale leases remain, and the user has `Auto Disconnect` enabled.
