package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.Formatters
import com.example.domain.CommandSnippet
import com.example.domain.SessionStatus
import com.example.domain.TerminalFontFamily
import com.example.network.TailscaleProgressState
import com.example.network.VpnOwnership
import com.example.settings.AppSettings
import com.example.ssh.ManagedSessionUiState
import com.example.terminal.MetricsLoadState
import com.example.terminal.TerminalPaletteProvider
import com.example.terminal.TerminalViewModel
import com.example.ui.components.BannerTone
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.HostTrustIndicator
import com.example.ui.components.ObsidianEmptyState
import com.example.ui.components.ObsidianMetadataTag
import com.example.ui.components.ObsidianStatusBanner
import com.example.ui.components.ObsidianSurfaceCard
import com.example.ui.theme.LocalObsidianColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ObsidianRadius
import com.example.ui.theme.ObsidianSpacing
import com.example.ui.theme.performSubtleConfirm
import kotlinx.coroutines.delay
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onGoToServers: () -> Unit,
    appSettings: AppSettings = AppSettings()
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()
    val searchVisible by viewModel.searchVisible.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchMatches by viewModel.searchMatches.collectAsStateWithLifecycle()
    val metricsState by viewModel.metricsState.collectAsStateWithLifecycle()

    val activeSession = sessions.firstOrNull { it.sessionId == activeSessionId } ?: sessions.firstOrNull()
    val handle = activeSession?.let { viewModel.getHandle(it.sessionId) }

    var showSoftKeyboard by remember { mutableStateOf(false) }
    var showFunctionKeys by remember { mutableStateOf(false) }
    var showSnippetsSheet by remember { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    var snippetConfirmTarget by remember { mutableStateOf<CommandSnippet?>(null) }

    val monoTypeface = remember(context, appSettings.terminalFontFamily) {
        when (appSettings.terminalFontFamily) {
            TerminalFontFamily.JETBRAINS_MONO ->
                ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
            TerminalFontFamily.SYSTEM_MONO -> Typeface.MONOSPACE
        }
    }
    val focusRequester = remember { FocusRequester() }

    if (sessions.isEmpty() || activeSession == null || handle == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(ObsidianSpacing.xxl),
            contentAlignment = Alignment.Center
        ) {
            ObsidianEmptyState(
                icon = Icons.Default.Terminal,
                title = "ยังไม่มี Terminal Session ที่เปิดอยู่",
                subtitle = "เลือกเซิร์ฟเวอร์จากหน้า Servers แล้วแตะ Connect เพื่อเปิด Interactive PTY Shell",
                actionLabel = "ไปยังหน้าเซิร์ฟเวอร์",
                onAction = onGoToServers
            )
        }
        return
    }

    val modState by handle.modifierManager.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        // Obsidian Terminal Session Tabs & Controls
        Surface(color = colors.surface) {
            Column {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sessions, key = { it.sessionId }) { session ->
                        val isSelected = session.sessionId == activeSession.sessionId
                        val dotColor = when (session.status) {
                            SessionStatus.CONNECTED -> colors.lime
                            SessionStatus.CONNECTING,
                            SessionStatus.VERIFYING_HOST,
                            SessionStatus.AUTHENTICATING -> colors.cyan
                            SessionStatus.RECONNECTING -> colors.amber
                            SessionStatus.ERROR -> colors.red
                            else -> colors.textSecondary
                        }
                        Surface(
                            color = if (isSelected) colors.cyan.copy(alpha = 0.14f) else colors.elevated,
                            shape = RoundedCornerShape(ObsidianRadius.sm),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colors.cyan.copy(alpha = 0.55f) else colors.subtleBorder
                            ),
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(ObsidianRadius.sm))
                                .clickable { viewModel.selectTab(session.sessionId) }
                                .testTag("terminal_tab_${session.sessionId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = session.profile.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) colors.cyan else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { viewModel.closeTab(session.sessionId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "ปิดแท็บ",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Session Status + Horizontal Uptime & Host Info Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionStatusPill(status = activeSession.status, compact = true)
                    val showVpnBadge = activeSession.tailscaleState != TailscaleProgressState.IDLE ||
                        activeSession.vpnOwnership == VpnOwnership.APP_REQUESTED ||
                        activeSession.vpnOwnership == VpnOwnership.EXTERNAL
                    if (showVpnBadge) {
                        val badgeText = when {
                            activeSession.tailscaleState.isInProgress -> activeSession.tailscaleState.label
                            activeSession.vpnOwnership == VpnOwnership.EXTERNAL -> "External VPN"
                            activeSession.vpnOwnership == VpnOwnership.UNKNOWN -> "VPN (Unknown)"
                            activeSession.vpnOwnership == VpnOwnership.APP_REQUESTED -> "Tailscale"
                            else -> activeSession.tailscaleState.label
                        }
                        val badgeTint = when {
                            activeSession.tailscaleState.isError -> colors.red
                            activeSession.vpnOwnership == VpnOwnership.EXTERNAL ||
                                activeSession.vpnOwnership == VpnOwnership.UNKNOWN -> colors.amber
                            activeSession.vpnOwnership == VpnOwnership.APP_REQUESTED -> colors.lime
                            else -> colors.cyan
                        }
                        ObsidianMetadataTag(
                            text = badgeText,
                            tint = badgeTint,
                            modifier = Modifier.testTag("terminal_tailscale_status_pill")
                        )
                    }
                    if (activeSession.status == SessionStatus.CONNECTED && activeSession.connectedAtMs != null) {
                        TerminalUptimeText(
                            connectedAtMs = activeSession.connectedAtMs,
                            color = colors.lime
                        )
                    }
                    if (activeSession.latencyMs != null) {
                        Text(
                            text = "${activeSession.latencyMs}ms",
                            style = MonoCodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                            color = colors.cyan,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
                    ) {
                        Text(
                            text = "${activeSession.profile.username}@${activeSession.profile.host}:${activeSession.profile.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (activeSession.meta.hostFingerprint.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Host Key Verified",
                                tint = colors.cyan,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Quick Terminal Action Toolbar Row (uncramped full-width horizontal strip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            showDashboardSheet = true
                            viewModel.loadServerMetrics(activeSession.sessionId)
                        },
                        modifier = Modifier.testTag("terminal_dashboard_button")
                    ) {
                        Icon(Icons.Default.MonitorHeart, contentDescription = "ข้อมูลเซิร์ฟเวอร์ (Dashboard)", tint = colors.cyan)
                    }
                    IconButton(
                        onClick = { showSnippetsSheet = true },
                        modifier = Modifier.testTag("terminal_snippets_button")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Command Snippets")
                    }
                    IconButton(
                        onClick = { viewModel.toggleSearch() },
                        modifier = Modifier.testTag("terminal_search_toggle")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "ค้นหาผลลัพธ์เทอร์มินัล")
                    }
                    IconButton(onClick = {
                        val selected = handle.selectionController?.copySelection()
                        val textToCopy = if (!selected.isNullOrEmpty()) {
                            selected
                        } else {
                            handle.scrollbackBuffer.getAllLines().takeLast(20).joinToString("\n")
                        }
                        copyToClipboard(context, textToCopy)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "คัดลอก")
                    }
                    IconButton(onClick = {
                        pasteFromClipboard(context)?.let { text ->
                            handle.insertTextWithoutExecuting(text)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "วาง")
                    }
                    if (activeSession.status == SessionStatus.CONNECTED) {
                        IconButton(
                            onClick = { viewModel.disconnect(activeSession.sessionId) },
                            modifier = Modifier.testTag("terminal_disconnect_button")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = "ตัดการเชื่อมต่อ", tint = colors.amber)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.reconnect(activeSession.sessionId) },
                            modifier = Modifier.testTag("terminal_reconnect_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "เชื่อมต่อใหม่", tint = colors.cyan)
                        }
                    }
                }

                if (!activeSession.errorMessage.isNullOrBlank()) {
                    val actionLabel = when {
                        activeSession.showInstallTailscaleAction -> "Install Tailscale"
                        activeSession.showOpenTailscaleAction -> "Open Tailscale"
                        else -> "Reconnect"
                    }
                    val onBannerAction: () -> Unit = when {
                        activeSession.showInstallTailscaleAction -> { { viewModel.openTailscalePlayStore() } }
                        activeSession.showOpenTailscaleAction -> { { viewModel.openTailscaleApp() } }
                        else -> { { viewModel.reconnect(activeSession.sessionId) } }
                    }
                    ObsidianStatusBanner(
                        message = activeSession.errorMessage,
                        tone = BannerTone.ERROR,
                        actionLabel = actionLabel,
                        onAction = onBannerAction,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else if (!activeSession.networkWarning.isNullOrBlank()) {
                    ObsidianStatusBanner(
                        message = activeSession.networkWarning,
                        tone = BannerTone.WARNING,
                        actionLabel = "Reconnect",
                        onAction = { viewModel.reconnect(activeSession.sessionId) },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else if (!activeSession.tailscaleNotice.isNullOrBlank() && activeSession.status == SessionStatus.CONNECTING) {
                    ObsidianStatusBanner(
                        message = activeSession.tailscaleNotice,
                        tone = BannerTone.INFO,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                AnimatedVisibility(visible = searchVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it, activeSession.sessionId) },
                            label = { Text("ค้นหาใน Scrollback (${searchMatches.size} รายการ)") },
                            singleLine = true,
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("terminal_search_input")
                        )
                        if (searchMatches.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 110.dp)
                            ) {
                                items(searchMatches.take(20)) { (lineNo, text) ->
                                    Text(
                                        text = "#$lineNo: $text",
                                        style = MonoCodeStyle,
                                        color = colors.cyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.border)
            }
        }

        // High-Readability ConnectBot termlib Terminal Canvas
        val themePreset = activeSession.profile.terminalTheme
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalPaletteProvider.backgroundColor(themePreset))
        ) {
            Terminal(
                terminalEmulator = handle.emulator,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("terminal_canvas"),
                typeface = monoTypeface,
                initialFontSize = activeSession.profile.terminalFontSizeSp.sp,
                minFontSize = 8.sp,
                maxFontSize = 24.sp,
                backgroundColor = TerminalPaletteProvider.backgroundColor(themePreset),
                foregroundColor = TerminalPaletteProvider.foregroundColor(themePreset),
                selectionBackgroundColor = TerminalPaletteProvider.selectionBgColor(themePreset),
                selectionForegroundColor = TerminalPaletteProvider.selectionFgColor(themePreset),
                keyboardEnabled = true,
                showSoftKeyboard = showSoftKeyboard,
                focusRequester = focusRequester,
                onTerminalTap = {
                    showSoftKeyboard = true
                    runCatching { focusRequester.requestFocus() }
                },
                modifierManager = handle.modifierManager,
                onSelectionControllerAvailable = { handle.selectionController = it },
                onPasteRequest = {
                    pasteFromClipboard(context)?.let { handle.insertTextWithoutExecuting(it) }
                }
            )
        }

        // Mobile Developer Extra Keyboard Toolbar
        if (appSettings.showExtraKeyboardToolbar) {
            Surface(color = colors.surface) {
                Column {
                    HorizontalDivider(color = colors.border)
                    AnimatedVisibility(visible = showFunctionKeys) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fKeys = listOf(
                                "F1" to VTermKey.FUNCTION_1,
                                "F2" to VTermKey.FUNCTION_2,
                                "F3" to VTermKey.FUNCTION_3,
                                "F4" to VTermKey.FUNCTION_4,
                                "F5" to VTermKey.FUNCTION_5,
                                "F6" to VTermKey.FUNCTION_6,
                                "F7" to VTermKey.FUNCTION_7,
                                "F8" to VTermKey.FUNCTION_8,
                                "F9" to VTermKey.FUNCTION_9,
                                "F10" to VTermKey.FUNCTION_10,
                                "F11" to VTermKey.FUNCTION_11,
                                "F12" to VTermKey.FUNCTION_12
                            )
                            fKeys.forEach { (label, code) ->
                                ToolbarKeyButton(label = label) {
                                    haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                                    handle.dispatchToolbarKey(code)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarKeyButton(
                            label = "KBD",
                            active = showSoftKeyboard,
                            onClick = {
                                haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                                showSoftKeyboard = !showSoftKeyboard
                                if (showSoftKeyboard) runCatching { focusRequester.requestFocus() }
                            }
                        )
                        ToolbarKeyButton(label = "ESC") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.ESCAPE)
                        }
                        ToolbarKeyButton(label = "TAB") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.TAB)
                        }
                        ToolbarKeyButton(
                            label = if (modState.ctrlLocked) "CTRL*" else "CTRL",
                            active = modState.ctrlActive || modState.ctrlLocked,
                            onClick = {
                                haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                                handle.modifierManager.toggleCtrl()
                            }
                        )
                        ToolbarKeyButton(
                            label = "ALT",
                            active = modState.altActive,
                            onClick = {
                                haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                                handle.modifierManager.toggleAlt()
                            }
                        )
                        ToolbarKeyButton(
                            label = "SHIFT",
                            active = modState.shiftActive,
                            onClick = {
                                haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                                handle.modifierManager.toggleShift()
                            }
                        )
                        ToolbarKeyButton(label = "^C") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.insertTextWithoutExecuting("\u0003")
                        }
                        ToolbarKeyButton(label = "←") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.LEFT)
                        }
                        ToolbarKeyButton(label = "↓") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.DOWN)
                        }
                        ToolbarKeyButton(label = "↑") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.UP)
                        }
                        ToolbarKeyButton(label = "→") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.RIGHT)
                        }
                        ToolbarKeyButton(label = "HOME") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.HOME)
                        }
                        ToolbarKeyButton(label = "END") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.END)
                        }
                        ToolbarKeyButton(label = "DEL") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.DEL)
                        }
                        ToolbarKeyButton(label = "BKSP") {
                            haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                            handle.dispatchToolbarKey(VTermKey.BACKSPACE)
                        }
                        ToolbarKeyButton(
                            label = "Fn",
                            active = showFunctionKeys,
                            onClick = {
                                haptic.performSubtleConfirm(appSettings.hapticKeyFeedback)
                                showFunctionKeys = !showFunctionKeys
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSnippetsSheet) {
        SnippetsBottomSheet(
            snippets = snippets,
            onCopy = { cmd -> copyToClipboard(context, cmd) },
            onInsert = { snippet ->
                if (snippet.requiresConfirmation) {
                    snippetConfirmTarget = snippet
                } else {
                    viewModel.insertSnippetWithoutAutoExecute(activeSession.sessionId, snippet.command)
                    showSnippetsSheet = false
                }
            },
            onSaveSnippet = { viewModel.saveSnippet(it) },
            onDeleteSnippet = { viewModel.deleteSnippet(it.id) },
            onDismiss = { showSnippetsSheet = false }
        )
    }

    snippetConfirmTarget?.let { snippet ->
        AlertDialog(
            onDismissRequest = { snippetConfirmTarget = null },
            containerColor = colors.surface,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = colors.amber) },
            title = { Text("ยืนยันการแทรกคำสั่งอันตราย") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("คำสั่งนี้ถูกทำเครื่องหมายว่าอาจกระทบการทำงานของระบบ โปรดตรวจสอบก่อนแทรก (ระบบจะไม่กด Enter รันให้อัตโนมัติ):")
                    Surface(
                        color = colors.elevated,
                        shape = RoundedCornerShape(ObsidianRadius.sm),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = snippet.command,
                            style = MonoCodeStyle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.insertSnippetWithoutAutoExecute(activeSession.sessionId, snippet.command)
                    snippetConfirmTarget = null
                    showSnippetsSheet = false
                }) {
                    Text("แทรกคำสั่ง")
                }
            },
            dismissButton = {
                TextButton(onClick = { snippetConfirmTarget = null }) { Text("ยกเลิก") }
            }
        )
    }

    if (showDashboardSheet) {
        ServerDashboardSheet(
            session = activeSession,
            metricsState = metricsState,
            onRefresh = { viewModel.loadServerMetrics(activeSession.sessionId) },
            onDismiss = { showDashboardSheet = false }
        )
    }
}

@Composable
private fun ToolbarKeyButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalObsidianColors.current
    Surface(
        color = if (active) colors.cyan else colors.elevated,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else colors.textPrimary,
        shape = RoundedCornerShape(ObsidianRadius.sm),
        border = BorderStroke(1.dp, if (active) colors.cyan else colors.border),
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(ObsidianRadius.sm))
            .clickable(onClick = onClick)
            .testTag("kbd_key_$label")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                style = MonoCodeStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetsBottomSheet(
    snippets: List<CommandSnippet>,
    onCopy: (String) -> Unit,
    onInsert: (CommandSnippet) -> Unit,
    onSaveSnippet: (CommandSnippet) -> Unit,
    onDeleteSnippet: (CommandSnippet) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var query by remember { mutableStateOf("") }
    var editingSnippet by remember { mutableStateOf<CommandSnippet?>(null) }

    val filtered = remember(snippets, query) {
        val q = query.trim()
        if (q.isEmpty()) snippets
        else snippets.filter {
            it.title.contains(q, true) || it.command.contains(q, true) || it.groupName.contains(q, true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Command Snippets", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Button(onClick = {
                    editingSnippet = CommandSnippet(
                        id = "",
                        title = "",
                        command = "",
                        groupName = "Custom",
                        description = "",
                        requiresConfirmation = false
                    )
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("สร้าง Snippet")
                }
            }
            Text(
                text = "แทรกคำสั่งลงใน Prompt เท่านั้น (ไม่รันอัตโนมัติ)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ค้นหา Snippet") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(ObsidianRadius.md),
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { snippet ->
                    ObsidianSurfaceCard(
                        shape = RoundedCornerShape(ObsidianRadius.md),
                        elevated = true
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
                                Text(snippet.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                ObsidianMetadataTag(text = snippet.groupName)
                            }
                            Text(snippet.command, style = MonoCodeStyle, color = colors.cyan)
                            if (snippet.description.isNotBlank()) {
                                Text(
                                    snippet.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { editingSnippet = snippet }) {
                                    Icon(Icons.Default.Edit, contentDescription = "แก้ไข")
                                }
                                IconButton(onClick = { onDeleteSnippet(snippet) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = colors.red)
                                }
                                OutlinedButton(onClick = { onCopy(snippet.command) }) {
                                    Text("คัดลอก")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(onClick = { onInsert(snippet) }) {
                                    Text("แทรก")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingSnippet?.let { draft ->
        var title by remember(draft) { mutableStateOf(draft.title) }
        var command by remember(draft) { mutableStateOf(draft.command) }
        var group by remember(draft) { mutableStateOf(draft.groupName) }
        var desc by remember(draft) { mutableStateOf(draft.description) }
        var requireConfirm by remember(draft) { mutableStateOf(draft.requiresConfirmation) }

        AlertDialog(
            onDismissRequest = { editingSnippet = null },
            containerColor = colors.surface,
            title = { Text(if (draft.id.isBlank()) "สร้าง Snippet ใหม่" else "แก้ไข Snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("ชื่อคำสั่ง") }, singleLine = true)
                    OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("คำสั่ง") }, textStyle = MonoCodeStyle)
                    OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("หมวดหมู่") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("คำอธิบาย") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requireConfirm, onCheckedChange = { requireConfirm = it })
                        Text("ต้องยืนยันก่อนแทรก (สำหรับคำสั่งอันตราย)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSnippet(
                            draft.copy(
                                title = title,
                                command = command,
                                groupName = group,
                                description = desc,
                                requiresConfirmation = requireConfirm
                            )
                        )
                        editingSnippet = null
                    },
                    enabled = title.isNotBlank() && command.isNotBlank()
                ) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(onClick = { editingSnippet = null }) { Text("ยกเลิก") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ServerDashboardSheet(
    session: ManagedSessionUiState,
    metricsState: MetricsLoadState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Server Health Dashboard", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    Text(
                        text = "${session.profile.name} (${session.profile.username}@${session.profile.host})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.cyan
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "รีเฟรชค่าเซิร์ฟเวอร์", tint = colors.cyan)
                }
            }

            if (session.meta.kexAlgorithm.isNotBlank()) {
                ObsidianSurfaceCard(
                    shape = RoundedCornerShape(ObsidianRadius.md),
                    elevated = true
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("การเข้ารหัส SSH ที่ใช้งานอยู่", style = MaterialTheme.typography.titleSmall, color = colors.cyan)
                            HostTrustIndicator(isTrusted = session.meta.hostFingerprint.isNotBlank())
                        }
                        Text("KEX: ${session.meta.kexAlgorithm}", style = MonoCodeStyle, color = colors.textPrimary)
                        Text("Host Key: ${session.meta.hostKeyAlgorithm}", style = MonoCodeStyle, color = colors.textSecondary)
                        Text("Cipher: ${session.meta.cipherC2S}", style = MonoCodeStyle, color = colors.textSecondary)
                        if (session.meta.hostFingerprint.isNotBlank()) {
                            Text("Fingerprint: ${session.meta.hostFingerprint}", style = MaterialTheme.typography.labelSmall, color = colors.lime)
                        }
                    }
                }
            }

            when (metricsState) {
                is MetricsLoadState.Idle, MetricsLoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = colors.cyan)
                            Text("กำลังอ่านข้อมูลสถิติจริงจากเซิร์ฟเวอร์...", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
                is MetricsLoadState.Error -> {
                    ObsidianStatusBanner(
                        message = metricsState.message,
                        tone = BannerTone.ERROR,
                        actionLabel = "ลองใหม่",
                        onAction = onRefresh
                    )
                }
                is MetricsLoadState.Loaded -> {
                    val m = metricsState.metrics
                    val isStale = (nowMs - m.retrievedAt) > 60_000L

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ObsidianMetadataTag(
                            text = "อัปเดตเมื่อ ${Formatters.formatTimestamp(m.retrievedAt)}",
                            tint = if (isStale) colors.amber else colors.textSecondary
                        )
                        if (isStale) {
                            ObsidianMetadataTag(text = "ข้อมูลเก่ากว่า 1 นาที", icon = Icons.Default.Warning, tint = colors.amber)
                        }
                        session.latencyMs?.let { rtt ->
                            ObsidianMetadataTag(text = "RTT $rtt ms", icon = Icons.Default.Speed, tint = colors.cyan, monospace = true)
                        }
                    }

                    if (!m.availabilityNote.isNullOrBlank()) {
                        ObsidianStatusBanner(message = m.availabilityNote, tone = BannerTone.WARNING)
                    }

                    ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ระบบปฏิบัติการ & Uptime", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Text("Hostname: ${m.hostname ?: "ไม่พร้อมใช้งาน"}", style = MonoCodeStyle, color = colors.cyan)
                            Text("OS: ${m.osInfo ?: "ไม่พร้อมใช้งาน"}", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                            Text("Kernel: ${m.kernelInfo ?: "ไม่พร้อมใช้งาน"}", style = MonoCodeStyle, color = colors.textSecondary)
                            Text("Uptime: ${m.uptimeText ?: "ไม่พร้อมใช้งาน"}", style = MaterialTheme.typography.bodySmall, color = colors.lime)
                            Text("Load Avg: ${m.loadAverage ?: "ไม่พร้อมใช้งาน"}", style = MonoCodeStyle, color = colors.textSecondary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ObsidianSurfaceCard(
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            elevated = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("CPU Usage", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                                val cpuPct = m.cpuUsagePercent
                                val cpuColor = when {
                                    cpuPct == null -> colors.textSecondary
                                    cpuPct >= 90f -> colors.red
                                    cpuPct >= 70f -> colors.amber
                                    else -> colors.cyan
                                }
                                Text(
                                    text = cpuPct?.let { "${"%.1f".format(it)}%" } ?: "N/A",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cpuColor
                                )
                                cpuPct?.let { pct ->
                                    LinearProgressIndicator(
                                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                                        color = cpuColor,
                                        trackColor = colors.border,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        ObsidianSurfaceCard(
                            shape = RoundedCornerShape(ObsidianRadius.md),
                            elevated = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Memory, null, tint = colors.lime, modifier = Modifier.size(14.dp))
                                    Text("Memory", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                                }
                                val memRatio = if (m.memoryUsedMb != null && m.memoryTotalMb != null && m.memoryTotalMb > 0) {
                                    (m.memoryUsedMb.toFloat() / m.memoryTotalMb).coerceIn(0f, 1f)
                                } else null
                                val memColor = when {
                                    memRatio == null -> colors.textSecondary
                                    memRatio >= 0.90f -> colors.red
                                    memRatio >= 0.70f -> colors.amber
                                    else -> colors.lime
                                }
                                val memLabel = if (m.memoryUsedMb != null && m.memoryTotalMb != null) {
                                    "${m.memoryUsedMb}/${m.memoryTotalMb} MB"
                                } else "N/A"
                                Text(text = memLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = memColor)
                                if (memRatio != null) {
                                    LinearProgressIndicator(
                                        progress = { memRatio },
                                        color = memColor,
                                        trackColor = colors.border,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    if (m.diskSummary.isNotEmpty()) {
                        ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Storage, null, tint = colors.cyan, modifier = Modifier.size(16.dp))
                                    Text("พื้นที่ดิสก์ (Disk)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                }
                                m.diskSummary.forEach { d ->
                                    val diskColor = when {
                                        d.usePercent >= 90 -> colors.red
                                        d.usePercent >= 70 -> colors.amber
                                        else -> colors.cyan
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(d.mountPoint, style = MonoCodeStyle, color = colors.textPrimary)
                                            Text("${d.usedText} / ${d.totalText} (${d.usePercent}%)", style = MaterialTheme.typography.labelSmall, color = diskColor)
                                        }
                                        LinearProgressIndicator(
                                            progress = { (d.usePercent / 100f).coerceIn(0f, 1f) },
                                            color = diskColor,
                                            trackColor = colors.border,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (m.networkInterfaces.isNotEmpty()) {
                        ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("อินเทอร์เฟซเครือข่าย", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                m.networkInterfaces.forEach { net ->
                                    Text("${net.name}: ${net.addresses}", style = MonoCodeStyle, color = colors.cyan)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TerminalUptimeText(
    connectedAtMs: Long,
    color: androidx.compose.ui.graphics.Color
) {
    var nowMs by remember(connectedAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    Text(
        text = Formatters.formatDuration(nowMs - connectedAtMs),
        style = MonoCodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
        color = color,
        maxLines = 1,
        softWrap = false
    )
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("TrueSeekrSSH", text))
}

private fun pasteFromClipboard(context: Context): String? {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val item = cb.primaryClip?.getItemAt(0) ?: return null
    return item.text?.toString()
}
