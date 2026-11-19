package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AppAccentPreset
import com.example.domain.AppThemeMode
import com.example.domain.TerminalCursorStyle
import com.example.domain.TerminalFontFamily
import com.example.domain.TerminalThemePreset
import com.example.security.PinLockManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pocketSshPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketssh_settings")

enum class AutoLockTimeout(val seconds: Int, val label: String) {
    IMMEDIATE(0, "ทันทีเมื่อพับแอป"),
    THIRTY_SECONDS(30, "30 วินาที"),
    ONE_MINUTE(60, "1 นาที"),
    FIVE_MINUTES(300, "5 นาที"),
    NEVER(-1, "เฉพาะตอนเปิดแอป")
}

data class AppSettings(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val protectScreenshots: Boolean = false,
    val defaultTerminalFontSizeSp: Float = 13f,
    val defaultTerminalTheme: TerminalThemePreset = TerminalThemePreset.OBSIDIAN_AURORA,
    val defaultTermType: String = "xterm-256color",
    val defaultKeepAliveSec: Int = 30,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val accentPreset: AppAccentPreset = AppAccentPreset.CYAN,
    val terminalFontFamily: TerminalFontFamily = TerminalFontFamily.JETBRAINS_MONO,
    val scrollbackLines: Int = 2000,
    val showExtraKeyboardToolbar: Boolean = true,
    val cursorStyle: TerminalCursorStyle = TerminalCursorStyle.BLOCK,
    val hapticKeyFeedback: Boolean = true,
    val defaultConnectTimeoutSec: Int = 15,
    val autoReconnectPrompt: Boolean = true
)

class AppPreferencesRepository(
    private val context: Context,
    private val customDataStore: DataStore<Preferences>? = null
) {
    private val dataStore: DataStore<Preferences>
        get() = customDataStore ?: context.pocketSshPrefsDataStore

    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val AUTO_LOCK_SEC = intPreferencesKey("auto_lock_sec")
        val PROTECT_SCREENSHOTS = booleanPreferencesKey("protect_screenshots")
        val TERM_FONT_SIZE = floatPreferencesKey("term_font_size")
        val TERM_THEME = stringPreferencesKey("term_theme")
        val TERM_TYPE = stringPreferencesKey("term_type")
        val KEEPALIVE_SEC = intPreferencesKey("keepalive_sec")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val TERM_FONT_FAMILY = stringPreferencesKey("term_font_family")
        val SCROLLBACK_LINES = intPreferencesKey("scrollback_lines")
        val SHOW_EXTRA_TOOLBAR = booleanPreferencesKey("show_extra_toolbar")
        val CURSOR_STYLE = stringPreferencesKey("cursor_style")
        val HAPTIC_KEY_FEEDBACK = booleanPreferencesKey("haptic_key_feedback")
        val DEFAULT_TIMEOUT_SEC = intPreferencesKey("default_timeout_sec")
        val AUTO_RECONNECT_PROMPT = booleanPreferencesKey("auto_reconnect_prompt")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val timeoutSec = prefs[Keys.AUTO_LOCK_SEC] ?: AutoLockTimeout.ONE_MINUTE.seconds
        val themeName = prefs[Keys.TERM_THEME] ?: TerminalThemePreset.OBSIDIAN_AURORA.name
        val pinHash = prefs[Keys.PIN_HASH].orEmpty()
        val themeModeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name
        val accentName = prefs[Keys.ACCENT_PRESET] ?: AppAccentPreset.CYAN.name
        val fontFamilyName = prefs[Keys.TERM_FONT_FAMILY] ?: TerminalFontFamily.JETBRAINS_MONO.name
        val cursorName = prefs[Keys.CURSOR_STYLE] ?: TerminalCursorStyle.BLOCK.name

        AppSettings(
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            hasPinConfigured = pinHash.isNotBlank(),
            autoLockTimeout = AutoLockTimeout.entries.firstOrNull { it.seconds == timeoutSec }
                ?: AutoLockTimeout.ONE_MINUTE,
            protectScreenshots = prefs[Keys.PROTECT_SCREENSHOTS] ?: false,
            defaultTerminalFontSizeSp = prefs[Keys.TERM_FONT_SIZE] ?: 13f,
            defaultTerminalTheme = TerminalThemePreset.entries.firstOrNull { it.name == themeName }
                ?: TerminalThemePreset.OBSIDIAN_AURORA,
            defaultTermType = prefs[Keys.TERM_TYPE] ?: "xterm-256color",
            defaultKeepAliveSec = prefs[Keys.KEEPALIVE_SEC] ?: 30,
            themeMode = AppThemeMode.entries.firstOrNull { it.name == themeModeName }
                ?: AppThemeMode.DARK,
            accentPreset = AppAccentPreset.entries.firstOrNull { it.name == accentName }
                ?: AppAccentPreset.CYAN,
            terminalFontFamily = TerminalFontFamily.entries.firstOrNull { it.name == fontFamilyName }
                ?: TerminalFontFamily.JETBRAINS_MONO,
            scrollbackLines = prefs[Keys.SCROLLBACK_LINES] ?: 2000,
            showExtraKeyboardToolbar = prefs[Keys.SHOW_EXTRA_TOOLBAR] ?: true,
            cursorStyle = TerminalCursorStyle.entries.firstOrNull { it.name == cursorName }
                ?: TerminalCursorStyle.BLOCK,
            hapticKeyFeedback = prefs[Keys.HAPTIC_KEY_FEEDBACK] ?: true,
            defaultConnectTimeoutSec = prefs[Keys.DEFAULT_TIMEOUT_SEC] ?: 15,
            autoReconnectPrompt = prefs[Keys.AUTO_RECONNECT_PROMPT] ?: true
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setPin(pin: CharArray) {
        val record = PinLockManager.createPinHash(pin)
        dataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = record.saltBase64
            prefs[Keys.PIN_HASH] = record.hashBase64
            prefs[Keys.APP_LOCK_ENABLED] = true
        }
    }

    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.PIN_HASH)
            prefs[Keys.APP_LOCK_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        var matched = false
        dataStore.edit { prefs ->
            val salt = prefs[Keys.PIN_SALT].orEmpty()
            val hash = prefs[Keys.PIN_HASH].orEmpty()
            matched = PinLockManager.verifyPin(pin, salt, hash)
        }
        return matched
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { it[Keys.AUTO_LOCK_SEC] = timeout.seconds }
    }

    suspend fun setProtectScreenshots(enabled: Boolean) {
        dataStore.edit { it[Keys.PROTECT_SCREENSHOTS] = enabled }
    }

    suspend fun setDefaultTerminalFontSize(fontSizeSp: Float) {
        dataStore.edit {
            it[Keys.TERM_FONT_SIZE] = fontSizeSp.coerceIn(9f, 24f)
        }
    }

    suspend fun setDefaultTerminalTheme(theme: TerminalThemePreset) {
        dataStore.edit { it[Keys.TERM_THEME] = theme.name }
    }

    suspend fun setDefaultTermType(termType: String) {
        dataStore.edit { it[Keys.TERM_TYPE] = termType }
    }

    suspend fun setDefaultKeepAliveSec(seconds: Int) {
        dataStore.edit { it[Keys.KEEPALIVE_SEC] = seconds.coerceIn(0, 300) }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AppAccentPreset) {
        dataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setTerminalFontFamily(family: TerminalFontFamily) {
        dataStore.edit { it[Keys.TERM_FONT_FAMILY] = family.name }
    }

    suspend fun setScrollbackLines(lines: Int) {
        dataStore.edit { it[Keys.SCROLLBACK_LINES] = lines.coerceIn(500, 10000) }
    }

    suspend fun setShowExtraKeyboardToolbar(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_EXTRA_TOOLBAR] = show }
    }

    suspend fun setCursorStyle(style: TerminalCursorStyle) {
        dataStore.edit { it[Keys.CURSOR_STYLE] = style.name }
    }

    suspend fun setHapticKeyFeedback(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_KEY_FEEDBACK] = enabled }
    }

    suspend fun setDefaultConnectTimeoutSec(seconds: Int) {
        dataStore.edit { it[Keys.DEFAULT_TIMEOUT_SEC] = seconds.coerceIn(5, 120) }
    }

    suspend fun setAutoReconnectPrompt(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT_PROMPT] = enabled }
    }
}
