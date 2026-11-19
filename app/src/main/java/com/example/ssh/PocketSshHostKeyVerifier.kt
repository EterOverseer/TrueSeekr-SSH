package com.example.ssh

import com.example.core.HostFingerprintUtils
import com.example.data.KnownHostRepository
import com.example.domain.HostKeyVerificationRequest
import kotlinx.coroutines.CompletableDeferred
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.PublicKey
import java.util.UUID

/**
 * Strict Host Key Verifier backed by local Room KnownHostRepository.
 * - Never silently accepts unknown hosts.
 * - Strictly rejects any changed host key for a previously trusted endpoint.
 * - Associates trust with (host, port, keyType).
 */
class PocketSshHostKeyVerifier(
    private val host: String,
    private val port: Int,
    private val knownHostRepository: KnownHostRepository,
    private val onVerificationPrompt: suspend (HostKeyVerificationRequest, CompletableDeferred<Boolean>) -> Unit,
    private val onHostKeyMismatch: suspend (HostKeyVerificationRequest.KeyMismatch) -> Unit,
    private val onVerified: (sha256Fingerprint: String) -> Unit
) : HostKeyVerifier {

    override suspend fun verify(key: PublicKey): Boolean {
        val encoded = key.encoded
        val actualBase64 = HostFingerprintUtils.encodePublicKeyBase64(encoded)
        val actualSha256 = HostFingerprintUtils.sha256OpenSsh(encoded)
        val actualHex = HostFingerprintUtils.sha256HexColon(encoded)

        val existing = knownHostRepository.findTrustedHost(host, port, key.type)
        if (existing != null) {
            return if (existing.publicKeyBase64 == actualBase64) {
                knownHostRepository.markVerified(existing.id)
                onVerified(actualSha256)
                true
            } else {
                // Host key changed! Never accept silently; surface loud security warning.
                onHostKeyMismatch(
                    HostKeyVerificationRequest.KeyMismatch(
                        requestId = UUID.randomUUID().toString(),
                        host = host,
                        port = port,
                        keyType = key.type,
                        actualSha256 = actualSha256,
                        actualColonHex = actualHex,
                        expectedSha256 = existing.sha256Fingerprint
                    )
                )
                false
            }
        }

        val decision = CompletableDeferred<Boolean>()
        val request = HostKeyVerificationRequest.UnknownHost(
            requestId = UUID.randomUUID().toString(),
            host = host,
            port = port,
            keyType = key.type,
            actualSha256 = actualSha256,
            actualColonHex = actualHex
        )
        onVerificationPrompt(request, decision)
        val approved = try {
            decision.await()
        } catch (_: Throwable) {
            false
        }
        if (approved) {
            knownHostRepository.trustHost(host, port, key.type, encoded)
            onVerified(actualSha256)
        }
        return approved
    }
}
