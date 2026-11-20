package com.example

import android.content.Context
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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileDao
import com.example.data.ProfileRepository
import com.example.data.SshProfileEntity
import com.example.data.TmuxSessionRepository
import com.example.domain.AuthMethodType
import com.example.domain.ManagedTmuxSession
import com.example.domain.ProfileSortOrder
import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.profiles.ServersViewModel
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.CredentialVault
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.settings.AppPreferencesRepository
import com.example.ssh.ManagedSessionUiState
import com.example.ssh.SshSessionManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ProfileManagementTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: PocketSshDatabase
    private lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var vault: CredentialVault
    private lateinit var profileRepo: ProfileRepository
    private lateinit var knownHostRepo: KnownHostRepository
    private lateinit var tmuxRepo: TmuxSessionRepository
    private lateinit var prefsRepo: AppPreferencesRepository
    private lateinit var sshSessionManager: SshSessionManager
    private lateinit var viewModel: ServersViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testId = UUID.randomUUID().toString()
        tempDir = File(context.cacheDir, "prof_mgmt_test_$testId").apply { mkdirs() }
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
        tmuxRepo = TmuxSessionRepository(db.tmuxSessionDao())
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "prefs_$testId.preferences_pb") }
        )
        prefsRepo = AppPreferencesRepository(context, customDataStore = ds)
        val networkMonitor = NetworkMonitor(context)
        sshSessionManager = SshSessionManager(
            profileRepository = profileRepo,
            knownHostRepository = knownHostRepo,
            credentialVault = vault,
            networkMonitor = networkMonitor,
            preferencesRepository = prefsRepo,
            tmuxSessionRepository = tmuxRepo
        )
        runBlocking {
            profileRepo.ensureBuiltInGroups()
        }
        viewModel = ServersViewModel(
            profileRepository = profileRepo,
            credentialVault = vault,
            sshSessionManager = sshSessionManager,
            preferencesRepository = prefsRepo,
            networkMonitor = networkMonitor,
            knownHostRepository = knownHostRepo
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun bulkDelete_cleansTargetMetadataAndVaultOnly_keepsExternalFilesKnownHostsAndUnrelatedProfiles(): Unit = runBlocking {
        val externalUserKey = File(tempDir, "user_owned_id_ed25519").apply {
            writeText("-----BEGIN OPENSSH PRIVATE KEY-----\nUSER_KEY\n-----END OPENSSH PRIVATE KEY-----")
        }

        val p1 = profileRepo.saveProfile(
            SshProfile(id = "p1", name = "Alpha", host = "10.0.0.1", username = "root", authMethod = AuthMethodType.PASSWORD),
            newPasswordChars = "alpha-pass".toCharArray()
        )
        val p2 = profileRepo.saveProfile(
            SshProfile(id = "p2", name = "Beta", host = "10.0.0.2", username = "ubuntu", authMethod = AuthMethodType.PRIVATE_KEY),
            newPrivateKeyPem = externalUserKey.readText()
        )
        val pKeep = profileRepo.saveProfile(
            SshProfile(id = "p3", name = "Gamma Keep", host = "10.0.0.3", username = "ops", authMethod = AuthMethodType.PASSWORD),
            newPasswordChars = "gamma-pass".toCharArray()
        )

        profileRepo.recordSuccessfulConnection("p1", 1000L)
        profileRepo.recordSuccessfulConnection("p3", 2000L)
        tmuxRepo.recordSession(ManagedTmuxSession("ts_p1_a", "p1", 1000L, 1000L))
        tmuxRepo.recordSession(ManagedTmuxSession("ts_p3_a", "p3", 2000L, 2000L))
        knownHostRepo.trustHost("10.0.0.1", 22, "ssh-ed25519", byteArrayOf(1, 2, 3))

        val result = profileRepo.deleteProfiles(listOf("p1", "p2"), removeAssociatedCredentials = true)

        assertEquals(2, result.deletedProfilesSanitized.size)
        assertEquals(2, result.deletedCredentialCount)
        assertTrue(result.deletedProfilesSanitized.all { it.credentialRefId == null })

        // Room records & local recent/tmux refs for deleted profiles are gone
        assertNull(profileRepo.getProfileById("p1"))
        assertNull(profileRepo.getProfileById("p2"))
        assertTrue(tmuxRepo.getForProfileOnce("p1").isEmpty())
        assertFalse(vault.hasCredential(p1.credentialRefId))
        assertFalse(vault.hasCredential(p2.credentialRefId))

        // Unrelated profile, its credential, its recent history, its tmux row, known host, and external file are untouched
        assertNotNull(profileRepo.getProfileById("p3"))
        assertTrue(vault.hasCredential(pKeep.credentialRefId))
        assertEquals("gamma-pass", vault.readSecretString(pKeep.credentialRefId))
        assertEquals(1, profileRepo.recentConnections.first().size)
        assertEquals(1, tmuxRepo.getForProfileOnce("p3").size)
        assertNotNull(knownHostRepo.findTrustedHost("10.0.0.1", 22, "ssh-ed25519"))
        assertTrue(externalUserKey.exists())
    }

    @Test
    fun deleteFailure_preservesDatabaseAndVaultConsistency(): Unit = runBlocking {
        val saved = profileRepo.saveProfile(
            SshProfile(id = "fail-1", name = "Keep On Fail", host = "10.1.1.1", username = "admin"),
            newPasswordChars = "stay-safe".toCharArray()
        )
        val failingDao = object : ProfileDao by db.profileDao() {
            override suspend fun deleteProfilesByIds(ids: List<String>) {
                error("Simulated SQLite write error")
            }
        }
        val failingRepo = ProfileRepository(
            profileDao = failingDao,
            groupDao = db.profileGroupDao(),
            recentDao = db.recentConnectionDao(),
            credentialVault = vault,
            database = db
        )

        var caught: Throwable? = null
        try {
            failingRepo.deleteProfiles(listOf("fail-1"), removeAssociatedCredentials = true)
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        // Profile and Vault credential must still exist intact
        assertNotNull(profileRepo.getProfileById("fail-1"))
        assertTrue(vault.hasCredential(saved.credentialRefId))
        assertEquals("stay-safe", vault.readSecretString(saved.credentialRefId))
    }

    @Test
    fun duplicateProfile_createsUniqueUuid_andIsolatesSecretsWhenOptedIn(): Unit = runBlocking {
        val original = profileRepo.saveProfile(
            SshProfile(id = "orig-1", name = "Primary Node", host = "10.2.2.2", username = "root"),
            newPasswordChars = "node-secret".toCharArray()
        )

        val defaultCopy = profileRepo.duplicateProfile(original.id)
        assertNotNull(defaultCopy)
        assertNotEquals(original.id, defaultCopy!!.id)
        assertNull(defaultCopy.credentialRefId)

        val copyWithSecret = profileRepo.duplicateProfile(original.id, includeCredential = true)
        assertNotNull(copyWithSecret)
        assertNotEquals(original.id, copyWithSecret!!.id)
        assertNotNull(copyWithSecret.credentialRefId)
        assertNotEquals(original.credentialRefId, copyWithSecret.credentialRefId)
        assertEquals("node-secret", vault.readSecretString(copyWithSecret.credentialRefId))

        // Deleting original + credential must NOT delete the explicitly copied credential
        profileRepo.deleteProfile(original.id, removeAssociatedCredential = true)
        assertFalse(vault.hasCredential(original.credentialRefId))
        assertTrue(vault.hasCredential(copyWithSecret.credentialRefId))
        assertEquals("node-secret", vault.readSecretString(copyWithSecret.credentialRefId))
    }

    @Test
    fun activeSessionDeleteWarning_cancelOrDisconnectAndDelete_leavesOtherSessionsIntact() {
        lateinit var profA: SshProfile
        lateinit var profB: SshProfile
        runBlocking {
            profA = profileRepo.saveProfile(
                SshProfile(id = "act-a", name = "Active A", host = "10.3.0.1", username = "a"),
                newPasswordChars = "secret-a".toCharArray()
            )
            profB = profileRepo.saveProfile(
                SshProfile(id = "act-b", name = "Active B", host = "10.3.0.2", username = "b"),
                newPasswordChars = "secret-b".toCharArray()
            )
        }
        sshSessionManager.registerTestSessionStateForTests(
            ManagedSessionUiState(sessionId = "sess-a", profile = profA, status = SessionStatus.CONNECTED)
        )
        sshSessionManager.registerTestSessionStateForTests(
            ManagedSessionUiState(sessionId = "sess-b", profile = profB, status = SessionStatus.CONNECTED)
        )

        composeRule.setContent {
            PocketSshTheme {
                val uiState by viewModel.uiState.collectAsState()
                ServersScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToTerminal = {}
                )
            }
        }

        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("Active A").fetchSemanticsNodes().isNotEmpty()
        }

        // Open details for Active A -> click Delete
        composeRule.onNodeWithTag("servers_list").performScrollToNode(hasTestTag("profile_card_act-a"))
        composeRule.onNodeWithTag("profile_card_act-a").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("details_delete_profile_button").performClick()
        composeRule.waitForIdle()

        // Active session warning + Disconnect and Delete action must appear
        composeRule.onNodeWithTag("active_session_delete_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_disconnect_and_delete_button").assertIsDisplayed()

        // Trying confirm without disconnect flag does not delete silently
        viewModel.confirmPendingDelete(removeAssociatedCredentials = true, disconnectActiveFirst = false)
        assertNotNull(runBlocking { profileRepo.getProfileById("act-a") })
        assertEquals(2, sshSessionManager.sessions.value.size)

        // Cancel preserves both profile A and active session
        composeRule.onNodeWithTag("cancel_delete_profile_button").performClick()
        composeRule.waitForIdle()
        assertNotNull(runBlocking { profileRepo.getProfileById("act-a") })
        assertEquals(2, sshSessionManager.sessions.value.size)

        // Trigger again via three-dot menu and choose Disconnect & Delete
        composeRule.onNodeWithTag("servers_list").performScrollToNode(hasTestTag("profile_more_act-a"))
        composeRule.onNodeWithTag("profile_more_act-a").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet_delete_profile_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_disconnect_and_delete_button").performClick()

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.uiState.value.allProfiles.none { it.id == "act-a" }
        }

        // Only session A closed; profile B + session B + credential B remain untouched
        assertEquals(listOf("sess-b"), sshSessionManager.sessions.value.map { it.sessionId })
        assertNotNull(runBlocking { profileRepo.getProfileById("act-b") })
        assertTrue(vault.hasCredential(profB.credentialRefId))
        assertFalse(vault.hasCredential(profA.credentialRefId))

        // Undo restores only non-secret config of Active A
        val undoState = viewModel.uiState.value.undoDeleteState
        assertNotNull(undoState)
        assertNull(undoState!!.nonSecretProfiles.single().credentialRefId)

        composeRule.onNodeWithTag("undo_delete_button").performClick()
        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.uiState.value.allProfiles.any { it.id == "act-a" }
        }
        val restoredA = runBlocking { profileRepo.getProfileById("act-a") }
        assertNotNull(restoredA)
        assertNull(restoredA?.credentialRefId)
    }

    @Test
    fun bulkSelection_selectAllDeselectAll_scopedExport_andBulkDeleteWithUndo(): Unit = runBlocking {
        profileRepo.saveProfile(
            SshProfile(id = "b1", name = "Old Server", host = "10.4.0.1", username = "u1", createdAt = 1000L),
            newPasswordChars = "pw1".toCharArray()
        )
        profileRepo.saveProfile(
            SshProfile(id = "b2", name = "Mid Server", host = "10.4.0.2", username = "u2", createdAt = 2000L),
            newPasswordChars = "pw2".toCharArray()
        )
        profileRepo.saveProfile(
            SshProfile(id = "b3", name = "New Server", host = "10.4.0.3", username = "u3", createdAt = 3000L),
            newPasswordChars = "pw3".toCharArray()
        )

        // Verify Date Created sort order
        viewModel.setSortOrder(ProfileSortOrder.CREATED_DESC)

        composeRule.setContent {
            PocketSshTheme {
                val uiState by viewModel.uiState.collectAsState()
                ServersScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToTerminal = {}
                )
            }
        }

        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("New Server").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(
            listOf("b3", "b2", "b1"),
            viewModel.uiState.value.filteredProfiles.map { it.id }
        )

        // Entering selection mode does NOT delete any profiles
        composeRule.onNodeWithTag("toggle_selection_mode_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bulk_selection_bar").assertIsDisplayed()
        assertEquals(3, viewModel.uiState.value.allProfiles.size)

        // Select All -> Deselect All -> Select 2 items
        composeRule.onNodeWithTag("bulk_select_all_button").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("b1", "b2", "b3"), viewModel.uiState.value.selectedProfileIds)

        composeRule.onNodeWithTag("bulk_deselect_all_button").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.selectedProfileIds.isEmpty())
        composeRule.onNodeWithTag("bulk_delete_selected_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("servers_list").performScrollToNode(hasTestTag("select_profile_checkbox_b1"))
        composeRule.onNodeWithTag("select_profile_checkbox_b1").performClick()
        composeRule.onNodeWithTag("servers_list").performScrollToNode(hasTestTag("select_profile_checkbox_b2"))
        composeRule.onNodeWithTag("select_profile_checkbox_b2").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("b1", "b2"), viewModel.uiState.value.selectedProfileIds)

        // Scoped Export Selected produces backup with ONLY the 2 selected profiles
        val exportedSelectedJson = viewModel.buildExportContent(null, viewModel.uiState.value.selectedProfileIds).getOrThrow()
        val parsed = BackupCryptoManager.importBackup(exportedSelectedJson, null) as BackupImportResult.Success
        assertEquals(setOf("b1", "b2"), parsed.bundle.profiles.map { it.id }.toSet())

        // Open Export Selected dialog and verify count label
        composeRule.onNodeWithTag("bulk_export_selected_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ส่งออกโปรไฟล์ที่เลือก (2)").assertIsDisplayed()
        composeRule.onNodeWithText("Export Preview (2 โปรไฟล์)").assertIsDisplayed()
        composeRule.onNodeWithText("ปิด").performClick()
        composeRule.waitForIdle()

        // Bulk delete selected -> shows bulk confirmation count -> confirm
        composeRule.onNodeWithTag("bulk_delete_selected_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ลบโปรไฟล์ที่เลือก 2 รายการ?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_profile_button").performClick()

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.uiState.value.allProfiles.size == 1
        }
        assertEquals(listOf("b3"), viewModel.uiState.value.allProfiles.map { it.id })

        // Undo restores non-secret config of b1 & b2 (credentialRefId must remain null)
        composeRule.onNodeWithTag("undo_delete_button").performClick()
        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.uiState.value.allProfiles.size == 3
        }
        assertNull(profileRepo.getProfileById("b1")?.credentialRefId)
        assertNull(profileRepo.getProfileById("b2")?.credentialRefId)
    }

    @Test
    fun editProfileScreen_exposesDeleteActionWithConfirmation(): Unit = runBlocking {
        val prof = profileRepo.saveProfile(
            SshProfile(id = "edit-del", name = "Editable Host", host = "10.5.0.1", username = "ops")
        )
        viewModel.openEditEditor(prof)

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
                        pendingDeleteConfirmation = uiState.pendingDelete,
                        onUpdate = { viewModel.updateEditorDraft(it) },
                        onCancel = { viewModel.closeEditor() },
                        onSaveProfile = {},
                        onSaveAndConnect = {},
                        onDeleteProfile = { viewModel.requestDeleteEditingProfile() },
                        onConfirmPendingDelete = { removeCreds, disconnect ->
                            viewModel.confirmPendingDelete(removeCreds, disconnect)
                        },
                        onCancelPendingDelete = { viewModel.cancelPendingDelete() }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_delete_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ลบโปรไฟล์ \"Editable Host\"?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_profile_button").performClick()

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.editorDraft.value == null && viewModel.uiState.value.allProfiles.isEmpty()
        }
    }
}
