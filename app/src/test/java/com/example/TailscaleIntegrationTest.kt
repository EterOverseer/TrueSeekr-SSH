package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.domain.AuthMethodType
import com.example.domain.SshProfile
import com.example.network.ServerReachabilityChecker
import com.example.network.TailscaleConnectionCoordinator
import com.example.network.TailscaleController
import com.example.network.TailscaleInstallState
import com.example.network.TailscaleLaunchResult
import com.example.network.TailscaleOwnershipStore
import com.example.network.TailscalePreparationResult
import com.example.network.TailscaleProgressState
import com.example.network.TailscaleSessionLeaseManager
import com.example.network.TailscaleVpnStatus
import com.example.network.VpnOwnership
import com.example.network.VpnStateProvider
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.CredentialVault
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.settings.AppPreferencesRepository
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class TailscaleIntegrationTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var prefsRepo: AppPreferencesRepository
    private lateinit var fakeController: FakeTailscaleController
    private lateinit var fakeVpnState: FakeVpnStateProvider
    private lateinit var fakeReachability: FakeReachabilityChecker
    private lateinit var ownershipStore: TailscaleOwnershipStore
    private lateinit var leaseManager: TailscaleSessionLeaseManager
    private var virtualTimeMs: Long = 10_000L

    private fun createCoordinator(pollMs: Long = 10L): TailscaleConnectionCoordinator {
        return TailscaleConnectionCoordinator(
            controller = fakeController,
            vpnStateProvider = fakeVpnState,
            ownershipStore = ownershipStore,
            leaseManager = leaseManager,
            reachabilityChecker = fakeReachability,
            preferencesRepository = prefsRepo,
            timeProvider = { virtualTimeMs },
            pollIntervalMs = pollMs
        )
    }

    private fun tailscaleProfile(
        id: String = "prof-ts",
        host: String = "100.80.10.2",
        port: Int = 22,
        autoTailscale: Boolean = true
    ) = SshProfile(
        id = id,
        name = "Tailnet Server $id",
        host = host,
        port = port,
        username = "ops",
        authMethod = AuthMethodType.PASSWORD,
        autoTailscaleConnect = autoTailscale
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        tempDir = File(context.cacheDir, "tailscale_test_$id").apply { mkdirs() }
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "ts_prefs_$id.preferences_pb") }
        )
        prefsRepo = AppPreferencesRepository(context, customDataStore = ds)
        fakeController = FakeTailscaleController()
        fakeVpnState = FakeVpnStateProvider()
        fakeReachability = FakeReachabilityChecker(defaultReachable = true)
        val sharedPrefs = context.getSharedPreferences("ts_ownership_test_$id", Context.MODE_PRIVATE)
        ownershipStore = TailscaleOwnershipStore(
            prefs = sharedPrefs,
            currentPidProvider = { 1001 },
            initialVpnActive = false
        )
        leaseManager = TailscaleSessionLeaseManager()
        virtualTimeMs = 10_000L
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun `global switch disabled ignores profile autoTailscaleConnect and never launches Tailscale`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(false)
        val coordinator = createCoordinator()
        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile(autoTailscale = true))

        assertTrue(res is TailscalePreparationResult.Proceed)
        val proceed = res as TailscalePreparationResult.Proceed
        assertFalse(proceed.leaseGranted)
        assertEquals(0, fakeController.connectCalls)
        assertEquals(0, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())

        coordinator.onSessionEnded("s1")
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `global switch enabled with profile toggle disabled never launches Tailscale`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        val coordinator = createCoordinator()
        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile(autoTailscale = false))

        assertTrue(res is TailscalePreparationResult.Proceed)
        assertFalse((res as TailscalePreparationResult.Proceed).leaseGranted)
        assertEquals(0, fakeController.connectCalls)

        coordinator.onSessionEnded("s1")
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `tailscale not installed fails cleanly with install prompt`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        fakeController.installState = TailscaleInstallState.NOT_INSTALLED
        val coordinator = createCoordinator()

        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertTrue(res is TailscalePreparationResult.Failed)
        val failed = res as TailscalePreparationResult.Failed
        assertEquals(TailscaleProgressState.NOT_INSTALLED, failed.progressState)
        assertTrue(failed.canInstallTailscale)
        assertEquals(0, fakeController.connectCalls)
    }

    @Test
    fun `tailscale automation unavailable fails cleanly with openTailscale action`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        fakeController.installState = TailscaleInstallState.AUTOMATION_UNAVAILABLE
        val coordinator = createCoordinator()

        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertTrue(res is TailscalePreparationResult.Failed)
        val failed = res as TailscalePreparationResult.Failed
        assertEquals(TailscaleProgressState.LOGIN_OR_PERMISSION_REQUIRED, failed.progressState)
        assertTrue(failed.canOpenTailscale)
    }

    @Test
    fun `app starts Tailscale marks APP_REQUESTED grants lease and disconnects on last session close`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = {
            fakeVpnState.setVpnActive(true)
        }
        val coordinator = createCoordinator()

        val stages = mutableListOf<TailscaleProgressState>()
        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile()) { stages.add(it) }

        assertTrue(res is TailscalePreparationResult.Proceed)
        val proceed = res as TailscalePreparationResult.Proceed
        assertTrue(proceed.leaseGranted)
        assertEquals(VpnOwnership.APP_REQUESTED, proceed.ownership)
        assertEquals(1, fakeController.connectCalls)
        assertEquals(1, leaseManager.activeLeaseCount())
        assertTrue(stages.contains(TailscaleProgressState.CHECKING_TAILSCALE))
        assertTrue(stages.contains(TailscaleProgressState.STARTING_TAILSCALE))
        assertTrue(stages.contains(TailscaleProgressState.WAITING_FOR_VPN))
        assertTrue(stages.contains(TailscaleProgressState.VERIFYING_SERVER))

        coordinator.onSessionEnded("s1")
        assertEquals(0, leaseManager.activeLeaseCount())
        assertEquals(1, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())
    }

    @Test
    fun `timeout when VPN fails to come up reports TIMEOUT and keeps ownership NONE`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleConnectTimeoutSec(10)
        // Advance virtual clock on every status read so loop terminates fast
        val advancingCoordinator = TailscaleConnectionCoordinator(
            controller = fakeController,
            vpnStateProvider = object : VpnStateProvider by fakeVpnState {
                override fun readCurrentVpnStatus(): TailscaleVpnStatus {
                    virtualTimeMs += 3_000L
                    return fakeVpnState.readCurrentVpnStatus()
                }
            },
            ownershipStore = ownershipStore,
            leaseManager = leaseManager,
            reachabilityChecker = fakeReachability,
            preferencesRepository = prefsRepo,
            timeProvider = { virtualTimeMs },
            pollIntervalMs = 1L
        )

        val res = advancingCoordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertTrue(res is TailscalePreparationResult.Failed)
        val failed = res as TailscalePreparationResult.Failed
        assertEquals(TailscaleProgressState.TIMEOUT, failed.progressState)
        assertTrue(failed.canOpenTailscale)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())
        assertEquals(0, leaseManager.activeLeaseCount())
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `no false positive when VPN is up but target SSH server is unreachable`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = { fakeVpnState.setVpnActive(true) }
        fakeReachability.defaultReachable = false

        val coordinator = TailscaleConnectionCoordinator(
            controller = fakeController,
            vpnStateProvider = fakeVpnState,
            ownershipStore = ownershipStore,
            leaseManager = leaseManager,
            reachabilityChecker = object : ServerReachabilityChecker {
                override suspend fun isServerReachable(host: String, port: Int, timeoutMs: Int): Boolean {
                    virtualTimeMs += 2_500L
                    return false
                }
            },
            preferencesRepository = prefsRepo,
            timeProvider = { virtualTimeMs },
            pollIntervalMs = 1L
        )

        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertTrue(res is TailscalePreparationResult.Failed)
        assertEquals(TailscaleProgressState.FAILED, (res as TailscalePreparationResult.Failed).progressState)
        assertEquals(0, leaseManager.activeLeaseCount())
        // App started VPN cleanly during this failed check, so it also cleans up its own VPN
        assertEquals(1, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())
    }

    @Test
    fun `external VPN already connected before SSH never gets claimed and never gets disconnected`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeVpnState.setVpnActive(true) // Already active externally before app opens SSH

        val coordinator = createCoordinator()
        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile())

        assertTrue(res is TailscalePreparationResult.Proceed)
        val proceed = res as TailscalePreparationResult.Proceed
        assertEquals(TailscaleProgressState.REUSING_EXTERNAL_VPN, proceed.progressState)
        assertEquals(VpnOwnership.EXTERNAL, proceed.ownership)
        assertFalse(proceed.leaseGranted)
        assertEquals(0, fakeController.connectCalls)

        coordinator.onSessionEnded("s1")
        assertEquals(0, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.EXTERNAL, ownershipStore.current())
    }

    @Test
    fun `manual external VPN start transitions idle ownership to EXTERNAL and prevents auto disconnect`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        val coordinator = createCoordinator()
        delay(30)

        fakeVpnState.setVpnActive(true)
        delay(30)

        assertEquals(VpnOwnership.EXTERNAL, ownershipStore.current())
        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertTrue(res is TailscalePreparationResult.Proceed)
        assertEquals(VpnOwnership.EXTERNAL, (res as TailscalePreparationResult.Proceed).ownership)

        coordinator.onSessionEnded("s1")
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `external VPN manually disconnected while APP_REQUESTED skips DISCONNECT_VPN and resets NONE`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = { fakeVpnState.setVpnActive(true) }
        val coordinator = createCoordinator()

        coordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertEquals(VpnOwnership.APP_REQUESTED, ownershipStore.current())

        // User stops Tailscale manually outside TrueSeekrSSH
        fakeVpnState.setVpnActive(false)
        delay(30)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())

        coordinator.onSessionEnded("s1")
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `multiple concurrent sessions share VPN lease and only disconnect when last session ends`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = { fakeVpnState.setVpnActive(true) }
        val coordinator = createCoordinator()

        val resA = coordinator.prepareVpnForSession("tabA", tailscaleProfile(id = "a"))
        val resB = coordinator.prepareVpnForSession("tabB", tailscaleProfile(id = "b"))

        assertTrue(resA is TailscalePreparationResult.Proceed)
        assertTrue(resB is TailscalePreparationResult.Proceed)
        assertEquals(1, fakeController.connectCalls)
        assertEquals(2, leaseManager.activeLeaseCount())

        // Close first tab -> VPN MUST stay connected
        coordinator.onSessionEnded("tabA")
        assertEquals(1, leaseManager.activeLeaseCount())
        assertEquals(0, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.APP_REQUESTED, ownershipStore.current())

        // Close second (last) tab -> now disconnect
        coordinator.onSessionEnded("tabB")
        assertEquals(0, leaseManager.activeLeaseCount())
        assertEquals(1, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())
    }

    @Test
    fun `simultaneous session preparations deduplicate CONNECT_VPN and prevent premature disconnect`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = {
            fakeVpnState.setVpnActive(true)
        }
        val coordinator = createCoordinator()

        val deferredA = async { coordinator.prepareVpnForSession("tab1", tailscaleProfile("p1")) }
        val deferredB = async { coordinator.prepareVpnForSession("tab2", tailscaleProfile("p2")) }
        val resA = deferredA.await()
        val resB = deferredB.await()

        assertTrue(resA is TailscalePreparationResult.Proceed)
        assertTrue(resB is TailscalePreparationResult.Proceed)
        assertEquals(1, fakeController.connectCalls)
        assertEquals(2, leaseManager.activeLeaseCount())
    }

    @Test
    fun `rapid disconnect during concurrent acquire never disconnects newly starting VPN`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = { fakeVpnState.setVpnActive(true) }
        val slowReachability = object : ServerReachabilityChecker {
            override suspend fun isServerReachable(host: String, port: Int, timeoutMs: Int): Boolean {
                delay(60)
                return true
            }
        }
        val coordinator = TailscaleConnectionCoordinator(
            controller = fakeController,
            vpnStateProvider = fakeVpnState,
            ownershipStore = ownershipStore,
            leaseManager = leaseManager,
            reachabilityChecker = slowReachability,
            preferencesRepository = prefsRepo,
            timeProvider = { virtualTimeMs },
            pollIntervalMs = 5L
        )

        coordinator.prepareVpnForSession("tab1", tailscaleProfile("p1"))
        val startTab2 = async { coordinator.prepareVpnForSession("tab2", tailscaleProfile("p2")) }
        delay(15)
        // tab1 ends WHILE tab2 is mid-acquire
        coordinator.onSessionEnded("tab1")
        assertEquals(0, fakeController.disconnectCalls)

        val res2 = startTab2.await()
        assertTrue(res2 is TailscalePreparationResult.Proceed)
        assertEquals(1, leaseManager.activeLeaseCount())
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `auto disconnect disabled keeps Tailscale connected after last session ends`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(false)
        fakeController.onConnect = { fakeVpnState.setVpnActive(true) }
        val coordinator = createCoordinator()

        coordinator.prepareVpnForSession("s1", tailscaleProfile())
        coordinator.onSessionEnded("s1")

        assertEquals(0, leaseManager.activeLeaseCount())
        assertEquals(0, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.APP_REQUESTED, ownershipStore.current())
    }

    @Test
    fun `process restart downgrades stale APP_REQUESTED ownership to UNKNOWN and blocks auto disconnect`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        val sharedPrefs = context.getSharedPreferences("restart_ownership_${UUID.randomUUID()}", Context.MODE_PRIVATE)

        val oldStore = TailscaleOwnershipStore(
            prefs = sharedPrefs,
            currentPidProvider = { 5000 },
            initialVpnActive = false
        )
        oldStore.markAppRequested(1000L)
        assertEquals(VpnOwnership.APP_REQUESTED, oldStore.current())

        // Simulate app process kill + restart with different PID while VPN is still active
        val restartedStore = TailscaleOwnershipStore(
            prefs = sharedPrefs,
            currentPidProvider = { 9999 },
            initialVpnActive = true
        )
        assertEquals(VpnOwnership.UNKNOWN, restartedStore.current())

        fakeVpnState.setVpnActive(true)
        val coordinator = TailscaleConnectionCoordinator(
            controller = fakeController,
            vpnStateProvider = fakeVpnState,
            ownershipStore = restartedStore,
            leaseManager = leaseManager,
            reachabilityChecker = fakeReachability,
            preferencesRepository = prefsRepo,
            timeProvider = { virtualTimeMs },
            pollIntervalMs = 5L
        )

        val res = coordinator.prepareVpnForSession("s1", tailscaleProfile())
        assertTrue(res is TailscalePreparationResult.Proceed)
        assertEquals(VpnOwnership.UNKNOWN, (res as TailscalePreparationResult.Proceed).ownership)
        assertFalse(res.leaseGranted)

        coordinator.onAllSessionsEnded()
        assertEquals(0, fakeController.disconnectCalls)
    }

    @Test
    fun `room MIGRATION_1_2 upgrades v1 database non-destructively with autoTailscaleConnect defaulting false`() = runBlocking {
        val dbFile = File(tempDir, "migration_test.db")
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ssh_profiles` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `host` TEXT NOT NULL,
                `port` INTEGER NOT NULL,
                `username` TEXT NOT NULL,
                `authMethod` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `groupName` TEXT NOT NULL,
                `tagsSerialized` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `connectTimeoutSec` INTEGER NOT NULL,
                `keepAliveIntervalSec` INTEGER NOT NULL,
                `terminalType` TEXT NOT NULL,
                `terminalFontSizeSp` REAL NOT NULL,
                `terminalTheme` TEXT NOT NULL,
                `initialWorkingDir` TEXT NOT NULL,
                `credentialRefId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastConnectedAt` INTEGER,
                `connectionCount` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ssh_profiles_host_port_username` ON `ssh_profiles` (`host`, `port`, `username`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ssh_profiles_groupName` ON `ssh_profiles` (`groupName`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ssh_profiles_isFavorite` ON `ssh_profiles` (`isFavorite`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `profile_groups` (
                `name` TEXT NOT NULL PRIMARY KEY,
                `isBuiltIn` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recent_connections` (
                `profileId` TEXT NOT NULL PRIMARY KEY,
                `lastConnectedAt` INTEGER NOT NULL,
                `successCount` INTEGER NOT NULL,
                FOREIGN KEY(`profileId`) REFERENCES `ssh_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_connections_lastConnectedAt` ON `recent_connections` (`lastConnectedAt`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `known_hosts` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `host` TEXT NOT NULL,
                `port` INTEGER NOT NULL,
                `keyType` TEXT NOT NULL,
                `publicKeyBase64` TEXT NOT NULL,
                `sha256Fingerprint` TEXT NOT NULL,
                `trustedAt` INTEGER NOT NULL,
                `lastVerifiedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_known_hosts_host_port_keyType` ON `known_hosts` (`host`, `port`, `keyType`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `command_snippets` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `command` TEXT NOT NULL,
                `groupName` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `requiresConfirmation` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO ssh_profiles VALUES (
                'legacy-1', 'Legacy NAS', '192.168.1.99', 22, 'admin', 'PASSWORD', '',
                'LAN', 'nas', 1, 15, 30, 'xterm-256color', 13.0, 'OBSIDIAN_AURORA',
                '/root', NULL, 1000, 1000, NULL, 0
            )
            """.trimIndent()
        )
        rawDb.version = 1
        rawDb.close()

        val migratedRoomDb = Room.databaseBuilder(context, PocketSshDatabase::class.java, dbFile.absolutePath)
            .addMigrations(PocketSshDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val migratedProfile = migratedRoomDb.profileDao().getProfileById("legacy-1")
        assertNotNull(migratedProfile)
        assertEquals("Legacy NAS", migratedProfile?.name)
        assertFalse(migratedProfile!!.autoTailscaleConnect)

        // Update autoTailscaleConnect = true and verify round-trip persistence
        val vault = CredentialVault(File(tempDir, "vault"), SoftwareAesGcmCryptoEngine())
        val repo = ProfileRepository(
            migratedRoomDb.profileDao(),
            migratedRoomDb.profileGroupDao(),
            migratedRoomDb.recentConnectionDao(),
            vault
        )
        repo.saveProfile(migratedProfile.toDomain().copy(autoTailscaleConnect = true))
        assertTrue(repo.getProfileById("legacy-1")?.autoTailscaleConnect == true)
        migratedRoomDb.close()
    }

    @Test
    fun `DataStore persists tailscale settings and backup preserves autoTailscaleConnect`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(false)
        prefsRepo.setTailscaleConnectTimeoutSec(45)

        val loaded = prefsRepo.settings.first()
        assertTrue(loaded.tailscaleAutomationEnabled)
        assertFalse(loaded.tailscaleAutoDisconnect)
        assertEquals(45, loaded.tailscaleConnectTimeoutSec)

        // Export & import backup preserves autoTailscaleConnect and accepts legacy backups missing it
        val tsProf = tailscaleProfile("exp-1", autoTailscale = true)
        val exported = BackupCryptoManager.exportEncryptedBackup(listOf(tsProf), emptyList(), "pw1234".toCharArray())
        val imported = BackupCryptoManager.importBackup(exported, "pw1234".toCharArray())
        assertTrue(imported is BackupImportResult.Success)
        val restored = (imported as BackupImportResult.Success).bundle.profiles.single()
        assertTrue(restored.autoTailscaleConnect)

        val legacyJson = """{"version":1,"exportedAt":100,"groups":[],"profiles":[{"id":"leg-1","name":"Old","host":"10.0.0.1","username":"u"}]}"""
        val legacyImport = BackupCryptoManager.importBackup(legacyJson, null)
        assertTrue(legacyImport is BackupImportResult.Success)
        assertFalse((legacyImport as BackupImportResult.Success).bundle.profiles.single().autoTailscaleConnect)
    }

    @Test
    fun `SshSessionManager disconnectAllSessions triggers Tailscale auto disconnect when safe`() = runBlocking {
        prefsRepo.setTailscaleAutomationEnabled(true)
        prefsRepo.setTailscaleAutoDisconnect(true)
        fakeController.onConnect = { fakeVpnState.setVpnActive(true) }
        val coordinator = createCoordinator()

        val db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val vault = CredentialVault(File(tempDir, "mgr_vault"), SoftwareAesGcmCryptoEngine())
        val profileRepo = ProfileRepository(db.profileDao(), db.profileGroupDao(), db.recentConnectionDao(), vault)
        val knownHostRepo = KnownHostRepository(db.knownHostDao())
        val manager = SshSessionManager(
            profileRepository = profileRepo,
            knownHostRepository = knownHostRepo,
            credentialVault = vault,
            networkMonitor = NetworkMonitor(context),
            preferencesRepository = prefsRepo,
            tailscaleCoordinator = coordinator
        )

        val prep = coordinator.prepareVpnForSession("sess-x", tailscaleProfile())
        assertTrue(prep is TailscalePreparationResult.Proceed)
        assertEquals(VpnOwnership.APP_REQUESTED, ownershipStore.current())

        manager.disconnectAllSessions()
        delay(80)
        assertEquals(1, fakeController.disconnectCalls)
        assertEquals(VpnOwnership.NONE, ownershipStore.current())
        db.close()
    }

    private class FakeTailscaleController : TailscaleController {
        var installState: TailscaleInstallState = TailscaleInstallState.INSTALLED
        var connectCalls: Int = 0
        var disconnectCalls: Int = 0
        var openAppCalls: Int = 0
        var openStoreCalls: Int = 0
        var onConnect: () -> Unit = {}
        var onDisconnect: () -> Unit = {}

        override fun checkInstallState(): TailscaleInstallState = installState

        override fun launchConnectVpn(): TailscaleLaunchResult {
            connectCalls++
            onConnect()
            return TailscaleLaunchResult(launched = true, installState = installState)
        }

        override fun launchDisconnectVpn(): TailscaleLaunchResult {
            disconnectCalls++
            onDisconnect()
            return TailscaleLaunchResult(launched = true, installState = installState)
        }

        override fun openTailscaleApp(): Boolean {
            openAppCalls++
            return true
        }

        override fun openTailscalePlayStore(): Boolean {
            openStoreCalls++
            return true
        }
    }

    private class FakeVpnStateProvider : VpnStateProvider {
        private val _vpnStatus = MutableStateFlow(TailscaleVpnStatus(hasTransportVpn = false, hasInternet = true))
        override val vpnStatus: StateFlow<TailscaleVpnStatus> = _vpnStatus.asStateFlow()

        fun setVpnActive(active: Boolean) {
            _vpnStatus.value = TailscaleVpnStatus(
                hasTransportVpn = active,
                hasInternet = true,
                interfaceNames = if (active) listOf("tun0") else emptyList(),
                hasTailscaleHint = active
            )
        }

        override fun readCurrentVpnStatus(): TailscaleVpnStatus = _vpnStatus.value
    }

    private class FakeReachabilityChecker(var defaultReachable: Boolean = true) : ServerReachabilityChecker {
        override suspend fun isServerReachable(host: String, port: Int, timeoutMs: Int): Boolean = defaultReachable
    }
}
