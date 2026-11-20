package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.domain.DashboardDataSource
import com.example.domain.HealthRefreshInterval
import com.example.domain.MetricAvailability
import com.example.domain.RemoteTmuxSession
import com.example.domain.SessionStatus
import com.example.domain.TerminalFontFamily
import com.example.domain.TmuxCheckResult
import com.example.network.TailscaleProgressState
import com.example.network.VpnOwnership
import com.example.settings.AppSettings
import com.example.ssh.ManagedSessionUiState
import com.example.ssh.TmuxManager
import com.example.terminal.MetricsLoadState
import com.example.terminal.TerminalPaletteProvider
import com.example.terminal.TerminalViewModel
import com.example.terminal.TmuxSheetUiState
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
    val metricsBySession by viewModel.metricsBySession.collectAsStateWithLifecycle()
    val dashboardInterval by viewModel.dashboardInterval.collectAsStateWithLifecycle()
    val tmuxUiState by viewModel.tmuxUiState.collectAsStateWithLifecycle()
    val reconnectTmuxMap by viewModel.reconnectTmuxSuggestions.collectAsStateWithLifecycle()

    val activeSession = sessions.firstOrNull { it.sessionId == activeSessionId } ?: sessions.firstOrNull()
    val handle = activeSession?.let { viewModel.getHandle(it.sessionId) }
    val activeMetricsState = activeSession?.let { metricsBySession[it.sessionId] } ?: MetricsLoadState.Idle

    var showSoftKeyboard by remember { mutableStateOf(false) }
    var showFunctionKeys by remember { mutableStateOf(false) }
    var showSnippetsSheet by remember { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    var showTmuxSheet by remember { mutableStateOf(false) }
    var snippetConfirmTarget by remember { mutableStateOf<CommandSnippet?>(null) }

    val monoTypeface = remember(context, appSettings.terminalFontFamily) {
        when (appSettings.terminalFontFamily) {
            TerminalFontFamily.JETBRAINS_MONO ->
                ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
            TerminalFontFamily.SYSTEM_MONO -> Typeface.MONOSPACE
        }
    }
    val focusRequester = remember { FocusRequester() }

    // Detect re-attachable TrueSeekrSSH-managed tmux sessions after SSH connect/reconnect
    LaunchedEffect(activeSession?.sessionId, activeSession?.status, appSettings.tmuxIntegrationEnabled) {
        val current = activeSession
        if (current != null && appSettings.tmuxIntegrationEnabled && current.status == SessionStatus.CONNECTED) {
            viewModel.checkReconnectTmuxSuggestions(current.sessionId, current.profile.id)
        }
    }

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
                            SessionStatus.CONNECTION_IDLE -> colors.cyan
                            SessionStatus.CONNECTING,
                            SessionStatus.VERIFYING_HOST,
                            SessionStatus.AUTHENTICATING -> colors.cyan
                            SessionStatus.CHECKING_CONNECTION,
                            SessionStatus.RECONNECTING -> colors.amber
                            SessionStatus.CONNECTION_LOST,
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
                    if (activeSession.status.isLiveConnected && activeSession.connectedAtMs != null) {
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
                        onClick = { showDashboardSheet = true },
                        modifier = Modifier.testTag("terminal_dashboard_button")
                    ) {
                        Icon(Icons.Default.MonitorHeart, contentDescription = "ข้อมูลเซิร์ฟเวอร์ (Dashboard)", tint = colors.cyan)
                    }
                    IconButton(
                        onClick = {
                            showTmuxSheet = true
                            if (appSettings.tmuxIntegrationEnabled) {
                                viewModel.openTmuxSheet(activeSession.sessionId, activeSession.profile.id)
                            }
                        },
                        modifier = Modifier.testTag("terminal_tmux_button")
                    ) {
                        Icon(Icons.Default.DynamicFeed, contentDescription = "Persistent Session (tmux)", tint = colors.lime)
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
                    if (activeSession.status.isLiveConnected) {
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

                val suggestedTmux = reconnectTmuxMap[activeSession.sessionId].orEmpty()
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
                } else if (!activeSession.keepAliveNotice.isNullOrBlank()) {
                    ObsidianStatusBanner(
                        message = activeSession.keepAliveNotice,
                        tone = BannerTone.WARNING,
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
                } else if (suggestedTmux.isNotEmpty() && activeSession.status.isLiveConnected) {
                    val target = suggestedTmux.first()
                    ObsidianStatusBanner(
                        message = "พบ Persistent tmux session '${target.name}' บนเซิร์ฟเวอร์",
                        tone = BannerTone.INFO,
                        actionLabel = "Re-attach",
                        onAction = { viewModel.attachOrCreateTmux(activeSession.sessionId, target.name) },
                        onDismiss = { viewModel.dismissReconnectTmuxSuggestion(activeSession.sessionId) },
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
        LaunchedEffect(activeSession.sessionId, activeSession.status.isLiveConnected, dashboardInterval, appSettings.healthDashboardEnabled) {
            if (appSettings.healthDashboardEnabled && activeSession.status.isLiveConnected) {
                viewModel.startDashboardPolling(activeSession.sessionId, dashboardInterval)
            } else {
                viewModel.stopDashboardPolling()
            }
        }
        DisposableEffect(Unit) {
            onDispose { viewModel.stopDashboardPolling() }
        }
        ServerDashboardSheet(
            session = activeSession,
            dashboardEnabled = appSettings.healthDashboardEnabled,
            metricsState = activeMetricsState,
            selectedInterval = dashboardInterval,
            onSelectInterval = { interval ->
                viewModel.setDashboardInterval(activeSession.sessionId, interval)
            },
            onRefresh = { viewModel.loadServerMetrics(activeSession.sessionId, forceSlow = true) },
            onDismiss = {
                viewModel.stopDashboardPolling()
                showDashboardSheet = false
            }
        )
    }

    if (showTmuxSheet) {
        TmuxBottomSheet(
            session = activeSession,
            tmuxEnabled = appSettings.tmuxIntegrationEnabled,
            uiState = tmuxUiState,
            onRefresh = { viewModel.refreshTmuxSessions(activeSession.sessionId, activeSession.profile.id) },
            onCreateOrAttach = { customName ->
                viewModel.attachOrCreateTmux(activeSession.sessionId, customName) {
                    showTmuxSheet = false
                }
            },
            onDetach = { viewModel.detachTmux(activeSession.sessionId) },
            onKillSession = { name ->
                viewModel.killTmuxSession(activeSession.sessionId, activeSession.profile.id, name)
            },
            onCopyCommand = { cmd -> copyToClipboard(context, cmd) },
            onDismiss = { showTmuxSheet = false }
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

@Composable
private fun MetricSparkline(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return
    Canvas(modifier = modifier.fillMaxWidth().height(28.dp)) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { idx, value ->
            val x = idx * stepX
            val normalizedY = (1f - (value.coerceIn(0f, 100f) / 100f))
            val y = normalizedY * (size.height - 4f) + 2f
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ServerDashboardSheet(
    session: ManagedSessionUiState,
    dashboardEnabled: Boolean,
    metricsState: MetricsLoadState,
    selectedInterval: HealthRefreshInterval,
    onSelectInterval: (HealthRefreshInterval) -> Unit,
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
                if (dashboardEnabled && session.status.isLiveConnected) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("dashboard_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรชค่าเซิร์ฟเวอร์", tint = colors.cyan)
                    }
                }
            }

            if (!dashboardEnabled) {
                ObsidianStatusBanner(
                    message = "Server Health Dashboard ถูกปิดอยู่ในการตั้งค่า (Settings > Server Health Dashboard)",
                    tone = BannerTone.INFO
                )
            } else if (!session.status.isLiveConnected) {
                ObsidianStatusBanner(
                    message = "ต้องเชื่อมต่อ SSH ก่อนจึงจะอ่านข้อมูลสุขภาพเซิร์ฟเวอร์ได้",
                    tone = BannerTone.WARNING
                )
            } else {
                // Interval selector
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HealthRefreshInterval.entries.forEach { interval ->
                        FilterChip(
                            selected = selectedInterval == interval,
                            onClick = { onSelectInterval(interval) },
                            label = { Text(interval.label) },
                            modifier = Modifier.testTag("dashboard_interval_${interval.name}")
                        )
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
                    is MetricsLoadState.Unsupported -> {
                        ObsidianStatusBanner(
                            message = "${metricsState.reason} (${metricsState.detectedOs})",
                            tone = BannerTone.WARNING
                        )
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
                        val staleThresholdMs = if (selectedInterval.seconds > 0) {
                            maxOf(selectedInterval.seconds * 2_500L, 65_000L)
                        } else {
                            65_000L
                        }
                        val isStale = metricsState.staleWarning != null || (nowMs - m.retrievedAt) > staleThresholdMs

                        if (metricsState.isRefreshing) {
                            LinearProgressIndicator(
                                color = colors.cyan,
                                trackColor = colors.border,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ObsidianMetadataTag(
                                text = m.dataSource.label,
                                tint = if (m.dataSource == DashboardDataSource.FASTFETCH || m.dataSource == DashboardDataSource.MIXED) colors.lime else colors.cyan
                            )
                            ObsidianMetadataTag(
                                text = if (metricsState.isRefreshing) "กำลังรีเฟรช..." else "อัปเดต ${Formatters.formatTimestamp(m.retrievedAt)}",
                                tint = if (isStale) colors.amber else colors.textSecondary
                            )
                            if (isStale) {
                                ObsidianMetadataTag(text = "Stale Data", icon = Icons.Default.Warning, tint = colors.amber)
                            }
                            session.latencyMs?.let { rtt ->
                                ObsidianMetadataTag(text = "RTT $rtt ms", icon = Icons.Default.Speed, tint = colors.cyan, monospace = true)
                            }
                        }

                        metricsState.staleWarning?.let { warn ->
                            ObsidianStatusBanner(
                                message = warn,
                                tone = BannerTone.WARNING,
                                actionLabel = "ลองใหม่",
                                onAction = onRefresh
                            )
                        }

                        if (!m.availabilityNote.isNullOrBlank()) {
                            ObsidianStatusBanner(message = m.availabilityNote, tone = BannerTone.WARNING)
                        }

                        // System Identity
                        ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ระบบปฏิบัติการ & Uptime", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                Text("Hostname: ${m.hostname ?: "Unavailable"}", style = MonoCodeStyle, color = colors.cyan)
                                Text("OS: ${m.osInfo ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                                Text("Kernel: ${m.kernelInfo ?: "Unavailable"}", style = MonoCodeStyle, color = colors.textSecondary)
                                Text("Uptime: ${m.uptimeText ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall, color = colors.lime)
                                Text("Load Avg: ${m.loadAverage ?: "Unavailable"}", style = MonoCodeStyle, color = colors.textSecondary)
                            }
                        }

                        // CPU & Memory Grid
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ObsidianSurfaceCard(
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                elevated = true,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("CPU Usage", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                                    val cpuPct = if (m.cpuAvailability == MetricAvailability.AVAILABLE) m.cpuUsagePercent else null
                                    val cpuColor = when {
                                        cpuPct == null -> colors.textSecondary
                                        cpuPct >= 90f -> colors.red
                                        cpuPct >= 70f -> colors.amber
                                        else -> colors.cyan
                                    }
                                    Text(
                                        text = cpuPct?.let { "${"%.1f".format(it)}%" } ?: "Unavailable",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = cpuColor
                                    )
                                    if (!m.cpuModel.isNullOrBlank()) {
                                        Text(
                                            text = m.cpuModel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    cpuPct?.let { pct ->
                                        LinearProgressIndicator(
                                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                                            color = cpuColor,
                                            trackColor = colors.border,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    MetricSparkline(values = m.cpuHistory, lineColor = cpuColor)
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
                                    val memAvail = m.memoryAvailability == MetricAvailability.AVAILABLE &&
                                        m.memoryUsedMb != null && m.memoryTotalMb != null && m.memoryTotalMb > 0
                                    val memRatio = if (memAvail) {
                                        (m.memoryUsedMb!!.toFloat() / m.memoryTotalMb!!.toFloat()).coerceIn(0f, 1f)
                                    } else null
                                    val memColor = when {
                                        memRatio == null -> colors.textSecondary
                                        memRatio >= 0.90f -> colors.red
                                        memRatio >= 0.70f -> colors.amber
                                        else -> colors.lime
                                    }
                                    val memLabel = if (memAvail) "${m.memoryUsedMb}/${m.memoryTotalMb} MB" else "Unavailable"
                                    Text(
                                        text = memLabel,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = memColor
                                    )
                                    if (memRatio != null) {
                                        LinearProgressIndicator(
                                            progress = { memRatio },
                                            color = memColor,
                                            trackColor = colors.border,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    MetricSparkline(values = m.memoryHistory, lineColor = memColor)
                                }
                            }
                        }

                        // Network Throughput & Temperature Row
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ObsidianSurfaceCard(
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                elevated = true,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.SwapVert, null, tint = colors.cyan, modifier = Modifier.size(14.dp))
                                        Text("Net Throughput", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                                    }
                                    if (m.networkThroughputAvailability == MetricAvailability.AVAILABLE &&
                                        m.networkRxBytesPerSec != null && m.networkTxBytesPerSec != null
                                    ) {
                                        Text(
                                            text = "↓ ${Formatters.formatBytes(m.networkRxBytesPerSec)}/s",
                                            style = MonoCodeStyle,
                                            color = colors.lime
                                        )
                                        Text(
                                            text = "↑ ${Formatters.formatBytes(m.networkTxBytesPerSec)}/s",
                                            style = MonoCodeStyle,
                                            color = colors.cyan
                                        )
                                    } else {
                                        Text("Unavailable", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                            }
                            ObsidianSurfaceCard(
                                shape = RoundedCornerShape(ObsidianRadius.md),
                                elevated = true,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Thermostat, null, tint = colors.amber, modifier = Modifier.size(14.dp))
                                        Text("Temperature", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                                    }
                                    when {
                                        m.temperatureAvailability == MetricAvailability.AVAILABLE && m.temperatureCelsius != null -> {
                                            val tempColor = when {
                                                m.temperatureCelsius >= 80f -> colors.red
                                                m.temperatureCelsius >= 65f -> colors.amber
                                                else -> colors.lime
                                            }
                                            Text(
                                                text = "${"%.1f".format(m.temperatureCelsius)}°C",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = tempColor
                                            )
                                            m.temperatureSource?.let { src ->
                                                Text(src, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                            }
                                        }
                                        m.temperatureAvailability == MetricAvailability.PERMISSION_DENIED -> {
                                            Text("Permission denied", style = MaterialTheme.typography.bodySmall, color = colors.amber)
                                        }
                                        else -> {
                                            Text("Unavailable", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                        }
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
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TmuxBottomSheet(
    session: ManagedSessionUiState,
    tmuxEnabled: Boolean,
    uiState: TmuxSheetUiState,
    onRefresh: () -> Unit,
    onCreateOrAttach: (String?) -> Unit,
    onDetach: () -> Unit,
    onKillSession: (String) -> Unit,
    onCopyCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalObsidianColors.current
    var customNameInput by remember { mutableStateOf("") }
    var sessionToConfirmKill by remember { mutableStateOf<RemoteTmuxSession?>(null) }

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
                    Text("Persistent Session (tmux)", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    Text(
                        text = "รักษางานบนเซิร์ฟเวอร์เมื่อปิดหน้าจอหรือเครือข่ายหลุด (Opt-in)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                if (tmuxEnabled && session.status.isLiveConnected) {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("tmux_refresh_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช tmux sessions", tint = colors.cyan)
                    }
                }
            }

            if (!tmuxEnabled) {
                ObsidianStatusBanner(
                    message = "tmux Integration ถูกปิดอยู่ในการตั้งค่า (Settings > Persistent Sessions)",
                    tone = BannerTone.INFO
                )
            } else if (!session.status.isLiveConnected) {
                ObsidianStatusBanner(
                    message = "กรุณาเชื่อมต่อ SSH ก่อนจึงจะจัดการ tmux sessions บนเซิร์ฟเวอร์ได้",
                    tone = BannerTone.WARNING
                )
            } else {
                uiState.feedbackMessage?.let { msg ->
                    ObsidianStatusBanner(message = msg, tone = BannerTone.INFO)
                }
                uiState.errorMessage?.let { err ->
                    ObsidianStatusBanner(message = err, tone = BannerTone.ERROR)
                }

                when (val check = uiState.checkResult) {
                    null -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.cyan)
                        }
                    }
                    is TmuxCheckResult.Error -> {
                        ObsidianStatusBanner(
                            message = check.message,
                            tone = BannerTone.ERROR,
                            actionLabel = "ลองใหม่",
                            onAction = onRefresh
                        )
                    }
                    is TmuxCheckResult.NotInstalled -> {
                        ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md), elevated = true) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "ไม่พบ tmux บนเซิร์ฟเวอร์ (${check.detectedOs})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.amber
                                )
                                Text(
                                    text = "TrueSeekrSSH จะไม่ติดตั้งแพ็กเกจให้โดยอัตโนมัติ คุณสามารถติดตั้งด้วยคำสั่งด้านล่าง:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                                check.installInstructions.forEach { cmdLine ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cmdLine,
                                            style = MonoCodeStyle,
                                            color = colors.cyan,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { onCopyCommand(cmdLine.substringAfter(": ").trim()) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "คัดลอกคำสั่งติดตั้ง", tint = colors.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is TmuxCheckResult.Available -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ObsidianMetadataTag(text = check.versionText, tint = colors.lime)
                            ObsidianMetadataTag(text = "${uiState.remoteSessions.size} remote", tint = colors.cyan)
                        }

                        // Create / Attach controls
                        ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md), elevated = true) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customNameInput,
                                    onValueChange = { customNameInput = it },
                                    label = { Text("ชื่อ Session (เว้นว่างเพื่อใช้ชื่อปลอดภัยอัตโนมัติ)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(ObsidianRadius.md),
                                    modifier = Modifier.fillMaxWidth().testTag("tmux_custom_name_input")
                                )
                                if (customNameInput.isNotBlank()) {
                                    val previewSanitized = TmuxManager.sanitizeSessionNameInput(customNameInput)
                                    Text(
                                        text = "ชื่อที่จะใช้จริง: $previewSanitized",
                                        style = MonoCodeStyle,
                                        color = colors.cyan
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { onCreateOrAttach(customNameInput.takeIf { it.isNotBlank() }) },
                                        enabled = !uiState.isActionRunning,
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("tmux_create_attach_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("เริ่ม / เชื่อมต่อ")
                                    }
                                    OutlinedButton(
                                        onClick = onDetach,
                                        enabled = !uiState.isActionRunning,
                                        modifier = Modifier.height(48.dp).testTag("tmux_detach_button")
                                    ) {
                                        Text("Detach")
                                    }
                                }
                            }
                        }

                        // Remote Sessions List
                        Text(
                            text = "Remote tmux Sessions",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        if (uiState.remoteSessions.isEmpty()) {
                            Text(
                                text = "ยังไม่มี tmux session ที่รันอยู่บนเซิร์ฟเวอร์",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        } else {
                            uiState.remoteSessions.forEach { remote ->
                                ObsidianSurfaceCard(shape = RoundedCornerShape(ObsidianRadius.md), elevated = true) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(remote.name, style = MonoCodeStyle.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                ObsidianMetadataTag(
                                                    text = if (remote.isManagedByApp) "TrueSeekrSSH" else "External",
                                                    tint = if (remote.isManagedByApp) colors.lime else colors.amber
                                                )
                                                ObsidianMetadataTag(
                                                    text = "${remote.windowsCount} win • ${if (remote.isAttached) "attached" else "detached"}",
                                                    tint = colors.textSecondary
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = { onCreateOrAttach(remote.name) },
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text("Attach")
                                        }
                                        IconButton(
                                            onClick = { sessionToConfirmKill = remote },
                                            modifier = Modifier.testTag("tmux_kill_${remote.name}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Kill session", tint = colors.red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    sessionToConfirmKill?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToConfirmKill = null },
            containerColor = colors.surface,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = colors.amber) },
            title = { Text("ยืนยันการปิด tmux Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("คุณต้องการส่งคำสั่งปิด '${target.name}' ถาวรหรือไม่? โปรแกรมที่รันอยู่ใน session นี้จะถูกหยุดทันที")
                    if (!target.isManagedByApp) {
                        Text(
                            text = "คำเตือน: session นี้ไม่ได้สร้างโดย TrueSeekrSSH (External Session)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.amber
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onKillSession(target.name)
                        sessionToConfirmKill = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier.testTag("confirm_kill_tmux_button")
                ) {
                    Text("ยืนยันปิด Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToConfirmKill = null }) { Text("ยกเลิก") }
            }
        )
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
