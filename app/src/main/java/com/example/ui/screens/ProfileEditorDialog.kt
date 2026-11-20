package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.AuthMethodType
import com.example.domain.ProfileGroup
import com.example.domain.TerminalThemePreset
import com.example.profiles.ProfileEditorDraft
import com.example.ui.components.BannerTone
import com.example.ui.components.ObsidianStatusBanner
import com.example.ui.components.ObsidianSurfaceCard
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianRadius
import com.example.ui.theme.ObsidianSpacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorDialog(
    draft: ProfileEditorDraft,
    groups: List<ProfileGroup>,
    globalTailscaleEnabled: Boolean = false,
    onUpdate: ((ProfileEditorDraft) -> ProfileEditorDraft) -> Unit,
    onCancel: () -> Unit,
    onSaveProfile: () -> Unit,
    onSaveAndConnect: () -> Unit
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()?.let { keyText ->
                onUpdate { it.copy(privateKeyInput = keyText, clearExistingCredential = false) }
            }
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = colors.background,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (draft.existingId == null) "เพิ่มโปรไฟล์เซิร์ฟเวอร์" else "แก้ไขโปรไฟล์",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "บันทึกโปรไฟล์ถาวรก่อนเชื่อมต่อ SSH",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "ปิดหน้าแก้ไข"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onUpdate { it.copy(isFavorite = !it.isFavorite) } },
                            modifier = Modifier.testTag("editor_favorite_toggle")
                        ) {
                            Icon(
                                imageVector = if (draft.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "ปักหมุดรายการโปรด",
                                tint = if (draft.isFavorite) colors.lime else colors.textSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surface
                    )
                )
            },
            bottomBar = {
                Surface(color = colors.surface) {
                    Column {
                        HorizontalDivider(color = colors.border)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("editor_cancel_button")
                            ) {
                                Text("ยกเลิก")
                            }
                            OutlinedButton(
                                onClick = onSaveProfile,
                                enabled = !draft.isSaving,
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("editor_save_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("บันทึก")
                            }
                            Button(
                                onClick = onSaveAndConnect,
                                enabled = !draft.isSaving,
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.cyan,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(48.dp)
                                    .testTag("editor_save_connect_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("บันทึก & เชื่อมต่อ", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!draft.validationError.isNullOrBlank()) {
                    ObsidianStatusBanner(
                        message = draft.validationError,
                        tone = BannerTone.ERROR
                    )
                }

                if (draft.duplicateWarningProfiles.isNotEmpty()) {
                    ObsidianStatusBanner(
                        message = "พบโปรไฟล์อื่นที่ใช้ ${draft.username}@${draft.host}:${draft.portText} แล้ว (${draft.duplicateWarningProfiles.joinToString { it.name }}) — สามารถบันทึกแยกได้หากตั้งใจใช้คนละโปรไฟล์",
                        tone = BannerTone.WARNING
                    )
                }

                // Required endpoint section
                ObsidianSurfaceCard(elevated = true) {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ข้อมูลเซิร์ฟเวอร์ (จำเป็น)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.cyan
                        )

                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { v -> onUpdate { it.copy(name = v) } },
                            label = { Text("ชื่อโปรไฟล์ (เช่น Production VPS)") },
                            singleLine = true,
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_name_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = draft.host,
                                onValueChange = { v -> onUpdate { it.copy(host = v) } },
                                label = { Text("Hostname / IP / Tailnet") },
                                singleLine = true,
                                textStyle = MonoCodeStyle,
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                modifier = Modifier
                                    .weight(2.2f)
                                    .testTag("editor_host_input")
                            )
                            OutlinedTextField(
                                value = draft.portText,
                                onValueChange = { v -> onUpdate { it.copy(portText = v.filter(Char::isDigit).take(5)) } },
                                label = { Text("Port") },
                                singleLine = true,
                                textStyle = MonoCodeStyle,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_port_input")
                            )
                        }

                        OutlinedTextField(
                            value = draft.username,
                            onValueChange = { v -> onUpdate { it.copy(username = v) } },
                            label = { Text("Username") },
                            singleLine = true,
                            textStyle = MonoCodeStyle,
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_username_input")
                        )
                    }
                }

                // Authentication & Credential Vault section
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = colors.lime)
                            Column {
                                Text(
                                    text = "วิธีรับรองตัวตน & Keystore Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "เข้ารหัส AES-256-GCM แยกจาก Metadata",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuthMethodType.entries.forEach { method ->
                                FilterChip(
                                    selected = draft.authMethod == method,
                                    onClick = { onUpdate { it.copy(authMethod = method) } },
                                    label = { Text(method.label) },
                                    modifier = Modifier.testTag("auth_method_${method.name}")
                                )
                            }
                        }

                        if (draft.hasStoredCredential && !draft.clearExistingCredential) {
                            Surface(
                                color = colors.lime.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(ObsidianRadius.sm),
                                border = BorderStroke(1.dp, colors.lime.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "มี Credential เข้ารหัสบันทึกไว้แล้ว",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.lime
                                    )
                                    TextButton(
                                        onClick = { onUpdate { it.copy(clearExistingCredential = true) } }
                                    ) {
                                        Text("ลบออก", color = colors.red)
                                    }
                                }
                            }
                        }

                        when (draft.authMethod) {
                            AuthMethodType.PASSWORD -> {
                                OutlinedTextField(
                                    value = draft.passwordInput,
                                    onValueChange = { v -> onUpdate { it.copy(passwordInput = v, clearExistingCredential = false) } },
                                    label = {
                                        Text(
                                            if (draft.hasStoredCredential && !draft.clearExistingCredential) {
                                                "รหัสผ่านใหม่ (เว้นว่างเพื่อใช้ค่าเดิม)"
                                            } else {
                                                "รหัสผ่าน SSH"
                                            }
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(ObsidianRadius.md),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "แสดง/ซ่อนรหัสผ่าน"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("editor_password_input")
                                )
                            }
                            AuthMethodType.PRIVATE_KEY, AuthMethodType.PRIVATE_KEY_PASSPHRASE -> {
                                OutlinedButton(
                                    onClick = { keyFilePicker.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(ObsidianRadius.md),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("เลือกไฟล์ Private Key (SAF)")
                                }
                                OutlinedTextField(
                                    value = draft.privateKeyInput,
                                    onValueChange = { v -> onUpdate { it.copy(privateKeyInput = v, clearExistingCredential = false) } },
                                    label = {
                                        Text(
                                            if (draft.hasStoredCredential && !draft.clearExistingCredential) {
                                                "วาง Private Key ใหม่ (เว้นว่างเพื่อใช้คีย์เดิม)"
                                            } else {
                                                "วางเนื้อหา Private Key (PEM / OpenSSH)"
                                            }
                                        )
                                    },
                                    minLines = 3,
                                    maxLines = 6,
                                    textStyle = MonoCodeStyle,
                                    shape = RoundedCornerShape(ObsidianRadius.md),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("editor_private_key_input")
                                )
                                if (draft.authMethod == AuthMethodType.PRIVATE_KEY_PASSPHRASE) {
                                    OutlinedTextField(
                                        value = draft.passphraseInput,
                                        onValueChange = { v -> onUpdate { it.copy(passphraseInput = v) } },
                                        label = { Text("Passphrase (ใช้เชื่อมต่อทันที ไม่เก็บลงดิสก์ถาวร)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(ObsidianRadius.md),
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Organization & Metadata section
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("การจัดกลุ่มและแท็ก", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            groups.forEach { group ->
                                FilterChip(
                                    selected = draft.groupName == group.name,
                                    onClick = { onUpdate { it.copy(groupName = group.name) } },
                                    label = { Text(group.name) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = draft.tagsText,
                            onValueChange = { v -> onUpdate { it.copy(tagsText = v) } },
                            label = { Text("Tags (คั่นด้วยจุลภาค เช่น docker, nas, arm64)") },
                            singleLine = true,
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { v -> onUpdate { it.copy(description = v) } },
                            label = { Text("คำอธิบายเพิ่มเติม (ไม่บังคับ)") },
                            maxLines = 2,
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Network & Tailscale Automation
                ObsidianSurfaceCard {
                    Column(
                        modifier = Modifier.padding(ObsidianSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = colors.cyan)
                            Column {
                                Text(
                                    text = "เครือข่ายและ Tailscale อัตโนมัติ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "ใช้แอป Tailscale อย่างเป็นทางการบน Android",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "เปิด Tailscale อัตโนมัติก่อนเชื่อม SSH",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (globalTailscaleEnabled) colors.textPrimary else colors.textSecondary
                                )
                                Text(
                                    text = if (globalTailscaleEnabled) {
                                        "สั่งเปิด Tailscale ก่อนเชื่อม และปิดอัตโนมัติเฉพาะเมื่อแอปเป็นคนเริ่มและปิดทุกแท็บแล้ว"
                                    } else {
                                        "Master Switch ใน Settings ปิดอยู่ (ค่าของโปรไฟล์ยังถูกเก็บไว้ แต่จะไม่สั่งเปิด Tailscale อัตโนมัติ)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = draft.autoTailscaleConnect,
                                enabled = globalTailscaleEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdate { it.copy(autoTailscaleConnect = enabled) }
                                },
                                modifier = Modifier.testTag("editor_auto_tailscale_switch")
                            )
                        }
                    }
                }

                // Advanced & Terminal preferences
                ObsidianSurfaceCard {
                    Column(modifier = Modifier.padding(ObsidianSpacing.md)) {
                        TextButton(
                            onClick = { showAdvanced = !showAdvanced },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = colors.cyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showAdvanced) "ซ่อนการตั้งค่าขั้นสูงและเทอร์มินัล" else "ตั้งค่าขั้นสูงและเทอร์มินัล (PTY / Timeout / Theme)",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = draft.connectTimeoutText,
                                        onValueChange = { v -> onUpdate { it.copy(connectTimeoutText = v.filter(Char::isDigit).take(3)) } },
                                        label = { Text("Timeout (วินาที)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = draft.keepAliveText,
                                        onValueChange = { v -> onUpdate { it.copy(keepAliveText = v.filter(Char::isDigit).take(3)) } },
                                        label = { Text("Keepalive (วินาที)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = draft.initialWorkingDir,
                                    onValueChange = { v -> onUpdate { it.copy(initialWorkingDir = v) } },
                                    label = { Text("Initial Working Directory (เช่น /var/www)") },
                                    singleLine = true,
                                    textStyle = MonoCodeStyle,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Terminal Type (TERM)", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("xterm-256color", "vt100", "screen-256color").forEach { term ->
                                        FilterChip(
                                            selected = draft.terminalType == term,
                                            onClick = { onUpdate { it.copy(terminalType = term) } },
                                            label = { Text(term) }
                                        )
                                    }
                                }
                                Text("ธีมเทอร์มินัล", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TerminalThemePreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = draft.terminalTheme == preset,
                                            onClick = { onUpdate { it.copy(terminalTheme = preset) } },
                                            label = { Text(preset.displayName) }
                                        )
                                    }
                                }
                                Text("ขนาดตัวอักษร: ${draft.terminalFontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.titleSmall)
                                Slider(
                                    value = draft.terminalFontSizeSp,
                                    onValueChange = { sz -> onUpdate { it.copy(terminalFontSizeSp = sz) } },
                                    valueRange = 10f..22f
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("ปักหมุดเป็นรายการโปรด", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = draft.isFavorite,
                                        onCheckedChange = { fav -> onUpdate { it.copy(isFavorite = fav) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
