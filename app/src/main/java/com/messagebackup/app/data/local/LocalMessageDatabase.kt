package com.messagebackup.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MediaAttachment
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import com.messagebackup.app.model.VaultMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance, persistent local SQLite database cache for conversation threads,
 * messages, MMS attachments, and Media Vault items.
 *
 * Configured with Write-Ahead Logging (WAL) for lock-free concurrent reads and writes,
 * enabling instantaneous 0 ms cold loads.
 */
class LocalMessageDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS threads (
                thread_id INTEGER PRIMARY KEY,
                address TEXT,
                contact_name TEXT,
                snippet TEXT,
                timestamp INTEGER,
                message_count INTEGER,
                has_attachment INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_threads_time ON threads(timestamp DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                message_id INTEGER PRIMARY KEY,
                thread_id INTEGER,
                type TEXT,
                direction TEXT,
                sender TEXT,
                recipient TEXT,
                body TEXT,
                timestamp INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_thread_time ON messages(thread_id, timestamp DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS attachments (
                part_id INTEGER PRIMARY KEY,
                message_id INTEGER,
                content_type TEXT,
                uri_string TEXT,
                file_name TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachments_msg ON attachments(message_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vault_items (
                part_id INTEGER PRIMARY KEY,
                message_id INTEGER,
                thread_id INTEGER,
                timestamp INTEGER,
                content_type TEXT,
                file_name TEXT,
                uri_string TEXT,
                contact_name TEXT,
                contact_address TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_date ON vault_items(timestamp DESC)")

        createExtraTables(db)
    }

    private fun createExtraTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS odin_chat_history (
                id TEXT PRIMARY KEY,
                thread_key TEXT NOT NULL,
                sender TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_error INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_odin_thread_time ON odin_chat_history(thread_key, timestamp ASC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS starred_memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id INTEGER NOT NULL,
                thread_id INTEGER NOT NULL,
                contact_name TEXT NOT NULL,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                source_type TEXT DEFAULT 'SMS'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_starred_time ON starred_memories(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createExtraTables(db)
        } else {
            db.execSQL("DROP TABLE IF EXISTS threads")
            db.execSQL("DROP TABLE IF EXISTS messages")
            db.execSQL("DROP TABLE IF EXISTS attachments")
            db.execSQL("DROP TABLE IF EXISTS vault_items")
            onCreate(db)
        }
    }

    /* ------------------------------------------------------------------------
       Thread Operations
       ------------------------------------------------------------------------ */

    suspend fun getCachedThreads(): List<ConversationThread> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ConversationThread>()
        val db = readableDatabase
        db.rawQuery("SELECT thread_id, address, contact_name, snippet, timestamp, message_count, has_attachment FROM threads ORDER BY timestamp DESC", null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("thread_id")
            val addrIdx = c.getColumnIndexOrThrow("address")
            val nameIdx = c.getColumnIndexOrThrow("contact_name")
            val snipIdx = c.getColumnIndexOrThrow("snippet")
            val timeIdx = c.getColumnIndexOrThrow("timestamp")
            val countIdx = c.getColumnIndexOrThrow("message_count")
            val attachIdx = c.getColumnIndexOrThrow("has_attachment")

            while (c.moveToNext()) {
                list.add(
                    ConversationThread(
                        threadId = c.getLong(idIdx),
                        address = c.getString(addrIdx) ?: "",
                        contactName = c.getString(nameIdx),
                        snippet = c.getString(snipIdx) ?: "",
                        timestamp = c.getLong(timeIdx),
                        messageCount = c.getInt(countIdx),
                        hasAttachment = c.getInt(attachIdx) == 1
                    )
                )
            }
        }
        list
    }

    suspend fun saveThreads(threads: List<ConversationThread>) = withContext(Dispatchers.IO) {
        if (threads.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO threads (thread_id, address, contact_name, snippet, timestamp, message_count, has_attachment)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            for (t in threads) {
                stmt.clearBindings()
                stmt.bindLong(1, t.threadId)
                stmt.bindString(2, t.address)
                if (t.contactName != null) stmt.bindString(3, t.contactName) else stmt.bindNull(3)
                stmt.bindString(4, t.snippet)
                stmt.bindLong(5, t.timestamp)
                stmt.bindLong(6, t.messageCount.toLong())
                stmt.bindLong(7, if (t.hasAttachment) 1L else 0L)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /* ------------------------------------------------------------------------
       Message Operations
       ------------------------------------------------------------------------ */

    suspend fun getCachedMessagesForThread(
        threadId: Long,
        limit: Int? = null,
        beforeTimestamp: Long? = null
    ): List<MessageItem> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<MessageItem>()
        val db = readableDatabase

        val query: String
        val args: Array<String>
        if (beforeTimestamp != null) {
            query = if (limit != null) {
                "SELECT message_id, thread_id, type, direction, sender, recipient, body, timestamp FROM messages WHERE thread_id = ? AND timestamp < ? ORDER BY timestamp DESC LIMIT ?"
            } else {
                "SELECT message_id, thread_id, type, direction, sender, recipient, body, timestamp FROM messages WHERE thread_id = ? AND timestamp < ? ORDER BY timestamp ASC"
            }
            args = if (limit != null) {
                arrayOf(threadId.toString(), beforeTimestamp.toString(), limit.toString())
            } else {
                arrayOf(threadId.toString(), beforeTimestamp.toString())
            }
        } else {
            query = if (limit != null) {
                "SELECT message_id, thread_id, type, direction, sender, recipient, body, timestamp FROM messages WHERE thread_id = ? ORDER BY timestamp DESC LIMIT ?"
            } else {
                "SELECT message_id, thread_id, type, direction, sender, recipient, body, timestamp FROM messages WHERE thread_id = ? ORDER BY timestamp ASC"
            }
            args = if (limit != null) {
                arrayOf(threadId.toString(), limit.toString())
            } else {
                arrayOf(threadId.toString())
            }
        }

        val messageIds = mutableListOf<Long>()
        db.rawQuery(query, args)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("message_id")
            val tidIdx = c.getColumnIndexOrThrow("thread_id")
            val typeIdx = c.getColumnIndexOrThrow("type")
            val dirIdx = c.getColumnIndexOrThrow("direction")
            val senderIdx = c.getColumnIndexOrThrow("sender")
            val recIdx = c.getColumnIndexOrThrow("recipient")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val timeIdx = c.getColumnIndexOrThrow("timestamp")

            while (c.moveToNext()) {
                val msgId = c.getLong(idIdx)
                messageIds.add(msgId)
                val typeStr = c.getString(typeIdx)
                val dirStr = c.getString(dirIdx)
                messages.add(
                    MessageItem(
                        messageId = msgId,
                        threadId = c.getLong(tidIdx),
                        type = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.SMS },
                        direction = try { MessageDirection.valueOf(dirStr) } catch (_: Exception) { MessageDirection.INCOMING },
                        sender = c.getString(senderIdx) ?: "",
                        recipient = c.getString(recIdx) ?: "",
                        body = c.getString(bodyIdx) ?: "",
                        timestamp = c.getLong(timeIdx),
                        attachments = emptyList()
                    )
                )
            }
        }

        // Attach cached media attachments if any
        if (messageIds.isNotEmpty()) {
            val inClause = messageIds.joinToString(",")
            val attachmentMap = mutableMapOf<Long, MutableList<MediaAttachment>>()
            db.rawQuery("SELECT part_id, message_id, content_type, uri_string, file_name FROM attachments WHERE message_id IN ($inClause)", null)?.use { ac ->
                val pIdIdx = ac.getColumnIndexOrThrow("part_id")
                val mIdIdx = ac.getColumnIndexOrThrow("message_id")
                val ctIdx = ac.getColumnIndexOrThrow("content_type")
                val uriIdx = ac.getColumnIndexOrThrow("uri_string")
                val fnIdx = ac.getColumnIndexOrThrow("file_name")

                while (ac.moveToNext()) {
                    val mId = ac.getLong(mIdIdx)
                    val list = attachmentMap.getOrPut(mId) { mutableListOf() }
                    list.add(
                        MediaAttachment(
                            partId = ac.getLong(pIdIdx),
                            contentType = ac.getString(ctIdx) ?: "",
                            uriString = ac.getString(uriIdx) ?: "",
                            fileName = ac.getString(fnIdx) ?: ""
                        )
                    )
                }
            }

            for (i in messages.indices) {
                val atts = attachmentMap[messages[i].messageId]
                if (!atts.isNullOrEmpty()) {
                    messages[i] = messages[i].copy(attachments = atts)
                }
            }
        }

        messages.sortBy { it.timestamp }
        messages
    }

    /**
     * Quickly returns the highest message timestamp and total cached message count for a thread,
     * allowing instant zero-lag verification of whether new messages have arrived on the device.
     */
    suspend fun getThreadCachedStats(threadId: Long): Pair<Long, Int> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        var maxTime = 0L
        var count = 0
        db.rawQuery("SELECT MAX(timestamp), COUNT(message_id) FROM messages WHERE thread_id = ?", arrayOf(threadId.toString()))?.use { c ->
            if (c.moveToFirst()) {
                maxTime = c.getLong(0)
                count = c.getInt(1)
            }
        }
        Pair(maxTime, count)
    }

    suspend fun saveMessages(messages: List<MessageItem>) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            val msgStmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO messages (message_id, thread_id, type, direction, sender, recipient, body, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            val attachStmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO attachments (part_id, message_id, content_type, uri_string, file_name)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            )

            for (m in messages) {
                msgStmt.clearBindings()
                msgStmt.bindLong(1, m.messageId)
                msgStmt.bindLong(2, m.threadId)
                msgStmt.bindString(3, m.type.name)
                msgStmt.bindString(4, m.direction.name)
                msgStmt.bindString(5, m.sender)
                msgStmt.bindString(6, m.recipient)
                msgStmt.bindString(7, m.body)
                msgStmt.bindLong(8, m.timestamp)
                msgStmt.executeInsert()

                for (att in m.attachments) {
                    attachStmt.clearBindings()
                    attachStmt.bindLong(1, att.partId)
                    attachStmt.bindLong(2, m.messageId)
                    attachStmt.bindString(3, att.contentType)
                    attachStmt.bindString(4, att.uriString)
                    attachStmt.bindString(5, att.fileName)
                    attachStmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /* ------------------------------------------------------------------------
       Media Vault Operations
       ------------------------------------------------------------------------ */

    suspend fun getCachedVaultItems(): List<VaultMediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VaultMediaItem>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT part_id, message_id, thread_id, timestamp, content_type, file_name, uri_string, contact_name, contact_address FROM vault_items ORDER BY timestamp DESC",
            null
        )?.use { c ->
            val pIdIdx = c.getColumnIndexOrThrow("part_id")
            val mIdIdx = c.getColumnIndexOrThrow("message_id")
            val tIdIdx = c.getColumnIndexOrThrow("thread_id")
            val timeIdx = c.getColumnIndexOrThrow("timestamp")
            val ctIdx = c.getColumnIndexOrThrow("content_type")
            val fnIdx = c.getColumnIndexOrThrow("file_name")
            val uriIdx = c.getColumnIndexOrThrow("uri_string")
            val cnIdx = c.getColumnIndexOrThrow("contact_name")
            val caIdx = c.getColumnIndexOrThrow("contact_address")

            while (c.moveToNext()) {
                list.add(
                    VaultMediaItem(
                        partId = c.getLong(pIdIdx),
                        messageId = c.getLong(mIdIdx),
                        threadId = c.getLong(tIdIdx),
                        timestamp = c.getLong(timeIdx),
                        contentType = c.getString(ctIdx) ?: "",
                        fileName = c.getString(fnIdx) ?: "",
                        uriString = c.getString(uriIdx) ?: "",
                        contactName = c.getString(cnIdx) ?: "",
                        contactAddress = c.getString(caIdx) ?: ""
                    )
                )
            }
        }
        list
    }

    suspend fun saveVaultItems(items: List<VaultMediaItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO vault_items (part_id, message_id, thread_id, timestamp, content_type, file_name, uri_string, contact_name, contact_address)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            for (item in items) {
                stmt.clearBindings()
                stmt.bindLong(1, item.partId)
                stmt.bindLong(2, item.messageId)
                stmt.bindLong(3, item.threadId)
                stmt.bindLong(4, item.timestamp)
                stmt.bindString(5, item.contentType)
                stmt.bindString(6, item.fileName)
                stmt.bindString(7, item.uriString)
                stmt.bindString(8, item.contactName)
                stmt.bindString(9, item.contactAddress)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /* ------------------------------------------------------------------------
       O.D.I.N. Persistent Conversation History Operations
       ------------------------------------------------------------------------ */

    suspend fun getOdinChatHistory(threadKey: String): List<com.messagebackup.app.domain.ai.OdinChatMessage> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.messagebackup.app.domain.ai.OdinChatMessage>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, sender, text, timestamp, is_error FROM odin_chat_history WHERE thread_key = ? ORDER BY timestamp ASC",
            arrayOf(threadKey)
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val senderIdx = c.getColumnIndexOrThrow("sender")
            val textIdx = c.getColumnIndexOrThrow("text")
            val tsIdx = c.getColumnIndexOrThrow("timestamp")
            val errIdx = c.getColumnIndexOrThrow("is_error")

            while (c.moveToNext()) {
                val senderStr = c.getString(senderIdx) ?: "ODIN"
                val senderEnum = runCatching {
                    com.messagebackup.app.domain.ai.OdinChatMessage.MessageSender.valueOf(senderStr)
                }.getOrDefault(com.messagebackup.app.domain.ai.OdinChatMessage.MessageSender.ODIN)

                list.add(
                    com.messagebackup.app.domain.ai.OdinChatMessage(
                        id = c.getString(idIdx),
                        sender = senderEnum,
                        text = c.getString(textIdx) ?: "",
                        timestamp = c.getLong(tsIdx),
                        isError = c.getInt(errIdx) == 1
                    )
                )
            }
        }
        list
    }

    suspend fun insertOdinChatMessage(threadKey: String, msg: com.messagebackup.app.domain.ai.OdinChatMessage) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", msg.id)
            put("thread_key", threadKey)
            put("sender", msg.sender.name)
            put("text", msg.text)
            put("timestamp", msg.timestamp)
            put("is_error", if (msg.isError) 1 else 0)
        }
        db.insertWithOnConflict("odin_chat_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun clearOdinChatHistoryForThread(threadKey: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("odin_chat_history", "thread_key = ?", arrayOf(threadKey))
    }

    suspend fun clearAllOdinChatHistory() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.execSQL("DELETE FROM odin_chat_history")
    }

    /* ------------------------------------------------------------------------
       Starred / Pinned Vault Memories Operations
       ------------------------------------------------------------------------ */

    suspend fun getStarredMemories(): List<StarredMemoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StarredMemoryItem>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, message_id, thread_id, contact_name, sender, body, timestamp, source_type FROM starred_memories ORDER BY timestamp DESC",
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val msgIdIdx = c.getColumnIndexOrThrow("message_id")
            val tIdIdx = c.getColumnIndexOrThrow("thread_id")
            val cnIdx = c.getColumnIndexOrThrow("contact_name")
            val sIdx = c.getColumnIndexOrThrow("sender")
            val bIdx = c.getColumnIndexOrThrow("body")
            val tsIdx = c.getColumnIndexOrThrow("timestamp")
            val srcIdx = c.getColumnIndexOrThrow("source_type")

            while (c.moveToNext()) {
                list.add(
                    StarredMemoryItem(
                        id = c.getLong(idIdx),
                        messageId = c.getLong(msgIdIdx),
                        threadId = c.getLong(tIdIdx),
                        contactName = c.getString(cnIdx) ?: "",
                        sender = c.getString(sIdx) ?: "",
                        body = c.getString(bIdx) ?: "",
                        timestamp = c.getLong(tsIdx),
                        sourceType = c.getString(srcIdx) ?: "SMS"
                    )
                )
            }
        }
        list
    }

    suspend fun addStarredMemory(
        messageId: Long,
        threadId: Long,
        contactName: String,
        sender: String,
        body: String,
        timestamp: Long,
        sourceType: String = "SMS"
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        // Avoid duplicate starring of the same message
        db.delete("starred_memories", "message_id = ? AND body = ?", arrayOf(messageId.toString(), body))
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("thread_id", threadId)
            put("contact_name", contactName)
            put("sender", sender)
            put("body", body)
            put("timestamp", timestamp)
            put("source_type", sourceType)
        }
        db.insert("starred_memories", null, values)
    }

    suspend fun removeStarredMemory(id: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("starred_memories", "id = ?", arrayOf(id.toString()))
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.execSQL("DELETE FROM threads")
        db.execSQL("DELETE FROM messages")
        db.execSQL("DELETE FROM attachments")
        db.execSQL("DELETE FROM vault_items")
    }

    companion object {
        private const val DATABASE_NAME = "message_backup_cache.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var instance: LocalMessageDatabase? = null

        fun getInstance(context: Context): LocalMessageDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocalMessageDatabase(context).also { instance = it }
            }
        }
    }
}

data class StarredMemoryItem(
    val id: Long,
    val messageId: Long,
    val threadId: Long,
    val contactName: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val sourceType: String = "SMS"
)
