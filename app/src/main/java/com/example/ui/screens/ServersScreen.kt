package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.Formatters
import com.example.domain.AuthMethodType
import com.example.domain.ProfileGroup
import com.example.domain.ProfileSortOrder
import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.profiles.PendingDeleteConfirmation
import com.example.profiles.ServersUiState
import com.example.profiles.ServersViewModel
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.ExportBackupPreview
import com.example.security.ImportDuplicateStrategy
import com.example.security.ImportPreviewSummary
import com.example.ssh.ManagedSessionUiState
import com.example.ui.components.BackupExportImportDialog
import com.example.ui.components.BannerTone
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.CreateEncryptedBackupDialog
import com.example.ui.components.DecryptBackupPassphraseDialog
import com.example.ui.components.ExportProfilesDialog
import com.example.ui.components.HostTrustIndicator
import com.example.ui.components.ImportErrorDialog
import com.example.ui.components.ImportFileMode
import com.example.ui.components.ImportPreviewDialog
import com.example.ui.components.ObsidianEmptyState
import com.example.ui.components.ObsidianMetadataTag
import com.example.ui.components.ObsidianSectionHeader
import com.example.ui.components.ObsidianStatusBanner
import com.example.ui.components.ObsidianSurfaceCard
import com.example.ui.components.OverviewMetricTile
import com.example.ui.components.readBackupTextFromUri
import com.example.ui.components.writeBackupTextToUri
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianRadius
import com.example.ui.theme.ObsidianSpacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServersScreen(
    uiState: ServersUiState,
    viewModel: ServersViewModel,
    onNavigateToTerminal: () -> Unit,
    onOpenSettings: () -> Unit = {},
    appLockEnabled: Boolean = false,
    vaultCount: Int = 0
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showExportProfilesDialog by remember { mutableStateOf(false) }
    var showCreateEncryptedBackupDialog by remember { mutableStateOf(false) }
    var exportTargetProfileIds by remember { mutableStateOf<Set<String>?>(null) }
    var actionSheetProfile by remember { mutableStateOf<SshProfile?>(null) }
    var profileForDetails by remember { mutableStateOf<SshProfile?>(null) }
    var profileToDuplicate by remember { mutableStateOf<SshProfile?>(null) }
    var profileToRename by remember { mutableStateOf<SshProfile?>(null) }
    var profileToMoveGroup by remember { mutableStateOf<SshProfile?>(null) }
    var pendingExportPayload by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingImportMode by remember { mutableStateOf(ImportFileMode.ANY_SUPPORTED) }
    var importedRawText by remember { mutableStateOf<String?>(null) }

    val pendingImportReview by viewModel.pendingImportReview.collectAsState()
    var importParseError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val pending = pendingExportPayload
        pendingExportPayload = null
        if (uri != null && pending != null) {
            val (payload, isEncrypted) = pending
            writeBackupTextToUri(context, uri, payload)
                .onSuccess {
                    viewModel.postMessageBanner(
                        if (isEncrypted) {
                            "บันทึกไฟล์สำรองเข้ารหัส (.tsshbackup) เรียบร้อยแล้ว"
                        } else {
                            "ส่งออกโปรไฟล์ (JSON) เรียบร้อยแล้ว"
                        }
                    )
                }
                .onFailure { err ->
                    viewModel.postErrorBanner(err.message ?: "ไม่สามารถบันทึกไฟล์สำรองข้อมูลได้")
                }
        }
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importParseError = null
            readBackupTextFromUri(context, uri)
                .onSuccess { text ->
                    val isEncrypted = BackupCryptoManager.isEncryptedBackup(text)
                    if (pendingImportMode == ImportFileMode.ENCRYPTED_ONLY && !isEncrypted) {
                        importParseError = "ไฟล์ที่เลือกไม่ใช่ไฟล์สำรองแบบเข้ารหัส (.tsshbackup) กรุณาใช้เมนู Import Profiles สำหรับไฟล์ JSON"
                    } else if (isEncrypted) {
                        importedRawText = text
                    } else {
                        val res = viewModel.prepareImportPreview(text, null, requireEncrypted = false)
                        if (res is BackupImportResult.Error) {
                            importParseError = res.message
                        }
                    }
                }
                .onFailure { err ->
                    importParseError = err.message ?: "ไม่สามารถเปิดไฟล์สำรองข้อมูลได้"
                }
        }
    }

    val connectedCount = remember(uiState.activeSessions) {
        uiState.activeSessions.count { it.status.isLiveConnected }
    }
    val attentionCount = remember(uiState.activeSessions) {
        uiState.activeSessions.count {
            it.status == SessionStatus.ERROR ||
                it.status == SessionStatus.CONNECTION_LOST ||
                it.status == SessionStatus.CHECKING_CONNECTION ||
                !it.networkWarning.isNullOrBlank()
        }
    }

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openCreateEditor() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("เพิ่มเซิร์ฟเวอร์", fontWeight = FontWeight.SemiBold) },
                    containerColor = colors.cyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(ObsidianRadius.lg),
                    modifier = Modifier.testTag("add_profile_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isSelectionMode) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    BulkSelectionActionBar(
                        selectedCount = uiState.selectedProfileIds.size,
                        totalVisibleCount = uiState.filteredProfiles.size,
                        onSelectAll = { viewModel.selectAllVisible() },
                        onDeselectAll = { viewModel.deselectAll() },
                        onExportSelected = {
                            exportTargetProfileIds = uiState.selectedProfileIds
                            showBackupDialog = true
                        },
                        onDeleteSelected = { viewModel.requestBulkDelete() },
                        onCancelSelection = { viewModel.exitSelectionMode() }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("servers_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // 1. Command Center Hero Header
            item {
                CommandCenterHeader(
                    uiState = uiState,
                    appLockEnabled = appLockEnabled,
                    vaultCount = vaultCount,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortExpandedChange = { sortMenuExpanded = it },
                    onSelectSortOrder = { viewModel.setSortOrder(it) },
                    overflowMenuExpanded = overflowMenuExpanded,
                    onOverflowExpandedChange = { overflowMenuExpanded = it },
                    onOpenGroups = { showGroupDialog = true },
                    onOpenBackup = {
                        exportTargetProfileIds = null
                        showBackupDialog = true
                    },
                    onImportProfiles = {
                        pendingImportMode = ImportFileMode.ANY_SUPPORTED
                        importSafLauncher.launch(arrayOf("*/*"))
                    },
                    onExportProfiles = {
                        exportTargetProfileIds = null
                        showExportProfilesDialog = true
                    },
                    onCreateEncryptedBackup = {
                        exportTargetProfileIds = null
                        showCreateEncryptedBackupDialog = true
                    },
                    onRestoreEncryptedBackup = {
                        pendingImportMode = ImportFileMode.ENCRYPTED_ONLY
                        importSafLauncher.launch(arrayOf("*/*"))
                    },
                    onEnterSelectionMode = { viewModel.enterSelectionMode() },
                    onOpenSettings = onOpenSettings
                )
            }

            // 2. Real-state KPI Overview Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OverviewMetricTile(
                        value = connectedCount.toString(),
                        label = "Connected",
                        icon = Icons.Default.Bolt,
                        accentColor = colors.lime,
                        modifier = Modifier.weight(1f)
                    )
                    OverviewMetricTile(
                        value = uiState.allProfiles.size.toString(),
                        label = "Saved Profiles",
                        icon = Icons.Default.Dns,
                        accentColor = colors.cyan,
                        modifier = Modifier.weight(1f)
                    )
                    OverviewMetricTile(
                        value = attentionCount.toString(),
                        label = "Attention",
                        icon = Icons.Default.Warning,
                        accentColor = if (attentionCount > 0) colors.red else colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!uiState.errorBanner.isNullOrBlank()) {
                item {
                    ObsidianStatusBanner(
                        message = uiState.errorBanner,
                        tone = BannerTone.ERROR,
                        onDismiss = { viewModel.clearErrorBanner() }
                    )
                }
            }

            uiState.undoDeleteState?.let { undoState ->
                item {
                    ObsidianStatusBanner(
                        message = undoState.message,
                        tone = BannerTone.WARNING,
                        actionLabel = "เลิกทำ (Undo)",
                        actionTestTag = "undo_delete_button",
                        onAction = { viewModel.undoLastDelete() },
                        onDismiss = { viewModel.dismissUndo() }
                    )
                }
            }

            if (!uiState.messageBanner.isNullOrBlank()) {
                item {
                    ObsidianStatusBanner(
                        message = uiState.messageBanner,
                        tone = BannerTone.INFO,
                        onDismiss = { viewModel.clearBanner() }
                    )
                }
            }

            // 3. Active SSH Sessions Section (Real runtime sessions only)
            if (uiState.activeSessions.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.selectedGroup == null) {
                item {
                    ObsidianSectionHeader(
                        title = "Active Sessions",
                        icon = Icons.Default.Terminal,
                        count = uiState.activeSessions.size,
                        accentColor = colors.lime
                    )
                }
                items(uiState.activeSessions, key = { "active_${it.sessionId}" }) { session ->
                    val hostTrusted = session.meta.hostFingerprint.isNotBlank() ||
                        uiState.trustedHostKeys.containsKey("${session.profile.host.trim().lowercase()}:${session.profile.port}")
                    ActiveSessionCard(
                        session = session,
                        isHostTrusted = hostTrusted,
                        onOpenTerminal = { viewModel.selectActiveSession(session.sessionId, onNavigateToTerminal) },
                        onReconnect = { viewModel.reconnectActiveSession(session.sessionId, onNavigateToTerminal) }
                    )
                }
            }

            // 4. Favorite Servers
            if (uiState.favoriteProfiles.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.selectedGroup == null) {
                item {
                    ObsidianSectionHeader(
                        title = "Favorite Servers",
                        icon = Icons.Default.Star,
                        count = uiState.favoriteProfiles.size,
                        accentColor = colors.lime
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.favoriteProfiles, key = { "fav_${it.id}" }) { fav ->
                            val session = uiState.activeSessions.firstOrNull { it.profile.id == fav.id }
                            FavoriteServerCard(
                                profile = fav,
                                sessionStatus = session?.status,
                                isTrusted = uiState.trustedHostKeys.containsKey("${fav.host.trim().lowercase()}:${fav.port}"),
                                onConnect = { viewModel.connectSavedProfile(fav, onNavigateToTerminal) },
                                onMoreActions = { actionSheetProfile = fav }
                            )
                        }
                    }
                }
            }

            // 5. Recently Connected Servers
            if (uiState.recentEntries.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.selectedGroup == null) {
                item {
                    ObsidianSectionHeader(
                        title = "Recently Connected",
                        icon = Icons.Default.History,
                        count = uiState.recentEntries.size,
                        accentColor = colors.cyan,
                        trailingAction = {
                            TextButton(
                                onClick = { viewModel.clearRecentHistory() },
                                modifier = Modifier.testTag("clear_recent_button")
                            ) {
                                Text("ล้างประวัติ")
                            }
                        }
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.recentEntries, key = { "rec_${it.first.id}" }) { (profile, rec) ->
                            ObsidianSurfaceCard(
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                elevated = true,
                                modifier = Modifier.width(248.dp),
                                onClick = { viewModel.connectSavedProfile(profile, onNavigateToTerminal) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Reconnect",
                                            tint = colors.cyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "${profile.username}@${profile.host}:${profile.port}",
                                        style = MonoCodeStyle,
                                        color = colors.cyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${Formatters.formatTimestamp(rec.lastConnectedAt)} • ${rec.successCount} ครั้ง",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Search Bar & Group Chips + All Servers
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ObsidianSectionHeader(
                        title = "All Servers",
                        icon = Icons.Default.Dns,
                        count = uiState.filteredProfiles.size,
                        accentColor = colors.cyan,
                        trailingAction = {
                            if (uiState.allProfiles.isNotEmpty() && !uiState.isSelectionMode) {
                                TextButton(
                                    onClick = { viewModel.enterSelectionMode() },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("toggle_selection_mode_button")
                                ) {
                                    Icon(
                                        Icons.Default.Checklist,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("เลือกหลายรายการ")
                                }
                            }
                        }
                    )
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("ค้นหาชื่อ, Host, IP, Username, Tag...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "ล้างคำค้นหา")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(ObsidianRadius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_search_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedGroup == null,
                                onClick = { viewModel.selectGroupFilter(null) },
                                label = { Text("ทั้งหมด (${uiState.allProfiles.size})") },
                                modifier = Modifier.testTag("group_chip_all")
                            )
                        }
                        items(uiState.groups, key = { it.name }) { group ->
                            FilterChip(
                                selected = uiState.selectedGroup == group.name,
                                onClick = {
                                    val next = if (uiState.selectedGroup == group.name) null else group.name
                                    viewModel.selectGroupFilter(next)
                                },
                                label = { Text(group.name) },
                                modifier = Modifier.testTag("group_chip_${group.name}")
                            )
                        }
                    }
                }
            }

            if (uiState.filteredProfiles.isEmpty()) {
                item {
                    ObsidianEmptyState(
                        icon = Icons.Default.Dns,
                        title = if (uiState.allProfiles.isEmpty()) {
                            "ยังไม่มีโปรไฟล์เซิร์ฟเวอร์"
                        } else {
                            "ไม่พบโปรไฟล์ที่ตรงกับคำค้นหา"
                        },
                        subtitle = "บันทึกโปรไฟล์ SSH (LAN, VPS หรือ Tailscale MagicDNS) เพื่อเชื่อมต่ออย่างปลอดภัยในปุ่มเดียว",
                        actionLabel = "สร้างโปรไฟล์แรก",
                        onAction = { viewModel.openCreateEditor() }
                    )
                }
            } else {
                items(uiState.filteredProfiles, key = { it.id }) { profile ->
                    val matchingSession = uiState.activeSessions.firstOrNull { it.profile.id == profile.id }
                    val isTrusted = uiState.trustedHostKeys.containsKey(
                        "${profile.host.trim().lowercase()}:${profile.port}"
                    )
                    ProfileCard(
                        profile = profile,
                        sessionStatus = matchingSession?.status,
                        isHostTrusted = isTrusted,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = profile.id in uiState.selectedProfileIds,
                        onToggleSelected = { viewModel.toggleProfileSelection(profile.id) },
                        onEnterSelectionMode = { viewModel.enterSelectionMode(profile.id) },
                        onConnect = { viewModel.connectSavedProfile(profile, onNavigateToTerminal) },
                        onToggleFavorite = { viewModel.toggleFavorite(profile) },
                        onOpenActions = { actionSheetProfile = profile },
                        onOpenDetails = { profileForDetails = profile }
                    )
                }
            }
        }
        }
    }

    // Bottom sheet for clean accessible Profile Actions
    actionSheetProfile?.let { profile ->
        ModalBottomSheet(
            onDismissRequest = { actionSheetProfile = null },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    Text(
                        text = "${profile.username}@${profile.host}:${profile.port}",
                        style = MonoCodeStyle,
                        color = colors.cyan
                    )
                }
                HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 6.dp))
                SheetActionRow(
                    icon = Icons.Default.PlayArrow,
                    tint = colors.cyan,
                    label = "เชื่อมต่อ (Connect)",
                    onClick = {
                        actionSheetProfile = null
                        viewModel.connectSavedProfile(profile, onNavigateToTerminal)
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.Edit,
                    tint = colors.textPrimary,
                    label = "แก้ไขโปรไฟล์",
                    onClick = {
                        actionSheetProfile = null
                        viewModel.openEditEditor(profile)
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.Info,
                    tint = colors.textPrimary,
                    label = "ดูรายละเอียดและสถิติ",
                    onClick = {
                        actionSheetProfile = null
                        profileForDetails = profile
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.ContentCopy,
                    tint = colors.textPrimary,
                    label = "คัดลอกโปรไฟล์ (Duplicate)",
                    testTag = "sheet_duplicate_profile_button",
                    onClick = {
                        actionSheetProfile = null
                        if (profile.credentialRefId != null) {
                            profileToDuplicate = profile
                        } else {
                            viewModel.duplicateProfile(profile, includeCredential = false)
                        }
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.Edit,
                    tint = colors.textPrimary,
                    label = "เปลี่ยนชื่อโปรไฟล์",
                    onClick = {
                        actionSheetProfile = null
                        profileToRename = profile
                    }
                )
                SheetActionRow(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    tint = colors.textPrimary,
                    label = "ย้ายกลุ่ม (Move Group)",
                    onClick = {
                        actionSheetProfile = null
                        profileToMoveGroup = profile
                    }
                )
                if (profile.credentialRefId != null) {
                    SheetActionRow(
                        icon = Icons.Default.Key,
                        tint = colors.amber,
                        label = "ลบ Credential ที่เก็บใน Vault",
                        onClick = {
                            actionSheetProfile = null
                            viewModel.removeStoredCredentialOnly(profile)
                        }
                    )
                }
                SheetActionRow(
                    icon = Icons.Default.Delete,
                    tint = colors.red,
                    label = "ลบโปรไฟล์",
                    testTag = "sheet_delete_profile_button",
                    onClick = {
                        actionSheetProfile = null
                        viewModel.requestDeleteProfile(profile)
                    }
                )
            }
        }
    }

    // Profile details dialog
    profileForDetails?.let { profile ->
        val trustedFingerprint = uiState.trustedHostKeys["${profile.host.trim().lowercase()}:${profile.port}"]
        AlertDialog(
            onDismissRequest = { profileForDetails = null },
            containerColor = colors.surface,
            title = { Text(profile.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Endpoint: ${profile.username}@${profile.host}:${profile.port}", style = MonoCodeStyle, color = colors.cyan)
                    Text("Group: ${profile.groupName}")
                    Text("Auth: ${profile.authMethod.label}")
                    Text("Terminal: ${profile.terminalType} (${profile.terminalTheme.displayName})")
                    Text("Timeout: ${profile.connectTimeoutSec}s • Keepalive: ${profile.keepAliveIntervalSec}s")
                    Text(
                        text = if (trustedFingerprint != null) "Host Trust: $trustedFingerprint" else "Host Trust: ยังไม่เคยบันทึก Fingerprint",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (trustedFingerprint != null) colors.lime else colors.amber
                    )
                    if (profile.initialWorkingDir.isNotBlank()) {
                        Text("Working Dir: ${profile.initialWorkingDir}", style = MonoCodeStyle)
                    }
                    if (profile.tags.isNotEmpty()) {
                        Text("Tags: ${profile.tags.joinToString(", ")}")
                    }
                    if (profile.description.isNotBlank()) {
                        Text("หมายเหตุ: ${profile.description}")
                    }
                    Text("เชื่อมต่อสำเร็จล่าสุด: ${Formatters.formatTimestamp(profile.lastConnectedAt)}")
                    Text("จำนวนครั้งที่เชื่อมต่อสำเร็จ: ${profile.connectionCount}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = profile
                    profileForDetails = null
                    viewModel.connectSavedProfile(target, onNavigateToTerminal)
                }) {
                    Text("Connect")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val target = profile
                            profileForDetails = null
                            viewModel.requestDeleteProfile(target)
                        },
                        modifier = Modifier.testTag("details_delete_profile_button")
                    ) {
                        Text("ลบ", color = colors.red)
                    }
                    TextButton(onClick = {
                        val target = profile
                        profileForDetails = null
                        viewModel.openEditEditor(target)
                    }) {
                        Text("แก้ไข")
                    }
                }
            }
        )
    }

    profileToDuplicate?.let { sourceProfile ->
        DuplicateProfileDialog(
            profile = sourceProfile,
            onConfirm = { includeCred ->
                viewModel.duplicateProfile(sourceProfile, includeCredential = includeCred)
                profileToDuplicate = null
            },
            onDismiss = { profileToDuplicate = null }
        )
    }

    uiState.pendingDelete?.takeIf { !it.fromEditor }?.let { pending ->
        ProfileDeleteConfirmationDialog(
            pending = pending,
            onConfirmDelete = { removeCreds, disconnectActive ->
                viewModel.confirmPendingDelete(
                    removeAssociatedCredentials = removeCreds,
                    disconnectActiveFirst = disconnectActive
                )
            },
            onCancel = { viewModel.cancelPendingDelete() }
        )
    }

    // Rename profile dialog
    profileToRename?.let { profile ->
        var newName by remember(profile) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { profileToRename = null },
            containerColor = colors.surface,
            title = { Text("เปลี่ยนชื่อโปรไฟล์") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("ชื่อใหม่") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProfile(profile, newName)
                        profileToRename = null
                    },
                    enabled = newName.isNotBlank()
                ) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(onClick = { profileToRename = null }) { Text("ยกเลิก") }
            }
        )
    }

    // Move to group dialog
    profileToMoveGroup?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToMoveGroup = null },
            containerColor = colors.surface,
            title = { Text("ย้ายไปกลุ่ม") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.groups.forEach { group ->
                        FilterChip(
                            selected = profile.groupName == group.name,
                            onClick = {
                                viewModel.moveToGroup(profile, group.name)
                                profileToMoveGroup = null
                            },
                            label = { Text(group.name) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { profileToMoveGroup = null }) { Text("ปิด") }
            }
        )
    }

    if (showGroupDialog) {
        GroupManagerDialog(
            groups = uiState.groups,
            onCreateGroup = { viewModel.createGroup(it) },
            onRenameGroup = { old, new -> viewModel.renameGroup(old, new) },
            onDeleteGroup = { viewModel.deleteGroup(it) },
            onDismiss = { showGroupDialog = false }
        )
    }

    if (showBackupDialog) {
        val scopedIds = exportTargetProfileIds
        val exportPreview = remember(uiState.allProfiles, scopedIds) {
            viewModel.getExportPreview(scopedIds)
        }
        BackupExportImportDialog(
            exportPreview = exportPreview,
            isSelectedSubset = scopedIds != null,
            onExportMetadata = {
                viewModel.buildExportContent(null, scopedIds)
                    .onSuccess { jsonText ->
                        pendingExportPayload = jsonText to false
                        exportSafLauncher.launch(BackupCryptoManager.STANDARD_EXPORT_FILENAME)
                        showBackupDialog = false
                        exportTargetProfileIds = null
                    }
                    .onFailure { err ->
                        showBackupDialog = false
                        exportTargetProfileIds = null
                        viewModel.postErrorBanner(err.message ?: "ส่งออกโปรไฟล์ไม่สำเร็จ")
                    }
            },
            onExportEncrypted = { pass, includeCreds ->
                viewModel.buildEncryptedFullBackup(pass, includeCreds, scopedIds)
                    .onSuccess { encryptedText ->
                        pendingExportPayload = encryptedText to true
                        exportSafLauncher.launch(BackupCryptoManager.ENCRYPTED_BACKUP_FILENAME)
                        showBackupDialog = false
                        exportTargetProfileIds = null
                    }
                    .onFailure { err ->
                        showBackupDialog = false
                        exportTargetProfileIds = null
                        viewModel.postErrorBanner(err.message ?: "สร้างไฟล์สำรองเข้ารหัสไม่สำเร็จ")
                    }
            },
            onChooseImportFile = {
                showBackupDialog = false
                exportTargetProfileIds = null
                pendingImportMode = ImportFileMode.ANY_SUPPORTED
                importSafLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = {
                showBackupDialog = false
                exportTargetProfileIds = null
            }
        )
    }

    if (showExportProfilesDialog) {
        val exportPreview = remember(uiState.allProfiles) {
            viewModel.getExportPreview(null)
        }
        ExportProfilesDialog(
            exportPreview = exportPreview,
            onConfirmExportJson = {
                viewModel.buildExportContent(null, null)
                    .onSuccess { jsonText ->
                        pendingExportPayload = jsonText to false
                        showExportProfilesDialog = false
                        exportSafLauncher.launch(BackupCryptoManager.STANDARD_EXPORT_FILENAME)
                    }
                    .onFailure { err ->
                        showExportProfilesDialog = false
                        viewModel.postErrorBanner(err.message ?: "ส่งออกโปรไฟล์ไม่สำเร็จ")
                    }
            },
            onDismiss = { showExportProfilesDialog = false }
        )
    }

    if (showCreateEncryptedBackupDialog) {
        val exportPreview = remember(uiState.allProfiles) {
            viewModel.getExportPreview(null)
        }
        CreateEncryptedBackupDialog(
            exportPreview = exportPreview,
            onCreateBackup = { passphrase, includeCreds ->
                viewModel.buildEncryptedFullBackup(passphrase, includeCreds, null)
                    .map { encryptedText ->
                        pendingExportPayload = encryptedText to true
                        showCreateEncryptedBackupDialog = false
                        exportSafLauncher.launch(BackupCryptoManager.ENCRYPTED_BACKUP_FILENAME)
                    }
            },
            onDismiss = { showCreateEncryptedBackupDialog = false }
        )
    }

    importedRawText?.let { raw ->
        DecryptBackupPassphraseDialog(
            onDecrypt = { passphrase ->
                when (
                    val res = viewModel.prepareImportPreview(
                        rawText = raw,
                        passphrase = passphrase,
                        requireEncrypted = pendingImportMode == ImportFileMode.ENCRYPTED_ONLY
                    )
                ) {
                    is BackupImportResult.Success -> {
                        importedRawText = null
                        null
                    }
                    is BackupImportResult.Error -> res.message
                }
            },
            onDismiss = { importedRawText = null }
        )
    }

    pendingImportReview?.let { review ->
        ImportPreviewDialog(
            preview = review.preview,
            onDismiss = { viewModel.dismissImportPreview() },
            onConfirmImport = { strategy, onError ->
                viewModel.confirmImportWithStrategy(strategy) { msg ->
                    if (!msg.contains("สำเร็จ")) {
                        onError(msg)
                    }
                }
            }
        )
    }

    importParseError?.let { errorMsg ->
        ImportErrorDialog(
            errorMessage = errorMsg,
            onDismiss = { importParseError = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandCenterHeader(
    uiState: ServersUiState,
    appLockEnabled: Boolean,
    vaultCount: Int,
    sortMenuExpanded: Boolean,
    onSortExpandedChange: (Boolean) -> Unit,
    onSelectSortOrder: (ProfileSortOrder) -> Unit,
    overflowMenuExpanded: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenBackup: () -> Unit,
    onImportProfiles: () -> Unit,
    onExportProfiles: () -> Unit,
    onCreateEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalObsidianColors.current
    val shape = RoundedCornerShape(ObsidianRadius.xl)

    Surface(
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.auroraHeaderBrush(colors.cyan), shape)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TrueSeekrSSH",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "SSH & Server Command Center",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenBackup,
                        modifier = Modifier.size(48.dp).testTag("import_export_button")
                    ) {
                        Icon(
                            Icons.Default.ImportExport,
                            contentDescription = "ศูนย์สำรองและกู้คืนโปรไฟล์",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { onSortExpandedChange(true) },
                            modifier = Modifier.size(48.dp).testTag("sort_profiles_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "เรียงลำดับ",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { onSortExpandedChange(false) }
                        ) {
                            ProfileSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (uiState.sortOrder == order) "✓ ${order.label}" else order.label
                                        )
                                    },
                                    onClick = {
                                        onSelectSortOrder(order)
                                        onSortExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp).testTag("command_center_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "ตั้งค่าแอป",
                            tint = colors.cyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { onOverflowExpandedChange(true) },
                            modifier = Modifier.size(48.dp).testTag("servers_overflow_menu_button")
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "เมนูจัดการและสำรองโปรไฟล์",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { onOverflowExpandedChange(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Profiles") },
                                leadingIcon = {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = colors.cyan)
                                },
                                modifier = Modifier.testTag("overflow_import_profiles_item"),
                                onClick = {
                                    onOverflowExpandedChange(false)
                                    onImportProfiles()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Profiles") },
                                leadingIcon = {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = colors.cyan)
                                },
                                modifier = Modifier.testTag("overflow_export_profiles_item"),
                                onClick = {
                                    onOverflowExpandedChange(false)
                                    onExportProfiles()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Encrypted Backup") },
                                leadingIcon = {
                                    Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = colors.lime)
                                },
                                modifier = Modifier.testTag("overflow_create_encrypted_backup_item"),
                                onClick = {
                                    onOverflowExpandedChange(false)
                                    onCreateEncryptedBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore Encrypted Backup") },
                                leadingIcon = {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = colors.amber)
                                },
                                modifier = Modifier.testTag("overflow_restore_encrypted_backup_item"),
                                onClick = {
                                    onOverflowExpandedChange(false)
                                    onRestoreEncryptedBackup()
                                }
                            )
                            HorizontalDivider(color = colors.subtleBorder)
                            DropdownMenuItem(
                                text = { Text("จัดการกลุ่มโปรไฟล์") },
                                leadingIcon = {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = colors.textSecondary)
                                },
                                modifier = Modifier.testTag("manage_groups_button"),
                                onClick = {
                                    onOverflowExpandedChange(false)
                                    onOpenGroups()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("เลือกหลายรายการ (Bulk)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Checklist, contentDescription = null, tint = colors.textSecondary)
                                },
                                modifier = Modifier.testTag("overflow_enter_selection_mode_item"),
                                onClick = {
                                    onOverflowExpandedChange(false)
                                    onEnterSelectionMode()
                                }
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ObsidianMetadataTag(
                    text = uiState.networkStatus.transportType.label,
                    icon = if (uiState.networkStatus.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    tint = if (uiState.networkStatus.isConnected) colors.lime else colors.red
                )
                ObsidianMetadataTag(
                    text = if (appLockEnabled) "App Lock Active" else "Keystore Vault ($vaultCount)",
                    icon = if (appLockEnabled) Icons.Default.Lock else Icons.Default.Shield,
                    tint = if (appLockEnabled) colors.lime else colors.cyan
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionCard(
    session: ManagedSessionUiState,
    isHostTrusted: Boolean,
    onOpenTerminal: () -> Unit,
    onReconnect: () -> Unit
) {
    val colors = LocalObsidianColors.current
    val isConnected = session.status.isLiveConnected
    var nowMs by remember(isConnected, session.connectedAtMs) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(isConnected, session.connectedAtMs) {
        while (isConnected && session.connectedAtMs != null) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val accent = when {
        session.status == SessionStatus.CONNECTED || session.status == SessionStatus.CONNECTION_IDLE -> colors.lime.copy(alpha = 0.45f)
        session.status == SessionStatus.CHECKING_CONNECTION -> colors.amber.copy(alpha = 0.45f)
        session.status == SessionStatus.ERROR || session.status == SessionStatus.CONNECTION_LOST -> colors.red.copy(alpha = 0.45f)
        else -> colors.cyan.copy(alpha = 0.35f)
    }

    ObsidianSurfaceCard(
        elevated = true,
        accentBorder = accent,
        onClick = onOpenTerminal
    ) {
        Column(
            modifier = Modifier.padding(ObsidianSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = session.profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.profile.username}@${session.profile.host}:${session.profile.port}",
                        style = MonoCodeStyle,
                        color = colors.cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ConnectionStatusPill(status = session.status, compact = true)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isConnected && session.connectedAtMs != null) {
                    ObsidianMetadataTag(
                        text = "Uptime ${Formatters.formatDuration(nowMs - session.connectedAtMs)}",
                        icon = Icons.Default.Timer,
                        tint = colors.lime,
                        monospace = true
                    )
                }
                if (session.latencyMs != null) {
                    ObsidianMetadataTag(
                        text = "${session.latencyMs} ms",
                        icon = Icons.Default.Speed,
                        tint = colors.cyan,
                        monospace = true
                    )
                }
                if (session.routeLabel.isNotBlank()) {
                    ObsidianMetadataTag(
                        text = session.routeLabel,
                        icon = Icons.Default.Hub,
                        tint = colors.textSecondary
                    )
                }
                HostTrustIndicator(isTrusted = isHostTrusted)
            }

            if (!session.errorMessage.isNullOrBlank()) {
                Text(
                    text = session.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.status == SessionStatus.DISCONNECTED ||
                    session.status == SessionStatus.CONNECTION_LOST ||
                    session.status == SessionStatus.ERROR
                ) {
                    OutlinedButton(
                        onClick = onReconnect,
                        shape = RoundedCornerShape(ObsidianRadius.md),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reconnect")
                    }
                }
                Button(
                    onClick = onOpenTerminal,
                    shape = RoundedCornerShape(ObsidianRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) colors.lime else colors.cyan,
                        contentColor = if (isConnected) Color(0xFF162903) else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_terminal_${session.sessionId}")
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("เปิด Terminal", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FavoriteServerCard(
    profile: SshProfile,
    sessionStatus: SessionStatus?,
    isTrusted: Boolean,
    onConnect: () -> Unit,
    onMoreActions: () -> Unit
) {
    val colors = LocalObsidianColors.current
    ObsidianSurfaceCard(
        elevated = true,
        shape = RoundedCornerShape(ObsidianRadius.lg),
        modifier = Modifier.width(232.dp),
        onClick = onConnect
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onMoreActions,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "ตัวเลือกโปรไฟล์",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = "${profile.username}@${profile.host}:${profile.port}",
                style = MonoCodeStyle,
                color = colors.cyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sessionStatus != null && sessionStatus != SessionStatus.IDLE) {
                ConnectionStatusPill(status = sessionStatus, compact = true)
            } else {
                HostTrustIndicator(isTrusted = isTrusted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    profile: SshProfile,
    sessionStatus: SessionStatus?,
    isHostTrusted: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onEnterSelectionMode: () -> Unit = {},
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val colors = LocalObsidianColors.current
    val accentBorder = when {
        isSelectionMode && isSelected -> colors.cyan
        sessionStatus?.isLiveConnected == true -> colors.lime.copy(alpha = 0.45f)
        else -> null
    }

    ObsidianSurfaceCard(
        elevated = true,
        accentBorder = accentBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        onClick = if (isSelectionMode) onToggleSelected else onOpenDetails,
        onLongClick = if (!isSelectionMode) onEnterSelectionMode else null
    ) {
        Column(
            modifier = Modifier.padding(ObsidianSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelected() },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("select_profile_checkbox_${profile.id}")
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.username}@${profile.host}:${profile.port}",
                        style = MonoCodeStyle.copy(fontWeight = FontWeight.Medium),
                        color = colors.cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("favorite_toggle_${profile.id}")
                        ) {
                            Icon(
                                imageVector = if (profile.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "รายการโปรด",
                                tint = if (profile.isFavorite) colors.lime else colors.textSecondary
                            )
                        }
                        IconButton(
                            onClick = onOpenActions,
                            modifier = Modifier.testTag("profile_more_${profile.id}")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "เมนูเพิ่มเติม", tint = colors.textSecondary)
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sessionStatus != null && sessionStatus != SessionStatus.IDLE) {
                    ConnectionStatusPill(status = sessionStatus, compact = true)
                }
                ObsidianMetadataTag(text = profile.groupName)
                ObsidianMetadataTag(
                    text = when (profile.authMethod) {
                        AuthMethodType.PASSWORD -> "Password"
                        AuthMethodType.PRIVATE_KEY -> "Private Key"
                        AuthMethodType.PRIVATE_KEY_PASSPHRASE -> "Key + Passphrase"
                    },
                    icon = Icons.Default.Key
                )
                if (profile.autoTailscaleConnect) {
                    ObsidianMetadataTag(
                        text = "Tailscale",
                        icon = Icons.Default.Hub,
                        tint = colors.cyan
                    )
                }
                HostTrustIndicator(isTrusted = isHostTrusted)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ล่าสุด: ${Formatters.formatTimestamp(profile.lastConnectedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (!isSelectionMode) {
                    Button(
                        onClick = onConnect,
                        shape = RoundedCornerShape(ObsidianRadius.md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.cyan,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("connect_profile_${profile.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalObsidianColors.current
    ListItem(
        headlineContent = {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = tint)
        },
        colors = ListItemDefaults.colors(containerColor = colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkSelectionActionBar(
    selectedCount: Int,
    totalVisibleCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExportSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit
) {
    val colors = LocalObsidianColors.current
    ObsidianSurfaceCard(
        elevated = true,
        accentBorder = colors.cyan.copy(alpha = 0.5f),
        shape = RoundedCornerShape(ObsidianRadius.lg),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bulk_selection_bar")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "เลือกแล้ว $selectedCount รายการ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.cyan,
                    modifier = Modifier.testTag("bulk_selection_count")
                )
                TextButton(
                    onClick = onCancelSelection,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bulk_cancel_selection_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ยกเลิก")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = totalVisibleCount > 0,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bulk_select_all_button")
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("เลือกทั้งหมด")
                }
                OutlinedButton(
                    onClick = onDeselectAll,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bulk_deselect_all_button")
                ) {
                    Icon(Icons.Default.Deselect, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ล้างที่เลือก")
                }
                OutlinedButton(
                    onClick = onExportSelected,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bulk_export_selected_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ส่งออกที่เลือก")
                }
                Button(
                    onClick = onDeleteSelected,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bulk_delete_selected_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ลบที่เลือก ($selectedCount)")
                }
            }
        }
    }
}

@Composable
private fun DuplicateProfileDialog(
    profile: SshProfile,
    onConfirm: (includeCredential: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var includeCredential by remember(profile.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("คัดลอกโปรไฟล์ \"${profile.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ระบบจะสร้างโปรไฟล์ใหม่ด้วย ID ที่ไม่ซ้ำกัน โดยค่าเริ่มต้นจะไม่คัดลอกรหัสผ่าน/Private Key เพื่อความปลอดภัย",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeCredential = !includeCredential }
                ) {
                    Checkbox(
                        checked = includeCredential,
                        onCheckedChange = { includeCredential = it },
                        modifier = Modifier.testTag("duplicate_include_credential_checkbox")
                    )
                    Text(
                        text = "คัดลอก Credential ที่เก็บไว้แยกชุดใหม่ไปด้วย",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(includeCredential) },
                modifier = Modifier.testTag("confirm_duplicate_profile_button")
            ) {
                Text("คัดลอก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก") }
        }
    )
}

@Composable
fun ProfileDeleteConfirmationDialog(
    pending: PendingDeleteConfirmation,
    onConfirmDelete: (removeAssociatedCredentials: Boolean, disconnectActiveFirst: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var removeCredentials by remember(pending) { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(ObsidianRadius.xl),
            border = BorderStroke(1.dp, if (pending.hasActiveSessions) colors.red else colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (pending.hasActiveSessions) Icons.Default.Warning else Icons.Default.Delete,
                        contentDescription = null,
                        tint = colors.red
                    )
                    Text(
                        text = if (pending.isBulk) {
                            "ลบโปรไฟล์ที่เลือก ${pending.profiles.size} รายการ?"
                        } else {
                            "ลบโปรไฟล์ \"${pending.profiles.first().name}\"?"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = colors.elevated,
                        shape = RoundedCornerShape(ObsidianRadius.md),
                        border = BorderStroke(1.dp, colors.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (pending.isBulk) {
                                Text(
                                    text = "รายการที่เลือกทั้งหมด ${pending.profiles.size} โปรไฟล์:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                pending.profiles.take(6).forEach { p ->
                                    Text(
                                        text = "• ${p.name} (${p.username}@${p.host}:${p.port})",
                                        style = MonoCodeStyle,
                                        color = colors.cyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (pending.profiles.size > 6) {
                                    Text(
                                        text = "...และอีก ${pending.profiles.size - 6} รายการ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                }
                            } else {
                                val p = pending.profiles.first()
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${p.username}@${p.host}:${p.port}",
                                    style = MonoCodeStyle,
                                    color = colors.cyan
                                )
                            }
                        }
                    }

                    if (pending.hasActiveSessions) {
                        Surface(
                            color = colors.red.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            border = BorderStroke(1.dp, colors.red.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_session_delete_warning")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "กำลังมีเซสชัน SSH เชื่อมต่ออยู่ (${pending.activeSessionsForTargets.size} เซสชัน)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.red
                                )
                                pending.activeSessionsForTargets.forEach { session ->
                                    Text(
                                        text = "• ${session.profile.name} — ${session.profile.username}@${session.profile.host}:${session.profile.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textPrimary
                                    )
                                }
                                Text(
                                    text = "หากดำเนินการต่อ แอปจะตัดการเชื่อมต่อเฉพาะเซสชันของโปรไฟล์ที่ลบเท่านั้น (ไม่กระทบเซสชันอื่น)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = "การลบจะนำการตั้งค่าโปรไฟล์ที่บันทึกไว้ออกจากแอป TrueSeekrSSH เท่านั้น โดยไม่ลบไฟล์ Private Key ภายนอกของผู้ใช้ ไม่ลบ Known Hosts Trust และไม่ปิดเซสชัน tmux บนเซิร์ฟเวอร์",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )

                    if (pending.hasAnyStoredCredential) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { removeCredentials = !removeCredentials }
                        ) {
                            Checkbox(
                                checked = removeCredentials,
                                onCheckedChange = { removeCredentials = it },
                                modifier = Modifier.testTag("delete_associated_credential_checkbox")
                            )
                            Text(
                                text = "ลบ Credential (รหัสผ่าน/Private Key) ที่แอปจัดการไว้ใน Vault ด้วย",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("cancel_delete_profile_button")
                    ) {
                        Text("ยกเลิก")
                    }
                    Button(
                        onClick = { onConfirmDelete(removeCredentials, pending.hasActiveSessions) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag(
                                if (pending.hasActiveSessions) {
                                    "confirm_disconnect_and_delete_button"
                                } else {
                                    "confirm_delete_profile_button"
                                }
                            )
                    ) {
                        Text(
                            if (pending.hasActiveSessions) "ตัดการเชื่อมต่อและลบ" else "ยืนยันการลบ",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupManagerDialog(
    groups: List<ProfileGroup>,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var newGroupName by remember { mutableStateOf("") }
    var groupToRename by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("จัดการกลุ่มโปรไฟล์") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("ชื่อกลุ่มใหม่") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onCreateGroup(newGroupName)
                            newGroupName = ""
                        },
                        enabled = newGroupName.isNotBlank()
                    ) { Text("เพิ่ม") }
                }

                groups.forEach { group ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (group.isBuiltIn) "${group.name} (พื้นฐาน)" else group.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!group.isBuiltIn) {
                            Row {
                                IconButton(onClick = {
                                    groupToRename = group.name
                                    renameText = group.name
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "เปลี่ยนชื่อกลุ่ม")
                                }
                                IconButton(onClick = { onDeleteGroup(group.name) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "ลบกลุ่ม", tint = colors.red)
                                }
                            }
                        }
                    }
                }

                if (groupToRename != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("ชื่อใหม่สำหรับ $groupToRename") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            onRenameGroup(groupToRename!!, renameText)
                            groupToRename = null
                        }) { Text("OK") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด") }
        }
    )
}

