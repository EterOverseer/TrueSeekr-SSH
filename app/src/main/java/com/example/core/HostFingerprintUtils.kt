package com.example.core

import java.security.MessageDigest
import kotlin.io.encoding.Base64

object HostFingerprintUtils {
    /**
     * Calculates the standard OpenSSH SHA-256 fingerprint (e.g., "SHA256:abcdef...").
     */
    fun sha256OpenSsh(encodedPublicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
        val b64 = Base64.withPadding(Base64.PaddingOption.ABSENT).encode(digest)
        return "SHA256:$b64"
    }

    /**
     * Calculates a colon-separated hex SHA-256 fingerprint for visual verification.
     */
    fun sha256HexColon(encodedPublicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
        return digest.joinToString(":") { byte -> "%02x".format(byte) }
    }

    fun encodePublicKeyBase64(encodedPublicKey: ByteArray): String {
        return Base64.encode(encodedPublicKey)
    }

    fun hostKeyIdentifier(host: String, port: Int): String {
        val normalizedHost = host.trim().lowercase()
        return if (port == 22) normalizedHost else "[$normalizedHost]:$port"
    }
}
