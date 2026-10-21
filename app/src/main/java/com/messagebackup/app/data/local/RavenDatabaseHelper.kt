package com.messagebackup.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.messagebackup.app.domain.mesh.RavenPeer

/**
 * SQLite storage for trusted Raven Mesh peers and cryptographic contact cards.
 */
class RavenDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "raven_mesh.db"
        private const val DATABASE_VERSION = 3

        const val TABLE_PEERS = "raven_peers"
        const val COL_PEER_ID = "peer_id"               // e.g. "raven://valkyrie-7a3f"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_PUBLIC_KEY = "public_key"
        const val COL_LAST_KNOWN_IP = "last_known_ip"
        const val COL_LAST_SEEN = "last_seen_timestamp"
        const val COL_IS_TRUSTED = "is_trusted"
        const val COL_AVATAR_COLOR = "avatar_color"
        const val COL_STATUS = "peer_status"

        const val TABLE_MESSAGES = "raven_messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_PEER_ID = "peer_id"
        const val COL_MSG_SENDER_CALLSIGN = "sender_callsign"
        const val COL_MSG_SENDER_NAME = "sender_display_name"
        const val COL_MSG_IS_OUTBOUND = "is_outbound"
        const val COL_MSG_TEXT = "message_text"
        const val COL_MSG_TYPE = "message_type"
        const val COL_MSG_TIMESTAMP = "timestamp"
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
                $COL_STATUS TEXT DEFAULT 'CONNECTED'
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
                $COL_MSG_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PEERS ADD COLUMN $COL_STATUS TEXT DEFAULT 'CONNECTED'")
            } catch (_: Exception) {
            }
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
                        $COL_MSG_TIMESTAMP INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
    }

    fun upsertPeer(peer: RavenPeer): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PEER_ID, peer.peerId)
            put(COL_DISPLAY_NAME, peer.displayName)
            put(COL_PUBLIC_KEY, peer.publicKey)
            put(COL_LAST_KNOWN_IP, peer.lastKnownIp)
            put(COL_LAST_SEEN, peer.lastSeenTimestamp)
            put(COL_IS_TRUSTED, if (peer.isTrusted) 1 else 0)
            put(COL_AVATAR_COLOR, peer.avatarColor)
            put(COL_STATUS, peer.status.name)
        }
        val result = db.insertWithOnConflict(TABLE_PEERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return result != -1L
    }

    fun getAllPeers(): List<RavenPeer> {
        val peers = mutableListOf<RavenPeer>()
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

            while (c.moveToNext()) {
                val statusStr = if (idxStatus != -1 && !c.isNull(idxStatus)) c.getString(idxStatus) else "CONNECTED"
                val status = try {
                    com.messagebackup.app.domain.mesh.RavenPeerStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    com.messagebackup.app.domain.mesh.RavenPeerStatus.CONNECTED
                }

                peers.add(
                    RavenPeer(
                        peerId = c.getString(idxId),
                        displayName = c.getString(idxName),
                        publicKey = c.getString(idxKey),
                        lastKnownIp = c.getString(idxIp),
                        lastSeenTimestamp = c.getLong(idxSeen),
                        isTrusted = c.getInt(idxTrusted) == 1,
                        avatarColor = c.getString(idxColor) ?: "#0284c7",
                        status = status
                    )
                )
            }
        }
        return peers
    }

    fun deletePeer(peerId: String): Boolean {
        val db = writableDatabase
        deleteMessagesForPeer(peerId)
        return db.delete(TABLE_PEERS, "$COL_PEER_ID = ?", arrayOf(peerId)) > 0
    }

    fun insertMessage(msg: RavenChatMessage): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_PEER_ID, msg.peerId)
            put(COL_MSG_SENDER_CALLSIGN, msg.senderCallsign)
            put(COL_MSG_SENDER_NAME, msg.senderDisplayName)
            put(COL_MSG_IS_OUTBOUND, if (msg.isOutbound) 1 else 0)
            put(COL_MSG_TEXT, msg.messageText)
            put(COL_MSG_TYPE, msg.messageType)
            put(COL_MSG_TIMESTAMP, msg.timestamp)
        }
        return db.insert(TABLE_MESSAGES, null, values)
    }

    fun getMessagesForPeer(peerId: String): List<RavenChatMessage> {
        val messages = mutableListOf<RavenChatMessage>()
        val db = readableDatabase
        val cleanId = peerId.removePrefix("raven://").trim()
        val withPrefix = "raven://$cleanId"

        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COL_MSG_PEER_ID = ? OR $COL_MSG_PEER_ID = ? OR $COL_MSG_PEER_ID LIKE ?",
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

            while (c.moveToNext()) {
                messages.add(
                    RavenChatMessage(
                        id = c.getLong(idxId),
                        peerId = c.getString(idxPeerId),
                        senderCallsign = c.getString(idxCallsign),
                        senderDisplayName = c.getString(idxName),
                        isOutbound = c.getInt(idxOutbound) == 1,
                        messageText = c.getString(idxText),
                        messageType = c.getString(idxType) ?: "TEXT",
                        timestamp = c.getLong(idxTs)
                    )
                )
            }
        }
        return messages
    }

    fun deleteMessagesForPeer(peerId: String): Boolean {
        val db = writableDatabase
        val cleanId = peerId.removePrefix("raven://").trim()
        val withPrefix = "raven://$cleanId"
        return db.delete(
            TABLE_MESSAGES,
            "$COL_MSG_PEER_ID = ? OR $COL_MSG_PEER_ID = ? OR $COL_MSG_PEER_ID LIKE ?",
            arrayOf(cleanId, withPrefix, "%$cleanId%")
        ) > 0
    }
}

/**
 * Data model for persistent Raven Mesh E2EE chat messages and voice snippets.
 */
data class RavenChatMessage(
    val id: Long = 0,
    val peerId: String,
    val senderCallsign: String,
    val senderDisplayName: String,
    val isOutbound: Boolean,
    val messageText: String,
    val messageType: String = "TEXT",
    val timestamp: Long = System.currentTimeMillis()
)
