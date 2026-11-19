package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.HostFingerprintUtils
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.domain.HostKeyVerificationRequest
import com.example.ssh.PocketSshHostKeyVerifier
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.CryptoPolicyPreset
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.PublicKey
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.connectbot.sshlib.SshCryptoPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HostKeyAndSshTest {

    private lateinit var db: PocketSshDatabase
    private lateinit var knownHostRepo: KnownHostRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        knownHostRepo = KnownHostRepository(db.knownHostDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `unknown host requires approval, subsequent match passes, mismatch rejects`() = runTest {
        val originalKeyBytes = byteArrayOf(10, 20, 30, 40, 50, 60)
        val changedKeyBytes = byteArrayOf(99, 88, 77, 66, 55, 44)
        var mismatchTriggered: HostKeyVerificationRequest.KeyMismatch? = null
        var promptedCount = 0

        val verifier = PocketSshHostKeyVerifier(
            host = "192.168.1.10",
            port = 22,
            knownHostRepository = knownHostRepo,
            onVerificationPrompt = { _, decision: CompletableDeferred<Boolean> ->
                promptedCount++
                decision.complete(true)
            },
            onHostKeyMismatch = { mismatchTriggered = it },
            onVerified = {}
        )

        // First connection: unknown host -> prompts & trusts
        assertTrue(verifier.verify(PublicKey("ssh-ed25519", originalKeyBytes)))
        assertEquals(1, promptedCount)

        // Second connection: same key -> no prompt, returns true
        assertTrue(verifier.verify(PublicKey("ssh-ed25519", originalKeyBytes)))
        assertEquals(1, promptedCount)

        // Third connection: changed key -> strictly rejects & emits mismatch warning
        assertFalse(verifier.verify(PublicKey("ssh-ed25519", changedKeyBytes)))
        assertNotNull(mismatchTriggered)
        assertEquals(
            HostFingerprintUtils.sha256OpenSsh(originalKeyBytes),
            mismatchTriggered?.expectedSha256
        )
        assertEquals(
            HostFingerprintUtils.sha256OpenSsh(changedKeyBytes),
            mismatchTriggered?.actualSha256
        )
    }

    @Test
    fun `unknown host rejection returns false and does not store trust`() = runTest {
        val verifier = PocketSshHostKeyVerifier(
            host = "untrusted.local",
            port = 2222,
            knownHostRepository = knownHostRepo,
            onVerificationPrompt = { _, decision -> decision.complete(false) },
            onHostKeyMismatch = {},
            onVerified = {}
        )
        assertFalse(verifier.verify(PublicKey("ssh-ed25519", byteArrayOf(1, 2, 3))))
        assertEquals(null, knownHostRepo.findTrustedHost("untrusted.local", 2222, "ssh-ed25519"))
    }

    @Test
    fun `cbssh SshClient returns error cleanly on handshake timeout or closed port`() = runTest {
        val server = ServerSocket(0)
        val freePort = server.localPort
        server.close()

        val client = SshClient(
            SshClientConfig {
                host = "127.0.0.1"
                port = freePort
                handshakeTimeoutMs = 1500L
                cryptoPolicy(SshCryptoPolicy.resolve(CryptoPolicyPreset.DEFAULT))
                hostKeyVerifier = object : HostKeyVerifier {
                    override suspend fun verify(key: PublicKey): Boolean = false
                }
            }
        )
        val result = client.connect()
        assertFalse(result is ConnectResult.Success)
    }

    @Test
    fun `server metrics parser handles real output and missing fields gracefully`() {
        val sample = """
            ===HOST===
            zima-server
            ===OS===
            Debian GNU/Linux 12 (bookworm)
            ===KERNEL===
            Linux 6.1.0-18-amd64
            ===UPTIME===
            up 4 days, 3 hours
            ===LOAD===
            0.24 0.18 0.12
            ===CPU===
            14.5
            ===MEM===
            2048 8192
            ===DISK===
            /dev/sda1|/|10485760|52428800|20%
            ===NET===
            eth0|192.168.1.50/24
        """.trimIndent()

        val metrics = SshSessionManager.parseServerMetrics(sample)
        assertEquals("zima-server", metrics.hostname)
        assertEquals("Debian GNU/Linux 12 (bookworm)", metrics.osInfo)
        assertEquals(14.5f, metrics.cpuUsagePercent)
        assertEquals(2048L, metrics.memoryUsedMb)
        assertEquals(8192L, metrics.memoryTotalMb)
        assertEquals(1, metrics.diskSummary.size)
        assertEquals("/", metrics.diskSummary.first().mountPoint)
        assertEquals(20, metrics.diskSummary.first().usePercent)
        assertEquals(1, metrics.networkInterfaces.size)
        assertEquals(null, metrics.availabilityNote)

        val restricted = SshSessionManager.parseServerMetrics("===HOST===\nrestricted-box")
        assertNotNull(restricted.availabilityNote)
    }
}
