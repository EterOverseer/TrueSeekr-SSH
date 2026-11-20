package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.domain.AuthMethodType
import com.example.domain.SshProfile
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.CredentialVault
import com.example.security.EncryptedBackupEnvelope
import com.example.security.ImportDuplicateStrategy
import com.example.security.SoftwareAesGcmCryptoEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupV2SecurityTest {

    private lateinit var context: Context
    private lateinit var sourceDir: File
    private lateinit var destDir: File
    private lateinit var sourceVault: CredentialVault
    private lateinit var destVault: CredentialVault
    private lateinit var sourceDb: PocketSshDatabase
    private lateinit var destDb: PocketSshDatabase
    private lateinit var sourceRepo: ProfileRepository
    private lateinit var destRepo: ProfileRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        sourceDir = File(context.cacheDir, "bkp_src_$id").apply { mkdirs() }
        destDir = File(context.cacheDir, "bkp_dst_$id").apply { mkdirs() }

        sourceVault = CredentialVault(File(sourceDir, "vault"), SoftwareAesGcmCryptoEngine())
        destVault = CredentialVault(File(destDir, "vault"), SoftwareAesGcmCryptoEngine())

        sourceDb = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        destDb = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        sourceRepo = ProfileRepository(
            sourceDb.profileDao(),
            sourceDb.profileGroupDao(),
            sourceDb.recentConnectionDao(),
            sourceVault,
            sourceDb
        )
        destRepo = ProfileRepository(
            destDb.profileDao(),
            destDb.profileGroupDao(),
            destDb.recentConnectionDao(),
            destVault,
            destDb
        )
    }

    @After
    fun tearDown() {
        sourceDb.close()
        destDb.close()
        sourceDir.deleteRecursively()
        destDir.deleteRecursively()
    }

    @Test
    fun `standard JSON export contains zero secrets or vault refs`() = runBlocking {
        val saved = sourceRepo.saveProfile(
            SshProfile(
                id = "p-json-1",
                name = "Prod API",
                host = "10.20.30.40",
                username = "ubuntu",
                authMethod = AuthMethodType.PASSWORD
            ),
            newPasswordChars = "SuperSecretPassword123!".toCharArray()
        )
        assertNotNull(saved.credentialRefId)

        val jsonText = BackupCryptoManager.exportMetadataJson(listOf(saved), listOf("CustomOps"))
        assertFalse(jsonText.contains("SuperSecretPassword123!"))
        assertFalse(jsonText.contains(saved.credentialRefId!!))
        assertTrue(jsonText.contains("\"metadataOnly\": true"))

        val imported = BackupCryptoManager.importBackup(jsonText, null) as BackupImportResult.Success
        val preview = BackupCryptoManager.analyzeImportPreview(imported.bundle, emptyList(), isEncrypted = false)
        assertEquals(1, preview.newCount)
        assertEquals(1, preview.missingAuthCount)
        assertEquals(0, preview.embeddedCredentialCount)
    }

    @Test
    fun `encrypted full backup restores passwords and private keys and re-encrypts in destination Keystore vault`() = runBlocking {
        val p1 = sourceRepo.saveProfile(
            SshProfile(
                id = "p-pass",
                name = "App Server",
                host = "app.internal",
                username = "deploy",
                authMethod = AuthMethodType.PASSWORD,
                autoTailscaleConnect = true
            ),
            newPasswordChars = "Pass#2026".toCharArray()
        )
        val pemKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nTEST_PRIVATE_KEY_BODY\n-----END OPENSSH PRIVATE KEY-----"
        val p2 = sourceRepo.saveProfile(
            SshProfile(
                id = "p-key",
                name = "DB Server",
                host = "db.internal",
                username = "postgres",
                authMethod = AuthMethodType.PRIVATE_KEY
            ),
            newPrivateKeyPem = pemKey
        )

        val backupPass = "BackupMaster#99".toCharArray()
        val encrypted = BackupCryptoManager.exportEncryptedFullBackup(
            profiles = listOf(p1, p2),
            groups = listOf("Databases"),
            passphrase = backupPass.copyOf(),
            credentialVault = sourceVault,
            includeCredentials = true
        )

        assertFalse(encrypted.contains("Pass#2026"))
        assertFalse(encrypted.contains("TEST_PRIVATE_KEY_BODY"))
        assertTrue(encrypted.contains(EncryptedBackupEnvelope.FORMAT_ID_V2))

        val imported = BackupCryptoManager.importBackup(encrypted, "BackupMaster#99".toCharArray())
        assertTrue(imported is BackupImportResult.Success)
        val bundle = (imported as BackupImportResult.Success).bundle

        val preview = BackupCryptoManager.analyzeImportPreview(bundle, emptyList(), isEncrypted = true)
        assertEquals(2, preview.newCount)
        assertEquals(2, preview.embeddedCredentialCount)
        assertEquals(0, preview.missingAuthCount)

        val summary = destRepo.importWithStrategy(bundle, ImportDuplicateStrategy.SKIP)
        assertEquals(2, summary.importedNewCount)
        assertEquals(2, summary.reEncryptedCredentialsCount)

        val restoredP1 = destRepo.getProfileById("p-pass")!!
        val restoredP2 = destRepo.getProfileById("p-key")!!
        assertNotEquals(p1.credentialRefId, restoredP1.credentialRefId)
        assertNotEquals(p2.credentialRefId, restoredP2.credentialRefId)
        assertTrue(restoredP1.autoTailscaleConnect)

        val restoredPwd = destVault.readSecretString(restoredP1.credentialRefId!!)
        assertEquals("Pass#2026", restoredPwd)
        val restoredKey = destVault.readSecretString(restoredP2.credentialRefId!!)
        assertEquals(pemKey, restoredKey)
    }

    @Test
    fun `tampered AAD, wrong password, future version, and oversized files fail safely`() = runBlocking {
        val prof = SshProfile(id = "p-sec", name = "Sec", host = "1.2.3.4", username = "root")
        val encrypted = BackupCryptoManager.exportEncryptedFullBackup(
            profiles = listOf(prof),
            groups = emptyList(),
            passphrase = "CorrectPassword".toCharArray(),
            credentialVault = null,
            includeCredentials = false
        )

        // Wrong password
        val wrong = BackupCryptoManager.importBackup(encrypted, "WrongPassword".toCharArray())
        assertTrue(wrong is BackupImportResult.Error)

        // Mutate AAD-bound envelope iterations without changing ciphertext -> GCM AAD auth tag must reject
        val strictJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val envelope = strictJson.decodeFromString<EncryptedBackupEnvelope>(encrypted)
        val tampered = strictJson.encodeToString(envelope.copy(iterations = envelope.iterations + 1))
        val tamperedRes = BackupCryptoManager.importBackup(tampered, "CorrectPassword".toCharArray())
        assertTrue(tamperedRes is BackupImportResult.Error)

        // Stripped format field must also be rejected rather than parsed as empty bundle
        val stripped = Json.encodeToString(envelope.copy(iterations = envelope.iterations + 1))
        assertTrue(BackupCryptoManager.importBackup(stripped, "CorrectPassword".toCharArray()) is BackupImportResult.Error)

        // Unsupported future version JSON
        val futureJson = """{"formatVersion":99,"profiles":[{"id":"1","name":"X","host":"1.1.1.1","username":"u"}]}"""
        val futureRes = BackupCryptoManager.importBackup(futureJson, null)
        assertTrue(futureRes is BackupImportResult.Error)

        // Oversized (> 10 MiB)
        val huge = " ".repeat(BackupCryptoManager.MAX_BACKUP_BYTES + 10)
        val hugeRes = BackupCryptoManager.importBackup(huge, null)
        assertTrue(hugeRes is BackupImportResult.Error)
    }

    @Test
    fun `duplicate strategies SKIP, REPLACE, KEEP_BOTH and atomic rollback behave deterministically`() = runBlocking {
        val existing = destRepo.saveProfile(
            SshProfile(
                id = "dup-1",
                name = "Gateway",
                host = "192.168.10.5",
                port = 22,
                username = "admin",
                description = "Original"
            ),
            newPasswordChars = "OldPass".toCharArray()
        )

        val incoming = SshProfile(
            id = "dup-other-uuid",
            name = "Gateway Backup",
            host = "192.168.10.5",
            port = 22,
            username = "admin",
            description = "FromBackup"
        )
        val json = BackupCryptoManager.exportMetadataJson(listOf(incoming), emptyList())
        val bundle = (BackupCryptoManager.importBackup(json, null) as BackupImportResult.Success).bundle

        val preview = BackupCryptoManager.analyzeImportPreview(
            bundle = bundle,
            existingProfiles = listOf(existing),
            isEncrypted = false
        )
        assertEquals(0, preview.newCount)
        assertEquals(1, preview.duplicateCount)

        // 1. SKIP
        val skipSummary = destRepo.importWithStrategy(bundle, ImportDuplicateStrategy.SKIP)
        assertEquals(1, skipSummary.skippedDuplicateCount)
        assertEquals("Original", destRepo.getProfileById("dup-1")?.description)

        // 2. KEEP_BOTH
        val keepSummary = destRepo.importWithStrategy(bundle, ImportDuplicateStrategy.KEEP_BOTH)
        assertEquals(1, keepSummary.keptBothCount)
        assertEquals(2, destDb.profileDao().getAllProfilesOnce().size)

        // 3. REPLACE (matched on existing UUID/endpoint, updates metadata while preserving existing vault ref when metadata-only)
        val replaceBundle = bundle.copy(profiles = listOf(incoming.copy(id = "dup-1", description = "ReplacedMeta")))
        val replaceSummary = destRepo.importWithStrategy(replaceBundle, ImportDuplicateStrategy.REPLACE)
        assertEquals(1, replaceSummary.replacedCount)
        val afterReplace = destRepo.getProfileById("dup-1")!!
        assertEquals("ReplacedMeta", afterReplace.description)
        assertEquals(existing.credentialRefId, afterReplace.credentialRefId)

        // 4. Atomic rollback on DB error cleans up newly written vault files and reverts DB writes
        val newCredEncrypted = BackupCryptoManager.exportEncryptedFullBackup(
            profiles = listOf(SshProfile(id = "rollback-p", name = "WillFail", host = "5.5.5.5", username = "u")),
            groups = emptyList(),
            passphrase = "Pass1234".toCharArray(),
            credentialVault = null,
            includeCredentials = false
        )
        val validBundle = (BackupCryptoManager.importBackup(newCredEncrypted, "Pass1234".toCharArray()) as BackupImportResult.Success).bundle
        val withBadGroupBundle = validBundle.copy(
            embeddedCredentials = mapOf(
                "rollback-p" to com.example.security.BackupCredentialEntry(
                    com.example.security.StoredCredentialKind.PASSWORD,
                    "RollbackSecret"
                )
            ),
            // Insert valid profile PLUS duplicate primary key clash inside raw DAO? Better: close DB to induce failure during tx!
        )
        val preVaultFiles = destVault.listAllSummaries().map { it.refId }.toSet()
        // Trigger transaction failure by closing a dedicated temp DB before importWithStrategy runs
        val rollbackDb = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val rollbackVaultDir = File(destDir, "rb_vault").apply { mkdirs() }
        val rbVault = CredentialVault(rollbackVaultDir, SoftwareAesGcmCryptoEngine())
        val rbRepo = ProfileRepository(rollbackDb.profileDao(), rollbackDb.profileGroupDao(), rollbackDb.recentConnectionDao(), rbVault, rollbackDb)
        rollbackDb.close()
        val thrown = runCatching { rbRepo.importWithStrategy(withBadGroupBundle, ImportDuplicateStrategy.SKIP) }.isFailure
        assertTrue(thrown)
        assertEquals(0, rbVault.listAllSummaries().size)
        assertEquals(preVaultFiles, destVault.listAllSummaries().map { it.refId }.toSet())
    }
}
