package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.domain.AppAccentPreset
import com.example.domain.AppThemeMode
import com.example.domain.AuthMethodType
import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.profiles.ServersViewModel
import com.example.security.CredentialVault
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.settings.AppPreferencesRepository
import com.example.settings.SettingsViewModel
import com.example.ssh.SshSessionManager
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.HostTrustIndicator
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
class ObsidianRedesignUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: PocketSshDatabase
    private lateinit var vaultDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var vault: CredentialVault
    private lateinit var profileRepo: ProfileRepository
    private lateinit var knownHostRepo: KnownHostRepository
    private lateinit var prefsRepo: AppPreferencesRepository
    private lateinit var serversViewModel: ServersViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val testId = UUID.randomUUID().toString()
        vaultDir = File(context.cacheDir, "obsidian_ui_test_$testId").apply { mkdirs() }
        vault = CredentialVault(vaultDir, SoftwareAesGcmCryptoEngine())
        profileRepo = ProfileRepository(
            profileDao = db.profileDao(),
            groupDao = db.profileGroupDao(),
            recentDao = db.recentConnectionDao(),
            credentialVault = vault
        )
        knownHostRepo = KnownHostRepository(db.knownHostDao())
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(vaultDir, "test_prefs_$testId.preferences_pb") }
        )
        prefsRepo = AppPreferencesRepository(context, customDataStore = testDataStore)
        val networkMonitor = NetworkMonitor(context)
        val sshSessionManager = SshSessionManager(
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
        vaultDir.deleteRecursively()
    }

    @Test
    fun commandCenter_displaysOverviewKpis_hostTrust_andProfileBottomSheet() {
        val trustedProfile = SshProfile(
            id = "prof-trusted",
            name = "Aurora Gateway",
            host = "10.10.0.5",
            port = 22,
            username = "ops",
            authMethod = AuthMethodType.PRIVATE_KEY,
            groupName = "Production",
            isFavorite = true
        )
        runBlocking {
            profileRepo.saveProfile(trustedProfile, newPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\nTEST\n-----END PRIVATE KEY-----")
            knownHostRepo.trustHost("10.10.0.5", 22, "ssh-ed25519", byteArrayOf(1, 2, 3, 4))
        }

        composeRule.setContent {
            PocketSshTheme {
                val uiState by serversViewModel.uiState.collectAsState()
                ServersScreen(
                    uiState = uiState,
                    viewModel = serversViewModel,
                    onNavigateToTerminal = {},
                    onOpenSettings = {},
                    appLockEnabled = true,
                    vaultCount = 1
                )
            }
        }

        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("Aurora Gateway").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("SSH & Server Command Center").assertIsDisplayed()
        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Saved Profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Attention").assertIsDisplayed()
        composeRule.onNodeWithText("App Lock Active").assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Servers").assertIsDisplayed()

        // Scroll if needed and open bottom sheet actions on the profile card
        composeRule.onNodeWithTag("servers_list").performScrollToNode(hasTestTag("profile_more_prof-trusted"))
        composeRule.onNodeWithTag("profile_more_prof-trusted").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("แก้ไขโปรไฟล์").assertIsDisplayed()
        composeRule.onNodeWithText("คัดลอกโปรไฟล์ (Duplicate)").assertIsDisplayed()
    }

    @Test
    fun statusPillsAndHostTrustIndicators_renderConsistentLabels() {
        composeRule.setContent {
            PocketSshTheme {
                Column {
                    ConnectionStatusPill(status = SessionStatus.CONNECTED)
                    ConnectionStatusPill(status = SessionStatus.CONNECTING)
                    ConnectionStatusPill(status = SessionStatus.RECONNECTING)
                    ConnectionStatusPill(status = SessionStatus.DISCONNECTED)
                    ConnectionStatusPill(status = SessionStatus.ERROR)
                    HostTrustIndicator(isTrusted = true)
                    HostTrustIndicator(isTrusted = false)
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(SessionStatus.CONNECTED.label).assertIsDisplayed()
        composeRule.onNodeWithText(SessionStatus.CONNECTING.label).assertIsDisplayed()
        composeRule.onNodeWithText(SessionStatus.RECONNECTING.label).assertIsDisplayed()
        composeRule.onNodeWithText(SessionStatus.DISCONNECTED.label).assertIsDisplayed()
        composeRule.onNodeWithText(SessionStatus.ERROR.label).assertIsDisplayed()
        composeRule.onNodeWithText("Host Verified").assertIsDisplayed()
        composeRule.onNodeWithText("Unverified Host").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_switchesThemeModeAndAccentPreset_andPersistsInDataStore() {
        composeRule.setContent {
            val currentSettings by prefsRepo.settings.collectAsState(initial = com.example.settings.AppSettings())
            PocketSshTheme(
                themeMode = currentSettings.themeMode,
                accentPreset = currentSettings.accentPreset
            ) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("รูปลักษณ์และธีม (Appearance)").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_theme_mode_${AppThemeMode.LIGHT.name}")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_accent_${AppAccentPreset.LIME.name}")
            .performClick()

        composeRule.waitUntil(5000L) {
            val current = settingsViewModel.settings.value
            current.themeMode == AppThemeMode.LIGHT && current.accentPreset == AppAccentPreset.LIME
        }

        val finalSettings = runBlocking { prefsRepo.settings.first() }
        assertEquals(AppThemeMode.LIGHT, finalSettings.themeMode)
        assertEquals(AppAccentPreset.LIME, finalSettings.accentPreset)
    }
}
