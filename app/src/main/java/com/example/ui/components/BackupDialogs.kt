package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.security.BackupCryptoManager
import com.example.security.ExportBackupPreview
import com.example.security.ImportDuplicateStrategy
import com.example.security.ImportPreviewSummary
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.ObsidianRadius
import java.io.ByteArrayOutputStream

enum class ImportFileMode {
    ANY_SUPPORTED,
    ENCRYPTED_ONLY
}

fun readBackupTextFromUri(context: Context, uri: Uri): Result<String> = runCatching {
    val stream = context.contentResolver.openInputStream(uri)
        ?: error("ไม่สามารถเปิดอ่านไฟล์ที่เลือกได้")
    stream.use { input ->
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var totalBytes = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > BackupCryptoManager.MAX_BACKUP_BYTES) {
                error("ไฟล์สำรองมีขนาดใหญ่เกิน 10 MB")
            }
            out.write(buffer, 0, read)
        }
        val text = out.toByteArray().toString(Charsets.UTF_8)
        if (text.isBlank()) {
            error("ไฟล์สำรองข้อมูลว่างเปล่าหรือไม่สามารถอ่านได้")
        }
        text
    }
}

fun writeBackupTextToUri(context: Context, uri: Uri, payload: String): Result<Unit> = runCatching {
    val stream = context.contentResolver.openOutputStream(uri)
        ?: error("ไม่สามารถเปิดไฟล์ปลายทางเพื่อบันทึกได้")
    stream.use { out ->
        out.write(payload.toByteArray(Charsets.UTF_8))
        out.flush()
    }
}

@Composable
fun ExportProfilesDialog(
    exportPreview: ExportBackupPreview,
    onConfirmExportJson: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current

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
                .widthIn(max = 500.dp)
                .heightIn(max = 620.dp)
                .testTag("export_profiles_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = colors.cyan)
                    Text(
                        text = "ส่งออกโปรไฟล์ (Export Profiles)",
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
                            Text(
                                text = "Export Preview (${exportPreview.totalProfiles} โปรไฟล์)",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "รูปแบบ: Standard JSON (.json) — ส่งออกเฉพาะข้อมูลการตั้งค่าโปรไฟล์และกลุ่ม",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "• ไม่รวมรหัสผ่าน, Private Key, Android Keystore หรือ Trusted Host Keys เพื่อความปลอดภัย",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.amber
                            )
                        }
                    }

                    if (exportPreview.totalProfiles == 0) {
                        Text(
                            text = "ยังไม่มีโปรไฟล์เซิร์ฟเวอร์ที่บันทึกไว้สำหรับส่งออก",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.red
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("ยกเลิก")
                    }
                    Button(
                        onClick = onConfirmExportJson,
                        enabled = exportPreview.totalProfiles > 0,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_export_json_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เลือกตำแหน่งบันทึก .json", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateEncryptedBackupDialog(
    exportPreview: ExportBackupPreview,
    onCreateBackup: (passphrase: String, includeCredentials: Boolean) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var includeCredentials by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val passphraseValid = passphrase.length >= 6 && passphrase == confirmPassphrase

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
                .testTag("create_encrypted_backup_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = colors.lime)
                    Text(
                        text = "สร้างไฟล์สำรองเข้ารหัส",
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
                            Text(
                                text = "Backup Preview (${exportPreview.totalProfiles} โปรไฟล์ • .tsshbackup)",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "• มี Credential ใน Vault: ${exportPreview.storedCredentialCount} • ไม่มีใน Vault: ${exportPreview.missingStoredCredentialCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "• เข้ารหัส AES-256-GCM + PBKDF2-HMAC-SHA256 (210,000 รอบ)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.cyan
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeCredentials = !includeCredentials }
                    ) {
                        Checkbox(
                            checked = includeCredentials,
                            onCheckedChange = { includeCredentials = it },
                            modifier = Modifier.testTag("create_encrypted_include_creds_checkbox")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "รวมรหัสผ่านและ Private Key จาก Vault",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "เมื่อนำไปกู้คืนในเครื่องใหม่ Credential จะถูกเข้ารหัสซ้ำด้วย Android Keystore ทันที",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            validationError = null
                        },
                        label = { Text("รหัสผ่านไฟล์สำรอง (อย่างน้อย 6 ตัว)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_encrypted_passphrase_input")
                    )

                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = {
                            confirmPassphrase = it
                            validationError = null
                        },
                        label = { Text("ยืนยันรหัสผ่านอีกครั้ง") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassphrase.isNotEmpty() && confirmPassphrase != passphrase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_encrypted_confirm_passphrase_input")
                    )

                    if (passphrase.isNotEmpty() && passphrase.length < 6) {
                        Text(
                            text = "รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.amber
                        )
                    } else if (confirmPassphrase.isNotEmpty() && confirmPassphrase != passphrase) {
                        Text(
                            text = "รหัสผ่านทั้งสองช่องไม่ตรงกัน",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.red
                        )
                    }

                    if (!validationError.isNullOrBlank()) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.red
                        )
                    }

                    if (exportPreview.totalProfiles == 0) {
                        Text(
                            text = "ยังไม่มีโปรไฟล์เซิร์ฟเวอร์ที่บันทึกไว้สำหรับสำรองข้อมูล",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.red
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("ยกเลิก")
                    }
                    Button(
                        onClick = {
                            onCreateBackup(passphrase, includeCredentials)
                                .onFailure { err ->
                                    validationError = err.message ?: "ไม่สามารถเข้ารหัสไฟล์สำรองได้"
                                }
                        },
                        enabled = exportPreview.totalProfiles > 0 && passphraseValid,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_create_encrypted_backup_button")
                    ) {
                        Text("สร้าง .tsshbackup", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun DecryptBackupPassphraseDialog(
    onDecrypt: (passphrase: String) -> String?,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var passphrase by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

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
                .widthIn(max = 500.dp)
                .heightIn(max = 600.dp)
                .testTag("decrypt_backup_passphrase_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = colors.cyan)
                    Text(
                        text = "ถอดรหัสไฟล์สำรองข้อมูล",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                }

                Text(
                    text = "ไฟล์นี้เข้ารหัสด้วย AES-256-GCM + PBKDF2 กรุณากรอกรหัสผ่านสำรองข้อมูลเพื่อดูตัวอย่างก่อนนำเข้า:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
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
                    isError = !feedback.isNullOrBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_decrypt_passphrase_input")
                )

                if (!feedback.isNullOrBlank()) {
                    Text(
                        text = feedback!!,
                        color = colors.red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("backup_decrypt_error_text")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("ยกเลิก")
                    }
                    Button(
                        onClick = {
                            val err = onDecrypt(passphrase)
                            if (err != null) {
                                feedback = err
                            }
                        },
                        enabled = passphrase.isNotEmpty(),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_decrypt_backup_button")
                    ) {
                        Text("ถอดรหัสและตรวจสอบ")
                    }
                }
            }
        }
    }
}

@Composable
fun ImportErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(ObsidianRadius.xl),
            border = BorderStroke(1.dp, colors.red.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 480.dp)
                .testTag("backup_import_error_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.red)
                    Text(
                        text = "ไม่สามารถอ่านไฟล์สำรองข้อมูล",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.red
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("ตกลง")
                    }
                }
            }
        }
    }
}

@Composable
fun BackupExportImportDialog(
    exportPreview: ExportBackupPreview,
    isSelectedSubset: Boolean = false,
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
                    text = if (isSelectedSubset) {
                        "ส่งออกโปรไฟล์ที่เลือก (${exportPreview.totalProfiles})"
                    } else {
                        "สำรองและกู้คืนโปรไฟล์"
                    },
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
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) { Text("ปิด") }
                }
            }
        }
    }
}

@Composable
fun ImportPreviewDialog(
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
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) { Text("ยกเลิก") }
                    Button(
                        onClick = { onConfirmImport(selectedStrategy) { err -> applyError = err } },
                        enabled = preview.newCount > 0 || (preview.duplicateCount > 0 && selectedStrategy != ImportDuplicateStrategy.SKIP),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_import_button")
                    ) {
                        Text("ยืนยันนำเข้า")
                    }
                }
            }
        }
    }
}
