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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.core.Formatters
import com.example.domain.AuthMethodType
import com.example.domain.ProfileGroup
import com.example.domain.ProfileSortOrder
import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.profiles.ServersUiState
import com.example.profiles.ServersViewModel
import com.example.security.BackupCryptoManager
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

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
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
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()?.let { text ->
                importedRawText = text
            }
        }
    }

    val connectedCount = remember(uiState.activeSessions) {
        uiState.activeSessions.count { it.status == SessionStatus.CONNECTED }
    }
    val attentionCount = remember(uiState.activeSessions) {
        uiState.activeSessions.count { it.status == SessionStatus.ERROR || !it.networkWarning.isNullOrBlank() }
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
        BackupExportImportDialog(
            onExportMetadata = {
                viewModel.buildExportContent(null).onSuccess { jsonText ->
                    pendingExportPayload = jsonText
                    exportSafLauncher.launch("pocketssh-profiles-metadata.json")
                    showBackupDialog = false
                }
            },
            onExportEncrypted = { pass ->
                viewModel.buildExportContent(pass).onSuccess { encryptedText ->
                    pendingExportPayload = encryptedText
                    exportSafLauncher.launch("pocketssh-backup.pocketssh")
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
        val isEncrypted = BackupCryptoManager.isEncryptedBackup(raw)
        var passphrase by remember { mutableStateOf("") }
        var feedback by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { importedRawText = null },
            containerColor = colors.surface,
            title = { Text("นำเข้าโปรไฟล์ SSH") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isEncrypted) {
                            "ไฟล์นี้เข้ารหัสด้วย AES-256-GCM กรุณากรอก Passphrase เพื่อถอดรหัส:"
                        } else {
                            "พบไฟล์ Metadata โปรไฟล์ JSON (ระบบจะไม่เขียนทับโปรไฟล์เดิม และจะไม่เชื่อถือ Host Key อัตโนมัติ)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isEncrypted) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Passphrase") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!feedback.isNullOrBlank()) {
                        Text(feedback!!, color = colors.red, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importBackupContent(raw, passphrase.takeIf { isEncrypted }) { msg ->
                        if (msg.contains("สำเร็จ")) {
                            importedRawText = null
                        } else {
                            feedback = msg
                        }
                    }
                }) {
                    Text("นำเข้า")
                }
            },
            dismissButton = {
                TextButton(onClick = { importedRawText = null }) { Text("ยกเลิก") }
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
    val isConnected = session.status == SessionStatus.CONNECTED
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
        isConnected -> colors.lime.copy(alpha = 0.45f)
        session.status == SessionStatus.ERROR -> colors.red.copy(alpha = 0.45f)
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
                if (session.status == SessionStatus.DISCONNECTED || session.status == SessionStatus.ERROR) {
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
    val accentBorder = if (sessionStatus == SessionStatus.CONNECTED) colors.lime.copy(alpha = 0.45f) else null

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
    onExportMetadata: () -> Unit,
    onExportEncrypted: (String) -> Unit,
    onChooseImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("สำรองและกู้คืนโปรไฟล์") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ค่าเริ่มต้นจะส่งออกเฉพาะ Metadata ของโปรไฟล์เท่านั้น ไม่รวมรหัสผ่าน, Private Key หรือ Host Trust เพื่อความปลอดภัยสูงสุด",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                OutlinedButton(
                    onClick = onExportMetadata,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("ส่งออก Metadata (JSON)")
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase สำหรับไฟล์สำรองเข้ารหัส (อย่างน้อย 6 ตัว)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onExportEncrypted(passphrase) },
                    enabled = passphrase.length >= 6,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("ส่งออกแบบเข้ารหัส AES-256-GCM")
                }
                OutlinedButton(
                    onClick = onChooseImportFile,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("นำเข้าไฟล์สำรอง (.json / .pocketssh)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด") }
        }
    )
}
