package com.messagebackup.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.messagebackup.app.domain.mesh.RavenPeer
import com.messagebackup.app.domain.mesh.RavenPeerStatus

/**
 * SQLite storage for trusted Raven Mesh peers, cryptographic contact cards,
 * encrypted parcels, voice notes with transcriptions, and Loki's Mist ephemeral timers.
 */
class RavenDatabaseHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "raven_mesh.db"
        private const val DATABASE_VERSION = 5

        const val TABLE_PEERS = "raven_peers"
        const val COL_PEER_ID = "peer_id"               // Canonical lowercase "raven://callsign"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_PUBLIC_KEY = "public_key"
        const val COL_LAST_KNOWN_IP = "last_known_ip"
        const val COL_LAST_SEEN = "last_seen_timestamp"
        const val COL_IS_TRUSTED = "is_trusted"
        const val COL_AVATAR_COLOR = "avatar_color"
        const val COL_STATUS = "peer_status"
        const val COL_UNREAD_COUNT = "unread_count"
        const val COL_EPHEMERAL_TTL = "ephemeral_ttl_sec"

        const val TABLE_MESSAGES = "raven_messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_PEER_ID = "peer_id"
        const val COL_MSG_SENDER_CALLSIGN = "sender_callsign"
        const val COL_MSG_SENDER_NAME = "sender_display_name"
        const val COL_MSG_IS_OUTBOUND = "is_outbound"
        const val COL_MSG_TEXT = "message_text"
        const val COL_MSG_TYPE = "message_type" // TEXT, VOICE, IMAGE, FILE, LOCATION, SMS_EXCERPT
        const val COL_MSG_TIMESTAMP = "timestamp"
        const val COL_MSG_AUDIO_BASE64 = "audio_base64"
        const val COL_MSG_ATTACHMENT_BASE64 = "attachment_base64"
        const val COL_MSG_ATTACHMENT_NAME = "attachment_name"
        const val COL_MSG_ATTACHMENT_MIME = "attachment_mime"
        const val COL_MSG_TRANSCRIPTION = "transcription_text"
        const val COL_MSG_EXPIRES_AT = "expires_at_timestamp"
        const val COL_MSG_BURN_ON_READ = "burn_on_read"

        fun normalizePeerId(rawPeerId: String): String {
            val clean = rawPeerId.removePrefix("raven://").trim().lowercase()
            return "raven://$clean"
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PEERS (
                $COL_PEER_ID TEXT PRIMARY KEY,
                $COL_DISPLAY_NAME TEXT NOT NULL,
                $COL_PUBLIC_KEY TEXT NOT NULL,
                $COL_LAST_KNOWN_IP TEXT,
                $COL_LAST_SEEN INTEGER NOT NULL,
                $COL_IS_TRUSTED INTEGER DEFAULT 1,
                $COL_AVATAR_COLOR TEXT DEFAULT '#0284c7',
                $COL_STATUS TEXT DEFAULT 'CONNECTED',
                $COL_UNREAD_COUNT INTEGER DEFAULT 0,
                $COL_EPHEMERAL_TTL INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                $COL_MSG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MSG_PEER_ID TEXT NOT NULL,
                $COL_MSG_SENDER_CALLSIGN TEXT NOT NULL,
                $COL_MSG_SENDER_NAME TEXT NOT NULL,
                $COL_MSG_IS_OUTBOUND INTEGER NOT NULL,
                $COL_MSG_TEXT TEXT NOT NULL,
                $COL_MSG_TYPE TEXT DEFAULT 'TEXT',
                $COL_MSG_TIMESTAMP INTEGER NOT NULL,
                $COL_MSG_AUDIO_BASE64 TEXT,
                $COL_MSG_ATTACHMENT_BASE64 TEXT,
                $COL_MSG_ATTACHMENT_NAME TEXT,
                $COL_MSG_ATTACHMENT_MIME TEXT,
                $COL_MSG_TRANSCRIPTION TEXT,
                $COL_MSG_EXPIRES_AT INTEGER DEFAULT 0,
                $COL_MSG_BURN_ON_READ INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PEERS ADD COLUMN $COL_STATUS TEXT DEFAULT 'CONNECTED'")
            } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                        $COL_MSG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_MSG_PEER_ID TEXT NOT NULL,
                        $COL_MSG_SENDER_CALLSIGN TEXT NOT NULL,
                        $COL_MSG_SENDER_NAME TEXT NOT NULL,
                        $COL_MSG_IS_OUTBOUND INTEGER NOT NULL,
                        $COL_MSG_TEXT TEXT NOT NULL,
                        $COL_MSG_TYPE TEXT DEFAULT 'TEXT',
                        $COL_MSG_TIMESTAMP INTEGER NOT NULL,
                        $COL_MSG_AUDIO_BASE64 TEXT
                    )
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COL_MSG_AUDIO_BASE64 TEXT")
            } catch (_: Exception) {}
        }
        if (oldVersion < 5) {
            val peerCols = listOf(
                "$COL_UNREAD_COUNT INTEGER DEFAULT 0",
                "$COL_EPHEMERAL_TTL INTEGER DEFAULT 0"
            )
            for (col in peerCols) {
                try {
                    db.execSQL("ALTER TABLE $TABLE_PEERS ADD COLUMN $col")
                } catch (_: Exception) {}
            }

            val msgCols = listOf(
                "$COL_MSG_ATTACHMENT_BASE64 TEXT",
                "$COL_MSG_ATTACHMENT_NAME TEXT",
                "$COL_MSG_ATTACHMENT_MIME TEXT",
                "$COL_MSG_TRANSCRIPTION TEXT",
                "$COL_MSG_EXPIRES_AT INTEGER DEFAULT 0",
                "$COL_MSG_BURN_ON_READ INTEGER DEFAULT 0"
            )
            for (col in msgCols) {
                try {
                    db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $col")
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Upserts a peer with canonical normalized peer_id so duplicate rows (e.g. with/without raven:// or mixed case) never occur.
     */
    fun upsertPeer(peer: RavenPeer): Boolean {
        val db = writableDatabase
        val canonicalId = normalizePeerId(peer.peerId)
        val cleanWithoutPrefix = peer.peerId.removePrefix("raven://").trim()

        // Preserve existing unread count & ephemeral TTL if already in DB
        var existingUnread = 0
        var existingTtl = 0
        var existingStatus: RavenPeerStatus? = null
        db.rawQuery(
            "SELECT $COL_UNREAD_COUNT, $COL_EPHEMERAL_TTL, $COL_STATUS FROM $TABLE_PEERS WHERE LOWER($COL_PEER_ID) = ? OR LOWER($COL_PEER_ID) = ?",
            arrayOf(canonicalId, cleanWithoutPrefix.lowercase())
        )?.use { c ->
            if (c.moveToFirst()) {
                existingUnread = c.getInt(0)
                existingTtl = c.getInt(1)
                val stStr = c.getString(2)
                existingStatus = runCatching { RavenPeerStatus.valueOf(stStr) }.getOrNull()
            }
        }

        // Remove any legacy non-canonical duplicate rows for this same peer
        db.delete(
            TABLE_PEERS,
            "LOWER($COL_PEER_ID) = ? OR LOWER($COL_PEER_ID) = ?",
            arrayOf(canonicalId, cleanWithoutPrefix.lowercase())
        )

        // Never downgrade a CONNECTED peer back to PENDING
        val finalStatus = if (existingStatus == RavenPeerStatus.CONNECTED) {
            RavenPeerStatus.CONNECTED
        } else {
            peer.status
        }

        val values = ContentValues().apply {
            put(COL_PEER_ID, canonicalId)
            put(COL_DISPLAY_NAME, peer.displayName.removePrefix("raven://").ifBlank { cleanWithoutPrefix })
            put(COL_PUBLIC_KEY, peer.publicKey)
            put(COL_LAST_KNOWN_IP, peer.lastKnownIp)
            put(COL_LAST_SEEN, peer.lastSeenTimestamp)
            put(COL_IS_TRUSTED, if (peer.isTrusted) 1 else 0)
            put(COL_AVATAR_COLOR, peer.avatarColor)
            put(COL_STATUS, finalStatus.name)
            put(COL_UNREAD_COUNT, existingUnread)
            put(COL_EPHEMERAL_TTL, existingTtl)
        }
        val result = db.insertWithOnConflict(TABLE_PEERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return result != -1L
    }

    fun getAllPeers(): List<RavenPeer> {
        val peersMap = LinkedHashMap<String, RavenPeer>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PEERS,
            null,
            null,
            null,
            null,
            null,
            "$COL_LAST_SEEN DESC"
        )

        cursor.use { c ->
            val idxId = c.getColumnIndexOrThrow(COL_PEER_ID)
            val idxName = c.getColumnIndexOrThrow(COL_DISPLAY_NAME)
            val idxKey = c.getColumnIndexOrThrow(COL_PUBLIC_KEY)
            val idxIp = c.getColumnIndexOrThrow(COL_LAST_KNOWN_IP)
            val idxSeen = c.getColumnIndexOrThrow(COL_LAST_SEEN)
            val idxTrusted = c.getColumnIndexOrThrow(COL_IS_TRUSTED)
            val idxColor = c.getColumnIndexOrThrow(COL_AVATAR_COLOR)
            val idxStatus = c.getColumnIndex(COL_STATUS)
            val idxUnread = c.getColumnIndex(COL_UNREAD_COUNT)

            while (c.moveToNext()) {
                val rawId = c.getString(idxId)
                val canonicalId = normalizePeerId(rawId)
                val statusStr = if (idxStatus != -1 && !c.isNull(idxStatus)) c.getString(idxStatus) else "CONNECTED"
                val status = try {
                    RavenPeerStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    RavenPeerStatus.CONNECTED
                }
                val unread = if (idxUnread != -1 && !c.isNull(idxUnread)) c.getInt(idxUnread) else 0

                val peerObj = RavenPeer(
                    peerId = canonicalId,
                    displayName = c.getString(idxName),
                    publicKey = c.getString(idxKey),
                    lastKnownIp = c.getString(idxIp),
                    lastSeenTimestamp = c.getLong(idxSeen),
                    isTrusted = c.getInt(idxTrusted) == 1,
                    avatarColor = c.getString(idxColor) ?: "#0284c7",
                    status = status,
                    unreadCount = unread
                )

                val existing = peersMap[canonicalId]
                if (existing == null || (existing.status != RavenPeerStatus.CONNECTED && peerObj.status == RavenPeerStatus.CONNECTED)) {
                    peersMap[canonicalId] = peerObj
                }
            }
        }
        return peersMap.values.toList()
    }

    fun getUnreadCountForPeer(peerId: String): Int {
        val db = readableDatabase
        val canonicalId = normalizePeerId(peerId)
        db.rawQuery("SELECT $COL_UNREAD_COUNT FROM $TABLE_PEERS WHERE LOWER($COL_PEER_ID) = ?", arrayOf(canonicalId))?.use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    fun getTotalUnreadCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT SUM($COL_UNREAD_COUNT) FROM $TABLE_PEERS", null)?.use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    fun incrementUnreadCount(peerId: String) {
        val db = writableDatabase
        val canonicalId = normalizePeerId(peerId)
        db.execSQL("UPDATE $TABLE_PEERS SET $COL_UNREAD_COUNT = $COL_UNREAD_COUNT + 1 WHERE LOWER($COL_PEER_ID) = ?", arrayOf(canonicalId))
    }

    fun clearUnreadCount(peerId: String) {
        val db = writableDatabase
        val canonicalId = normalizePeerId(peerId)
        db.execSQL("UPDATE $TABLE_PEERS SET $COL_UNREAD_COUNT = 0 WHERE LOWER($COL_PEER_ID) = ?", arrayOf(canonicalId))
    }

    fun getEphemeralTtlForPeer(peerId: String): Int {
        val db = readableDatabase
        val canonicalId = normalizePeerId(peerId)
        db.rawQuery("SELECT $COL_EPHEMERAL_TTL FROM $TABLE_PEERS WHERE LOWER($COL_PEER_ID) = ?", arrayOf(canonicalId))?.use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    fun setEphemeralTtlForPeer(peerId: String, ttlSeconds: Int) {
        val db = writableDatabase
        val canonicalId = normalizePeerId(peerId)
        val values = ContentValues().apply {
            put(COL_EPHEMERAL_TTL, ttlSeconds)
        }
        db.update(TABLE_PEERS, values, "LOWER($COL_PEER_ID) = ?", arrayOf(canonicalId))
    }

    fun getLastMessageForPeer(peerId: String): RavenChatMessage? {
        purgeExpiredMessages()
        val msgs = getMessagesForPeer(peerId)
        return msgs.lastOrNull()
    }

    fun deletePeer(peerId: String): Boolean {
        val db = writableDatabase
        val canonicalId = normalizePeerId(peerId)
        val clean = peerId.removePrefix("raven://").trim().lowercase()
        deleteMessagesForPeer(peerId)
        return db.delete(
            TABLE_PEERS,
            "LOWER($COL_PEER_ID) = ? OR LOWER($COL_PEER_ID) = ?",
            arrayOf(canonicalId, clean)
        ) > 0
    }

    fun insertMessage(msg: RavenChatMessage): Long {
        val db = writableDatabase
        val canonicalPeerId = normalizePeerId(msg.peerId)
        val values = ContentValues().apply {
            put(COL_MSG_PEER_ID, canonicalPeerId)
            put(COL_MSG_SENDER_CALLSIGN, msg.senderCallsign)
            put(COL_MSG_SENDER_NAME, msg.senderDisplayName)
            put(COL_MSG_IS_OUTBOUND, if (msg.isOutbound) 1 else 0)
            put(COL_MSG_TEXT, msg.messageText)
            put(COL_MSG_TYPE, msg.messageType)
            put(COL_MSG_TIMESTAMP, msg.timestamp)
            put(COL_MSG_AUDIO_BASE64, msg.audioBase64)
            put(COL_MSG_ATTACHMENT_BASE64, msg.attachmentBase64)
            put(COL_MSG_ATTACHMENT_NAME, msg.attachmentName)
            put(COL_MSG_ATTACHMENT_MIME, msg.attachmentMimeType)
            put(COL_MSG_TRANSCRIPTION, msg.transcription)
            put(COL_MSG_EXPIRES_AT, msg.expiresAtTimestamp)
            put(COL_MSG_BURN_ON_READ, if (msg.burnOnRead) 1 else 0)
        }
        return db.insert(TABLE_MESSAGES, null, values)
    }

    fun updateMessageTranscription(messageId: Long, transcription: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_TRANSCRIPTION, transcription)
        }
        db.update(TABLE_MESSAGES, values, "$COL_MSG_ID = ?", arrayOf(messageId.toString()))
    }

    fun deleteMessageById(messageId: Long) {
        val db = writableDatabase
        db.delete(TABLE_MESSAGES, "$COL_MSG_ID = ?", arrayOf(messageId.toString()))
    }

    fun purgeExpiredMessages(): Int {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        return db.delete(
            TABLE_MESSAGES,
            "$COL_MSG_EXPIRES_AT > 0 AND $COL_MSG_EXPIRES_AT <= ?",
            arrayOf(now.toString())
        )
    }

    fun getMessagesForPeer(peerId: String): List<RavenChatMessage> {
        purgeExpiredMessages()
        val messages = mutableListOf<RavenChatMessage>()
        val db = readableDatabase
        val cleanId = peerId.removePrefix("raven://").trim().lowercase()
        val withPrefix = "raven://$cleanId"

        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "LOWER($COL_MSG_PEER_ID) = ? OR LOWER($COL_MSG_PEER_ID) = ? OR LOWER($COL_MSG_PEER_ID) LIKE ?",
            arrayOf(cleanId, withPrefix, "%$cleanId%"),
            null,
            null,
            "$COL_MSG_TIMESTAMP ASC"
        )

        cursor.use { c ->
            val idxId = c.getColumnIndexOrThrow(COL_MSG_ID)
            val idxPeerId = c.getColumnIndexOrThrow(COL_MSG_PEER_ID)
            val idxCallsign = c.getColumnIndexOrThrow(COL_MSG_SENDER_CALLSIGN)
            val idxName = c.getColumnIndexOrThrow(COL_MSG_SENDER_NAME)
            val idxOutbound = c.getColumnIndexOrThrow(COL_MSG_IS_OUTBOUND)
            val idxText = c.getColumnIndexOrThrow(COL_MSG_TEXT)
            val idxType = c.getColumnIndexOrThrow(COL_MSG_TYPE)
            val idxTs = c.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)
            val idxAudio = c.getColumnIndex(COL_MSG_AUDIO_BASE64)
            val idxAttB64 = c.getColumnIndex(COL_MSG_ATTACHMENT_BASE64)
            val idxAttName = c.getColumnIndex(COL_MSG_ATTACHMENT_NAME)
            val idxAttMime = c.getColumnIndex(COL_MSG_ATTACHMENT_MIME)
            val idxTrans = c.getColumnIndex(COL_MSG_TRANSCRIPTION)
            val idxExp = c.getColumnIndex(COL_MSG_EXPIRES_AT)
            val idxBurn = c.getColumnIndex(COL_MSG_BURN_ON_READ)

            while (c.moveToNext()) {
                val audioB64 = if (idxAudio != -1 && !c.isNull(idxAudio)) c.getString(idxAudio) else null
                val attB64 = if (idxAttB64 != -1 && !c.isNull(idxAttB64)) c.getString(idxAttB64) else null
                val attName = if (idxAttName != -1 && !c.isNull(idxAttName)) c.getString(idxAttName) else null
                val attMime = if (idxAttMime != -1 && !c.isNull(idxAttMime)) c.getString(idxAttMime) else null
                val trans = if (idxTrans != -1 && !c.isNull(idxTrans)) c.getString(idxTrans) else null
                val exp = if (idxExp != -1 && !c.isNull(idxExp)) c.getLong(idxExp) else 0L
                val burn = if (idxBurn != -1 && !c.isNull(idxBurn)) c.getInt(idxBurn) == 1 else false

                messages.add(
                    RavenChatMessage(
                        id = c.getLong(idxId),
                        peerId = c.getString(idxPeerId),
                        senderCallsign = c.getString(idxCallsign),
                        senderDisplayName = c.getString(idxName),
                        isOutbound = c.getInt(idxOutbound) == 1,
                        messageText = c.getString(idxText),
                        messageType = c.getString(idxType) ?: "TEXT",
                        timestamp = c.getLong(idxTs),
                        audioBase64 = audioB64,
                        attachmentBase64 = attB64,
                        attachmentName = attName,
                        attachmentMimeType = attMime,
                        transcription = trans,
                        expiresAtTimestamp = exp,
                        burnOnRead = burn
                    )
                )
            }
        }
        return messages
    }

    fun deleteMessagesForPeer(peerId: String): Boolean {
        val db = writableDatabase
        val cleanId = peerId.removePrefix("raven://").trim().lowercase()
        val withPrefix = "raven://$cleanId"
        return db.delete(
            TABLE_MESSAGES,
            "LOWER($COL_MSG_PEER_ID) = ? OR LOWER($COL_MSG_PEER_ID) = ? OR LOWER($COL_MSG_PEER_ID) LIKE ?",
            arrayOf(cleanId, withPrefix, "%$cleanId%")
        ) > 0
    }
}

/**
 * Data model for persistent Raven Mesh E2EE chat messages, voice snippets, parcels, and ephemeral timers.
 */
data class RavenChatMessage(
    val id: Long = 0,
    val peerId: String,
    val senderCallsign: String,
    val senderDisplayName: String,
    val isOutbound: Boolean,
    val messageText: String,
    val messageType: String = "TEXT", // TEXT, VOICE, IMAGE, FILE, LOCATION, SMS_EXCERPT
    val timestamp: Long = System.currentTimeMillis(),
    val audioBase64: String? = null,
    val attachmentBase64: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val transcription: String? = null,
    val expiresAtTimestamp: Long = 0L,
    val burnOnRead: Boolean = false
)
