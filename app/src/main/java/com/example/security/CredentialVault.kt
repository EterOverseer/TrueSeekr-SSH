package com.example.security

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.encoding.Base64

@Serializable
enum class StoredCredentialKind {
    PASSWORD,
    PRIVATE_KEY
}

@Serializable
data class EncryptedCredentialEnvelope(
    val refId: String,
    val kind: StoredCredentialKind,
    val ivBase64: String,
    val ciphertextBase64: String,
    val keySha256Short: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CredentialSummary(
    val refId: String,
    val kind: StoredCredentialKind,
    val keySha256Short: String?,
    val updatedAt: Long
)

/**
 * Stores encrypted SSH passwords and private key PEM/OpenSSH material separately from
 * Room profile metadata inside the app's noBackupFilesDir sandbox.
 *
 * Passphrases for encrypted private keys are NEVER persisted to disk by default;
 * they live only in ephemeral session memory when unlocked and are wiped on lock/disconnect.
 */
class CredentialVault(
    private val vaultDir: File,
    private val cryptoEngine: CryptoEngine
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val ephemeralPassphrases = mutableMapOf<String, CharArray>()

    constructor(context: Context, cryptoEngine: CryptoEngine = AndroidKeystoreCryptoEngine()) : this(
        vaultDir = File(context.noBackupFilesDir, "pocketssh_vault"),
        cryptoEngine = cryptoEngine
    )

    init {
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
    }

    @Synchronized
    fun savePassword(existingRefId: String?, passwordChars: CharArray): String {
        val refId = existingRefId?.takeIf { isValidRefId(it) } ?: UUID.randomUUID().toString()
        val utf8Bytes = String(passwordChars).toByteArray(Charsets.UTF_8)
        return try {
            writeEncryptedRecord(refId, StoredCredentialKind.PASSWORD, utf8Bytes, null)
            refId
        } finally {
            utf8Bytes.fill(0)
        }
    }

    @Synchronized
    fun savePrivateKey(existingRefId: String?, privateKeyPem: String): String {
        val refId = existingRefId?.takeIf { isValidRefId(it) } ?: UUID.randomUUID().toString()
        val utf8Bytes = privateKeyPem.trim().toByteArray(Charsets.UTF_8)
        val fingerprintShort = computeShortSha256(utf8Bytes)
        return try {
            writeEncryptedRecord(refId, StoredCredentialKind.PRIVATE_KEY, utf8Bytes, fingerprintShort)
            refId
        } finally {
            utf8Bytes.fill(0)
        }
    }

    @Synchronized
    fun readSecretString(refId: String?): String? {
        if (refId == null || !isValidRefId(refId)) return null
        val file = fileForRef(refId)
        if (!file.exists()) return null
        val envelope = runCatching {
            json.decodeFromString<EncryptedCredentialEnvelope>(file.readText(Charsets.UTF_8))
        }.getOrNull() ?: return null

        val iv = Base64.decode(envelope.ivBase64)
        val ciphertext = Base64.decode(envelope.ciphertextBase64)
        val decrypted = cryptoEngine.decrypt(EncryptedPayload(iv = iv, ciphertext = ciphertext))
        return try {
            String(decrypted, Charsets.UTF_8)
        } finally {
            decrypted.fill(0)
        }
    }

    @Synchronized
    fun getSummary(refId: String?): CredentialSummary? {
        if (refId == null || !isValidRefId(refId)) return null
        val file = fileForRef(refId)
        if (!file.exists()) return null
        val envelope = runCatching {
            json.decodeFromString<EncryptedCredentialEnvelope>(file.readText(Charsets.UTF_8))
        }.getOrNull() ?: return null
        return CredentialSummary(
            refId = envelope.refId,
            kind = envelope.kind,
            keySha256Short = envelope.keySha256Short,
            updatedAt = envelope.updatedAt
        )
    }

    @Synchronized
    fun hasCredential(refId: String?): Boolean {
        if (refId == null || !isValidRefId(refId)) return false
        return fileForRef(refId).exists()
    }

    @Synchronized
    fun deleteCredential(refId: String?) {
        if (refId == null || !isValidRefId(refId)) return
        clearEphemeralPassphrase(refId)
        val file = fileForRef(refId)
        if (file.exists()) {
            file.delete()
        }
    }

    @Synchronized
    fun listAllSummaries(): List<CredentialSummary> {
        val files = vaultDir.listFiles { f -> f.isFile && f.name.endsWith(".enc") } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val env = json.decodeFromString<EncryptedCredentialEnvelope>(file.readText(Charsets.UTF_8))
                CredentialSummary(env.refId, env.kind, env.keySha256Short, env.updatedAt)
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun setEphemeralPassphrase(refId: String, passphrase: CharArray) {
        clearEphemeralPassphrase(refId)
        ephemeralPassphrases[refId] = passphrase.copyOf()
    }

    @Synchronized
    fun getEphemeralPassphrase(refId: String): String? {
        val chars = ephemeralPassphrases[refId] ?: return null
        return String(chars)
    }

    @Synchronized
    fun clearEphemeralPassphrase(refId: String) {
        ephemeralPassphrases.remove(refId)?.fill('\u0000')
    }

    @Synchronized
    fun clearAllEphemeralPassphrases() {
        ephemeralPassphrases.values.forEach { it.fill('\u0000') }
        ephemeralPassphrases.clear()
    }

    @Synchronized
    fun deleteOrphanedCredentials(referencedIds: Set<String>): Int {
        val files = vaultDir.listFiles { f -> f.isFile && f.name.endsWith(".enc") } ?: return 0
        var removed = 0
        files.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            if (id !in referencedIds) {
                clearEphemeralPassphrase(id)
                if (file.delete()) removed++
            }
        }
        return removed
    }

    private fun writeEncryptedRecord(
        refId: String,
        kind: StoredCredentialKind,
        plaintext: ByteArray,
        keySha256Short: String?
    ) {
        val payload = cryptoEngine.encrypt(plaintext)
        val envelope = EncryptedCredentialEnvelope(
            refId = refId,
            kind = kind,
            ivBase64 = Base64.encode(payload.iv),
            ciphertextBase64 = Base64.encode(payload.ciphertext),
            keySha256Short = keySha256Short,
            updatedAt = System.currentTimeMillis()
        )
        fileForRef(refId).writeText(json.encodeToString(envelope), Charsets.UTF_8)
    }

    private fun computeShortSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.withPadding(Base64.PaddingOption.ABSENT).encode(digest).take(16)
    }

    private fun fileForRef(refId: String): File = File(vaultDir, "$refId.enc")

    private fun isValidRefId(refId: String): Boolean {
        return refId.matches(Regex("^[a-zA-Z0-9_-]{8,64}$"))
    }
}
