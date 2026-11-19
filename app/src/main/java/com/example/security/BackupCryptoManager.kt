package com.example.security

import com.example.domain.SshProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class ProfileBackupBundle(
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val metadataOnly: Boolean = true,
    val groups: List<String> = emptyList(),
    val profiles: List<SshProfile> = emptyList()
)

@Serializable
data class EncryptedBackupEnvelope(
    val format: String = FORMAT_ID,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = KDF_ITERATIONS,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
) {
    companion object {
        const val FORMAT_ID = "pocketssh-encrypted-backup-v1"
        const val KDF_ITERATIONS = 120_000
    }
}

sealed interface BackupImportResult {
    data class Success(val bundle: ProfileBackupBundle) : BackupImportResult
    data class Error(val message: String) : BackupImportResult
}

/**
 * Handles metadata-only JSON export and optional passphrase-encrypted profile backup.
 * NEVER includes plaintext passwords, passphrases, private keys, or auto-trusted host keys.
 */
object BackupCryptoManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun sanitizeForExport(profiles: List<SshProfile>, groups: List<String>): ProfileBackupBundle {
        val sanitized = profiles.map { profile ->
            profile.copy(
                credentialRefId = null // Never leak vault references or credentials
            )
        }
        return ProfileBackupBundle(
            formatVersion = 1,
            exportedAt = System.currentTimeMillis(),
            metadataOnly = true,
            groups = groups.distinct(),
            profiles = sanitized
        )
    }

    fun exportMetadataJson(profiles: List<SshProfile>, groups: List<String>): String {
        return json.encodeToString(sanitizeForExport(profiles, groups))
    }

    fun exportEncryptedBackup(
        profiles: List<SshProfile>,
        groups: List<String>,
        passphrase: CharArray
    ): String {
        require(passphrase.size >= 6) { "Passphrase ต้องมีอย่างน้อย 6 ตัวอักษร" }
        val plainJson = exportMetadataJson(profiles, groups).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveAesKey(passphrase, salt, EncryptedBackupEnvelope.KDF_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plainJson)
        plainJson.fill(0)

        val envelope = EncryptedBackupEnvelope(
            saltBase64 = Base64.encode(salt),
            ivBase64 = Base64.encode(cipher.iv),
            ciphertextBase64 = Base64.encode(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    fun isEncryptedBackup(rawContent: String): Boolean {
        return rawContent.contains(EncryptedBackupEnvelope.FORMAT_ID)
    }

    fun importBackup(rawContent: String, passphrase: CharArray? = null): BackupImportResult {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) {
            return BackupImportResult.Error("ไฟล์สำรองข้อมูลว่างเปล่า")
        }

        return if (isEncryptedBackup(trimmed)) {
            if (passphrase == null || passphrase.isEmpty()) {
                return BackupImportResult.Error("ไฟล์สำรองข้อมูลนี้ถูกเข้ารหัส กรุณากรอก Passphrase")
            }
            try {
                val envelope = json.decodeFromString<EncryptedBackupEnvelope>(trimmed)
                if (envelope.format != EncryptedBackupEnvelope.FORMAT_ID) {
                    return BackupImportResult.Error("รูปแบบไฟล์สำรองข้อมูลไม่รองรับ")
                }
                val salt = Base64.decode(envelope.saltBase64)
                val iv = Base64.decode(envelope.ivBase64)
                val ciphertext = Base64.decode(envelope.ciphertextBase64)
                val key = deriveAesKey(passphrase, salt, envelope.iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                val plainBytes = cipher.doFinal(ciphertext)
                val plainJson = String(plainBytes, Charsets.UTF_8)
                plainBytes.fill(0)
                parseAndValidateBundle(plainJson)
            } catch (_: Throwable) {
                BackupImportResult.Error("ถอดรหัสไม่สำเร็จ: Passphrase ไม่ถูกต้อง หรือไฟล์เสียหาย")
            }
        } else {
            parseAndValidateBundle(trimmed)
        }
    }

    private fun parseAndValidateBundle(jsonText: String): BackupImportResult {
        return try {
            val bundle = json.decodeFromString<ProfileBackupBundle>(jsonText)
            if (bundle.formatVersion != 1) {
                return BackupImportResult.Error("เวอร์ชันไฟล์สำรองข้อมูลไม่รองรับ (${bundle.formatVersion})")
            }
            val validProfiles = bundle.profiles.filter { profile ->
                profile.name.isNotBlank() &&
                    profile.host.isNotBlank() &&
                    profile.username.isNotBlank() &&
                    profile.port in 1..65535
            }.map { it.copy(credentialRefId = null) }

            if (bundle.profiles.isNotEmpty() && validProfiles.isEmpty()) {
                return BackupImportResult.Error("ไม่พบโปรไฟล์ที่ถูกต้องในไฟล์สำรองข้อมูล")
            }

            BackupImportResult.Success(bundle.copy(profiles = validProfiles))
        } catch (_: Throwable) {
            BackupImportResult.Error("ไม่สามารถอ่านไฟล์ JSON สำรองข้อมูลได้")
        }
    }

    private fun deriveAesKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
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
