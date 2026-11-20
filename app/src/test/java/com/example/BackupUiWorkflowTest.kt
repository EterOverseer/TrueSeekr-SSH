package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.domain.AuthMethodType
import com.example.domain.SshProfile
import com.example.profiles.ServersViewModel
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.CredentialVault
import com.example.security.ImportDuplicateStrategy
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.settings.AppPreferencesRepository
import com.example.settings.SettingsViewModel
import com.example.ssh.SshSessionManager
import com.example.ui.components.readBackupTextFromUri
import com.example.ui.components.writeBackupTextToUri
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PocketSshTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BackupUiWorkflowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: PocketSshDatabase
    private lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var vault: CredentialVault
    private lateinit var profileRepo: ProfileRepository
    private lateinit var knownHostRepo: KnownHostRepository
    private lateinit var prefsRepo: AppPreferencesRepository
    private lateinit var sshSessionManager: SshSessionManager
    private lateinit var serversViewModel: ServersViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        tempDir = File(context.cacheDir, "backup_ui_test_$id").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vault = CredentialVault(File(tempDir, "vault"), SoftwareAesGcmCryptoEngine())
        profileRepo = ProfileRepository(
            profileDao = db.profileDao(),
            groupDao = db.profileGroupDao(),
            recentDao = db.recentConnectionDao(),
            credentialVault = vault,
            database = db
        )
        knownHostRepo = KnownHostRepository(db.knownHostDao())
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "prefs_$id.preferences_pb") }
        )
        prefsRepo = AppPreferencesRepository(context, customDataStore = ds)
        val networkMonitor = NetworkMonitor(context)
        sshSessionManager = SshSessionManager(
            profileRepository = profileRepo,
            knownHostRepository = knownHostRepo,
            credentialVault = vault,
            networkMonitor = networkMonitor,
            preferencesRepository = prefsRepo
        )
        runBlocking {
            profileRepo.ensureBuiltInGroups()
        }
        serversViewModel = ServersViewModel(
            profileRepository = profileRepo,
            credentialVault = vault,
            sshSessionManager = sshSessionManager,
            preferencesRepository = prefsRepo,
            networkMonitor = networkMonitor,
            knownHostRepository = knownHostRepo
        )
        settingsViewModel = SettingsViewModel(
            preferencesRepository = prefsRepo,
            knownHostRepository = knownHostRepo,
            profileRepository = profileRepo,
            credentialVault = vault
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun serversScreen_overflowMenuShowsAllFourBackupItems_andTriggersExportAndEncryptedDialogs() {
        runBlocking {
            profileRepo.saveProfile(
                SshProfile(
                    id = "srv-ov-1",
                    name = "Edge Gateway",
                    host = "10.1.0.5",
                    username = "admin",
                    authMethod = AuthMethodType.PASSWORD
                ),
                newPasswordChars = "pass-1234".toCharArray()
            )
        }

        composeRule.setContent {
            PocketSshTheme {
                val state by serversViewModel.uiState.collectAsState()
                ServersScreen(
                    uiState = state,
                    viewModel = serversViewModel,
                    onNavigateToTerminal = {}
                )
            }
        }

        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("Edge Gateway").fetchSemanticsNodes().isNotEmpty()
        }

        // Open top-right overflow menu and verify all 4 Import/Export items
        composeRule.onNodeWithTag("servers_overflow_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overflow_import_profiles_item").assertIsDisplayed()
        composeRule.onNodeWithTag("overflow_export_profiles_item").assertIsDisplayed()
        composeRule.onNodeWithTag("overflow_create_encrypted_backup_item").assertIsDisplayed()
        composeRule.onNodeWithTag("overflow_restore_encrypted_backup_item").assertIsDisplayed()

        // 1. Export Profiles workflow dialog
        composeRule.onNodeWithTag("overflow_export_profiles_item").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_profiles_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Export Preview (1 โปรไฟล์)").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_export_json_button").assertIsEnabled()
        composeRule.onNodeWithText("ยกเลิก").performClick()
        composeRule.waitForIdle()

        // 2. Create Encrypted Backup workflow dialog with passphrase validation
        composeRule.onNodeWithTag("servers_overflow_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overflow_create_encrypted_backup_item").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_encrypted_backup_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_create_encrypted_backup_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("create_encrypted_passphrase_input").performTextInput("StrongPass!99")
        composeRule.onNodeWithTag("create_encrypted_confirm_passphrase_input").performTextInput("Mismatch")
        composeRule.onNodeWithTag("confirm_create_encrypted_backup_button").assertIsNotEnabled()

        // Fix confirm passphrase and verify enabled
        composeRule.onNodeWithTag("create_encrypted_confirm_passphrase_input")
            .performClick()
        composeRule.onNodeWithTag("create_encrypted_confirm_passphrase_input")
            .performTextReplacement("StrongPass!99")
        composeRule.onNodeWithTag("confirm_create_encrypted_backup_button").assertIsEnabled()
        composeRule.onNodeWithText("ยกเลิก").performClick()
        composeRule.waitForIdle()

        // Verify requireEncrypted enforcement for Restore Encrypted Backup mode
        val plainJson = serversViewModel.buildExportContent(null).getOrThrow()
        val rejectedPlain = serversViewModel.prepareImportPreview(plainJson, null, requireEncrypted = true)
        assertTrue(rejectedPlain is BackupImportResult.Error)
    }

    @Test
    fun settingsScreen_backupAndRestoreSection_exposesAllFourItemsAndEndToEndWorkflow() {
        runBlocking {
            profileRepo.saveProfile(
                SshProfile(
                    id = "set-srv-1",
                    name = "Prod DB",
                    host = "10.2.0.10",
                    username = "postgres",
                    authMethod = AuthMethodType.PASSWORD
                ),
                newPasswordChars = "DbSecret#1".toCharArray()
            )
        }

        composeRule.setContent {
            PocketSshTheme {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            settingsViewModel.allProfiles.value.size == 1
        }

        // Scroll to Backup & Restore section and verify all 4 items are visible & clickable
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_restore_encrypted_backup_item"))

        composeRule.onNodeWithTag("settings_import_profiles_item").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_export_profiles_item").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_create_encrypted_backup_item").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_restore_encrypted_backup_item").assertIsDisplayed()

        // Open Export Profiles dialog from Settings
        composeRule.onNodeWithTag("settings_export_profiles_item").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_profiles_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Export Preview (1 โปรไฟล์)").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_export_json_button").assertIsEnabled()
        composeRule.onNodeWithText("ยกเลิก").performClick()
        composeRule.waitForIdle()

        // Open Create Encrypted Backup dialog from Settings
        composeRule.onNodeWithTag("settings_create_encrypted_backup_item").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_encrypted_backup_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("create_encrypted_passphrase_input").performTextInput("VaultKey#2026")
        composeRule.onNodeWithTag("create_encrypted_confirm_passphrase_input").performTextInput("VaultKey#2026")
        composeRule.onNodeWithTag("confirm_create_encrypted_backup_button").assertIsEnabled()
        composeRule.onNodeWithText("ยกเลิก").performClick()
        composeRule.waitForIdle()

        // Verify real SettingsViewModel export + file I/O + encrypted restore + duplicate preview + Keystore import
        val exportedJson = settingsViewModel.buildStandardJsonExport().getOrThrow()
        assertFalse(BackupCryptoManager.isEncryptedBackup(exportedJson))
        assertTrue(exportedJson.contains("Prod DB"))
        assertFalse(exportedJson.contains("DbSecret#1"))

        val encryptedTssh = settingsViewModel.buildEncryptedFullBackup(
            passphrase = "VaultKey#2026",
            includeCredentials = true
        ).getOrThrow()
        assertTrue(BackupCryptoManager.isEncryptedBackup(encryptedTssh))

        // Verify SAF file read/write helpers
        val targetFile = File(tempDir, "backup-test.tsshbackup")
        val targetUri = Uri.fromFile(targetFile)
        assertTrue(writeBackupTextToUri(context, targetUri, encryptedTssh).isSuccess)
        val readBack = readBackupTextFromUri(context, targetUri).getOrThrow()
        assertEquals(encryptedTssh, readBack)

        // Verify Restore Encrypted Backup rejects plain JSON and wrong passphrase
        assertTrue(settingsViewModel.prepareImportPreview(exportedJson, null, requireEncrypted = true) is BackupImportResult.Error)
        assertTrue(settingsViewModel.prepareImportPreview(readBack, "WrongPass", requireEncrypted = true) is BackupImportResult.Error)

        // Delete existing profile & credentials, then wait for StateFlow sync before encrypted restore
        runBlocking {
            profileRepo.deleteProfiles(listOf("set-srv-1"), removeAssociatedCredentials = true)
        }
        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            settingsViewModel.allProfiles.value.isEmpty()
        }
        settingsViewModel.refreshVaultCount()
        assertEquals(0, settingsViewModel.vaultCount.value)

        val previewRes = settingsViewModel.prepareImportPreview(readBack, "VaultKey#2026", requireEncrypted = true)
        assertTrue(previewRes is BackupImportResult.Success)

        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("ตรวจสอบก่อนนำเข้า (Import Preview)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("ทั้งหมด: 1 • โปรไฟล์ใหม่: 1 • ซ้ำกับที่มีอยู่: 0").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_import_button").performClick()

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            settingsViewModel.allProfiles.value.size == 1 && settingsViewModel.vaultCount.value == 1
        }

        val restored = runBlocking { profileRepo.getProfileById("set-srv-1") }
        assertEquals("Prod DB", restored?.name)
        assertEquals("DbSecret#1", vault.readSecretString(restored?.credentialRefId!!))
    }
}
