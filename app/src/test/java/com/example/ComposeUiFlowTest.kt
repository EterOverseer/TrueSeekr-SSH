package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.domain.HostKeyVerificationRequest
import com.example.profiles.ServersViewModel
import com.example.security.CredentialVault
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.settings.AppPreferencesRepository
import com.example.ssh.SshSessionManager
import com.example.ui.components.HostKeyMismatchDialog
import com.example.ui.components.UnknownHostKeyDialog
import com.example.ui.screens.ProfileEditorDialog
import com.example.ui.screens.ServersScreen
import com.example.ui.theme.PocketSshTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ComposeUiFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: PocketSshDatabase
    private lateinit var vaultDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var vault: CredentialVault
    private lateinit var profileRepo: ProfileRepository
    private lateinit var viewModel: ServersViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val testId = UUID.randomUUID().toString()
        vaultDir = File(context.cacheDir, "ui_test_vault_$testId").apply { mkdirs() }
        vault = CredentialVault(vaultDir, SoftwareAesGcmCryptoEngine())
        profileRepo = ProfileRepository(
            profileDao = db.profileDao(),
            groupDao = db.profileGroupDao(),
            recentDao = db.recentConnectionDao(),
            credentialVault = vault
        )
        runBlocking {
            profileRepo.ensureBuiltInGroups()
        }
        val knownHostRepo = KnownHostRepository(db.knownHostDao())
        val networkMonitor = NetworkMonitor(context)
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(vaultDir, "compose_flow_prefs_$testId.preferences_pb") }
        )
        val prefsRepo = AppPreferencesRepository(context, customDataStore = testDataStore)
        val sshSessionManager = SshSessionManager(
            profileRepository = profileRepo,
            knownHostRepository = knownHostRepo,
            credentialVault = vault,
            networkMonitor = networkMonitor,
            preferencesRepository = prefsRepo
        )
        viewModel = ServersViewModel(
            profileRepository = profileRepo,
            credentialVault = vault,
            sshSessionManager = sshSessionManager,
            preferencesRepository = prefsRepo,
            networkMonitor = networkMonitor
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
        vaultDir.deleteRecursively()
    }

    @Test
    fun saveBeforeConnect_validatesBlankFields_andPersistsValidProfile() {
        composeRule.setContent {
            PocketSshTheme {
                val uiState by viewModel.uiState.collectAsState()
                val draft by viewModel.editorDraft.collectAsState()

                ServersScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToTerminal = {}
                )

                draft?.let { currentDraft ->
                    ProfileEditorDialog(
                        draft = currentDraft,
                        groups = uiState.groups,
                        onUpdate = { viewModel.updateEditorDraft(it) },
                        onCancel = { viewModel.closeEditor() },
                        onSaveProfile = {
                            viewModel.saveEditorProfile(connectAfterSave = false) {}
                        },
                        onSaveAndConnect = {
                            viewModel.saveEditorProfile(connectAfterSave = true) {}
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_profile_fab").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("editor_save_connect_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Trying Save & Connect with empty fields shows validation error and does not save
        composeRule.onNodeWithTag("editor_save_connect_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("กรุณาระบุชื่อโปรไฟล์ (Profile Name)").assertIsDisplayed()

        // Fill valid fields and save
        composeRule.onNodeWithTag("editor_name_input").performTextInput("Debian Lab")
        composeRule.onNodeWithTag("editor_host_input").performTextInput("192.168.50.10")
        composeRule.onNodeWithTag("editor_username_input").performTextInput("admin")
        composeRule.onNodeWithTag("editor_password_input").performTextInput("pass1234")
        composeRule.onNodeWithTag("editor_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("editor_save_button").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Debian Lab").fetchSemanticsNodes().isNotEmpty()
        }

        val savedList = runBlocking { profileRepo.allProfiles.first() }
        assertEquals(1, savedList.size)
        assertEquals("Debian Lab", savedList.first().name)
        assertEquals("192.168.50.10", savedList.first().host)

        // Verify saved card appears on ServersScreen and search filters properly
        composeRule.onNodeWithText("Debian Lab").assertIsDisplayed()
        composeRule.onNodeWithTag("server_search_input").performTextInput("nonexistent-host")
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("ไม่พบโปรไฟล์ที่ตรงกับคำค้นหา").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("ไม่พบโปรไฟล์ที่ตรงกับคำค้นหา").assertIsDisplayed()
    }

    @Test
    fun hostKeyDialogs_renderFingerprints_andTriggerCallbacks() {
        var approved = false
        var mismatchDismissed = false

        val unknownReq = HostKeyVerificationRequest.UnknownHost(
            requestId = "req-1",
            host = "10.0.0.5",
            port = 22,
            keyType = "ssh-ed25519",
            actualSha256 = "SHA256:TestFingerprintABC123",
            actualColonHex = "aa:bb:cc:dd"
        )
        val mismatchReq = HostKeyVerificationRequest.KeyMismatch(
            requestId = "req-2",
            host = "10.0.0.5",
            port = 22,
            keyType = "ssh-ed25519",
            actualSha256 = "SHA256:EvilModifiedKey",
            actualColonHex = "11:22:33:44",
            expectedSha256 = "SHA256:ExpectedKey"
        )

        composeRule.setContent {
            PocketSshTheme {
                UnknownHostKeyDialog(
                    request = unknownReq,
                    onApprove = { approved = true },
                    onReject = {}
                )
                HostKeyMismatchDialog(
                    mismatch = mismatchReq,
                    onDismiss = { mismatchDismissed = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("SHA256:TestFingerprintABC123").assertIsDisplayed()
        composeRule.onNodeWithText("SHA256:EvilModifiedKey").assertIsDisplayed()

        composeRule.onNodeWithTag("host_key_mismatch_dismiss").performClick()
        composeRule.onNodeWithTag("host_key_approve_button").performClick()
        composeRule.waitForIdle()

        assertTrue(mismatchDismissed)
        assertTrue(approved)
    }
}
