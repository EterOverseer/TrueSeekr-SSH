package com.example

import android.app.Application
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.data.SnippetRepository
import com.example.network.AndroidTailscaleIntentController
import com.example.network.AndroidTailscaleVpnMonitor
import com.example.network.SocketServerReachabilityChecker
import com.example.network.TailscaleConnectionCoordinator
import com.example.network.TailscaleOwnershipStore
import com.example.network.TailscaleSessionLeaseManager
import com.example.security.AndroidKeystoreCryptoEngine
import com.example.security.CredentialVault
import com.example.settings.AppPreferencesRepository
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: PocketSshDatabase = PocketSshDatabase.create(application)
    val networkMonitor: NetworkMonitor = NetworkMonitor(application)
    val cryptoEngine = AndroidKeystoreCryptoEngine()
    val credentialVault = CredentialVault(application, cryptoEngine)
    val preferencesRepository = AppPreferencesRepository(application)

    val tailscaleController = AndroidTailscaleIntentController(application)
    val tailscaleVpnMonitor = AndroidTailscaleVpnMonitor(application, networkMonitor)
    val tailscaleOwnershipStore = TailscaleOwnershipStore(
        context = application,
        initialVpnActive = tailscaleVpnMonitor.readCurrentVpnStatus().hasTransportVpn
    )
    val tailscaleLeaseManager = TailscaleSessionLeaseManager()
    val tailscaleCoordinator = TailscaleConnectionCoordinator(
        controller = tailscaleController,
        vpnStateProvider = tailscaleVpnMonitor,
        ownershipStore = tailscaleOwnershipStore,
        leaseManager = tailscaleLeaseManager,
        reachabilityChecker = SocketServerReachabilityChecker(),
        preferencesRepository = preferencesRepository
    )

    val profileRepository = ProfileRepository(
        profileDao = database.profileDao(),
        groupDao = database.profileGroupDao(),
        recentDao = database.recentConnectionDao(),
        credentialVault = credentialVault
    )

    val knownHostRepository = KnownHostRepository(database.knownHostDao())
    val snippetRepository = SnippetRepository(database.commandSnippetDao())

    val sshSessionManager = SshSessionManager(
        profileRepository = profileRepository,
        knownHostRepository = knownHostRepository,
        credentialVault = credentialVault,
        networkMonitor = networkMonitor,
        preferencesRepository = preferencesRepository,
        tailscaleCoordinator = tailscaleCoordinator
    )

    init {
        appScope.launch {
            profileRepository.ensureBuiltInGroups()
            snippetRepository.seedDefaultsIfEmpty()
        }
    }
}

class PocketSshApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
