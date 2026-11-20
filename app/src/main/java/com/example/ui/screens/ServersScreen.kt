package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
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
import com.example.profiles.ServersUiState
import com.example.profiles.ServersViewModel
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.ExportBackupPreview
import com.example.security.ImportDuplicateStrategy
import com.example.security.ImportPreviewSummary
import com.example.ssh.ManagedSessionUiState
import com.example.ui.components.BannerTone
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.HostTrustIndicator
import com.example.ui.components.ObsidianEmptyState
import com.example.ui.components.ObsidianMetadataTag
import com.example.ui.components.ObsidianSectionHeader
import com.example.ui.components.ObsidianStatusBanner
import com.example.ui.components.ObsidianSurfaceCard
import com.example.ui.components.OverviewMetricTile
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
    var showGroupDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var actionSheetProfile by remember { mutableStateOf<SshProfile?>(null) }
    var profileForDetails by remember { mutableStateOf<SshProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<SshProfile?>(null) }
    var profileToRename by remember { mutableStateOf<SshProfile?>(null) }
    var profileToMoveGroup by remember { mutableStateOf<SshProfile?>(null) }
    var pendingExportPayload by remember { mutableStateOf<String?>(null) }
    var importedRawText by remember { mutableStateOf<String?>(null) }

    val pendingImportReview by viewModel.pendingImportReview.collectAsState()
    var importParseError by remember { mutableStateOf<String?>(null) }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val payload = pendingExportPayload
        pendingExportPayload = null
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importParseError = null
            runCatching {
                val stream = context.contentResolver.openInputStream(uri)
                val raw = stream?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > BackupCryptoManager.MAX_BACKUP_BYTES) {
                        error("ไฟล์สำรองมีขนาดใหญ่เกิน 10 MB")
                    }
                    String(bytes, Charsets.UTF_8)
                }
                raw
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) {
                    if (BackupCryptoManager.isEncryptedBackup(text)) {
                        importedRawText = text
                    } else {
                        val res = viewModel.prepareImportPreview(text, null)
                        if (res is BackupImportResult.Error) {
                            importParseError = res.message
                        }
                    }
                } else {
                    importParseError = "ไฟล์สำรองข้อมูลว่างเปล่าหรือไม่สามารถอ่านได้"
                }
            }.onFailure { err ->
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    onOpenGroups = { showGroupDialog = true },
                    onOpenBackup = { showBackupDialog = true },
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
                        accentColor = colors.cyan
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
                        onConnect = { viewModel.connectSavedProfile(profile, onNavigateToTerminal) },
                        onToggleFavorite = { viewModel.toggleFavorite(profile) },
                        onOpenActions = { actionSheetProfile = profile },
                        onOpenDetails = { profileForDetails = profile }
                    )
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
                    onClick = {
                        actionSheetProfile = null
                        viewModel.duplicateProfile(profile)
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
                    onClick = {
                        actionSheetProfile = null
                        profileToDelete = profile
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
                TextButton(onClick = {
                    val target = profile
                    profileForDetails = null
                    viewModel.openEditEditor(target)
                }) {
                    Text("แก้ไข")
                }
            }
        )
    }

    // Delete confirmation dialog
    profileToDelete?.let { profile ->
        var removeCredential by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            containerColor = colors.surface,
            title = { Text("ลบโปรไฟล์ \"${profile.name}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "การลบโปรไฟล์นี้จะไม่ลบข้อมูล Host Key Trust (Known Hosts) ออกโดยอัตโนมัติ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (profile.credentialRefId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = removeCredential, onCheckedChange = { removeCredential = it })
                            Text(
                                text = "ลบ Credential (รหัสผ่าน/Private Key) ที่ผูกกับโปรไฟล์นี้ออกจาก Vault ด้วย",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProfile(profile, removeCredential)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier.testTag("confirm_delete_profile_button")
                ) {
                    Text("ยืนยันการลบ")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("ยกเลิก")
                }
            }
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
        val exportPreview = remember(uiState.allProfiles) { viewModel.getExportPreview() }
        BackupExportImportDialog(
            exportPreview = exportPreview,
            onExportMetadata = {
                viewModel.buildExportContent(null).onSuccess { jsonText ->
                    pendingExportPayload = jsonText
                    exportSafLauncher.launch(BackupCryptoManager.STANDARD_EXPORT_FILENAME)
                    showBackupDialog = false
                }
            },
            onExportEncrypted = { pass, includeCreds ->
                viewModel.buildEncryptedFullBackup(pass, includeCreds).onSuccess { encryptedText ->
                    pendingExportPayload = encryptedText
                    exportSafLauncher.launch(BackupCryptoManager.ENCRYPTED_BACKUP_FILENAME)
                    showBackupDialog = false
                }
            },
            onChooseImportFile = {
                showBackupDialog = false
                importSafLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showBackupDialog = false }
        )
    }

    importedRawText?.let { raw ->
        var passphrase by remember(raw) { mutableStateOf("") }
        var feedback by remember(raw) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { importedRawText = null },
            containerColor = colors.surface,
            title = { Text("ถอดรหัสไฟล์สำรองข้อมูล") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ไฟล์นี้เข้ารหัสด้วย AES-256-GCM + PBKDF2 กรุณากรอกรหัสผ่านสำรองข้อมูลเพื่อดูตัวอย่างก่อนนำเข้า:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            feedback = null
                        },
                        label = { Text("รหัสผ่านสำรองข้อมูล") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("backup_decrypt_passphrase_input")
                    )
                    if (!feedback.isNullOrBlank()) {
                        Text(feedback!!, color = colors.red, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val res = viewModel.prepareImportPreview(raw, passphrase)) {
                            is BackupImportResult.Success -> importedRawText = null
                            is BackupImportResult.Error -> feedback = res.message
                        }
                    },
                    enabled = passphrase.isNotEmpty()
                ) {
                    Text("ถอดรหัสและตรวจสอบ")
                }
            },
            dismissButton = {
                TextButton(onClick = { importedRawText = null }) { Text("ยกเลิก") }
            }
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
        AlertDialog(
            onDismissRequest = { importParseError = null },
            containerColor = colors.surface,
            title = { Text("ไม่สามารถอ่านไฟล์สำรองข้อมูล") },
            text = { Text(errorMsg, color = colors.red) },
            confirmButton = {
                TextButton(onClick = { importParseError = null }) { Text("ตกลง") }
            }
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
    onOpenGroups: () -> Unit,
    onOpenBackup: () -> Unit,
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
                        onClick = onOpenGroups,
                        modifier = Modifier.size(48.dp).testTag("manage_groups_button")
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = "จัดการกลุ่มเซิร์ฟเวอร์",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenBackup,
                        modifier = Modifier.size(48.dp).testTag("import_export_button")
                    ) {
                        Icon(
                            Icons.Default.ImportExport,
                            contentDescription = "นำเข้า/ส่งออกโปรไฟล์",
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
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val colors = LocalObsidianColors.current
    val accentBorder = if (sessionStatus?.isLiveConnected == true) colors.lime.copy(alpha = 0.45f) else null

    ObsidianSurfaceCard(
        elevated = true,
        accentBorder = accentBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        onClick = onOpenDetails
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

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
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
            .clickable(onClick = onClick)
    )
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

@Composable
private fun BackupExportImportDialog(
    exportPreview: ExportBackupPreview,
    onExportMetadata: () -> Unit,
    onExportEncrypted: (String, Boolean) -> Unit,
    onChooseImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var passphrase by remember { mutableStateOf("") }
    var includeCredentials by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(ObsidianRadius.xl),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "สำรองและกู้คืนโปรไฟล์",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
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
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Export Preview (${exportPreview.totalProfiles} โปรไฟล์)",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "• มี Credential ใน Vault: ${exportPreview.storedCredentialCount} • ไม่มีใน Vault: ${exportPreview.missingStoredCredentialCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "• Android Keystore keys และ Known Host Trust จะไม่ถูกส่งออก",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.amber
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onExportMetadata,
                        enabled = exportPreview.totalProfiles > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_standard_json_button")
                    ) {
                        Text("ส่งออก Standard JSON (Metadata เท่านั้น)")
                    }

                    HorizontalDivider(color = colors.subtleBorder)

                    Text(
                        text = "Encrypted Full Backup (.tsshbackup)",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.cyan
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeCredentials = !includeCredentials }
                    ) {
                        Checkbox(
                            checked = includeCredentials,
                            onCheckedChange = { includeCredentials = it },
                            modifier = Modifier.testTag("export_include_credentials_checkbox")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "รวมรหัสผ่านและ Private Key จาก Vault",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "เข้ารหัส AES-256-GCM + PBKDF2 (210,000 รอบ) และเข้ารหัสซ้ำด้วย Keystore ของเครื่องปลายทางเมื่อนำเข้า",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("รหัสผ่านสำรองข้อมูล (อย่างน้อย 6 ตัว)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_passphrase_input")
                    )
                    Button(
                        onClick = { onExportEncrypted(passphrase, includeCredentials) },
                        enabled = exportPreview.totalProfiles > 0 && passphrase.length >= 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_encrypted_backup_button")
                    ) {
                        Text(
                            if (includeCredentials) "ส่งออก Encrypted Full Backup" else "ส่งออก Encrypted Metadata Backup"
                        )
                    }

                    HorizontalDivider(color = colors.subtleBorder)

                    OutlinedButton(
                        onClick = onChooseImportFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("choose_import_file_button")
                    ) {
                        Text("นำเข้าไฟล์สำรอง (.json / .tsshbackup / .pocketssh)")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("ปิด") }
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreviewSummary,
    onDismiss: () -> Unit,
    onConfirmImport: (ImportDuplicateStrategy, onError: (String) -> Unit) -> Unit
) {
    val colors = LocalObsidianColors.current
    var selectedStrategy by remember { mutableStateOf(ImportDuplicateStrategy.SKIP) }
    var applyError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(ObsidianRadius.xl),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ตรวจสอบก่อนนำเข้า (Import Preview)",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
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
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (preview.isEncrypted) {
                                    "ประเภท: Encrypted Backup (v${preview.formatVersion})"
                                } else {
                                    "ประเภท: Standard JSON Metadata (v${preview.formatVersion})"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.cyan
                            )
                            Text(
                                text = "ทั้งหมด: ${preview.totalCount} • โปรไฟล์ใหม่: ${preview.newCount} • ซ้ำกับที่มีอยู่: ${preview.duplicateCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Credential ที่มากับไฟล์: ${preview.embeddedCredentialCount} • ยังไม่มีรหัส/Key (ต้องกรอกภายหลัง): ${preview.missingAuthCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (preview.missingAuthCount > 0) colors.amber else colors.lime
                            )
                            if (preview.invalidOrUnsupportedCount > 0) {
                                Text(
                                    text = "รายการไม่สมบูรณ์/ไม่รองรับ (จะถูกข้าม): ${preview.invalidOrUnsupportedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.red
                                )
                            }
                        }
                    }

                    if (preview.duplicateCount > 0) {
                        Text(
                            text = "วิธีจัดการโปรไฟล์ที่ซ้ำกัน:",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        ImportDuplicateStrategy.entries.forEach { strategy ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStrategy = strategy }
                                    .testTag("import_strategy_${strategy.name}")
                            ) {
                                RadioButton(
                                    selected = selectedStrategy == strategy,
                                    onClick = { selectedStrategy = strategy }
                                )
                                Text(
                                    text = strategy.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = "เพื่อความปลอดภัย Host Key จะไม่ถูกเชื่อถืออัตโนมัติ (TOFU จะถามยืนยันเมื่อเชื่อมต่อครั้งแรก)",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )

                    if (!applyError.isNullOrBlank()) {
                        Text(applyError!!, color = colors.red, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("ยกเลิก") }
                    Button(
                        onClick = { onConfirmImport(selectedStrategy) { err -> applyError = err } },
                        enabled = preview.newCount > 0 || (preview.duplicateCount > 0 && selectedStrategy != ImportDuplicateStrategy.SKIP),
                        modifier = Modifier.testTag("confirm_import_button")
                    ) {
                        Text("ยืนยันนำเข้า")
                    }
                }
            }
        }
    }
}
