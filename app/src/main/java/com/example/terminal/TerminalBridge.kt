package com.example.terminal

import android.os.Looper
import androidx.compose.ui.graphics.Color
import com.example.domain.TerminalThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.SelectionController
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory

data class ModifierUiState(
    val ctrlActive: Boolean = false,
    val altActive: Boolean = false,
    val shiftActive: Boolean = false,
    val ctrlLocked: Boolean = false
)

/**
 * Sticky/one-shot modifier state manager bridging the mobile terminal toolbar and ConnectBot termlib.
 * Modifier bitmask constants matching termlib KeyboardHandler: SHIFT=1, ALT=2, CTRL=4.
 */
class PocketModifierManager : ModifierManager {
    private val _state = MutableStateFlow(ModifierUiState())
    val state: StateFlow<ModifierUiState> = _state.asStateFlow()

    override fun isCtrlActive(): Boolean = _state.value.ctrlActive || _state.value.ctrlLocked
    override fun isAltActive(): Boolean = _state.value.altActive
    override fun isShiftActive(): Boolean = _state.value.shiftActive

    override fun clearTransients() {
        val current = _state.value
        _state.value = current.copy(
            ctrlActive = current.ctrlLocked,
            altActive = false,
            shiftActive = false
        )
    }

    fun toggleCtrl() {
        val cur = _state.value
        if (cur.ctrlLocked) {
            _state.value = cur.copy(ctrlActive = false, ctrlLocked = false)
        } else {
            _state.value = cur.copy(ctrlActive = !cur.ctrlActive)
        }
    }

    fun toggleCtrlLock() {
        val cur = _state.value
        val nextLocked = !cur.ctrlLocked
        _state.value = cur.copy(ctrlLocked = nextLocked, ctrlActive = nextLocked)
    }

    fun toggleAlt() {
        _state.value = _state.value.copy(altActive = !_state.value.altActive)
    }

    fun toggleShift() {
        _state.value = _state.value.copy(shiftActive = !_state.value.shiftActive)
    }

    fun currentBitmask(): Int {
        var mask = 0
        if (isShiftActive()) mask = mask or MOD_SHIFT
        if (isAltActive()) mask = mask or MOD_ALT
        if (isCtrlActive()) mask = mask or MOD_CTRL
        return mask
    }

    companion object {
        const val MOD_SHIFT = 1
        const val MOD_ALT = 2
        const val MOD_CTRL = 4
    }
}

/**
 * Maintains an ANSI-stripped rolling text buffer for scrollback search across real SSH output.
 */
class ScrollbackTextBuffer(
    private val maxLines: Int = 2000
) {
    private val lines = ArrayDeque<String>(maxLines)
    private val currentLine = StringBuilder()

    @Synchronized
    fun appendRawBytes(bytes: ByteArray) {
        val decoded = String(bytes, Charsets.UTF_8)
        val cleaned = ANSI_ESCAPE_REGEX.replace(decoded, "")
        for (ch in cleaned) {
            when (ch) {
                '\n' -> {
                    pushLine(currentLine.toString())
                    currentLine.setLength(0)
                }
                '\r' -> {
                    // Wait for newline or overwrite if bare CR
                }
                '\b' -> {
                    if (currentLine.isNotEmpty()) {
                        currentLine.deleteCharAt(currentLine.length - 1)
                    }
                }
                else -> {
                    if (!ch.isISOControl() || ch == '\t') {
                        currentLine.append(ch)
                    }
                }
            }
        }
    }

    @Synchronized
    fun search(query: String): List<Pair<Int, String>> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val snapshot = getAllLines()
        return snapshot.mapIndexedNotNull { index, line ->
            if (line.contains(q, ignoreCase = true)) index to line else null
        }
    }

    @Synchronized
    fun getAllLines(): List<String> {
        val list = ArrayList<String>(lines.size + 1)
        list.addAll(lines)
        if (currentLine.isNotEmpty()) {
            list.add(currentLine.toString())
        }
        return list
    }

    @Synchronized
    fun clear() {
        lines.clear()
        currentLine.setLength(0)
    }

    private fun pushLine(line: String) {
        if (lines.size >= maxLines) {
            lines.removeFirst()
        }
        lines.addLast(line)
    }

    companion object {
        private val ANSI_ESCAPE_REGEX = Regex(
            "\\u001B\\[[0-9;?]*[ -/]*[@-~]|\\u001B\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)|\\u001B[()][A-B0-2]"
        )
    }
}

object TerminalPaletteProvider {
    private val ansi16Palette = intArrayOf(
        0xFF161B22.toInt(), // 0 Black
        0xFFF87171.toInt(), // 1 Red
        0xFF4ADE80.toInt(), // 2 Green
        0xFFFBBF24.toInt(), // 3 Yellow
        0xFF60A5FA.toInt(), // 4 Blue
        0xFFC084FC.toInt(), // 5 Magenta
        0xFF2DD4BF.toInt(), // 6 Cyan (Teal)
        0xFFE6EDF3.toInt(), // 7 White
        0xFF6E7681.toInt(), // 8 Bright Black
        0xFFFCA5A5.toInt(), // 9 Bright Red
        0xFFA3E635.toInt(), // 10 Bright Green (Lime)
        0xFFFDE68A.toInt(), // 11 Bright Yellow
        0xFF93C5FD.toInt(), // 12 Bright Blue
        0xFFD8B4FE.toInt(), // 13 Bright Magenta
        0xFF5EEAD4.toInt(), // 14 Bright Cyan
        0xFFFFFFFF.toInt()  // 15 Bright White
    )

    fun applyTheme(emulator: TerminalEmulator, preset: TerminalThemePreset) {
        emulator.applyColorScheme(
            ansi16Palette,
            preset.foregroundArgb.toInt(),
            preset.backgroundArgb.toInt()
        )
    }

    fun backgroundColor(preset: TerminalThemePreset): Color = Color(preset.backgroundArgb)
    fun foregroundColor(preset: TerminalThemePreset): Color = Color(preset.foregroundArgb)
    fun selectionBgColor(preset: TerminalThemePreset): Color = Color(preset.selectionBgArgb)
    fun selectionFgColor(preset: TerminalThemePreset): Color = Color(preset.selectionFgArgb)
}

class TerminalSessionHandle(
    initialTheme: TerminalThemePreset,
    private val onSendBytesToSsh: (ByteArray) -> Unit,
    private val onTerminalResized: (rows: Int, cols: Int) -> Unit,
    private val onBellTriggered: () -> Unit = {},
    private val onClipboardCopyRequested: (String) -> Unit = {},
    private val onTitleChanged: (String) -> Unit = {},
    scrollbackMaxLines: Int = 2000
) {
    val modifierManager = PocketModifierManager()
    val scrollbackBuffer = ScrollbackTextBuffer(maxLines = scrollbackMaxLines.coerceIn(500, 10000))

    var selectionController: SelectionController? = null

    private var lastRows = 24
    private var lastCols = 80

    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        looper = Looper.getMainLooper(),
        initialRows = lastRows,
        initialCols = lastCols,
        defaultForeground = TerminalPaletteProvider.foregroundColor(initialTheme),
        defaultBackground = TerminalPaletteProvider.backgroundColor(initialTheme),
        onKeyboardInput = { bytes -> onSendBytesToSsh(bytes) },
        onBell = { onBellTriggered() },
        onResize = { dimensions: TerminalDimensions ->
            lastRows = dimensions.rows
            lastCols = dimensions.columns
            onTerminalResized(dimensions.rows, dimensions.columns)
        },
        onClipboardCopy = { copied -> onClipboardCopyRequested(copied) },
        onProgressChange = { _, _ -> },
        autoDetectUrls = true,
        boldAsBright = true
    ).also {
        TerminalPaletteProvider.applyTheme(it, initialTheme)
    }

    fun writeServerOutput(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        scrollbackBuffer.appendRawBytes(bytes)
        emulator.writeInput(bytes, 0, bytes.size)
    }

    fun writeLocalNotice(notice: String) {
        val bytes = "$notice\r\n".toByteArray(Charsets.UTF_8)
        writeServerOutput(bytes)
    }

    fun dispatchToolbarKey(vtermKey: Int) {
        val mods = modifierManager.currentBitmask()
        emulator.dispatchKey(mods, vtermKey)
        modifierManager.clearTransients()
    }

    fun dispatchToolbarCharacter(ch: Char) {
        val mods = modifierManager.currentBitmask()
        if ((mods and PocketModifierManager.MOD_CTRL) != 0 && ch in 'a'..'z') {
            val ctrlByte = byteArrayOf((ch.code - 'a'.code + 1).toByte())
            onSendBytesToSsh(ctrlByte)
        } else if ((mods and PocketModifierManager.MOD_CTRL) != 0 && ch in 'A'..'Z') {
            val ctrlByte = byteArrayOf((ch.code - 'A'.code + 1).toByte())
            onSendBytesToSsh(ctrlByte)
        } else {
            emulator.dispatchCharacter(mods, ch.code)
        }
        modifierManager.clearTransients()
    }

    fun insertTextWithoutExecuting(text: String) {
        if (text.isEmpty()) return
        onSendBytesToSsh(text.toByteArray(Charsets.UTF_8))
    }

    fun currentDimensions(): Pair<Int, Int> = lastCols to lastRows

    fun updateTheme(preset: TerminalThemePreset) {
        TerminalPaletteProvider.applyTheme(emulator, preset)
    }
}
