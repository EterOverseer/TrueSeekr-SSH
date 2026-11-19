package com.example

import com.example.core.Formatters
import com.example.domain.AuthMethodType
import com.example.domain.SshProfile
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.CredentialVault
import com.example.security.PinLockManager
import com.example.security.SoftwareAesGcmCryptoEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.AEADBadTagException

class SecurityUnitTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("pocketssh_sec_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `credential vault encrypts and decrypts and fails with wrong key`() {
        val engineA = SoftwareAesGcmCryptoEngine()
        val engineB = SoftwareAesGcmCryptoEngine()
        val vaultA = CredentialVault(tempDir, engineA)

        val refId = vaultA.savePassword(null, "SuperSecretPassword!123".toCharArray())
        assertEquals("SuperSecretPassword!123", vaultA.readSecretString(refId))

        // Reading raw file on disk must not contain plaintext password
        val rawDiskText = File(tempDir, "$refId.enc").readText()
        assertFalse(rawDiskText.contains("SuperSecretPassword!123"))

        // Reading with a different key must fail with AEADBadTagException
        val vaultB = CredentialVault(tempDir, engineB)
        var failedWithBadTag = false
        try {
            vaultB.readSecretString(refId)
        } catch (_: AEADBadTagException) {
            failedWithBadTag = true
        }
        assertTrue(failedWithBadTag)
    }

    @Test
    fun `pin lock verifies correct pin and rejects wrong pin`() {
        val record = PinLockManager.createPinHash("258096".toCharArray())
        assertTrue(PinLockManager.verifyPin("258096".toCharArray(), record.saltBase64, record.hashBase64))
        assertFalse(PinLockManager.verifyPin("000000".toCharArray(), record.saltBase64, record.hashBase64))
    }

    @Test
    fun `diagnostic redaction strips passwords and private key headers`() {
        val raw = "auth failed password=MySecret123 -----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"
        val sanitized = Formatters.redactDiagnosticText(raw)
        assertFalse(sanitized.contains("MySecret123"))
        assertFalse(sanitized.contains("OPENSSH PRIVATE KEY"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun `metadata export strips credential refs and encrypted backup validates passphrase`() {
        val profile = SshProfile(
            id = "p-export",
            name = "Docker Host",
            host = "100.64.0.12",
            port = 22,
            username = "ops",
            authMethod = AuthMethodType.PRIVATE_KEY,
            groupName = "Tailscale",
            credentialRefId = "secret-ref-should-be-stripped"
        )

        val plainExport = BackupCryptoManager.exportMetadataJson(listOf(profile), listOf("CustomGrp"))
        assertFalse(plainExport.contains("secret-ref-should-be-stripped"))

        val encrypted = BackupCryptoManager.exportEncryptedBackup(
            profiles = listOf(profile),
            groups = listOf("CustomGrp"),
            passphrase = "backup-passphrase".toCharArray()
        )
        assertTrue(BackupCryptoManager.isEncryptedBackup(encrypted))
        assertFalse(encrypted.contains("100.64.0.12"))

        // Wrong passphrase fails cleanly
        val badImport = BackupCryptoManager.importBackup(encrypted, "wrong-pass".toCharArray())
        assertTrue(badImport is BackupImportResult.Error)

        // Correct passphrase restores metadata with null credentialRefId
        val goodImport = BackupCryptoManager.importBackup(encrypted, "backup-passphrase".toCharArray())
        assertTrue(goodImport is BackupImportResult.Success)
        val restored = (goodImport as BackupImportResult.Success).bundle.profiles.first()
        assertEquals("Docker Host", restored.name)
        assertNull(restored.credentialRefId)
        assertNotNull(restored.id)
    }
}
