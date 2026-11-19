package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Formatters
import com.example.domain.SessionStatus
import com.example.domain.SftpFileItem
import com.example.sftp.SftpViewModel
import com.example.ui.components.BannerTone
import com.example.ui.components.ObsidianEmptyState
import com.example.ui.components.ObsidianMetadataTag
import com.example.ui.components.ObsidianStatusBanner
import com.example.ui.components.ObsidianSurfaceCard
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianRadius
import com.example.ui.theme.ObsidianSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    viewModel: SftpViewModel,
    onGoToServers: () -> Unit
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val connectedSessions = remember(sessions) {
        sessions.filter { it.status == SessionStatus.CONNECTED }
    }

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<SftpFileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SftpFileItem?>(null) }
    var pendingDownloadItem by remember { mutableStateOf<SftpFileItem?>(null) }

    LaunchedEffect(connectedSessions) {
        val currentSid = uiState.selectedSessionId
        if (connectedSessions.isNotEmpty() && connectedSessions.none { it.sessionId == currentSid }) {
            viewModel.attachOrOpenSession(connectedSessions.first().sessionId)
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (fileName, size) = queryDisplayNameAndSize(context, uri)
            viewModel.uploadLocalFile(
                contentResolver = context.contentResolver,
                localUri = uri,
                fileName = fileName ?: "upload.bin",
                totalBytes = size
            )
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val target = pendingDownloadItem
        pendingDownloadItem = null
        if (uri != null && target != null) {
            viewModel.downloadRemoteFile(
                contentResolver = context.contentResolver,
                item = target,
                destinationUri = uri
            )
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("จัดการไฟล์ SFTP", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        val activeSession = connectedSessions.firstOrNull { it.sessionId == uiState.selectedSessionId }
                        if (activeSession != null) {
                            Text(
                                text = "${activeSession.profile.username}@${activeSession.profile.host}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.cyan
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.selectedSessionId != null && connectedSessions.isNotEmpty()) {
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.testTag("sftp_view_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "มุมมองรายการ" else "มุมมองตาราง"
                            )
                        }
                        IconButton(
                            onClick = { showMkdirDialog = true },
                            modifier = Modifier.testTag("sftp_mkdir_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "สร้างโฟลเดอร์")
                        }
                        IconButton(
                            onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("sftp_upload_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "อัปโหลดไฟล์")
                        }
                        IconButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.testTag("sftp_refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        if (connectedSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(ObsidianSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                ObsidianEmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = "ยังไม่มีการเชื่อมต่อ SSH สำหรับ SFTP",
                    subtitle = "เชื่อมต่อกับเซิร์ฟเวอร์ในหน้า Servers ก่อน เพื่อเรียกดู อัปโหลด และดาวน์โหลดไฟล์ผ่าน SFTP",
                    actionLabel = "ไปหน้าเซิร์ฟเวอร์",
                    onAction = onGoToServers
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connectedSessions.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(connectedSessions, key = { it.sessionId }) { session ->
                        FilterChip(
                            selected = uiState.selectedSessionId == session.sessionId,
                            onClick = { viewModel.attachOrOpenSession(session.sessionId) },
                            label = { Text(session.profile.name) }
                        )
                    }
                }
            }

            // Tappable Breadcrumb Navigation Bar
            ObsidianSurfaceCard(
                shape = RoundedCornerShape(ObsidianRadius.md),
                elevated = true
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.navigateUp() },
                        enabled = uiState.currentPath.isNotBlank() && uiState.currentPath != "/"
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ขึ้นไปโฟลเดอร์ก่อนหน้า")
                    }
                    val breadcrumbs = remember(uiState.currentPath) {
                        buildBreadcrumbSegments(uiState.currentPath)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { idx, (label, targetPath) ->
                            val isLast = idx == breadcrumbs.lastIndex
                            Surface(
                                color = if (isLast) colors.cyan.copy(alpha = 0.14f) else colors.elevated,
                                shape = RoundedCornerShape(ObsidianRadius.sm),
                                border = if (isLast) BorderStroke(1.dp, colors.cyan.copy(alpha = 0.35f)) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ObsidianRadius.sm))
                                    .clickable(enabled = !isLast) { viewModel.navigateTo(targetPath) }
                            ) {
                                Text(
                                    text = label,
                                    style = MonoCodeStyle.copy(
                                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isLast) colors.cyan else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Active Transfer Card
            uiState.activeTransfer?.let { transfer ->
                ObsidianSurfaceCard(
                    shape = RoundedCornerShape(ObsidianRadius.md),
                    elevated = true,
                    accentBorder = colors.cyan.copy(alpha = 0.45f)
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
                                text = "${if (transfer.isUpload) "กำลังอัปโหลด" else "กำลังดาวน์โหลด"}: ${transfer.fileName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.cancelActiveTransfer() },
                                modifier = Modifier.testTag("sftp_cancel_transfer_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = "ยกเลิกการโอนย้าย", tint = colors.red)
                            }
                        }
                        val total = transfer.totalBytes
                        if (total != null && total > 0L) {
                            LinearProgressIndicator(
                                progress = { (transfer.bytesTransferred.toFloat() / total).coerceIn(0f, 1f) },
                                color = colors.cyan,
                                trackColor = colors.border,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${Formatters.formatBytes(transfer.bytesTransferred)} / ${Formatters.formatBytes(total)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        } else {
                            LinearProgressIndicator(
                                color = colors.cyan,
                                trackColor = colors.border,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = Formatters.formatBytes(transfer.bytesTransferred),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                ObsidianStatusBanner(
                    message = uiState.errorMessage!!,
                    tone = BannerTone.ERROR,
                    onDismiss = { viewModel.clearStatusBanner() }
                )
            } else if (!uiState.statusBanner.isNullOrBlank()) {
                ObsidianStatusBanner(
                    message = uiState.statusBanner!!,
                    tone = BannerTone.INFO,
                    onDismiss = { viewModel.clearStatusBanner() }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.cyan)
                }
            } else if (uiState.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ObsidianEmptyState(
                        icon = Icons.Default.FolderOpen,
                        title = "โฟลเดอร์นี้ว่างเปล่า",
                        subtitle = "ยังไม่มีไฟล์หรือโฟลเดอร์ย่อยใน ${uiState.currentPath.ifBlank { "/" }}"
                    )
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.entries, key = { it.fullPath }) { item ->
                        SftpFileGridCard(
                            item = item,
                            onClick = { if (item.isDirectory) viewModel.navigateTo(item.fullPath) },
                            onDownload = {
                                pendingDownloadItem = item
                                downloadLauncher.launch(item.name)
                            },
                            onRename = { itemToRename = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.entries, key = { it.fullPath }) { item ->
                        SftpFileListRow(
                            item = item,
                            onClick = { if (item.isDirectory) viewModel.navigateTo(item.fullPath) },
                            onDownload = {
                                pendingDownloadItem = item
                                downloadLauncher.launch(item.name)
                            },
                            onRename = { itemToRename = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    if (showMkdirDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false },
            containerColor = colors.surface,
            title = { Text("สร้างโฟลเดอร์ใหม่") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("ชื่อโฟลเดอร์") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createFolder(folderName)
                        showMkdirDialog = false
                    },
                    enabled = folderName.isNotBlank()
                ) { Text("สร้าง") }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false }) { Text("ยกเลิก") }
            }
        )
    }

    itemToRename?.let { item ->
        var newName by remember(item) { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            containerColor = colors.surface,
            title = { Text("เปลี่ยนชื่อ") },
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
                        viewModel.renameItem(item, newName)
                        itemToRename = null
                    },
                    enabled = newName.isNotBlank()
                ) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("ยกเลิก") }
            }
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = colors.surface,
            title = { Text("ยืนยันการลบ \"${item.name}\"?") },
            text = {
                Text("การลบไฟล์หรือโฟลเดอร์บนเซิร์ฟเวอร์ผ่าน SFTP ไม่สามารถย้อนกลับได้")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier.testTag("sftp_confirm_delete_button")
                ) { Text("ลบถาวร") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("ยกเลิก") }
            }
        )
    }
}

@Composable
private fun SftpFileListRow(
    item: SftpFileItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    ObsidianSurfaceCard(
        shape = RoundedCornerShape(ObsidianRadius.md),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(ObsidianRadius.sm))
                    .background(
                        (if (item.isDirectory) colors.amber else colors.cyan).copy(alpha = 0.12f)
                    )
                    .border(
                        1.dp,
                        (if (item.isDirectory) colors.amber else colors.cyan).copy(alpha = 0.28f),
                        RoundedCornerShape(ObsidianRadius.sm)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (item.isDirectory) colors.amber else colors.cyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObsidianMetadataTag(text = item.permissionsDisplay, monospace = true)
                    Text(
                        text = "${if (item.isDirectory) "DIR" else Formatters.formatBytes(item.sizeBytes)} • ${Formatters.formatUnixSeconds(item.modifiedEpochSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!item.isDirectory) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "ดาวน์โหลด", tint = colors.cyan)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "เมนูเพิ่มเติม", tint = colors.textSecondary)
                }
                SftpContextMenu(
                    expanded = menuExpanded,
                    isDirectory = item.isDirectory,
                    onDismiss = { menuExpanded = false },
                    onDownload = onDownload,
                    onRename = onRename,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun SftpFileGridCard(
    item: SftpFileItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    ObsidianSurfaceCard(
        shape = RoundedCornerShape(ObsidianRadius.md),
        elevated = true,
        onClick = onClick
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(ObsidianRadius.sm))
                        .background((if (item.isDirectory) colors.amber else colors.cyan).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (item.isDirectory) colors.amber else colors.cyan
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "เมนูเพิ่มเติม", tint = colors.textSecondary)
                    }
                    SftpContextMenu(
                        expanded = menuExpanded,
                        isDirectory = item.isDirectory,
                        onDismiss = { menuExpanded = false },
                        onDownload = onDownload,
                        onRename = onRename,
                        onDelete = onDelete
                    )
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${if (item.isDirectory) "Directory" else Formatters.formatBytes(item.sizeBytes)}\n${item.permissionsDisplay}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun SftpContextMenu(
    expanded: Boolean,
    isDirectory: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalObsidianColors.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!isDirectory) {
            DropdownMenuItem(
                text = { Text("ดาวน์โหลด") },
                leadingIcon = { Icon(Icons.Default.Download, null) },
                onClick = { onDismiss(); onDownload() }
            )
        }
        DropdownMenuItem(
            text = { Text("เปลี่ยนชื่อ") },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("ลบ", color = colors.red) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.red) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

private fun buildBreadcrumbSegments(rawPath: String): List<Pair<String, String>> {
    val clean = rawPath.ifBlank { "/" }
    if (clean == "/") return listOf("/" to "/")
    val parts = clean.split("/").filter { it.isNotBlank() }
    val result = mutableListOf("/" to "/")
    var running = ""
    for (part in parts) {
        running = "$running/$part"
        result.add(part to running)
    }
    return result
}

private fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String?, Long?> {
    var name: String? = null
    var size: Long? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
    }
    return name to size
}
