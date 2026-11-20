package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.SessionStatus
import com.example.profiles.ServersViewModel
import com.example.security.BiometricLockManager
import com.example.settings.AppSettings
import com.example.settings.SettingsViewModel
import com.example.sftp.SftpViewModel
import com.example.terminal.TerminalViewModel
import com.example.ui.components.AppLockOverlay
import com.example.ui.components.CredentialPromptDialog
import com.example.ui.components.HostKeyMismatchDialog
import com.example.ui.components.UnknownHostKeyDialog
import com.example.ui.screens.ProfileEditorDialog
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SftpScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.PocketSshTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object ServersRoute
@Serializable data object TerminalRoute
@Serializable data object FilesRoute
@Serializable data object SettingsRoute

private data class TopDestination(
    val route: Any,
    val routeQualifiedName: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PocketSshApplication).container

        setContent {
            val settings by container.preferencesRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            PocketSshTheme(
                themeMode = settings.themeMode,
                accentPreset = settings.accentPreset
            ) {
                PocketSshRoot(
                    activity = this,
                    container = container,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun PocketSshRoot(
    activity: FragmentActivity,
    container: AppContainer,
    settings: AppSettings
) {
    val colors = LocalObsidianColors.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Apply FLAG_SECURE only when explicitly opted-in by user in Settings
    LaunchedEffect(settings.protectScreenshots) {
        if (settings.protectScreenshots) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var isLocked by remember { mutableStateOf(false) }
    var lockInitialized by remember { mutableStateOf(false) }
    var lastBackgroundedAt by remember { mutableLongStateOf(0L) }
    var lockError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val initial = container.preferencesRepository.settings.first()
        isLocked = initial.appLockEnabled && initial.hasPinConfigured
        lockInitialized = true
    }

    DisposableEffect(lifecycleOwner, settings.appLockEnabled, settings.hasPinConfigured, settings.autoLockTimeout) {
        val observer = LifecycleEventObserver { _, event ->
            if (!settings.appLockEnabled || !settings.hasPinConfigured) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    lastBackgroundedAt = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    val elapsedSec = if (lastBackgroundedAt > 0L) {
                        (System.currentTimeMillis() - lastBackgroundedAt) / 1000L
                    } else 0L
                    val timeoutSec = settings.autoLockTimeout.seconds
                    if (lastBackgroundedAt > 0L && timeoutSec >= 0 && elapsedSec >= timeoutSec) {
                        isLocked = true
                        container.credentialVault.clearAllEphemeralPassphrases()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serversViewModel: ServersViewModel = viewModel(
        factory = ServersViewModel.Factory(
            container.profileRepository,
            container.credentialVault,
            container.sshSessionManager,
            container.preferencesRepository,
            container.networkMonitor,
            container.knownHostRepository
        )
    )
    val terminalViewModel: TerminalViewModel = viewModel(
        factory = TerminalViewModel.Factory(
            container.sshSessionManager,
            container.snippetRepository,
            container.preferencesRepository
        )
    )
    val sftpViewModel: SftpViewModel = viewModel(
        factory = SftpViewModel.Factory(container.sshSessionManager)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.preferencesRepository,
            container.knownHostRepository,
            container.profileRepository,
            container.credentialVault,
            container.tailscaleCoordinator
        )
    )

    val serversUiState by serversViewModel.uiState.collectAsStateWithLifecycle()
    val editorDraft by serversViewModel.editorDraft.collectAsStateWithLifecycle()
    val sessions by container.sshSessionManager.sessions.collectAsStateWithLifecycle()
    val hostKeyPrompt by container.sshSessionManager.hostKeyPrompt.collectAsStateWithLifecycle()
    val hostKeyMismatch by container.sshSessionManager.hostKeyMismatchAlert.collectAsStateWithLifecycle()
    val credentialPrompt by container.sshSessionManager.credentialPrompt.collectAsStateWithLifecycle()
    val vaultCount by settingsViewModel.vaultCount.collectAsStateWithLifecycle()

    val connectedCount = remember(sessions) {
        sessions.count { it.status.isLiveConnected }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = remember {
        listOf(
            TopDestination(
                route = ServersRoute,
                routeQualifiedName = ServersRoute::class.qualifiedName!!,
                label = "Servers",
                selectedIcon = Icons.Filled.Dns,
                unselectedIcon = Icons.Outlined.Dns,
                testTag = "nav_servers"
            ),
            TopDestination(
                route = TerminalRoute,
                routeQualifiedName = TerminalRoute::class.qualifiedName!!,
                label = "Terminal",
                selectedIcon = Icons.Filled.Terminal,
                unselectedIcon = Icons.Outlined.Terminal,
                testTag = "nav_terminal"
            ),
            TopDestination(
                route = FilesRoute,
                routeQualifiedName = FilesRoute::class.qualifiedName!!,
                label = "Files",
                selectedIcon = Icons.Filled.Folder,
                unselectedIcon = Icons.Outlined.Folder,
                testTag = "nav_files"
            ),
            TopDestination(
                route = SettingsRoute,
                routeQualifiedName = SettingsRoute::class.qualifiedName!!,
                label = "Settings",
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val navigateToTop = { route: Any ->
        val targetRouteName = route::class.qualifiedName
        if (currentRoute != targetRouteName) {
            navController.navigate(route) {
                popUpTo(ServersRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    Column {
                        HorizontalDivider(color = colors.border)
                        NavigationBar(
                            containerColor = colors.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentRoute == dest.routeQualifiedName
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTop(dest.route) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.cyan,
                                        selectedTextColor = colors.cyan,
                                        indicatorColor = colors.cyan.copy(alpha = 0.14f),
                                        unselectedIconColor = colors.textSecondary,
                                        unselectedTextColor = colors.textSecondary
                                    ),
                                    icon = {
                                        val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
                                        if (dest.route == TerminalRoute && connectedCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = colors.lime, contentColor = colors.background) {
                                                        Text(connectedCount.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(iconVector, contentDescription = dest.label)
                                            }
                                        } else {
                                            Icon(iconVector, contentDescription = dest.label)
                                        }
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = colors.surface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.routeQualifiedName
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTop(dest.route) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.cyan,
                                    selectedTextColor = colors.cyan,
                                    indicatorColor = colors.cyan.copy(alpha = 0.14f),
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary
                                ),
                                icon = {
                                    val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
                                    if (dest.route == TerminalRoute && connectedCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = colors.lime, contentColor = colors.background) {
                                                    Text(connectedCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(iconVector, contentDescription = dest.label)
                                        }
                                    } else {
                                        Icon(iconVector, contentDescription = dest.label)
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = colors.border)
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ServersRoute,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable<ServersRoute> {
                            ServersScreen(
                                uiState = serversUiState,
                                viewModel = serversViewModel,
                                onNavigateToTerminal = { navigateToTop(TerminalRoute) },
                                onOpenSettings = { navigateToTop(SettingsRoute) },
                                appLockEnabled = settings.appLockEnabled && settings.hasPinConfigured,
                                vaultCount = vaultCount
                            )
                        }
                        composable<TerminalRoute> {
                            TerminalScreen(
                                viewModel = terminalViewModel,
                                onGoToServers = { navigateToTop(ServersRoute) },
                                appSettings = settings
                            )
                        }
                        composable<FilesRoute> {
                            SftpScreen(
                                viewModel = sftpViewModel,
                                onGoToServers = { navigateToTop(ServersRoute) }
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(viewModel = settingsViewModel)
                        }
                    }
                }
            }
        }

        editorDraft?.let { draft ->
            ProfileEditorDialog(
                draft = draft,
                groups = serversUiState.groups,
                globalTailscaleEnabled = settings.tailscaleAutomationEnabled,
                pendingDeleteConfirmation = serversUiState.pendingDelete,
                onUpdate = { serversViewModel.updateEditorDraft(it) },
                onCancel = { serversViewModel.closeEditor() },
                onSaveProfile = {
                    serversViewModel.saveEditorProfile(connectAfterSave = false) {}
                },
                onSaveAndConnect = {
                    serversViewModel.saveEditorProfile(connectAfterSave = true) {
                        navigateToTop(TerminalRoute)
                    }
                },
                onDeleteProfile = if (draft.existingId != null) {
                    { serversViewModel.requestDeleteEditingProfile() }
                } else null,
                onConfirmPendingDelete = { removeCreds, disconnectActive ->
                    serversViewModel.confirmPendingDelete(
                        removeAssociatedCredentials = removeCreds,
                        disconnectActiveFirst = disconnectActive
                    )
                },
                onCancelPendingDelete = { serversViewModel.cancelPendingDelete() }
            )
        }

        hostKeyPrompt?.let { pending ->
            UnknownHostKeyDialog(
                request = pending.request,
                onApprove = { container.sshSessionManager.resolveHostKeyPrompt(true) },
                onReject = { container.sshSessionManager.resolveHostKeyPrompt(false) }
            )
        }

        hostKeyMismatch?.let { mismatch ->
            HostKeyMismatchDialog(
                mismatch = mismatch,
                onDismiss = { container.sshSessionManager.dismissHostKeyMismatch() }
            )
        }

        credentialPrompt?.let { prompt ->
            CredentialPromptDialog(
                prompt = prompt,
                onDismiss = { container.sshSessionManager.dismissCredentialPrompt() },
                onSubmit = { pwd, keyPem, passphrase, saveToVault ->
                    container.sshSessionManager.dismissCredentialPrompt()
                    container.sshSessionManager.connectProfile(
                        profile = prompt.profile,
                        oneTimePassword = pwd,
                        oneTimePrivateKeyPem = keyPem,
                        oneTimePassphrase = passphrase,
                        saveEnteredCredential = saveToVault,
                        onConnectedNavigateToTerminal = { navigateToTop(TerminalRoute) }
                    )
                }
            )
        }

        if (lockInitialized && isLocked) {
            AppLockOverlay(
                biometricEnabled = settings.biometricEnabled,
                hasPinConfigured = settings.hasPinConfigured,
                errorMessage = lockError,
                onRequestBiometric = {
                    BiometricLockManager.authenticate(
                        activity = activity,
                        title = "ปลดล็อก TrueSeekrSSH",
                        subtitle = "ยืนยันตัวตนเพื่อเข้าถึงโปรไฟล์และ Keystore Vault",
                        negativeButtonText = "ใช้ PIN",
                        onSuccess = {
                            lockError = null
                            isLocked = false
                        },
                        onError = { msg -> lockError = msg }
                    )
                },
                onSubmitPin = { enteredPin ->
                    scope.launch {
                        val ok = container.preferencesRepository.verifyPin(enteredPin.toCharArray())
                        if (ok) {
                            lockError = null
                            isLocked = false
                        } else {
                            lockError = "รหัส PIN ไม่ถูกต้อง"
                        }
                    }
                }
            )
        }
    }
}
