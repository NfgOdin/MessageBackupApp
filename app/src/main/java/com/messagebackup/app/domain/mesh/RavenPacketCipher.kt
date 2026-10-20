package com.messagebackup.app.domain.mesh

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Handles symmetric packet encryption and decryption using AES-256-GCM.
 * Each packet carries an ephemeral 12-byte random initialization vector (IV) and a 128-bit authentication tag.
 */
object RavenPacketCipher {

    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    /**
     * Encrypts plaintext string using the derived 256-bit shared secret key.
     * Returns a JSON envelope string containing the IV and ciphertext.
     */
    fun encryptPayload(plaintext: String, sharedSecretKey256: ByteArray): String {
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val secretKey = SecretKeySpec(sharedSecretKey256, "AES")

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ct", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString()
    }

    /**
     * Decrypts an envelope JSON using the derived 256-bit shared secret key.
     * Returns the plaintext string, or null if decryption/authentication fails.
     */
    fun decryptPayload(envelopeJson: String, sharedSecretKey256: ByteArray): String? {
        return try {
            val json = JSONObject(envelopeJson)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val cipherBytes = Base64.decode(json.getString("ct"), Base64.NO_WRAP)

            val secretKey = SecretKeySpec(sharedSecretKey256, "AES")
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING).apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
