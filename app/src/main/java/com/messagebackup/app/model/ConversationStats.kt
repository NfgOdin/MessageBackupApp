package com.messagebackup.app.model

/**
 * Aggregated analytics and statistics computed from conversation threads and messages.
 */
data class ConversationStats(
    val totalThreads: Int = 0,
    val totalMessages: Long = 0,
    val totalSms: Long = 0,
    val totalMms: Long = 0,
    val totalSent: Long = 0,
    val totalReceived: Long = 0,
    val totalMediaAttachments: Long = 0,
    val oldestTimestamp: Long? = null,
    val newestTimestamp: Long? = null,
    val topContacts: List<ContactMessageCount> = emptyList(),
    val busiestHour: Int? = null, // 0-23
    val busiestDayOfWeek: String? = null,
    val topSentWords: List<Pair<String, Int>> = emptyList(),
    val topReceivedWords: List<Pair<String, Int>> = emptyList(),
    val topEmojis: List<Pair<String, Int>> = emptyList(),
    val avgSentResponseMins: Long? = null,
    val avgReceivedResponseMins: Long? = null,
    val initiationRatio: Float = 0.5f // 0.0 (all contact) to 1.0 (all you)
)

data class ContactMessageCount(
    val displayName: String,
    val address: String,
    val count: Int,
    val percentage: Float
)
