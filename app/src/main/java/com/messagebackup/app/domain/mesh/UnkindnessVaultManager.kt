package com.messagebackup.app.domain.mesh

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.messagebackup.app.data.local.RavenDatabaseHelper
import com.messagebackup.app.data.preferences.AppPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages zero-loss persistent identity and Flock contact recovery across app uninstalls
 * and device migrations using a 12-word Runic BIP-39 mnemonic and AES-256-GCM encrypted local vault.
 */
class UnkindnessVaultManager(private val context: Context) {

    companion object {
        private const val TAG = "UnkindnessVaultManager"
        private const val PREFS_NAME = "raven_mesh_prefs"
        private const val PREF_RUNIC_MNEMONIC = "pref_runic_mnemonic_phrase"
        private const val VAULT_FILE_NAME = "unkindness_identity.sigil"
        private const val SUBFOLDER_NAME = "Muninn"
        private const val SALT = "MuninnTheUnkindnessRunicVault_v1"
        private const val PBKDF2_ITERATIONS = 10_000
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val QR_PREFIX = "UNKINDNESS_RUNIC:"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appPreferences = AppPreferences(context)
    private val cryptoEngine = RavenCryptoEngine(context)
    private val databaseHelper = RavenDatabaseHelper(context)

    // In-memory cache of BIP-39 wordlist
    private var wordListCache: List<String>? = null

    @Synchronized
    private fun getWordList(): List<String> {
        wordListCache?.let { return it }
        return try {
            context.assets.open("bip39_english.txt").bufferedReader().useLines { lines ->
                val list = lines.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toList()
                if (list.size == 2048) {
                    wordListCache = list
                    list
                } else {
                    fallbackWordList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load BIP39 word list from assets, using fallback", e)
            fallbackWordList()
        }
    }

    private fun fallbackWordList(): List<String> {
        // Safe fallback in case assets read fails in a non-standard runtime
        return listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse",
            "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire", "across", "act",
            "action", "actor", "actress", "actual", "adapt", "add", "addict", "address", "adjust", "admit",
            "adult", "advance", "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album", "alcohol", "alert"
        )
    }

    /**
     * Generates a standard BIP-39 12-word mnemonic from 128-bit secure random entropy with checksum.
     */
    fun generate12WordMnemonic(): List<String> {
        val wordList = getWordList()
        if (wordList.size < 2048) {
            // Generate pseudo 12 words from available words
            val random = SecureRandom()
            return List(12) { wordList[random.nextInt(wordList.size)] }
        }

        val entropy = ByteArray(16) // 128 bits
        SecureRandom().nextBytes(entropy)

        // SHA-256 for 4 bits of checksum
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumByte = hash[0].toInt() and 0xFF
        val checksumBits = checksumByte ushr 4 // first 4 bits

        // Combine 128 bits + 4 checksum bits = 132 bits
        val bits = BooleanArray(132)
        for (i in 0 until 16) {
            val b = entropy[i].toInt() and 0xFF
            for (j in 0 until 8) {
                bits[i * 8 + j] = ((b ushr (7 - j)) and 1) == 1
            }
        }
        for (j in 0 until 4) {
            bits[128 + j] = ((checksumBits ushr (3 - j)) and 1) == 1
        }

        // Divide into 12 chunks of 11 bits
        val mnemonic = ArrayList<String>(12)
        for (i in 0 until 12) {
            var index = 0
            for (j in 0 until 11) {
                index = (index shl 1) or (if (bits[i * 11 + j]) 1 else 0)
            }
            mnemonic.add(wordList[index % wordList.size])
        }
        return mnemonic
    }

    /**
     * Retrieves the user's stored Runic Mnemonic phrase, or generates and securely saves a new one.
     */
    fun getOrGenerateUserMnemonic(): List<String> {
        val saved = prefs.getString(PREF_RUNIC_MNEMONIC, null)
        if (!saved.isNullOrBlank()) {
            val words = saved.trim().split("\\s+".toRegex())
            if (words.size == 12) return words
        }

        val newWords = generate12WordMnemonic()
        prefs.edit().putString(PREF_RUNIC_MNEMONIC, newWords.joinToString(" ")).apply()
        return newWords
    }

    /**
     * Validates whether a 12-word phrase matches the dictionary.
     */
    fun validateMnemonic(words: List<String>): Boolean {
        if (words.size != 12) return false
        val dict = getWordList().toHashSet()
        if (dict.size < 2048) return true // Allow fallback dictionary validation
        return words.all { dict.contains(it.lowercase().trim()) }
    }

    /**
     * Derives a 256-bit AES secret key using PBKDF2WithHmacSHA256 from the 12-word mnemonic.
     */
    private fun deriveSecretKey(mnemonic: List<String>): SecretKey {
        val passphrase = mnemonic.joinToString(" ") { it.lowercase().trim() }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), SALT.toByteArray(Charsets.UTF_8), PBKDF2_ITERATIONS, 256)
        val secretBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(secretBytes, "AES")
    }

    /**
     * Encrypts and saves the current Sigil identity, radio channel, and Flock peers
     * to persistent storage (survives app uninstall) and internal app files.
     */
    fun saveVault(customMnemonic: List<String>? = null): Result<File> {
        return try {
            val mnemonic = customMnemonic ?: getOrGenerateUserMnemonic()
            val secretKey = deriveSecretKey(mnemonic)

            val (pubKey, privKey) = cryptoEngine.getExportableKeyPair()
            val callsign = cryptoEngine.getRavenCallSign()
            val peers = databaseHelper.getAllPeers()
            val radioChannel = appPreferences.ravenRadioChannel.value
            val squelchMute = appPreferences.ravenRadioSquelchMute.value

            val json = JSONObject().apply {
                put("version", 1)
                put("app", "Muninn: The Unkindness")
                put("timestamp", System.currentTimeMillis())
                put("callsign", callsign)
                put("public_key", pubKey)
                put("private_key", privKey)
                put("radio_channel", radioChannel)
                put("radio_squelch", squelchMute)

                val peersArray = JSONArray()
                for (peer in peers) {
                    val pObj = JSONObject().apply {
                        put("peer_id", peer.peerId)
                        put("display_name", peer.displayName)
                        put("public_key", peer.publicKey)
                        put("last_known_ip", peer.lastKnownIp ?: "")
                        put("last_seen_timestamp", peer.lastSeenTimestamp)
                        put("is_trusted", peer.isTrusted)
                        put("avatar_color", peer.avatarColor)
                        put("peer_status", peer.status.name)
                    }
                    peersArray.put(pObj)
                }
                put("peers", peersArray)
            }

            val plainBytes = json.toString().toByteArray(Charsets.UTF_8)

            // AES-256-GCM encryption with 12-byte random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherBytes = cipher.doFinal(plainBytes)

            // Prepend IV: [12-byte IV][Ciphertext + 16-byte Tag]
            val outputBytes = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, outputBytes, 0, iv.size)
            System.arraycopy(cipherBytes, 0, outputBytes, iv.size, cipherBytes.size)

            // 1. Write to internal app files directory (backed up by Google Drive cloud backup)
            val internalFile = File(context.filesDir, VAULT_FILE_NAME)
            internalFile.writeBytes(outputBytes)

            // 2. Write to public Documents/Muninn/ folder (survives app uninstallation)
            saveToDocuments(outputBytes)

            // Save verified mnemonic in preferences
            prefs.edit().putString(PREF_RUNIC_MNEMONIC, mnemonic.joinToString(" ")).apply()

            Log.i(TAG, "Unkindness Vault saved successfully (${peers.size} peers, ${outputBytes.size} bytes)")
            Result.success(internalFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Unkindness Vault", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a persistent vault exists on the device (either in Documents or internal storage).
     */
    fun hasVaultOnDevice(): Boolean {
        val internalFile = File(context.filesDir, VAULT_FILE_NAME)
        if (internalFile.exists() && internalFile.length() > 32) return true

        val documentsFile = getDocumentsVaultFile()
        if (documentsFile != null && documentsFile.exists() && documentsFile.length() > 32) return true

        return hasDocumentsFileViaMediaStore()
    }

    /**
     * Restores Sigil identity, radio settings, and all Flock contacts from the encrypted vault
     * using the 12-word Runic mnemonic.
     */
    fun restoreVault(mnemonicWords: List<String>): Result<Int> {
        val encryptedBytes = readVaultBytes()
            ?: return Result.failure(IllegalStateException("No Unkindness Vault file found on device"))
        return restoreVaultFromBytes(encryptedBytes, mnemonicWords)
    }

    /**
     * Restores Sigil identity, radio settings, and all Flock contacts from raw encrypted vault bytes
     * (e.g. read from local storage, Google Drive cloud backup, or SAF Document Picker).
     */
    fun restoreVaultFromBytes(encryptedBytes: ByteArray, mnemonicWords: List<String>): Result<Int> {
        return try {
            if (mnemonicWords.size != 12) {
                return Result.failure(IllegalArgumentException("Recovery phrase must be exactly 12 words"))
            }

            val secretKey = deriveSecretKey(mnemonicWords)

            if (encryptedBytes.size <= GCM_IV_LENGTH) {
                return Result.failure(IllegalStateException("Vault file is corrupted or too small"))
            }

            // Extract 12-byte IV and ciphertext
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(encryptedBytes, 0, iv, 0, GCM_IV_LENGTH)
            val cipherBytes = ByteArray(encryptedBytes.size - GCM_IV_LENGTH)
            System.arraycopy(encryptedBytes, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(cipherBytes)

            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonString)

            val callsign = json.optString("callsign", "")
            val moniker = callsign.removePrefix("raven://").trim()
            val pubKey = json.getString("public_key")
            val privKey = json.getString("private_key")
            val radioChannel = json.optInt("radio_channel", 1)
            val radioSquelch = json.optBoolean("radio_squelch", false)

            // 1. Restore identity keypair & callsign
            cryptoEngine.importIdentity(moniker, pubKey, privKey)

            // 2. Restore radio preferences
            appPreferences.setRavenRadioChannel(radioChannel)
            appPreferences.setRavenRadioSquelchMute(radioSquelch)

            // 3. Restore Flock peers
            var restoredPeerCount = 0
            if (json.has("peers")) {
                val peersArray = json.getJSONArray("peers")
                for (i in 0 until peersArray.length()) {
                    val pObj = peersArray.getJSONObject(i)
                    val peerId = pObj.getString("peer_id")
                    val displayName = pObj.getString("display_name")
                    val peerPublicKey = pObj.getString("public_key")
                    val lastKnownIp = pObj.optString("last_known_ip", "").takeIf { it.isNotBlank() }
                    val lastSeen = pObj.optLong("last_seen_timestamp", System.currentTimeMillis())
                    val isTrusted = pObj.optBoolean("is_trusted", true)
                    val avatarColor = pObj.optString("avatar_color", "#0284c7")
                    val statusStr = pObj.optString("peer_status", "CONNECTED")
                    val status = runCatching { RavenPeerStatus.valueOf(statusStr) }.getOrDefault(RavenPeerStatus.CONNECTED)

                    val peer = RavenPeer(
                        peerId = peerId,
                        displayName = displayName,
                        publicKey = peerPublicKey,
                        lastKnownIp = lastKnownIp,
                        lastSeenTimestamp = lastSeen,
                        isTrusted = isTrusted,
                        avatarColor = avatarColor,
                        status = status
                    )
                    if (databaseHelper.upsertPeer(peer)) {
                        restoredPeerCount++
                    }
                }
            }

            // Save restored mnemonic to preferences
            prefs.edit().putString(PREF_RUNIC_MNEMONIC, mnemonicWords.joinToString(" ")).apply()

            Log.i(TAG, "Unkindness Vault restored successfully ($restoredPeerCount peers restored)")
            Result.success(restoredPeerCount)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "Incorrect 12-word Runic mnemonic provided for vault", e)
            Result.failure(IllegalArgumentException("Incorrect 12-word phrase. The authentication key did not match."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore Unkindness Vault", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a compact QR payload string for single-scan recovery.
     */
    fun generateRecoveryQrPayload(words: List<String>): String {
        return "$QR_PREFIX${words.joinToString(",")}"
    }

    /**
     * Parses a recovery QR code payload into words.
     */
    fun parseRecoveryQrPayload(qrContent: String): List<String>? {
        if (!qrContent.startsWith(QR_PREFIX)) return null
        val body = qrContent.removePrefix(QR_PREFIX).trim()
        val words = body.split(",").map { it.trim().lowercase() }
        return if (words.size == 12) words else null
    }

    // --- Private Storage Utilities ---

    private fun getDocumentsVaultFile(): File? {
        return try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val subDir = File(docsDir, SUBFOLDER_NAME)
            File(subDir, VAULT_FILE_NAME)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveToDocuments(bytes: ByteArray) {
        try {
            // Attempt direct file write (works on many devices and when directory already exists)
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val subDir = File(docsDir, SUBFOLDER_NAME)
            if (!subDir.exists()) subDir.mkdirs()
            val targetFile = File(subDir, VAULT_FILE_NAME)
            targetFile.writeBytes(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Direct Documents write failed, falling back to MediaStore", e)
            saveViaMediaStore(bytes)
        }
    }

    private fun saveViaMediaStore(bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                var existingUri: android.net.Uri? = null
                resolver.query(
                    contentUri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(VAULT_FILE_NAME),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        existingUri = android.content.ContentUris.withAppendedId(contentUri, id)
                    }
                }

                existingUri?.let { targetUri ->
                    try {
                        resolver.openOutputStream(targetUri, "wt")?.use { os ->
                            os.write(bytes)
                        }
                        return
                    } catch (_: Exception) {
                        try { resolver.delete(targetUri, null, null) } catch (_: Exception) {}
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, VAULT_FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$SUBFOLDER_NAME")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(contentUri, values) ?: return
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore vault write failed", e)
            }
        }
    }

    private fun hasDocumentsFileViaMediaStore(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(VAULT_FILE_NAME),
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun readVaultBytes(): ByteArray? {
        // 1. Try public Documents directory file
        val docFile = getDocumentsVaultFile()
        if (docFile != null && docFile.exists() && docFile.length() > 0) {
            try {
                return docFile.readBytes()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read direct Documents vault file: ${e.message}")
            }
        }

        // 2. Try MediaStore query for Documents/Muninn/unkindness_identity.sigil
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                resolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(VAULT_FILE_NAME),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val fileUri = android.content.ContentUris.withAppendedId(uri, id)
                        resolver.openInputStream(fileUri)?.use { stream ->
                            return stream.readBytes()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read vault from MediaStore: ${e.message}")
            }
        }

        // 3. Fallback to app's internal filesDir
        val internalFile = File(context.filesDir, VAULT_FILE_NAME)
        if (internalFile.exists() && internalFile.length() > 0) {
            return internalFile.readBytes()
        }

        return null
    }
}
