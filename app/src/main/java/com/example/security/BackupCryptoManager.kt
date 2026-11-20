package com.example.security

import com.example.domain.AuthMethodType
import com.example.domain.SshProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

@Serializable
data class BackupCredentialEntry(
    val kind: StoredCredentialKind,
    val secretMaterial: String
)

@Serializable
data class ProfileBackupBundle(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val metadataOnly: Boolean = true,
    val groups: List<String> = emptyList(),
    val profiles: List<SshProfile> = emptyList(),
    val embeddedCredentials: Map<String, BackupCredentialEntry> = emptyMap(),
    val rawInvalidCount: Int = 0
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 2
    }
}

@Serializable
data class EncryptedBackupEnvelope(
    val format: String = FORMAT_ID_V2,
    val formatVersion: Int = 2,
    val kdf: String = KDF_ALGORITHM,
    val iterations: Int = KDF_ITERATIONS_V2,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
) {
    companion object {
        const val FORMAT_ID_V1 = "pocketssh-encrypted-backup-v1"
        const val FORMAT_ID_V2 = "trueseekr-encrypted-backup-v2"
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val KDF_ITERATIONS_V1 = 120_000
        const val KDF_ITERATIONS_V2 = 210_000
    }
}

enum class ImportDuplicateStrategy(val label: String) {
    SKIP("ข้ามรายการซ้ำ (Skip)"),
    REPLACE("เขียนทับโปรไฟล์เดิม (Replace)"),
    KEEP_BOTH("เก็บทั้งคู่ด้วย ID ใหม่ (Keep Both)")
}

enum class ImportItemClassification {
    NEW,
    DUPLICATE,
    INVALID_OR_UNSUPPORTED
}

data class ImportPreviewItem(
    val profile: SshProfile,
    val classification: ImportItemClassification,
    val matchedExistingId: String? = null,
    val matchedExistingName: String? = null,
    val missingAuth: Boolean = false,
    val hasEmbeddedCredential: Boolean = false,
    val reason: String? = null
)

data class ImportPreviewSummary(
    val isEncrypted: Boolean,
    val formatVersion: Int,
    val metadataOnly: Boolean,
    val totalCount: Int,
    val newCount: Int,
    val duplicateCount: Int,
    val missingAuthCount: Int,
    val invalidOrUnsupportedCount: Int,
    val embeddedCredentialCount: Int,
    val items: List<ImportPreviewItem>
)

data class ExportBackupPreview(
    val totalProfiles: Int,
    val storedCredentialCount: Int,
    val missingStoredCredentialCount: Int
)

data class ImportExecutionSummary(
    val importedNewCount: Int,
    val replacedCount: Int,
    val keptBothCount: Int,
    val skippedDuplicateCount: Int,
    val reEncryptedCredentialsCount: Int,
    val skippedInvalidCount: Int
) {
    val totalApplied: Int
        get() = importedNewCount + replacedCount + keptBothCount
}

sealed interface BackupImportResult {
    data class Success(val bundle: ProfileBackupBundle) : BackupImportResult
    data class Error(val message: String) : BackupImportResult
}

/**
 * Handles standard JSON profile export (metadata only, zero secrets) and encrypted full backups
 * using AES-256-GCM + PBKDF2-HMAC-SHA256 with unique salt/nonce and AAD envelope binding.
 * Never exports Android Keystore keys; destination re-encrypts restored secrets with its own Keystore.
 */
object BackupCryptoManager {
    const val STANDARD_EXPORT_FILENAME = "trueseekr-profiles.json"
    const val ENCRYPTED_BACKUP_FILENAME = "trueseekr-full-backup.tsshbackup"
    const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 // 10 MiB safety guard

    private val secureRandom = SecureRandom()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun buildExportPreview(
        profiles: List<SshProfile>,
        credentialVault: CredentialVault?
    ): ExportBackupPreview {
        var storedCount = 0
        var missingCount = 0
        profiles.forEach { profile ->
            val hasStored = credentialVault != null &&
                !profile.credentialRefId.isNullOrBlank() &&
                credentialVault.hasCredential(profile.credentialRefId)
            if (hasStored) {
                storedCount++
            } else {
                missingCount++
            }
        }
        return ExportBackupPreview(
            totalProfiles = profiles.size,
            storedCredentialCount = storedCount,
            missingStoredCredentialCount = missingCount
        )
    }

    fun sanitizeForExport(profiles: List<SshProfile>, groups: List<String>): ProfileBackupBundle {
        val sanitized = profiles.map { profile ->
            profile.copy(
                credentialRefId = null // Never leak vault references or credentials
            )
        }
        return ProfileBackupBundle(
            formatVersion = ProfileBackupBundle.CURRENT_FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            metadataOnly = true,
            groups = groups.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            profiles = sanitized,
            embeddedCredentials = emptyMap()
        )
    }

    fun exportMetadataJson(profiles: List<SshProfile>, groups: List<String>): String {
        return json.encodeToString(sanitizeForExport(profiles, groups))
    }

    /**
     * Backward-compatible encrypted metadata export API (no vault credentials included).
     */
    fun exportEncryptedBackup(
        profiles: List<SshProfile>,
        groups: List<String>,
        passphrase: CharArray
    ): String {
        return exportEncryptedFullBackup(
            profiles = profiles,
            groups = groups,
            passphrase = passphrase,
            credentialVault = null,
            includeCredentials = false
        )
    }

    /**
     * Explicit opt-in encrypted full backup. If includeCredentials is true, decrypts stored
     * passwords/private keys from CredentialVault into the encrypted payload in memory only,
     * encrypts using AES-256-GCM + PBKDF2-HMAC-SHA256 + AAD, and zeroes temporary buffers.
     */
    fun exportEncryptedFullBackup(
        profiles: List<SshProfile>,
        groups: List<String>,
        passphrase: CharArray,
        credentialVault: CredentialVault?,
        includeCredentials: Boolean
    ): String {
        require(passphrase.size >= 6) { "รหัสผ่านสำรองข้อมูลต้องมีอย่างน้อย 6 ตัวอักษร" }

        val sanitizedProfiles = profiles.map { it.copy(credentialRefId = null) }
        val credentialsMap = linkedMapOf<String, BackupCredentialEntry>()
        if (includeCredentials && credentialVault != null) {
            profiles.forEach { profile ->
                val refId = profile.credentialRefId
                if (!refId.isNullOrBlank()) {
                    val summary = runCatching { credentialVault.getSummary(refId) }.getOrNull()
                    val secret = runCatching { credentialVault.readSecretString(refId) }.getOrNull()
                    if (summary != null && !secret.isNullOrEmpty()) {
                        credentialsMap[profile.id] = BackupCredentialEntry(
                            kind = summary.kind,
                            secretMaterial = secret
                        )
                    }
                }
            }
        }

        val bundle = ProfileBackupBundle(
            formatVersion = ProfileBackupBundle.CURRENT_FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            metadataOnly = !includeCredentials || credentialsMap.isEmpty(),
            groups = groups.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            profiles = sanitizedProfiles,
            embeddedCredentials = credentialsMap
        )

        val plainBytes = json.encodeToString(bundle).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val saltB64 = Base64.encode(salt)
        val iterations = EncryptedBackupEnvelope.KDF_ITERATIONS_V2
        val aad = buildAad(
            format = EncryptedBackupEnvelope.FORMAT_ID_V2,
            formatVersion = 2,
            kdf = EncryptedBackupEnvelope.KDF_ALGORITHM,
            iterations = iterations,
            saltBase64 = saltB64
        )

        val key = deriveAesKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plainBytes)

            val envelope = EncryptedBackupEnvelope(
                format = EncryptedBackupEnvelope.FORMAT_ID_V2,
                formatVersion = 2,
                kdf = EncryptedBackupEnvelope.KDF_ALGORITHM,
                iterations = iterations,
                saltBase64 = saltB64,
                ivBase64 = Base64.encode(iv),
                ciphertextBase64 = Base64.encode(ciphertext)
            )
            json.encodeToString(envelope)
        } finally {
            plainBytes.fill(0)
        }
    }

    fun isEncryptedBackup(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        return trimmed.contains(EncryptedBackupEnvelope.FORMAT_ID_V2) ||
            trimmed.contains(EncryptedBackupEnvelope.FORMAT_ID_V1)
    }

    fun importBackup(rawContent: String, passphrase: CharArray? = null): BackupImportResult {
        if (rawContent.length > MAX_BACKUP_BYTES) {
            return BackupImportResult.Error("ไฟล์สำรองข้อมูลมีขนาดใหญ่เกินกำหนด (สูงสุด 10 MB)")
        }
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) {
            return BackupImportResult.Error("ไฟล์สำรองข้อมูลว่างเปล่า")
        }

        return if (isEncryptedBackup(trimmed)) {
            if (passphrase == null || passphrase.isEmpty()) {
                return BackupImportResult.Error("ไฟล์สำรองข้อมูลนี้ถูกเข้ารหัส กรุณากรอกรหัสผ่านสำรองข้อมูล")
            }
            try {
                val envelope = json.decodeFromString<EncryptedBackupEnvelope>(trimmed)
                val supportedFormat = envelope.format == EncryptedBackupEnvelope.FORMAT_ID_V2 ||
                    envelope.format == EncryptedBackupEnvelope.FORMAT_ID_V1
                if (!supportedFormat) {
                    return BackupImportResult.Error("รูปแบบไฟล์สำรองข้อมูลไม่รองรับ (${envelope.format})")
                }
                if (envelope.kdf != EncryptedBackupEnvelope.KDF_ALGORITHM || envelope.iterations !in 10_000..1_000_000) {
                    return BackupImportResult.Error("พารามิเตอร์การถอดรหัสไม่ปลอดภัยหรือไม่รองรับ")
                }

                val salt = Base64.decode(envelope.saltBase64)
                val iv = Base64.decode(envelope.ivBase64)
                val ciphertext = Base64.decode(envelope.ciphertextBase64)
                val key = deriveAesKey(passphrase, salt, envelope.iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                if (envelope.format == EncryptedBackupEnvelope.FORMAT_ID_V2) {
                    cipher.updateAAD(
                        buildAad(
                            format = envelope.format,
                            formatVersion = envelope.formatVersion,
                            kdf = envelope.kdf,
                            iterations = envelope.iterations,
                            saltBase64 = envelope.saltBase64
                        )
                    )
                }
                val plainBytes = cipher.doFinal(ciphertext)
                val plainJson = String(plainBytes, Charsets.UTF_8)
                plainBytes.fill(0)
                parseAndValidateBundle(plainJson)
            } catch (_: Throwable) {
                BackupImportResult.Error("ถอดรหัสไม่สำเร็จ: รหัสผ่านไม่ถูกต้อง หรือไฟล์ถูกแก้ไข/เสียหาย")
            }
        } else {
            parseAndValidateBundle(trimmed)
        }
    }

    fun analyzeImportPreview(
        bundle: ProfileBackupBundle,
        existingProfiles: List<SshProfile>,
        isEncrypted: Boolean
    ): ImportPreviewSummary {
        val existingById = existingProfiles.associateBy { it.id }
        val existingByEndpoint = existingProfiles.associateBy { endpointKey(it) }

        val previewItems = mutableListOf<ImportPreviewItem>()
        var newCount = 0
        var duplicateCount = 0
        var missingAuthCount = 0
        var invalidCount = bundle.rawInvalidCount

        bundle.profiles.forEach { profile ->
            val valid = isValidProfile(profile)
            if (!valid) {
                invalidCount++
                previewItems += ImportPreviewItem(
                    profile = profile,
                    classification = ImportItemClassification.INVALID_OR_UNSUPPORTED,
                    reason = "ข้อมูลHost/Port/Username ไม่สมบูรณ์"
                )
                return@forEach
            }

            val hasEmbeddedCred = bundle.embeddedCredentials[profile.id]?.secretMaterial?.isNotBlank() == true
            val missingAuth = !hasEmbeddedCred
            if (missingAuth) {
                missingAuthCount++
            }

            val matched = existingById[profile.id] ?: existingByEndpoint[endpointKey(profile)]
            if (matched != null) {
                duplicateCount++
                previewItems += ImportPreviewItem(
                    profile = profile,
                    classification = ImportItemClassification.DUPLICATE,
                    matchedExistingId = matched.id,
                    matchedExistingName = matched.name,
                    missingAuth = missingAuth,
                    hasEmbeddedCredential = hasEmbeddedCred
                )
            } else {
                newCount++
                previewItems += ImportPreviewItem(
                    profile = profile,
                    classification = ImportItemClassification.NEW,
                    missingAuth = missingAuth,
                    hasEmbeddedCredential = hasEmbeddedCred
                )
            }
        }

        return ImportPreviewSummary(
            isEncrypted = isEncrypted,
            formatVersion = bundle.formatVersion,
            metadataOnly = bundle.metadataOnly && bundle.embeddedCredentials.isEmpty(),
            totalCount = previewItems.size + bundle.rawInvalidCount,
            newCount = newCount,
            duplicateCount = duplicateCount,
            missingAuthCount = missingAuthCount,
            invalidOrUnsupportedCount = invalidCount,
            embeddedCredentialCount = bundle.embeddedCredentials.size,
            items = previewItems
        )
    }

    fun endpointKey(profile: SshProfile): String {
        return "${profile.host.trim().lowercase()}:${profile.port}:${profile.username.trim()}:${profile.authMethod.name}"
    }

    fun isValidProfile(profile: SshProfile): Boolean {
        return profile.name.isNotBlank() &&
            profile.host.isNotBlank() &&
            profile.username.isNotBlank() &&
            profile.port in 1..65535
    }

    private fun parseAndValidateBundle(jsonText: String): BackupImportResult {
        return try {
            val element = json.parseToJsonElement(jsonText)
            val obj = element as? kotlinx.serialization.json.JsonObject
                ?: return BackupImportResult.Error("โครงสร้างไฟล์สำรองข้อมูลไม่ถูกต้อง")
            if (obj["profiles"] !is kotlinx.serialization.json.JsonArray) {
                return BackupImportResult.Error("ไฟล์ JSON ไม่มีข้อมูลโปรไฟล์ที่รองรับ (profiles)")
            }

            val bundle = json.decodeFromString<ProfileBackupBundle>(jsonText)
            if (bundle.formatVersion !in 1..ProfileBackupBundle.CURRENT_FORMAT_VERSION) {
                return BackupImportResult.Error("เวอร์ชันไฟล์สำรองข้อมูลไม่รองรับ (${bundle.formatVersion})")
            }

            val validProfiles = mutableListOf<SshProfile>()
            var invalidCount = 0
            bundle.profiles.forEach { raw ->
                val cleaned = raw.copy(credentialRefId = null)
                if (isValidProfile(cleaned)) {
                    validProfiles += cleaned
                } else {
                    invalidCount++
                }
            }

            if (bundle.profiles.isNotEmpty() && validProfiles.isEmpty()) {
                return BackupImportResult.Error("ไม่พบโปรไฟล์ที่ถูกต้องในไฟล์สำรองข้อมูล")
            }

            BackupImportResult.Success(
                bundle.copy(
                    profiles = validProfiles,
                    rawInvalidCount = invalidCount
                )
            )
        } catch (_: Throwable) {
            BackupImportResult.Error("ไม่สามารถอ่านไฟล์ JSON สำรองข้อมูลได้ หรือโครงสร้างไฟล์ไม่ถูกต้อง")
        }
    }

    private fun buildAad(
        format: String,
        formatVersion: Int,
        kdf: String,
        iterations: Int,
        saltBase64: String
    ): ByteArray {
        return "$format|$formatVersion|$kdf|$iterations|$saltBase64".toByteArray(Charsets.UTF_8)
    }

    private fun deriveAesKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(EncryptedBackupEnvelope.KDF_ALGORITHM)
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        val encoded = factory.generateSecret(spec).encoded
        return try {
            SecretKeySpec(encoded, "AES")
        } finally {
            encoded.fill(0)
            spec.clearPassword()
        }
    }
}
