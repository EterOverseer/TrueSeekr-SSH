package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.AuthMethodType
import com.example.domain.CredentialPromptRequest
import com.example.domain.HostKeyVerificationRequest
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianRadius

@Composable
fun UnknownHostKeyDialog(
    request: HostKeyVerificationRequest.UnknownHost,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val colors = LocalObsidianColors.current
    AlertDialog(
        onDismissRequest = onReject,
        containerColor = colors.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.amber.copy(alpha = 0.14f))
                    .border(1.dp, colors.amber.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "ตรวจสอบ Host Key",
                    tint = colors.amber,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "ยืนยันลายนิ้วมือเซิร์ฟเวอร์ (Host Key)",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "เซิร์ฟเวอร์ ${request.host}:${request.port} ยังไม่อยู่ในรายการที่เชื่อถือ โปรดตรวจสอบ SHA-256 Fingerprint ก่อนอนุญาตเชื่อมต่อ:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Surface(
                    color = colors.elevated,
                    shape = RoundedCornerShape(ObsidianRadius.md),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Algorithm: ${request.keyType}",
                            style = MonoCodeStyle,
                            color = colors.cyan
                        )
                        Text(
                            text = request.actualSha256,
                            style = MonoCodeStyle.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = request.actualColonHex,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                    }
                }
                Text(
                    text = "เมื่อกด \"เชื่อถือและเชื่อมต่อ\" ระบบจะบันทึก Host Key นี้ไว้ตรวจสอบในการเชื่อมต่อครั้งถัดไป",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                modifier = Modifier.testTag("host_key_approve_button")
            ) {
                Text("เชื่อถือและเชื่อมต่อ")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onReject,
                modifier = Modifier.testTag("host_key_reject_button")
            ) {
                Text("ปฏิเสธ")
            }
        }
    )
}

@Composable
fun HostKeyMismatchDialog(
    mismatch: HostKeyVerificationRequest.KeyMismatch,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.red.copy(alpha = 0.14f))
                    .border(1.dp, colors.red.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GppBad,
                    contentDescription = "คำเตือน Host Key เปลี่ยนแปลง",
                    tint = colors.red,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "คำเตือนความปลอดภัย: Host Key เปลี่ยนแปลง!",
                color = colors.red,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "กุญแจของ ${mismatch.host}:${mismatch.port} (${mismatch.keyType}) ไม่ตรงกับค่าที่เคยบันทึกไว้ อาจเกิดจากการแอบอ้างเซิร์ฟเวอร์ (MITM) หรือเซิร์ฟเวอร์ถูกติดตั้งระบบใหม่ การเชื่อมต่อถูกปฏิเสธโดยอัตโนมัติ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary
                )
                Surface(
                    color = colors.red.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(ObsidianRadius.md),
                    border = BorderStroke(1.dp, colors.red.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ค่าที่คาดหวัง (Trusted):",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = mismatch.expectedSha256,
                            style = MonoCodeStyle,
                            color = colors.lime
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ค่าที่ได้รับจากเซิร์ฟเวอร์ (Actual):",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = mismatch.actualSha256,
                            style = MonoCodeStyle,
                            color = colors.red
                        )
                    }
                }
                Text(
                    text = "หากมั่นใจว่าเซิร์ฟเวอร์มีการสร้าง Host Key ใหม่จริง ให้ไปที่ Settings > Trusted Hosts เพื่อลบ Fingerprint เดิมออกก่อน",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                modifier = Modifier.testTag("host_key_mismatch_dismiss")
            ) {
                Text("ปิดการแจ้งเตือน")
            }
        }
    )
}

@Composable
fun CredentialPromptDialog(
    prompt: CredentialPromptRequest,
    onDismiss: () -> Unit,
    onSubmit: (
        password: CharArray?,
        privateKeyPem: String?,
        passphrase: CharArray?,
        saveToVault: Boolean
    ) -> Unit
) {
    val colors = LocalObsidianColors.current
    var secretInput by remember(prompt) { mutableStateOf("") }
    var passphraseInput by remember(prompt) { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var saveToVault by remember { mutableStateOf(!prompt.needsPassphraseOnly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.cyan.copy(alpha = 0.14f))
                    .border(1.dp, colors.cyan.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "ยืนยันตัวตน SSH",
                    tint = colors.cyan
                )
            }
        },
        title = {
            Text(text = prompt.profile.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${prompt.profile.username}@${prompt.profile.host}:${prompt.profile.port}",
                    style = MonoCodeStyle,
                    color = colors.cyan
                )
                Text(
                    text = prompt.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                if (prompt.needsPassphraseOnly) {
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Passphrase ของ Private Key") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "สลับการแสดงรหัส"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_passphrase_input")
                    )
                    Text(
                        text = "เพื่อความปลอดภัย Passphrase จะไม่ถูกบันทึกลงดิสก์ถาวร",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                } else if (prompt.profile.authMethod == AuthMethodType.PASSWORD) {
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        label = { Text("รหัสผ่าน SSH") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "สลับการแสดงรหัส"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_password_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveToVault, onCheckedChange = { saveToVault = it })
                        Text(
                            text = "บันทึกรหัสผ่านแบบเข้ารหัสใน Keystore Vault",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        label = { Text("SSH Private Key (PEM / OpenSSH)") },
                        minLines = 4,
                        maxLines = 6,
                        textStyle = MonoCodeStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_private_key_input")
                    )
                    if (prompt.profile.authMethod == AuthMethodType.PRIVATE_KEY_PASSPHRASE) {
                        OutlinedTextField(
                            value = passphraseInput,
                            onValueChange = { passphraseInput = it },
                            label = { Text("Passphrase (เฉพาะ Session นี้)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveToVault, onCheckedChange = { saveToVault = it })
                        Text(
                            text = "บันทึก Private Key แบบเข้ารหัสใน Vault",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (prompt.needsPassphraseOnly) {
                        onSubmit(null, null, passphraseInput.toCharArray(), false)
                    } else if (prompt.profile.authMethod == AuthMethodType.PASSWORD) {
                        onSubmit(secretInput.toCharArray(), null, null, saveToVault)
                    } else {
                        onSubmit(
                            null,
                            secretInput,
                            passphraseInput.takeIf { it.isNotEmpty() }?.toCharArray(),
                            saveToVault
                        )
                    }
                },
                enabled = if (prompt.needsPassphraseOnly) passphraseInput.isNotEmpty() else secretInput.isNotBlank(),
                modifier = Modifier.testTag("prompt_credential_submit")
            ) {
                Text("เชื่อมต่อ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@Composable
fun AppLockOverlay(
    biometricEnabled: Boolean,
    hasPinConfigured: Boolean,
    errorMessage: String?,
    onRequestBiometric: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    val colors = LocalObsidianColors.current
    var pinInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        ObsidianSurfaceCard(
            elevated = true,
            shape = RoundedCornerShape(ObsidianRadius.xl),
            accentBorder = colors.cyan.copy(alpha = 0.35f),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.cyan.copy(alpha = 0.14f))
                        .border(1.dp, colors.cyan.copy(alpha = 0.40f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "TrueSeekrSSH ถูกล็อก",
                        tint = colors.cyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "TrueSeekrSSH ถูกล็อก",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "ยืนยันตัวตนเพื่อเข้าถึงโปรไฟล์และ Keystore Vault",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                if (hasPinConfigured) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 12) pinInput = it.filter(Char::isDigit) },
                        label = { Text("รหัส PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_unlock_input")
                    )
                    Button(
                        onClick = {
                            onSubmitPin(pinInput)
                            pinInput = ""
                        },
                        enabled = pinInput.length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pin_unlock_button")
                    ) {
                        Text("ปลดล็อกด้วย PIN")
                    }
                }
                if (biometricEnabled) {
                    OutlinedButton(
                        onClick = onRequestBiometric,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("biometric_unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ปลดล็อกด้วย Biometric")
                    }
                }
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = colors.red,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
