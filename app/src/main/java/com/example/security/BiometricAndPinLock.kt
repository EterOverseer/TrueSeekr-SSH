package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import kotlin.io.encoding.Base64
import javax.crypto.spec.PBEKeySpec

object PinLockManager {
    private const val ITERATIONS = 100_000

    data class PinHashRecord(
        val saltBase64: String,
        val hashBase64: String
    )

    fun createPinHash(pin: CharArray): PinHashRecord {
        require(pin.size >= 4) { "PIN ต้องมีอย่างน้อย 4 หลัก" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        return PinHashRecord(
            saltBase64 = Base64.encode(salt),
            hashBase64 = Base64.encode(hash)
        )
    }

    fun verifyPin(pin: CharArray, saltBase64: String, expectedHashBase64: String): Boolean {
        if (pin.isEmpty() || saltBase64.isBlank() || expectedHashBase64.isBlank()) return false
        return try {
            val salt = Base64.decode(saltBase64)
            val expected = Base64.decode(expectedHashBase64)
            val actual = pbkdf2(pin, salt)
            MessageDigest.isEqual(actual, expected)
        } catch (_: Throwable) {
            false
        }
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

object BiometricLockManager {
    fun canAuthenticateBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("ยืนยันตัวตนด้วย Biometric ไม่สำเร็จ")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
