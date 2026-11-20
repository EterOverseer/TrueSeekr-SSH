package com.example.ssh

import com.example.core.Formatters
import com.example.core.NetworkMonitor
import com.example.data.KnownHostRepository
import com.example.data.ProfileRepository
import com.example.domain.AuthMethodType
import com.example.domain.CredentialPromptRequest
import com.example.domain.DiskMountInfo
import com.example.domain.HostKeyVerificationRequest
import com.example.domain.NetworkInterfaceInfo
import com.example.domain.ServerMetrics
import com.example.domain.SessionConnectionMeta
import com.example.domain.SessionStatus
import com.example.domain.SshProfile
import com.example.security.CredentialVault
import com.example.settings.AppPreferencesRepository
import com.example.terminal.TerminalSessionHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.CryptoPolicyPreset
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.connectbot.sshlib.SshCryptoPolicy
import org.connectbot.sshlib.SshSession
import java.util.UUID

data class PendingHostTrustPrompt(
    val request: HostKeyVerificationRequest.UnknownHost,
    val decision: CompletableDeferred<Boolean>
)

data class ManagedSessionUiState(
    val sessionId: String,
    val profile: SshProfile,
    val status: SessionStatus = SessionStatus.IDLE,
    val connectedAtMs: Long? = null,
    val latencyMs: Long? = null,
    val routeLabel: String = "",
    val meta: SessionConnectionMeta = SessionConnectionMeta(),
    val errorMessage: String? = null,
    val networkWarning: String? = null
)

class ManagedSessionRuntime(
    val sessionId: String,
    var profile: SshProfile,
    val terminalHandle: TerminalSessionHandle
) {
    var sshClient: SshClient? = null
    var shellSession: SshSession? = null
    var sftpClient: SftpClient? = null
    var ioJobs: MutableList<Job> = mutableListOf()
    var lastVerifiedFingerprint: String = ""
}

/**
 * Coordinates real SSH connections via ConnectBot cbssh, strict host key verification,
 * interactive PTY sessions via termlib, and SFTP subsystems.
 */
class SshSessionManager(
    private val profileRepository: ProfileRepository,
    private val knownHostRepository: KnownHostRepository,
    private val credentialVault: CredentialVault,
    private val networkMonitor: NetworkMonitor,
    private val preferencesRepository: AppPreferencesRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val runtimes = linkedMapOf<String, ManagedSessionRuntime>()

    private val _sessions = MutableStateFlow<List<ManagedSessionUiState>>(emptyList())
    val sessions: StateFlow<List<ManagedSessionUiState>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _hostKeyPrompt = MutableStateFlow<PendingHostTrustPrompt?>(null)
    val hostKeyPrompt: StateFlow<PendingHostTrustPrompt?> = _hostKeyPrompt.asStateFlow()

    private val _hostKeyMismatchAlert = MutableStateFlow<HostKeyVerificationRequest.KeyMismatch?>(null)
    val hostKeyMismatchAlert: StateFlow<HostKeyVerificationRequest.KeyMismatch?> = _hostKeyMismatchAlert.asStateFlow()

    private val _credentialPrompt = MutableStateFlow<CredentialPromptRequest?>(null)
    val credentialPrompt: StateFlow<CredentialPromptRequest?> = _credentialPrompt.asStateFlow()

    @Volatile
    private var cachedScrollbackLines: Int = 2000

    init {
        preferencesRepository?.let { repo ->
            scope.launch {
                repo.settings.collect { s ->
                    cachedScrollbackLines = s.scrollbackLines
                }
            }
        }
        scope.launch {
            networkMonitor.networkTransitions.collect { status ->
                if (!status.isConnected) {
                    _sessions.update { list ->
                        list.map { s ->
                            if (s.status == SessionStatus.CONNECTED) {
                                s.copy(networkWarning = "เครือข่ายขาดการเชื่อมต่อ — แตะ Reconnect เมื่อพร้อม")
                            } else s
                        }
                    }
                } else {
                    _sessions.update { list ->
                        list.map { s ->
                            val updatedRoute = status.transportType.label
                            if (s.networkWarning != null) {
                                s.copy(
                                    routeLabel = updatedRoute,
                                    networkWarning = "เครือข่ายเปลี่ยนเป็น $updatedRoute แล้ว หากค้างให้กด Reconnect"
                                )
                            } else {
                                s.copy(routeLabel = updatedRoute)
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (runtimes.containsKey(sessionId)) {
            _activeSessionId.value = sessionId
        }
    }

    fun dismissHostKeyMismatch() {
        _hostKeyMismatchAlert.value = null
    }

    fun dismissCredentialPrompt() {
        _credentialPrompt.value = null
    }

    fun resolveHostKeyPrompt(approved: Boolean) {
        val pending = _hostKeyPrompt.value ?: return
        _hostKeyPrompt.value = null
        pending.decision.complete(approved)
    }

    fun getRuntime(sessionId: String): ManagedSessionRuntime? = runtimes[sessionId]

    /**
     * Initiates a connection using a previously persisted SshProfile.
     * Never creates temporary unsaved profiles and never updates lastConnectedAt on failure.
     */
    fun connectProfile(
        profile: SshProfile,
        oneTimePassword: CharArray? = null,
        oneTimePrivateKeyPem: String? = null,
        oneTimePassphrase: CharArray? = null,
        saveEnteredCredential: Boolean = false,
        onConnectedNavigateToTerminal: () -> Unit = {}
    ) {
        val netStatus = networkMonitor.readCurrentStatus()
        if (!netStatus.isConnected) {
            val sessionId = ensureOrCreateRuntime(profile)
            updateSessionState(sessionId) {
                it.copy(
                    status = SessionStatus.ERROR,
                    errorMessage = "ไม่มีการเชื่อมต่อเครือข่าย (Wi-Fi / LAN / Tailscale VPN) กรุณาตรวจสอบก่อนเชื่อมต่อ"
                )
            }
            return
        }

        scope.launch {
            var workingProfile = profileRepository.getProfileById(profile.id) ?: profile

            // Resolve required secret before opening network socket if possible
            var passwordStr: String? = oneTimePassword?.let { String(it) }
            var privateKeyStr: String? = oneTimePrivateKeyPem
            var passphraseStr: String? = oneTimePassphrase?.let { String(it) }

            when (workingProfile.authMethod) {
                AuthMethodType.PASSWORD -> {
                    if (passwordStr.isNullOrEmpty()) {
                        passwordStr = credentialVault.readSecretString(workingProfile.credentialRefId)
                    }
                    if (passwordStr.isNullOrEmpty()) {
                        _credentialPrompt.value = CredentialPromptRequest(
                            profile = workingProfile,
                            reason = "กรอกรหัสผ่านสำหรับ ${workingProfile.username}@${workingProfile.host}",
                            needsPassphraseOnly = false
                        )
                        return@launch
                    }
                    if (saveEnteredCredential && oneTimePassword != null) {
                        workingProfile = profileRepository.saveProfile(
                            workingProfile,
                            newPasswordChars = oneTimePassword
                        )
                    }
                }
                AuthMethodType.PRIVATE_KEY, AuthMethodType.PRIVATE_KEY_PASSPHRASE -> {
                    if (privateKeyStr.isNullOrBlank()) {
                        privateKeyStr = credentialVault.readSecretString(workingProfile.credentialRefId)
                    }
                    if (privateKeyStr.isNullOrBlank()) {
                        _credentialPrompt.value = CredentialPromptRequest(
                            profile = workingProfile,
                            reason = "ไม่พบ Private Key ใน Vault กรุณานำเข้าหรือวาง Private Key สำหรับ ${workingProfile.name}",
                            needsPassphraseOnly = false
                        )
                        return@launch
                    }
                    if (saveEnteredCredential && !oneTimePrivateKeyPem.isNullOrBlank()) {
                        workingProfile = profileRepository.saveProfile(
                            workingProfile,
                            newPrivateKeyPem = oneTimePrivateKeyPem
                        )
                    }
                    val credRefId = workingProfile.credentialRefId
                    if (passphraseStr == null && credRefId != null) {
                        passphraseStr = credentialVault.getEphemeralPassphrase(credRefId)
                    }
                    if (oneTimePassphrase != null && credRefId != null) {
                        credentialVault.setEphemeralPassphrase(credRefId, oneTimePassphrase)
                    }
                }
            }

            val sessionId = ensureOrCreateRuntime(workingProfile)
            _activeSessionId.value = sessionId
            withContext(Dispatchers.Main) {
                onConnectedNavigateToTerminal()
            }

            performConnectInternal(
                sessionId = sessionId,
                profile = workingProfile,
                passwordStr = passwordStr,
                privateKeyStr = privateKeyStr,
                passphraseStr = passphraseStr
            )
        }
    }

    fun reconnectSession(sessionId: String) {
        val runtime = runtimes[sessionId] ?: return
        scope.launch {
            closeTransportOnly(runtime)
            val freshProfile = profileRepository.getProfileById(runtime.profile.id) ?: runtime.profile
            runtime.profile = freshProfile
            updateSessionState(sessionId) {
                it.copy(
                    profile = freshProfile,
                    status = SessionStatus.RECONNECTING,
                    errorMessage = null,
                    networkWarning = null
                )
            }
            connectProfile(freshProfile)
        }
    }

    fun disconnectSession(sessionId: String) {
        val runtime = runtimes[sessionId] ?: return
        scope.launch {
            closeTransportOnly(runtime)
            updateSessionState(sessionId) {
                it.copy(
                    status = SessionStatus.DISCONNECTED,
                    latencyMs = null,
                    networkWarning = null
                )
            }
            withContext(Dispatchers.Main) {
                runtime.terminalHandle.writeLocalNotice("\r\n[ตัดการเชื่อมต่อ SSH แล้ว]")
            }
        }
    }

    fun closeSessionTab(sessionId: String) {
        val runtime = runtimes.remove(sessionId) ?: return
        scope.launch {
            closeTransportOnly(runtime)
        }
        _sessions.update { list -> list.filterNot { it.sessionId == sessionId } }
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = _sessions.value.firstOrNull()?.sessionId
        }
    }

    fun disconnectAllSessions() {
        val ids = runtimes.keys.toList()
        ids.forEach { disconnectSession(it) }
        credentialVault.clearAllEphemeralPassphrases()
    }

    suspend fun getOrOpenSftp(sessionId: String): Result<SftpClient> = withContext(Dispatchers.IO) {
        val runtime = runtimes[sessionId]
            ?: return@withContext Result.failure(IllegalStateException("ไม่พบ Session ที่ใช้งานอยู่"))
        val existing = runtime.sftpClient
        if (existing != null && existing.isOpen) {
            return@withContext Result.success(existing)
        }
        val client = runtime.sshClient
            ?: return@withContext Result.failure(IllegalStateException("ยังไม่ได้เชื่อมต่อ SSH"))
        if (!client.isAuthenticated) {
            return@withContext Result.failure(IllegalStateException("Session ยังไม่ผ่านการยืนยันตัวตน"))
        }
        when (val result = client.openSftp()) {
            is SftpResult.Success -> {
                runtime.sftpClient = result.value
                Result.success(result.value)
            }
            is SftpResult.ServerError -> Result.failure(IllegalStateException("เปิด SFTP ไม่สำเร็จ: ${result.message}"))
            is SftpResult.ProtocolError -> Result.failure(IllegalStateException("SFTP Protocol error: ${result.message}"))
            is SftpResult.IoError -> Result.failure(IllegalStateException("SFTP I/O error: ${result.cause.message}"))
        }
    }

    /**
     * Executes controlled read-only diagnostic commands on the connected SSH server to populate
     * the Server Dashboard with real telemetry (never fabricated values).
     */
    suspend fun fetchServerMetrics(sessionId: String): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        val runtime = runtimes[sessionId]
            ?: return@withContext Result.failure(IllegalStateException("ไม่พบ Session ที่เชื่อมต่ออยู่"))
        val client = runtime.sshClient
            ?: return@withContext Result.failure(IllegalStateException("SSH ยังไม่ได้เชื่อมต่อ"))
        if (!client.isAuthenticated) {
            return@withContext Result.failure(IllegalStateException("Session ยังไม่ผ่านการยืนยันตัวตน"))
        }

        val cmd = listOf(
            "echo '===HOST===' && (hostname 2>/dev/null || uname -n)",
            "echo '===OS===' && (cat /etc/os-release 2>/dev/null | grep '^PRETTY_NAME=' | cut -d= -f2- | tr -d '\"' || uname -s)",
            "echo '===KERNEL===' && uname -sr",
            "echo '===UPTIME===' && (uptime -p 2>/dev/null || uptime 2>/dev/null)",
            "echo '===LOAD===' && (cat /proc/loadavg 2>/dev/null | awk '{print $1\" \"$2\" \"$3}')",
            "echo '===CPU===' && (grep 'cpu ' /proc/stat 2>/dev/null | awk '{u=$2+$4; t=$2+$4+$5; if(t>0) printf \"%.1f\", u*100/t}')",
            "echo '===MEM===' && (free -m 2>/dev/null | awk 'NR==2{print $3\" \"$2}')",
            "echo '===DISK===' && (df -kP 2>/dev/null | awk 'NR>1 && $1 !~ /^(tmpfs|devtmpfs|udev|overlay)/ {print $1\"|\"$6\"|\"$3\"|\"$2\"|\"$5}' | head -n 6)",
            "echo '===NET===' && (ip -o -4 addr show 2>/dev/null | awk '{print $2\"|\"$4}' | head -n 6)"
        ).joinToString("; ")

        val execStartNs = System.nanoTime()
        val execSession = client.openSession()
            ?: return@withContext Result.failure(IllegalStateException("ไม่สามารถเปิด Exec channel เพื่ออ่านค่าเซิร์ฟเวอร์ได้"))

        try {
            val started = execSession.requestExec(cmd)
            if (!started) {
                return@withContext Result.failure(IllegalStateException("เซิร์ฟเวอร์ไม่อนุญาตให้รันคำสั่งอ่านสถิติ (exec disabled)"))
            }

            val outputBuilder = StringBuilder()
            val stdout = execSession.stdout
            while (true) {
                val chunk = stdout.receiveCatching().getOrNull() ?: break
                outputBuilder.append(String(chunk, Charsets.UTF_8))
                if (outputBuilder.length > 64_000) break
            }
            val measuredRttMs = ((System.nanoTime() - execStartNs) / 1_000_000L).coerceAtLeast(1L)
            updateSessionState(sessionId) { it.copy(latencyMs = measuredRttMs) }
            val raw = outputBuilder.toString()
            Result.success(parseServerMetrics(raw))
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("อ่านข้อมูลเซิร์ฟเวอร์ไม่สำเร็จ: ${Formatters.redactDiagnosticText(t.message)}"))
        } finally {
            runCatching { execSession.close() }
        }
    }

    private suspend fun performConnectInternal(
        sessionId: String,
        profile: SshProfile,
        passwordStr: String?,
        privateKeyStr: String?,
        passphraseStr: String?
    ) {
        val runtime = runtimes[sessionId] ?: return
        try {
            updateSessionState(sessionId) {
                it.copy(status = SessionStatus.VERIFYING_HOST, errorMessage = null, networkWarning = null)
            }

            val verifier = PocketSshHostKeyVerifier(
                host = profile.host,
                port = profile.port,
                knownHostRepository = knownHostRepository,
                onVerificationPrompt = { request, decision ->
                    if (request is HostKeyVerificationRequest.UnknownHost) {
                        _hostKeyPrompt.value = PendingHostTrustPrompt(request, decision)
                    } else {
                        decision.complete(false)
                    }
                },
                onHostKeyMismatch = { mismatch ->
                    _hostKeyMismatchAlert.value = mismatch
                },
                onVerified = { sha256 ->
                    runtime.lastVerifiedFingerprint = sha256
                }
            )

            // Enforce modern SSH cryptography policy (Ed25519, RSA-SHA2, ChaCha20/AES-GCM, Curve25519)
            val cryptoPolicy = SshCryptoPolicy.resolve(CryptoPolicyPreset.DEFAULT)
            val config = SshClientConfig {
                host = profile.host
                port = profile.port
                hostKeyVerifier = verifier
                cryptoPolicy(cryptoPolicy)
                autoDisconnectOnLastChannelClose = false
                handshakeTimeoutMs = profile.connectTimeoutSec.coerceIn(5, 120) * 1000L
                keepAliveIntervalMs = profile.keepAliveIntervalSec.coerceIn(0, 300) * 1000L
            }
            val client = SshClient(config)
            runtime.sshClient = client

            updateSessionState(sessionId) {
                it.copy(
                    status = SessionStatus.CONNECTING,
                    routeLabel = networkMonitor.readCurrentStatus().transportType.label
                )
            }
            withContext(Dispatchers.Main) {
                runtime.terminalHandle.writeLocalNotice("กำลังเชื่อมต่อ ${profile.username}@${profile.host}:${profile.port}...")
            }

            val connectStartNs = System.nanoTime()
            val connectResult = client.connect()
            val measuredHandshakeMs = ((System.nanoTime() - connectStartNs) / 1_000_000L).coerceAtLeast(1L)
            when (connectResult) {
                is ConnectResult.Success -> {
                    updateSessionState(sessionId) { it.copy(latencyMs = measuredHandshakeMs) }
                }
                is ConnectResult.HostKeyRejected -> {
                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.ERROR,
                            errorMessage = "ปฏิเสธ Host Key: การเชื่อมต่อถูกยกเลิกเพื่อความปลอดภัย"
                        )
                    }
                    client.disconnect()
                    return
                }
                is ConnectResult.AlgorithmMismatch -> {
                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.ERROR,
                            errorMessage = "ไม่รองรับอัลกอริทึมที่ปลอดภัยร่วมกัน: ${connectResult.message}"
                        )
                    }
                    client.disconnect()
                    return
                }
                is ConnectResult.ProtocolError -> {
                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.ERROR,
                            errorMessage = "ข้อผิดพลาดโปรโตคอล SSH: ${Formatters.redactDiagnosticText(connectResult.message)}"
                        )
                    }
                    client.disconnect()
                    return
                }
                is ConnectResult.TransportError -> {
                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.ERROR,
                            errorMessage = "ไม่สามารถเชื่อมต่อเครือข่ายได้: ${Formatters.redactDiagnosticText(connectResult.cause.message)}"
                        )
                    }
                    client.disconnect()
                    return
                }
            }

            updateSessionState(sessionId) { it.copy(status = SessionStatus.AUTHENTICATING) }

            val authResult: AuthResult = when (profile.authMethod) {
                AuthMethodType.PASSWORD -> {
                    client.authenticatePassword(profile.username, passwordStr.orEmpty())
                }
                AuthMethodType.PRIVATE_KEY, AuthMethodType.PRIVATE_KEY_PASSPHRASE -> {
                    val pem = privateKeyStr.orEmpty()
                    val keyEncrypted = runCatching { client.isPrivateKeyEncrypted(pem) }.getOrDefault(false)
                    if (keyEncrypted && passphraseStr.isNullOrEmpty()) {
                        client.disconnect()
                        updateSessionState(sessionId) {
                            it.copy(
                                status = SessionStatus.IDLE,
                                errorMessage = "Private Key นี้ถูกเข้ารหัส กรุณากรอก Passphrase"
                            )
                        }
                        _credentialPrompt.value = CredentialPromptRequest(
                            profile = profile,
                            reason = "Private Key ถูกป้องกันด้วย Passphrase สำหรับ ${profile.name}",
                            needsPassphraseOnly = true
                        )
                        return
                    }
                    client.authenticatePublicKey(profile.username, pem, passphraseStr)
                }
            }

            when (authResult) {
                is AuthResult.Success -> {
                    // Record successful connection ONLY after authentication succeeds!
                    profileRepository.recordSuccessfulConnection(profile.id)

                    val connInfo = client.connectionInfo
                    val meta = SessionConnectionMeta(
                        kexAlgorithm = connInfo?.kexAlgorithm.orEmpty(),
                        hostKeyAlgorithm = connInfo?.serverHostKeyAlgorithm.orEmpty(),
                        cipherC2S = connInfo?.encryptionAlgorithmC2S.orEmpty(),
                        macC2S = connInfo?.macAlgorithmC2S.orEmpty(),
                        isPostQuantum = connInfo?.isPostQuantumSecure ?: false,
                        hostFingerprint = runtime.lastVerifiedFingerprint
                    )

                    val session = client.openSession()
                    if (session == null) {
                        updateSessionState(sessionId) {
                            it.copy(status = SessionStatus.ERROR, errorMessage = "เปิด SSH Session Channel ไม่สำเร็จ")
                        }
                        client.disconnect()
                        return
                    }
                    runtime.shellSession = session

                    val (cols, rows) = runtime.terminalHandle.currentDimensions()
                    session.requestPty(
                        profile.terminalType.ifBlank { "xterm-256color" },
                        cols.coerceAtLeast(20),
                        rows.coerceAtLeast(10),
                        0,
                        0,
                        byteArrayOf()
                    )
                    val shellStarted = session.requestShell()
                    if (!shellStarted) {
                        updateSessionState(sessionId) {
                            it.copy(status = SessionStatus.ERROR, errorMessage = "เปิด Interactive Shell ไม่สำเร็จ")
                        }
                        session.close()
                        client.disconnect()
                        return
                    }

                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.CONNECTED,
                            connectedAtMs = System.currentTimeMillis(),
                            meta = meta,
                            errorMessage = null
                        )
                    }

                    if (profile.initialWorkingDir.isNotBlank()) {
                        val safeCd = "cd '${profile.initialWorkingDir.replace("'", "'\\''")}'\n"
                        session.write(safeCd.toByteArray(Charsets.UTF_8))
                    }

                    attachSessionIo(runtime, client, session)
                }
                is AuthResult.Failure -> {
                    client.disconnect()
                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.ERROR,
                            errorMessage = "ยืนยันตัวตนไม่สำเร็จ (Allowed: ${authResult.allowedMethods.joinToString(", ")})"
                        )
                    }
                }
                is AuthResult.Error -> {
                    client.disconnect()
                    updateSessionState(sessionId) {
                        it.copy(
                            status = SessionStatus.ERROR,
                            errorMessage = "ยืนยันตัวตนผิดพลาด: ${Formatters.redactDiagnosticText(authResult.message)}"
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            runCatching { runtime.sshClient?.disconnect() }
            updateSessionState(sessionId) {
                it.copy(
                    status = SessionStatus.ERROR,
                    errorMessage = "การเชื่อมต่อล้มเหลว: ${Formatters.redactDiagnosticText(t.message ?: t::class.simpleName)}"
                )
            }
        }
    }

    private fun attachSessionIo(
        runtime: ManagedSessionRuntime,
        client: SshClient,
        session: SshSession
    ) {
        runtime.ioJobs.forEach { it.cancel() }
        runtime.ioJobs.clear()

        val stdoutJob = scope.launch {
            try {
                for (chunk in session.stdout) {
                    withContext(Dispatchers.Main) {
                        runtime.terminalHandle.writeServerOutput(chunk)
                    }
                }
            } catch (_: Throwable) {
                // Channel closed
            }
        }

        val stderrJob = scope.launch {
            try {
                for (chunk in session.stderr) {
                    withContext(Dispatchers.Main) {
                        runtime.terminalHandle.writeServerOutput(chunk)
                    }
                }
            } catch (_: Throwable) {
                // Channel closed
            }
        }

        val disconnectJob = scope.launch {
            try {
                client.disconnectedFlow.collect { cause ->
                    updateSessionState(runtime.sessionId) {
                        if (it.status == SessionStatus.CONNECTED || it.status == SessionStatus.RECONNECTING) {
                            it.copy(
                                status = SessionStatus.DISCONNECTED,
                                errorMessage = cause?.message?.let { msg -> Formatters.redactDiagnosticText(msg) }
                            )
                        } else it
                    }
                    withContext(Dispatchers.Main) {
                        runtime.terminalHandle.writeLocalNotice("\r\n[เซิร์ฟเวอร์ปิดการเชื่อมต่อ]")
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val exitWatchJob = scope.launch {
            try {
                session.exitInfo.await()
                delay(150)
                if (_sessions.value.firstOrNull { it.sessionId == runtime.sessionId }?.status == SessionStatus.CONNECTED) {
                    updateSessionState(runtime.sessionId) {
                        it.copy(status = SessionStatus.DISCONNECTED)
                    }
                }
            } catch (_: Throwable) {
            }
        }

        runtime.ioJobs.addAll(listOf(stdoutJob, stderrJob, disconnectJob, exitWatchJob))
    }

    private fun ensureOrCreateRuntime(profile: SshProfile): String {
        val currentRoute = networkMonitor.readCurrentStatus().transportType.label
        val existing = runtimes.values.firstOrNull { it.profile.id == profile.id }
        if (existing != null) {
            existing.profile = profile
            existing.terminalHandle.updateTheme(profile.terminalTheme)
            updateSessionState(existing.sessionId) { it.copy(profile = profile, routeLabel = currentRoute) }
            return existing.sessionId
        }

        val configuredScrollback = cachedScrollbackLines

        val sessionId = UUID.randomUUID().toString()
        lateinit var runtime: ManagedSessionRuntime
        val handle = TerminalSessionHandle(
            initialTheme = profile.terminalTheme,
            onSendBytesToSsh = { bytes ->
                val session = runtime.shellSession
                if (session != null && session.isOpen) {
                    scope.launch {
                        runCatching { session.write(bytes) }
                    }
                }
            },
            onTerminalResized = { rows, cols ->
                val session = runtime.shellSession
                if (session != null && session.isOpen) {
                    scope.launch {
                        runCatching { session.resizeTerminal(cols, rows, 0, 0) }
                    }
                }
            },
            scrollbackMaxLines = configuredScrollback
        )
        runtime = ManagedSessionRuntime(
            sessionId = sessionId,
            profile = profile,
            terminalHandle = handle
        )
        runtimes[sessionId] = runtime
        _sessions.update {
            it + ManagedSessionUiState(
                sessionId = sessionId,
                profile = profile,
                routeLabel = currentRoute
            )
        }
        return sessionId
    }

    private suspend fun closeTransportOnly(runtime: ManagedSessionRuntime) {
        runtime.ioJobs.forEach { it.cancel() }
        runtime.ioJobs.clear()
        runCatching { runtime.sftpClient?.close() }
        runtime.sftpClient = null
        runCatching { runtime.shellSession?.close() }
        runtime.shellSession = null
        runCatching { runtime.sshClient?.disconnect() }
        runtime.sshClient = null
    }

    private fun updateSessionState(
        sessionId: String,
        transform: (ManagedSessionUiState) -> ManagedSessionUiState
    ) {
        _sessions.update { list ->
            list.map { item -> if (item.sessionId == sessionId) transform(item) else item }
        }
    }

    companion object {
        fun parseServerMetrics(rawOutput: String): ServerMetrics {
            val sections = mutableMapOf<String, MutableList<String>>()
            var currentTag = ""
            rawOutput.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                    currentTag = trimmed.removePrefix("===").removeSuffix("===")
                    sections.getOrPut(currentTag) { mutableListOf() }
                } else if (currentTag.isNotEmpty() && trimmed.isNotEmpty()) {
                    sections.getOrPut(currentTag) { mutableListOf() }.add(trimmed)
                }
            }

            val host = sections["HOST"]?.firstOrNull()
            val osInfo = sections["OS"]?.firstOrNull()
            val kernel = sections["KERNEL"]?.firstOrNull()
            val uptime = sections["UPTIME"]?.firstOrNull()
            val load = sections["LOAD"]?.firstOrNull()
            val cpuPct = sections["CPU"]?.firstOrNull()?.toFloatOrNull()

            val memParts = sections["MEM"]?.firstOrNull()?.split(Regex("\\s+"))
            val memUsed = memParts?.getOrNull(0)?.toLongOrNull()
            val memTotal = memParts?.getOrNull(1)?.toLongOrNull()

            val disks = sections["DISK"].orEmpty().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 5) return@mapNotNull null
                val usedKb = parts[2].toLongOrNull() ?: 0L
                val totalKb = parts[3].toLongOrNull() ?: 0L
                val pct = parts[4].removeSuffix("%").trim().toIntOrNull() ?: 0
                DiskMountInfo(
                    filesystem = parts[0],
                    mountPoint = parts[1],
                    usedText = Formatters.formatBytes(usedKb * 1024L),
                    totalText = Formatters.formatBytes(totalKb * 1024L),
                    usePercent = pct
                )
            }

            val nets = sections["NET"].orEmpty().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 2) return@mapNotNull null
                NetworkInterfaceInfo(name = parts[0], addresses = parts[1])
            }

            val missingParts = buildList {
                if (cpuPct == null) add("CPU")
                if (memTotal == null) add("Memory")
                if (disks.isEmpty()) add("Disk")
                if (nets.isEmpty()) add("Network")
            }
            val note = if (missingParts.isNotEmpty()) {
                "บางค่าไม่สามารถอ่านได้บนบัญชี/ระบบนี้ (${missingParts.joinToString(", ")}) เนื่องจากสิทธิ์จำกัดหรือไม่ใช่ Linux /proc"
            } else null

            return ServerMetrics(
                osInfo = osInfo,
                kernelInfo = kernel,
                hostname = host,
                uptimeText = uptime,
                loadAverage = load,
                cpuUsagePercent = cpuPct,
                memoryUsedMb = memUsed,
                memoryTotalMb = memTotal,
                diskSummary = disks,
                networkInterfaces = nets,
                availabilityNote = note
            )
        }
    }
}
