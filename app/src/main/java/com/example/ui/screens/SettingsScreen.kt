package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.domain.AppAccentPreset
import com.example.domain.AppThemeMode
import com.example.domain.HealthRefreshInterval
import com.example.domain.TerminalCursorStyle
import com.example.domain.TerminalFontFamily
import com.example.domain.TerminalThemePreset
import com.example.network.TailscaleInstallState
import com.example.network.VpnOwnership
import com.example.security.BackupCryptoManager
import com.example.security.BackupImportResult
import com.example.security.BiometricLockManager
import com.example.settings.AutoLockTimeout
import com.example.settings.SettingsViewModel
import com.example.ui.components.BannerTone
import com.example.ui.components.CreateEncryptedBackupDialog
import com.example.ui.components.DecryptBackupPassphraseDialog
import com.example.ui.components.ExportProfilesDialog
import com.example.ui.components.ImportErrorDialog
import com.example.ui.components.ImportFileMode
import com.example.ui.components.ImportPreviewDialog
import com.example.ui.components.ObsidianMetadataTag
import com.example.ui.components.ObsidianSectionHeader
import com.example.ui.components.ObsidianStatusBanner
import com.example.ui.components.ObsidianSurfaceCard
import com.example.ui.components.readBackupTextFromUri
import com.example.ui.components.writeBackupTextToUri
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianRadius
import com.example.ui.theme.ObsidianSpacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tailscaleDiagnostics by viewModel.tailscaleDiagnostics.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHosts.collectAsStateWithLifecycle()
    val allProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val vaultCount by viewModel.vaultCount.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val feedbackIsError by viewModel.feedbackIsError.collectAsStateWithLifecycle()
    val pendingImportReview by viewModel.pendingImportReview.collectAsStateWithLifecycle()

    val biometricSupported = remember(context) { BiometricLockManager.canAuthenticateBiometric(context) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showSecurityArchDialog by remember { mutableStateOf(false) }

    var showExportProfilesDialog by remember { mutableStateOf(false) }
    var showCreateEncryptedBackupDialog by remember { mutableStateOf(false) }
    var pendingExportPayload by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingImportMode by remember { mutableStateOf(ImportFileMode.ANY_SUPPORTED) }
    var importedEncryptedRawText by remember { mutableStateOf<String?>(null) }
    var importParseError by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundKeepAliveEnabled(true)
        } else {
            viewModel.setBackgroundKeepAliveEnabled(false)
            viewModel.postFeedback("กรุณาอนุญาตการแจ้งเตือน (Notification) เพื่อแสดงแถบสถานะ Background Keep Alive บน Android 13+")
        }
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
                    viewModel.postFeedback(
                        if (isEncrypted) {
                            "บันทึกไฟล์สำรองเข้ารหัส (.tsshbackup) เรียบร้อยแล้ว"
                        } else {
                            "ส่งออกโปรไฟล์ (JSON) เรียบร้อยแล้ว"
                        }
                    )
                }
                .onFailure { err ->
                    viewModel.postFeedback(
                        err.message ?: "ไม่สามารถบันทึกไฟล์สำรองข้อมูลได้",
                        isError = true
                    )
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
                        importedEncryptedRawText = text
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

    LaunchedEffect(Unit) {
        viewModel.refreshVaultCount()
        viewModel.refreshTailscaleDiagnostics()
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ตั้งค่าและความปลอดภัย", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text(
                            text = "Obsidian Aurora Command Center Preferences",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!feedback.isNullOrBlank()) {
                item {
                    ObsidianStatusBanner(
                        message = feedback!!,
                        tone = if (feedbackIsError) BannerTone.ERROR else BannerTone.INFO,
                        onDismiss = { viewModel.clearFeedback() }
                    )
                }
            }

            // 1. APPEARANCE
            item {
                ObsidianSectionHeader(
                    title = "รูปลักษณ์และธีม (Appearance)",
                    icon = Icons.Default.Palette,
                    accentColor = colors.cyan
                )
            }
            item {
                ObsidianSurfaceCard(elevated = true) {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("โหมดการแสดงผล", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("settings_theme_mode_${mode.name}")
                                )
                            }
                        }

                        Text("สีเน้นหลัก (Accent Color)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppAccentPreset.entries.forEach { preset ->
                                val swatchColor = Color(preset.primaryArgb)
                                val selected = settings.accentPreset == preset
                                Surface(
                                    onClick = { viewModel.setAccentPreset(preset) },
                                    color = if (selected) swatchColor.copy(alpha = 0.16f) else colors.elevated,
                                    shape = RoundedCornerShape(ObsidianRadius.md),
                                    border = BorderStroke(1.dp, if (selected) swatchColor else colors.border),
                                    modifier = Modifier.testTag("settings_accent_${preset.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) swatchColor else colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Text("ธีมเทอร์มินัลเริ่มต้น", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TerminalThemePreset.entries.forEach { theme ->
                                FilterChip(
                                    selected = settings.defaultTerminalTheme == theme,
                                    onClick = { viewModel.setDefaultTheme(theme) },
                                    label = { Text(theme.displayName) }
                                )
                            }
                        }

                        Text(
                            text = "ขนาดตัวอักษรเริ่มต้น: ${settings.defaultTerminalFontSizeSp.roundToInt()} sp",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Slider(
                            value = settings.defaultTerminalFontSizeSp,
                            onValueChange = { viewModel.setDefaultFontSize(it) },
                            valueRange = 10f..22f
                        )

                        Text("ฟอนต์เทอร์มินัล", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalFontFamily.entries.forEach { family ->
                                FilterChip(
                                    selected = settings.terminalFontFamily == family,
                                    onClick = { viewModel.setTerminalFontFamily(family) },
                                    label = { Text(family.label) }
                                )
                            }
                        }
                    }
                }
            }

            // 2. TERMINAL
            item {
                ObsidianSectionHeader(
                    title = "เทอร์มินัลและคีย์บอร์ด (Terminal)",
                    icon = Icons.Default.Terminal,
                    accentColor = colors.lime
                )
            }
            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("ขนาด Scrollback Buffer (Search History)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500, 2000, 5000, 10000).forEach { lines ->
                                FilterChip(
                                    selected = settings.scrollbackLines == lines,
                                    onClick = { viewModel.setScrollbackLines(lines) },
                                    label = { Text("$lines บรรทัด") }
                                )
                            }
                        }

                        Text("รูปแบบเคอร์เซอร์ที่ต้องการ", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalCursorStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = settings.cursorStyle == style,
                                    onClick = { viewModel.setCursorStyle(style) },
                                    label = { Text(style.label) }
                                )
                            }
                        }

                        Text("ค่า TERM เริ่มต้น", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("xterm-256color", "vt100", "screen-256color").forEach { term ->
                                FilterChip(
                                    selected = settings.defaultTermType == term,
                                    onClick = { viewModel.setDefaultTermType(term) },
                                    label = { Text(term) }
                                )
                            }
                        }

                        SettingsSwitchRow(
                            title = "แสดงแถบปุ่มลัดคีย์บอร์ด (ESC / TAB / CTRL / Fn)",
                            subtitle = "แถบคำสั่งเสริมเหนือคีย์บอร์ดสำหรับงาน DevOps",
                            checked = settings.showExtraKeyboardToolbar,
                            onCheckedChange = { viewModel.setShowExtraKeyboardToolbar(it) }
                        )
                        SettingsSwitchRow(
                            title = "การสั่นเบาเมื่อกดปุ่มลัดเทอร์มินัล (Haptic)",
                            subtitle = "ตอบสนองสัมผัสเมื่อกด ESC, CTRL, ALT หรือ Arrows",
                            checked = settings.hapticKeyFeedback,
                            onCheckedChange = { viewModel.setHapticKeyFeedback(it) }
                        )
                    }
                }
            }

            // 3. SECURITY
            item {
                ObsidianSectionHeader(
                    title = "ความปลอดภัยและห้องนิรภัย (Security)",
                    icon = Icons.Default.Security,
                    accentColor = colors.amber
                )
            }
            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = colors.cyan)
                            Text("การล็อกแอปและ Biometric", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        }
                        Text(
                            text = "Biometric / PIN ใช้สำหรับป้องกันข้อมูล Local Vault บนเครื่องเท่านั้น ไม่แทนการยืนยันตัวตนกับ SSH Server",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("เปิดใช้งาน App Lock (PIN)", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                Text(
                                    text = if (settings.hasPinConfigured) "ตั้งค่า PIN แล้ว" else "ยังไม่ได้ตั้ง PIN",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !settings.hasPinConfigured) {
                                        showPinSetupDialog = true
                                    } else {
                                        viewModel.setAppLockEnabled(enabled)
                                    }
                                },
                                modifier = Modifier.testTag("settings_applock_switch")
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showPinSetupDialog = true },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(if (settings.hasPinConfigured) "เปลี่ยน PIN" else "ตั้งค่า PIN")
                            }
                            if (settings.hasPinConfigured) {
                                TextButton(
                                    onClick = { viewModel.disablePinAndAppLock() },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("ลบ PIN", color = colors.red)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Fingerprint, null, tint = colors.lime)
                                Column {
                                    Text("สแกนลายนิ้วมือ / ใบหน้า", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                    Text(
                                        text = if (biometricSupported) "อุปกรณ์รองรับ BiometricPrompt" else "อุปกรณ์ไม่ได้ตั้งค่า Biometric",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = settings.biometricEnabled,
                                enabled = biometricSupported && settings.hasPinConfigured,
                                onCheckedChange = { viewModel.setBiometricEnabled(it) }
                            )
                        }

                        Text("ล็อกอัตโนมัติเมื่อสลับแอป", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AutoLockTimeout.entries.forEach { timeout ->
                                FilterChip(
                                    selected = settings.autoLockTimeout == timeout,
                                    onClick = { viewModel.setAutoLockTimeout(timeout) },
                                    label = { Text(timeout.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ScreenshotMonitor, null, tint = colors.textSecondary)
                                Column {
                                    Text("ป้องกันภาพหน้าจอ (FLAG_SECURE)", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                    Text(
                                        "ซ่อนหน้าจอใน Recent Tasks และบล็อก Screenshot / Screen Streaming",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = settings.protectScreenshots,
                                onCheckedChange = { viewModel.setProtectScreenshots(it) }
                            )
                        }

                        HorizontalDivider(color = colors.subtleBorder)

                        // Keystore Vault
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, null, tint = colors.lime)
                            Text("Android Keystore Vault", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        }
                        Text(
                            text = "จัดเก็บรหัสผ่านและ Private Key แบบเข้ารหัส AES-256-GCM ($vaultCount รายการที่เข้ารหัสอยู่) แยกออกจาก Metadata และปิด Auto Backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        OutlinedButton(
                            onClick = { viewModel.cleanOrphanedCredentials() },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("ล้าง Credential ที่ไม่มีโปรไฟล์อ้างอิง")
                        }

                        HorizontalDivider(color = colors.subtleBorder)

                        // Trusted Hosts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, null, tint = colors.cyan)
                                Text("Trusted SSH Hosts (${knownHosts.size})", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            }
                            if (knownHosts.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearAllKnownHosts() }) {
                                    Text("ล้างทั้งหมด", color = colors.red)
                                }
                            }
                        }
                        if (knownHosts.isEmpty()) {
                            Text(
                                text = "ยังไม่มี Host Key ที่เชื่อถือ (ระบบจะถามยืนยัน SHA-256 Fingerprint ทุกครั้งเมื่อเชื่อมต่อโฮสต์ใหม่)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        } else {
                            knownHosts.forEach { host ->
                                Surface(
                                    color = colors.elevated,
                                    shape = RoundedCornerShape(ObsidianRadius.md),
                                    border = BorderStroke(1.dp, colors.subtleBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${host.host}:${host.port} (${host.keyType})", style = MonoCodeStyle, color = colors.textPrimary)
                                            Text(
                                                host.sha256Fingerprint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.cyan
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeKnownHost(host.id) },
                                            modifier = Modifier.testTag("revoke_host_${host.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "ยกเลิกความเชื่อถือ", tint = colors.red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3.5 BACKUP & RESTORE
            item {
                ObsidianSectionHeader(
                    title = "Backup & Restore",
                    icon = Icons.Default.Backup,
                    accentColor = colors.cyan
                )
            }
            item {
                ObsidianSurfaceCard(elevated = true) {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "สำรองและกู้คืนโปรไฟล์ SSH",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            ObsidianMetadataTag(
                                text = "${allProfiles.size} โปรไฟล์ • Vault $vaultCount",
                                tint = colors.cyan
                            )
                        }
                        Text(
                            text = "รองรับไฟล์ Standard JSON (.json) และไฟล์สำรองเข้ารหัสเต็มรูปแบบ (.tsshbackup) พร้อมตรวจสอบก่อนนำเข้า",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        HorizontalDivider(color = colors.subtleBorder)
                        SettingsActionRow(
                            title = "Import Profiles",
                            subtitle = "นำเข้าโปรไฟล์จากไฟล์ .json หรือ .tsshbackup พร้อมตัวเลือกจัดการรายการซ้ำ",
                            icon = Icons.Default.FileUpload,
                            accentColor = colors.cyan,
                            testTag = "settings_import_profiles_item",
                            onClick = {
                                pendingImportMode = ImportFileMode.ANY_SUPPORTED
                                importSafLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        HorizontalDivider(color = colors.subtleBorder)
                        SettingsActionRow(
                            title = "Export Profiles",
                            subtitle = "ส่งออกข้อมูลโปรไฟล์และกลุ่มเป็นไฟล์ JSON (ไม่รวมรหัสผ่าน/Key และ Known Hosts)",
                            icon = Icons.Default.FileDownload,
                            accentColor = colors.cyan,
                            testTag = "settings_export_profiles_item",
                            onClick = { showExportProfilesDialog = true }
                        )
                        HorizontalDivider(color = colors.subtleBorder)
                        SettingsActionRow(
                            title = "Create Encrypted Backup",
                            subtitle = "สร้างไฟล์สำรองเข้ารหัส AES-256-GCM (.tsshbackup) เลือกรวม Vault Credentials ได้",
                            icon = Icons.Default.EnhancedEncryption,
                            accentColor = colors.lime,
                            testTag = "settings_create_encrypted_backup_item",
                            onClick = { showCreateEncryptedBackupDialog = true }
                        )
                        HorizontalDivider(color = colors.subtleBorder)
                        SettingsActionRow(
                            title = "Restore Encrypted Backup",
                            subtitle = "ถอดรหัสและกู้คืนจากไฟล์ .tsshbackup เข้าสู่ฐานข้อมูลและ Android Keystore",
                            icon = Icons.Default.LockOpen,
                            accentColor = colors.amber,
                            testTag = "settings_restore_encrypted_backup_item",
                            onClick = {
                                pendingImportMode = ImportFileMode.ENCRYPTED_ONLY
                                importSafLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }

            // 4. CONNECTIONS
            item {
                ObsidianSectionHeader(
                    title = "การเชื่อมต่อและเครือข่าย (Connections)",
                    icon = Icons.Default.Cable,
                    accentColor = colors.cyan
                )
            }
            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Handshake Timeout เริ่มต้น", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 15, 30, 60).forEach { sec ->
                                FilterChip(
                                    selected = settings.defaultConnectTimeoutSec == sec,
                                    onClick = { viewModel.setDefaultConnectTimeoutSec(sec) },
                                    label = { Text("${sec}s") }
                                )
                            }
                        }

                        Text("Keepalive Interval เริ่มต้น", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 15, 30, 60).forEach { sec ->
                                FilterChip(
                                    selected = settings.defaultKeepAliveSec == sec,
                                    onClick = { viewModel.setDefaultKeepAlive(sec) },
                                    label = { Text(if (sec == 0) "ปิด" else "${sec}s") }
                                )
                            }
                        }

                        SettingsSwitchRow(
                            title = "แจ้งเตือน Reconnect เมื่อเครือข่ายสลับเส้นทาง",
                            subtitle = "เตือนเมื่อ Wi-Fi / Cellular / Tailscale VPN เปลี่ยนแปลง",
                            checked = settings.autoReconnectPrompt,
                            onCheckedChange = { viewModel.setAutoReconnectPrompt(it) }
                        )
                    }
                }
            }

            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("SSH Keepalive & Background Session", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        SettingsSwitchRow(
                            title = "เปิดใช้งาน SSH Keepalive Pings",
                            subtitle = "ส่ง SSH keepalive ping เป็นระยะเพื่อป้องกัน NAT/Firewall ตัดเซสชันและตรวจจับ Connection Lost",
                            checked = settings.sshKeepAliveEnabled,
                            onCheckedChange = { viewModel.setSshKeepAliveEnabled(it) },
                            switchModifier = Modifier.testTag("settings_ssh_keepalive_switch")
                        )
                        Text("รอบส่ง Keepalive (Global Default)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60, 120).forEach { sec ->
                                FilterChip(
                                    selected = settings.sshKeepAliveIntervalSec == sec,
                                    enabled = settings.sshKeepAliveEnabled,
                                    onClick = { viewModel.setSshKeepAliveIntervalSec(sec) },
                                    label = { Text("${sec}s") }
                                )
                            }
                        }
                        Text("จำนวนครั้งที่พลาดก่อนแจ้ง Connection Lost", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 5).forEach { maxMissed ->
                                FilterChip(
                                    selected = settings.sshKeepAliveMaxMissed == maxMissed,
                                    enabled = settings.sshKeepAliveEnabled,
                                    onClick = { viewModel.setSshKeepAliveMaxMissed(maxMissed) },
                                    label = { Text("${maxMissed} ครั้ง") }
                                )
                            }
                        }
                        SettingsSwitchRow(
                            title = "คงเซสชันในเบื้องหลัง (Foreground Service)",
                            subtitle = "แสดง Notification ถาวรพร้อมปุ่มหยุดเชื่อมต่ออย่างปลอดภัย (หยุดอัตโนมัติเมื่อปิดทุกแท็บ)",
                            checked = settings.backgroundKeepAliveEnabled,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    viewModel.setBackgroundKeepAliveEnabled(false)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setBackgroundKeepAliveEnabled(true)
                                }
                            },
                            switchModifier = Modifier.testTag("settings_background_keepalive_switch")
                        )
                        SettingsSwitchRow(
                            title = "ป้องกัน CPU หลับขณะเชื่อมต่อ (PARTIAL_WAKE_LOCK)",
                            subtitle = "ใช้พลังงานแบตเตอรี่เพิ่มขึ้น; ปล่อยล็อกทันทีเมื่อตัดการเชื่อมต่อหรือปิดสวิตช์",
                            checked = settings.keepCpuAwakeWhileConnected,
                            enabled = settings.backgroundKeepAliveEnabled,
                            onCheckedChange = { viewModel.setKeepCpuAwakeWhileConnected(it) },
                            switchModifier = Modifier.testTag("settings_keep_cpu_awake_switch")
                        )
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                val fallback = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                runCatching { context.startActivity(intent) }
                                    .onFailure { runCatching { context.startActivity(fallback) } }
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("ตั้งค่า Battery Optimization (ลดการถูกระบบปิดเบื้องหลัง)")
                        }
                    }
                }
            }

            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Server Health Dashboard & tmux", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        SettingsSwitchRow(
                            title = "เปิดใช้งาน Server Health Dashboard",
                            subtitle = "ใช้ Fastfetch JSON (--format json) หากมีบน Linux หรืออ่านค่าจาก /proc, /sys และ df แบบ Read-Only",
                            checked = settings.healthDashboardEnabled,
                            onCheckedChange = { viewModel.setHealthDashboardEnabled(it) },
                            switchModifier = Modifier.testTag("settings_health_dashboard_switch")
                        )
                        Text("รอบอัปเดต Dashboard อัตโนมัติ", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HealthRefreshInterval.entries.forEach { interval ->
                                FilterChip(
                                    selected = settings.healthRefreshInterval == interval,
                                    enabled = settings.healthDashboardEnabled,
                                    onClick = { viewModel.setHealthRefreshInterval(interval) },
                                    label = { Text(interval.label) },
                                    modifier = Modifier.testTag("settings_health_interval_${interval.name}")
                                )
                            }
                        }
                        SettingsSwitchRow(
                            title = "เปิดใช้งาน Persistent Sessions (tmux)",
                            subtitle = "จัดการ tmux session บนเซิร์ฟเวอร์แบบ opt-in (ไม่ติดตั้งแพ็กเกจอัตโนมัติ)",
                            checked = settings.tmuxIntegrationEnabled,
                            onCheckedChange = { viewModel.setTmuxIntegrationEnabled(it) },
                            switchModifier = Modifier.testTag("settings_tmux_switch")
                        )
                        Text("คำนำหน้าชื่อ tmux session ของแอป", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("trueSeekr", "ssh", "tssh").forEach { prefix ->
                                FilterChip(
                                    selected = settings.tmuxSessionPrefix == prefix,
                                    enabled = settings.tmuxIntegrationEnabled,
                                    onClick = { viewModel.setTmuxSessionPrefix(prefix) },
                                    label = { Text(prefix) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Hub, null, tint = colors.cyan)
                            Text("Tailscale Integration (Official App)", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        }
                        Text(
                            text = "ใช้แอป Tailscale อย่างเป็นทางการบน Android ผ่าน AutomationActivity โดยไม่ฝัง WireGuard ลงในแอป และไม่ตัดการเชื่อมต่อ VPN ที่ผู้ใช้เปิดไว้เองภายนอก",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )

                        SettingsSwitchRow(
                            title = "เปิดใช้งาน Tailscale Integration (Master Switch)",
                            subtitle = "อนุญาตให้โปรไฟล์ที่ตั้งค่าไว้สั่งเปิด/ปิด Tailscale อัตโนมัติ",
                            checked = settings.tailscaleAutomationEnabled,
                            onCheckedChange = { viewModel.setTailscaleAutomationEnabled(it) },
                            switchModifier = Modifier.testTag("settings_tailscale_master_switch")
                        )

                        SettingsSwitchRow(
                            title = "ปิด Tailscale อัตโนมัติเมื่อจบเซสชันสุดท้าย",
                            subtitle = "ปิดเฉพาะเมื่อแอปเป็นคนเริ่มและไม่มี Terminal แท็บที่ใช้งาน VPN เหลืออยู่",
                            checked = settings.tailscaleAutoDisconnect,
                            enabled = settings.tailscaleAutomationEnabled,
                            onCheckedChange = { viewModel.setTailscaleAutoDisconnect(it) },
                            switchModifier = Modifier.testTag("settings_tailscale_auto_disconnect_switch")
                        )

                        Text(
                            text = "เวลารอ Tailscale VPN (Timeout)",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (settings.tailscaleAutomationEnabled) colors.textPrimary else colors.textSecondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 45, 60).forEach { sec ->
                                FilterChip(
                                    selected = settings.tailscaleConnectTimeoutSec == sec,
                                    enabled = settings.tailscaleAutomationEnabled,
                                    onClick = { viewModel.setTailscaleConnectTimeoutSec(sec) },
                                    label = { Text("${sec}s") },
                                    modifier = Modifier.testTag("settings_tailscale_timeout_$sec")
                                )
                            }
                        }

                        Surface(
                            color = colors.elevated,
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            border = BorderStroke(1.dp, colors.subtleBorder),
                            modifier = Modifier.fillMaxWidth()
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
                                    Text("สถานะ Tailscale & VPN ปัจจุบัน", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                    TextButton(onClick = { viewModel.refreshTailscaleDiagnostics() }) {
                                        Text("รีเฟรช")
                                    }
                                }
                                Text(
                                    text = "• แอป Tailscale: ${tailscaleDiagnostics.installState.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tailscaleDiagnostics.installState == TailscaleInstallState.INSTALLED) colors.lime else colors.amber
                                )
                                Text(
                                    text = "• สถานะ VPN Transport: ${if (tailscaleDiagnostics.vpnStatus.hasTransportVpn) "เชื่อมต่อ VPN อยู่" else "ไม่มี VPN เชื่อมต่อ"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tailscaleDiagnostics.vpnStatus.hasTransportVpn) colors.cyan else colors.textSecondary
                                )
                                Text(
                                    text = "• Ownership: ${tailscaleDiagnostics.ownership.label}" +
                                        if (tailscaleDiagnostics.activeLeaseCount > 0) " (${tailscaleDiagnostics.activeLeaseCount} เซสชัน)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (tailscaleDiagnostics.ownership) {
                                        VpnOwnership.APP_REQUESTED -> colors.lime
                                        VpnOwnership.EXTERNAL, VpnOwnership.UNKNOWN -> colors.amber
                                        VpnOwnership.NONE -> colors.textSecondary
                                    }
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (tailscaleDiagnostics.installState == TailscaleInstallState.NOT_INSTALLED) {
                                        OutlinedButton(
                                            onClick = { viewModel.openTailscalePlayStore() },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("ติดตั้ง Tailscale")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.openTailscaleApp() },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("เปิดแอป Tailscale")
                                        }
                                    }
                                    if (tailscaleDiagnostics.ownership == VpnOwnership.APP_REQUESTED &&
                                        tailscaleDiagnostics.vpnStatus.hasTransportVpn
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.disconnectOwnedTailscaleVpn() },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("ปิด VPN ตอนนี้", color = colors.amber)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. ABOUT
            item {
                ObsidianSectionHeader(
                    title = "เกี่ยวกับ TrueSeekrSSH (About)",
                    icon = Icons.Default.Info,
                    accentColor = colors.textSecondary
                )
            }
            item {
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TrueSeekrSSH Native Client", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                Text(
                                    text = "cbssh 0.6.9 • termlib 0.1.0 • Keystore Vault",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            ObsidianMetadataTag(text = "v${BuildConfig.VERSION_NAME}", tint = colors.cyan, monospace = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showSecurityArchDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("สถาปัตยกรรมความปลอดภัย", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { showLicensesDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Gavel, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ใบอนุญาต", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        var pin1 by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            containerColor = colors.surface,
            title = { Text("ตั้งค่า PIN สำหรับ App Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { if (it.length <= 12) pin1 = it.filter(Char::isDigit) },
                        label = { Text("รหัส PIN (อย่างน้อย 4 หลัก)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { if (it.length <= 12) pin2 = it.filter(Char::isDigit) },
                        label = { Text("ยืนยัน PIN อีกครั้ง") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setPin(pin1)
                        showPinSetupDialog = false
                    },
                    enabled = pin1.length >= 4 && pin1 == pin2
                ) { Text("บันทึก PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text("ยกเลิก") }
            }
        )
    }

    if (showSecurityArchDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityArchDialog = false },
            containerColor = colors.surface,
            title = { Text("สถาปัตยกรรมความปลอดภัย TrueSeekrSSH") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Strict Host Key Verification (TOFU + MITM Detection): บันทึก SHA-256 Fingerprint ใน Room และปฏิเสธทันทีหาก Key เปลี่ยนแปลง", style = MaterialTheme.typography.bodySmall)
                    Text("• Credential Isolation: รหัสผ่านและ Private Key ถูกเข้ารหัส AES-256-GCM ผ่าน Android Keystore แยกจาก Profile Metadata", style = MaterialTheme.typography.bodySmall)
                    Text("• Ephemeral Passphrase: Passphrase ของ Private Key เก็บในหน่วยความจำเท่านั้น และล้างอัตโนมัติเมื่อแอปถูกล็อก", style = MaterialTheme.typography.bodySmall)
                    Text("• Android Auto Backup ถูกปิด (allowBackup=false) เพื่อป้องกันกุญแจรั่วไหลไปยัง Cloud Backup", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityArchDialog = false }) { Text("ปิด") }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = colors.surface,
            title = { Text("ไลบรารีและใบอนุญาต") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• ConnectBot cbssh (org.connectbot.sshlib:sshlib:0.6.9) — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• ConnectBot termlib (org.connectbot:termlib:0.1.0) — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• libvterm (bundled inside termlib) — MIT License", style = MaterialTheme.typography.bodySmall)
                    Text("• AndroidX Jetpack Compose, Room, DataStore, Navigation, Biometric — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• Kotlinx Coroutines & Serialization — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text("ปิด") }
            }
        )
    }

    if (showExportProfilesDialog) {
        val preview = remember(allProfiles, vaultCount) {
            viewModel.getExportPreview()
        }
        ExportProfilesDialog(
            exportPreview = preview,
            onConfirmExportJson = {
                viewModel.buildStandardJsonExport()
                    .onSuccess { jsonText ->
                        pendingExportPayload = jsonText to false
                        showExportProfilesDialog = false
                        exportSafLauncher.launch(BackupCryptoManager.STANDARD_EXPORT_FILENAME)
                    }
                    .onFailure { err ->
                        showExportProfilesDialog = false
                        viewModel.postFeedback(err.message ?: "ส่งออกโปรไฟล์ไม่สำเร็จ", isError = true)
                    }
            },
            onDismiss = { showExportProfilesDialog = false }
        )
    }

    if (showCreateEncryptedBackupDialog) {
        val preview = remember(allProfiles, vaultCount) {
            viewModel.getExportPreview()
        }
        CreateEncryptedBackupDialog(
            exportPreview = preview,
            onCreateBackup = { passphrase, includeCredentials ->
                viewModel.buildEncryptedFullBackup(passphrase, includeCredentials)
                    .map { encryptedText ->
                        pendingExportPayload = encryptedText to true
                        showCreateEncryptedBackupDialog = false
                        exportSafLauncher.launch(BackupCryptoManager.ENCRYPTED_BACKUP_FILENAME)
                    }
            },
            onDismiss = { showCreateEncryptedBackupDialog = false }
        )
    }

    importedEncryptedRawText?.let { raw ->
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
                        importedEncryptedRawText = null
                        null
                    }
                    is BackupImportResult.Error -> res.message
                }
            },
            onDismiss = { importedEncryptedRawText = null }
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

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = LocalObsidianColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(ObsidianRadius.md))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = accentColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(ObsidianRadius.sm),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = title,
            tint = colors.textSecondary
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    switchModifier: Modifier = Modifier
) {
    val colors = LocalObsidianColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.textPrimary else colors.textSecondary
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier
        )
    }
}
