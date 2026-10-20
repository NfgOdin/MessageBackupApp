package com.messagebackup.app.domain.mesh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Manages cryptographic identity, key derivation, and digital signatures for the Raven Mesh protocol.
 * Keys are anchored in hardware using the AndroidKeyStore provider with EC P-256 (secp256r1) curves
 * for universal hardware-backed TEE / StrongBox compatibility across modern Android devices.
 */
class RavenCryptoEngine(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_IDENTITY = "muninn_raven_mesh_identity_v1"
        private const val PREFS_NAME = "raven_mesh_prefs"
        private const val PREF_CALLSIGN_MONIKER = "pref_callsign_moniker"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        private val NORSE_PREFIXES = listOf(
            "Odin", "Huginn", "Muninn", "Freyja", "Thor", "Loki", "Valkyrie",
            "Fenrir", "Heimdall", "Mimir", "Tyr", "Baldur", "Skadi", "Yggdrasil"
        )
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves or generates the hardware-backed EC KeyPair for this device's Raven identity.
     */
    fun getOrCreateIdentityKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS_IDENTITY)) {
            val entry = keyStore.getEntry(KEY_ALIAS_IDENTITY, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                return KeyPair(entry.certificate.publicKey, entry.privateKey)
            }
        }

        // Generate a new EC P-256 keypair inside Android Keystore
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_IDENTITY,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or KeyProperties.PURPOSE_AGREE_KEY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            // EC Curve standard P-256 is universally supported across Android Keystore TEE
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Gets the public key encoded as Base64.
     */
    fun getPublicKeyBase64(): String {
        val keyPair = getOrCreateIdentityKeyPair()
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Derives a consistent, human-readable Raven Call-Sign (e.g. raven://Odin-7A3F).
     */
    fun getRavenCallSign(): String {
        var moniker = sharedPrefs.getString(PREF_CALLSIGN_MONIKER, null)
        if (moniker.isNullOrBlank()) {
            val pubBytes = getOrCreateIdentityKeyPair().public.encoded
            // Generate a 4-hex checksum from the public key
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(pubBytes)
            val suffix = String.format("%02X%02X", hash[0], hash[1])
            val randomPrefix = NORSE_PREFIXES[(hash[2].toInt() and 0xFF) % NORSE_PREFIXES.size]
            moniker = "$randomPrefix-$suffix"
            sharedPrefs.edit().putString(PREF_CALLSIGN_MONIKER, moniker).apply()
        }
        return "raven://$moniker"
    }

    /**
     * Updates the user's custom Call-Sign name/alias while preserving cryptographic uniqueness.
     */
    fun setRavenCallSignAlias(customAlias: String) {
        val clean = customAlias.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (clean.isBlank()) return
        val pubBytes = getOrCreateIdentityKeyPair().public.encoded
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(pubBytes)
        val suffix = String.format("%02X%02X", hash[0], hash[1])
        val newMoniker = if (clean.endsWith("-$suffix", ignoreCase = true)) clean else "$clean-$suffix"
        sharedPrefs.edit().putString(PREF_CALLSIGN_MONIKER, newMoniker).apply()
    }

    /**
     * Signs raw text or payload bytes with the private key.
     */
    fun signData(data: ByteArray): String {
        val privateKey = getOrCreateIdentityKeyPair().private
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(data)
        }
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }

    /**
     * Verifies a digital signature against a peer's public key.
     */
    fun verifySignature(data: ByteArray, signatureBase64: String, peerPublicKey: PublicKey): Boolean {
        return try {
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(peerPublicKey)
                update(data)
            }
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Derives an ECDH shared secret key with a peer's public key.
     */
    fun deriveSharedSecret(peerPublicKey: PublicKey): ByteArray {
        val myPrivateKey = getOrCreateIdentityKeyPair().private
        val keyAgreement = KeyAgreement.getInstance("ECDH").apply {
            init(myPrivateKey)
            doPhase(peerPublicKey, true)
        }
        val rawSecret = keyAgreement.generateSecret()
        // Run through HKDF / SHA-256 for symmetric key derivation
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        return sha256.digest(rawSecret)
    }

    /**
     * Parses a public key from Base64 string.
     */
    fun decodePublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
        val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
        val keyFactory = java.security.KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Builds the optical QR handshake payload string.
     */
    fun buildHandshakePayload(displayName: String, localIp: String? = null): String {
        val callsign = getRavenCallSign()
        val pubKey = getPublicKeyBase64()
        val timestamp = System.currentTimeMillis()

        // Signature over callsign + pubKey + timestamp to prevent tampering
        val claim = "$callsign|$pubKey|$timestamp"
        val sig = signData(claim.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("p", "RAVEN_MESH_v1")
            put("name", displayName)
            put("cs", callsign)
            put("pk", pubKey)
            put("ts", timestamp)
            put("sig", sig)
            if (localIp != null) put("ip", localIp)
        }.toString()
    }

    /**
     * Parses and verifies an incoming QR handshake payload.
     * Returns a VerifiedPeerHandshake or null if verification fails.
     */
    fun parseAndVerifyHandshake(jsonString: String): VerifiedPeerHandshake? {
        return try {
            val json = JSONObject(jsonString)
            val protocol = json.getString("p")
            if (protocol != "RAVEN_MESH_v1") return null

            val name = json.getString("name")
            val callsign = json.getString("cs")
            val pubKeyBase64 = json.getString("pk")
            val timestamp = json.getLong("ts")
            val signature = json.getString("sig")
            val ip = if (json.has("ip")) json.getString("ip") else null

            val peerPublicKey = decodePublicKey(pubKeyBase64)
            val claim = "$callsign|$pubKeyBase64|$timestamp"
            val isValid = verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPublicKey)

            if (!isValid) return null

            VerifiedPeerHandshake(
                displayName = name,
                callsign = callsign,
                publicKeyBase64 = pubKeyBase64,
                localIp = ip,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class VerifiedPeerHandshake(
    val displayName: String,
    val callsign: String,
    val publicKeyBase64: String,
    val localIp: String?,
    val timestamp: Long
)
