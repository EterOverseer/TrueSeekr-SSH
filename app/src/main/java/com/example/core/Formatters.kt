package com.example.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "-"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatTimestamp(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "ยังไม่เคยเชื่อมต่อ"
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatUnixSeconds(epochSeconds: Int?): String {
        if (epochSeconds == null || epochSeconds <= 0) return "-"
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        return sdf.format(Date(epochSeconds.toLong() * 1000L))
    }

    fun formatUnixPermissions(mode: Int?): String {
        if (mode == null) return "----------"
        val typeChar = when (mode and 0xF000) {
            0x4000 -> 'd'
            0xA000 -> 'l'
            else -> '-'
        }
        val chars = CharArray(10)
        chars[0] = typeChar
        val flags = intArrayOf(0x0100, 0x0080, 0x0040, 0x0020, 0x0010, 0x0008, 0x0004, 0x0002, 0x0001)
        val letters = charArrayOf('r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x')
        for (i in flags.indices) {
            chars[i + 1] = if ((mode and flags[i]) != 0) letters[i] else '-'
        }
        return String(chars)
    }

    fun isDirectoryMode(mode: Int?): Boolean {
        if (mode == null) return false
        return (mode and 0xF000) == 0x4000
    }

    /**
     * Redacts any potential secret tokens before diagnostic UI/log formatting.
     */
    fun redactDiagnosticText(message: String?): String {
        if (message.isNullOrBlank()) return ""
        return message
            .replace(Regex("(?i)(password|passphrase|secret|private[_\\s]?key)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]")
            .replace(Regex("-----BEGIN [A-Z ]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+PRIVATE KEY-----"), "[REDACTED_PRIVATE_KEY]")
    }
}
