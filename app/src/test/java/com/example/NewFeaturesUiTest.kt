package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.data.SnippetRepository
import com.example.domain.AuthMethodType
import com.example.domain.HealthRefreshInterval
import com.example.domain.SshProfile
import com.example.profiles.ServersViewModel
import com.example.security.BackupCryptoManager
import com.example.security.CredentialVault
import com.example.security.ImportDuplicateStrategy
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.settings.AppPreferencesRepository
import com.example.settings.SettingsViewModel
import com.example.ssh.SshSessionManager
import com.example.terminal.TerminalViewModel
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
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
class NewFeaturesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: PocketSshDatabase
    private lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var vault: CredentialVault
    private lateinit var profileRepo: ProfileRepository
    private lateinit var knownHostRepo: KnownHostRepository
    private lateinit var snippetRepo: SnippetRepository
    private lateinit var prefsRepo: AppPreferencesRepository
    private lateinit var sshSessionManager: SshSessionManager
    private lateinit var serversViewModel: ServersViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var terminalViewModel: TerminalViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        tempDir = File(context.cacheDir, "new_ui_test_$id").apply { mkdirs() }
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
        snippetRepo = SnippetRepository(db.commandSnippetDao())
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
        terminalViewModel = TerminalViewModel(
            sshSessionManager = sshSessionManager,
            snippetRepository = snippetRepo,
            preferencesRepository = prefsRepo
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun settingsScreen_keepAlive_healthDashboard_and_tmuxPreferencesPersistCleanly() {
        composeRule.setContent {
            PocketSshTheme {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_keep_cpu_awake_switch"))

        // Defaults: ssh keepalive ON, background keepalive OFF, cpu wakelock OFF & disabled
        composeRule.onNodeWithTag("settings_ssh_keepalive_switch").assertIsOn()
        composeRule.onNodeWithTag("settings_background_keepalive_switch").assertIsOff()
        composeRule.onNodeWithTag("settings_keep_cpu_awake_switch").assertIsOff().assertIsNotEnabled()

        // Enable Background Keep Alive via ViewModel (avoiding system runtime permission dialog in JVM test) and toggle CPU WakeLock
        settingsViewModel.setBackgroundKeepAliveEnabled(true)
        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            settingsViewModel.settings.value.backgroundKeepAliveEnabled
        }
        composeRule.onNodeWithTag("settings_keep_cpu_awake_switch").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_tmux_switch"))
        composeRule.onNodeWithTag("settings_health_dashboard_switch").assertIsOn()
        composeRule.onNodeWithTag("settings_health_interval_THIRTY_SECONDS").performClick()
        composeRule.onNodeWithTag("settings_tmux_switch").assertIsOn().performClick()

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val s = settingsViewModel.settings.value
            s.keepCpuAwakeWhileConnected &&
                s.healthRefreshInterval == HealthRefreshInterval.THIRTY_SECONDS &&
                !s.tmuxIntegrationEnabled
        }

        val finalSettings = runBlocking { prefsRepo.settings.first() }
        assertTrue(finalSettings.keepCpuAwakeWhileConnected)
        assertEquals(HealthRefreshInterval.THIRTY_SECONDS, finalSettings.healthRefreshInterval)
        assertFalse(finalSettings.tmuxIntegrationEnabled)
    }

    @Test
    fun serversScreen_backupDialogAndImportPreviewFlowWorkEndToEnd() {
        runBlocking {
            profileRepo.saveProfile(
                SshProfile(
                    id = "srv-1",
                    name = "Prod Server",
                    host = "10.0.0.10",
                    username = "deploy",
                    authMethod = AuthMethodType.PASSWORD
                ),
                newPasswordChars = "secret123".toCharArray()
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
            composeRule.onAllNodesWithText("Prod Server").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Backup dialog and verify Export Preview counts
        composeRule.onNodeWithTag("import_export_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Export Preview (1 โปรไฟล์)").assertIsDisplayed()
        composeRule.onNodeWithText("• มี Credential ใน Vault: 1 • ไม่มีใน Vault: 0").assertIsDisplayed()

        // Dismiss dialog cleanly
        composeRule.onNodeWithText("ปิด").performClick()
        composeRule.waitForIdle()

        // Feed a prepared JSON import containing 1 duplicate + 1 new profile and verify Import Preview dialog
        val backupJson = BackupCryptoManager.exportMetadataJson(
            profiles = listOf(
                SshProfile(id = "srv-1", name = "Prod Server Updated", host = "10.0.0.10", username = "deploy"),
                SshProfile(id = "srv-2", name = "Staging", host = "10.0.0.20", username = "ubuntu")
            ),
            groups = emptyList()
        )
        serversViewModel.prepareImportPreview(backupJson, null)

        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("ตรวจสอบก่อนนำเข้า (Import Preview)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("ทั้งหมด: 2 • โปรไฟล์ใหม่: 1 • ซ้ำกับที่มีอยู่: 1").assertIsDisplayed()
        composeRule.onNodeWithTag("import_strategy_${ImportDuplicateStrategy.REPLACE.name}").performClick()
        composeRule.onNodeWithTag("confirm_import_button").performClick()

        composeRule.waitUntil(5000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            serversViewModel.uiState.value.allProfiles.size == 2
        }

        val updated = runBlocking { profileRepo.getProfileById("srv-1") }
        assertEquals("Prod Server Updated", updated?.name)
    }

    @Test
    fun terminalViewModel_dashboardIntervalChangeAndStopCleanlyCancelJobs() {
        val sessionId = "sess-test-1"
        terminalViewModel.setDashboardInterval(sessionId, HealthRefreshInterval.SIXTY_SECONDS)
        assertEquals(HealthRefreshInterval.SIXTY_SECONDS, terminalViewModel.dashboardInterval.value)

        terminalViewModel.stopDashboardPolling()
        terminalViewModel.closeTab(sessionId)
        assertTrue(terminalViewModel.sessions.value.isEmpty())
    }
}
