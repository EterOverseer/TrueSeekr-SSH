package com.example.ssh

import com.example.data.TmuxSessionRepository
import com.example.domain.ManagedTmuxSession
import com.example.domain.RemoteTmuxSession
import com.example.domain.TmuxCheckResult

/**
 * Manages safe, strictly opt-in remote `tmux` persistent sessions over an active SSH connection.
 * Never auto-installs tmux, never executes sudo, validates/sanitizes all session identifiers,
 * distinguishes TrueSeekrSSH-managed sessions from external user sessions, and tracks managed
 * metadata in Room via TmuxSessionRepository.
 */
object TmuxManager {

    private val SAFE_NAME_REGEX = Regex("^[A-Za-z0-9_-]{1,48}$")

    fun sanitizePrefix(rawPrefix: String): String {
        return rawPrefix.replace(Regex("[^A-Za-z0-9_-]"), "").take(20).ifBlank { "trueSeekr" }
    }

    fun generateSessionName(prefix: String, profileId: String, sessionId: String): String {
        val cleanPrefix = sanitizePrefix(prefix)
        val cleanProfile = profileId.replace(Regex("[^A-Za-z0-9_-]"), "").take(12).ifBlank { "srv" }
        val cleanShortId = sessionId.replace(Regex("[^A-Za-z0-9_-]"), "").take(8).ifBlank { "tab" }
        return "${cleanPrefix}_${cleanProfile}_${cleanShortId}".take(48)
    }

    fun sanitizeSessionNameInput(rawName: String): String {
        return rawName.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").take(48).trim('_')
    }

    fun isValidSafeSessionName(sessionName: String): Boolean {
        return SAFE_NAME_REGEX.matches(sessionName)
    }

    fun posixSingleQuote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
    }

    /**
     * Checks whether `tmux` exists on the remote host without installing anything or using sudo.
     */
    suspend fun checkAvailability(executor: RemoteCommandExecutor): TmuxCheckResult {
        val check = executor.exec(
            "PATH=\"\$PATH:/usr/local/bin:/opt/homebrew/bin\"; " +
                "if command -v tmux >/dev/null 2>&1; then echo 'AVAILABLE'; tmux -V 2>/dev/null || true; " +
                "else echo 'MISSING'; uname -s 2>/dev/null || true; " +
                "awk -F= '/^ID=/{gsub(/\"/,\"\",\$2); print \"OS_ID=\"\$2; exit}' /etc/os-release 2>/dev/null || true; fi",
            timeoutMs = 5_000L
        ).getOrElse { err ->
            return TmuxCheckResult.Error(err.message ?: "ไม่สามารถตรวจสอบ tmux บนเซิร์ฟเวอร์ได้")
        }

        if (check.timedOut) {
            return TmuxCheckResult.Error("หมดเวลาในการตรวจสอบ tmux บนเซิร์ฟเวอร์")
        }

        val lines = check.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() == "AVAILABLE") {
            val version = lines.getOrNull(1).orEmpty().ifBlank { "tmux" }
            return TmuxCheckResult.Available(versionText = version)
        }

        val osKernel = lines.getOrNull(1).orEmpty().ifBlank { "Linux" }
        val osId = lines.firstOrNull { it.startsWith("OS_ID=") }?.removePrefix("OS_ID=")?.lowercase().orEmpty()
        return TmuxCheckResult.NotInstalled(
            detectedOs = if (osId.isNotBlank()) "$osKernel ($osId)" else osKernel,
            installInstructions = buildInstallInstructions(osKernel, osId)
        )
    }

    internal fun buildInstallInstructions(osKernel: String, osId: String): List<String> {
        return when {
            osKernel.equals("Darwin", ignoreCase = true) -> listOf(
                "macOS (Homebrew): brew install tmux"
            )
            osId in setOf("debian", "ubuntu", "linuxmint", "raspbian", "pop") -> listOf(
                "Debian / Ubuntu: sudo apt update && sudo apt install -y tmux"
            )
            osId in setOf("alpine") -> listOf(
                "Alpine Linux: sudo apk add tmux (หรือ apk add tmux)"
            )
            osId in setOf("fedora", "rhel", "centos", "rocky", "almalinux") -> listOf(
                "RHEL / Fedora / Rocky: sudo dnf install -y tmux"
            )
            osId in setOf("arch", "manjaro", "endeavouros") -> listOf(
                "Arch Linux: sudo pacman -S tmux"
            )
            else -> listOf(
                "Debian / Ubuntu: sudo apt update && sudo apt install -y tmux",
                "RHEL / Fedora: sudo dnf install -y tmux",
                "Alpine: apk add tmux",
                "Arch: sudo pacman -S tmux"
            )
        }
    }

    /**
     * Lists remote tmux sessions read-only and annotates which ones are managed by TrueSeekrSSH.
     */
    suspend fun listRemoteSessions(
        executor: RemoteCommandExecutor,
        profileId: String,
        managedPrefix: String,
        repository: TmuxSessionRepository?
    ): Result<List<RemoteTmuxSession>> {
        val cmd = "PATH=\"\$PATH:/usr/local/bin:/opt/homebrew/bin\"; " +
            "tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_attached}|#{session_created}|#{session_activity}' 2>/dev/null || true"
        val res = executor.exec(cmd, timeoutMs = 5_000L).getOrElse { return Result.failure(it) }
        if (res.timedOut) {
            return Result.failure(IllegalStateException("หมดเวลาในการอ่านรายการ tmux sessions"))
        }

        val knownManaged = repository?.getAllOnce().orEmpty().associateBy { it.sessionName }
        val cleanPrefix = sanitizePrefix(managedPrefix)
        val parsed = parseListSessionsOutput(
            rawStdout = res.stdout,
            knownManaged = knownManaged,
            managedPrefix = cleanPrefix,
            currentProfileId = profileId
        )
        return Result.success(parsed)
    }

    internal fun parseListSessionsOutput(
        rawStdout: String,
        knownManaged: Map<String, ManagedTmuxSession>,
        managedPrefix: String,
        currentProfileId: String
    ): List<RemoteTmuxSession> {
        return rawStdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("no server running", ignoreCase = true) }
            .mapNotNull { line ->
                val parts = line.split("|")
                val name = parts.getOrNull(0)?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val windows = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val attachedCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val createdEpoch = parts.getOrNull(3)?.toLongOrNull()
                val activityEpoch = parts.getOrNull(4)?.toLongOrNull()
                val stored = knownManaged[name]
                val isAppManaged = stored != null || name.startsWith("${managedPrefix}_")
                RemoteTmuxSession(
                    name = name,
                    windowsCount = windows.coerceAtLeast(1),
                    isAttached = attachedCount > 0,
                    createdEpochSec = createdEpoch,
                    activityEpochSec = activityEpoch,
                    isManagedByApp = isAppManaged,
                    associatedProfileId = stored?.profileId ?: if (isAppManaged) currentProfileId else null
                )
            }
            .sortedWith(
                compareByDescending<RemoteTmuxSession> { it.isManagedByApp && it.associatedProfileId == currentProfileId }
                    .thenByDescending { it.isManagedByApp }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }

    /**
     * Builds the interactive PTY command to create-or-attach to a specific tmux session safely.
     */
    fun buildAttachCommand(sessionName: String): String {
        require(isValidSafeSessionName(sessionName)) {
            "ชื่อ tmux session ไม่ปลอดภัย: ใช้ได้เฉพาะ A-Z, a-z, 0-9, _ และ - (1-48 ตัว)"
        }
        val quoted = posixSingleQuote(sessionName)
        return "tmux new-session -A -s $quoted\n"
    }

    /**
     * Safe tmux detach control sequence (Ctrl-B followed by 'd').
     */
    val DETACH_SEQUENCE: ByteArray = byteArrayOf(0x02, 'd'.code.toByte())

    /**
     * Kills only a single explicitly confirmed session name.
     */
    suspend fun killRemoteSession(
        executor: RemoteCommandExecutor,
        sessionName: String,
        repository: TmuxSessionRepository?
    ): Result<Unit> {
        val cleanName = sessionName.trim()
        if (cleanName.isBlank() || cleanName.contains('\n') || cleanName.contains('\r') || cleanName.contains('\u0000')) {
            return Result.failure(IllegalArgumentException("ชื่อ tmux session ไม่ถูกต้อง"))
        }
        val quoted = posixSingleQuote(cleanName)
        val cmd = "PATH=\"\$PATH:/usr/local/bin:/opt/homebrew/bin\"; tmux kill-session -t $quoted"
        val res = executor.exec(cmd, timeoutMs = 5_000L).getOrElse { return Result.failure(it) }
        if (res.timedOut) {
            return Result.failure(IllegalStateException("หมดเวลาในการปิด tmux session '$cleanName'"))
        }
        if (res.exitCode != null && res.exitCode != 0 && !res.stderr.contains("session not found", ignoreCase = true)) {
            val err = res.stderr.trim().ifBlank { "exit code ${res.exitCode}" }
            return Result.failure(IllegalStateException("ปิด tmux session ไม่สำเร็จ: $err"))
        }
        repository?.removeSession(cleanName)
        return Result.success(Unit)
    }
}
