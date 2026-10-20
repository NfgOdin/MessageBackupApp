package com.messagebackup.app.model

import java.util.UUID

/**
 * Classification category for extracted commitments and agreements.
 */
enum class CommitmentCategory(val label: String, val emoji: String) {
    TASK("Action Item", "📌"),
    MEETING("Meetup / Appointment", "📅"),
    FINANCIAL("Payment / Money", "💸"),
    PROMISE("Promise / Delivery", "🤝"),
    OTHER("Other Agreement", "💬")
}

/**
 * Status of an identified commitment.
 */
enum class CommitmentStatus {
    PENDING,
    COMPLETED,
    DISMISSED
}

/**
 * Represents a commitment, promise, task, or agreed deadline extracted from messages.
 *
 * @property id Unique UUID string
 * @property messageId Source message ID
 * @property threadId Associated conversation thread ID
 * @property contactName Person with whom the agreement was made
 * @property summary Brief summary of the commitment or promise
 * @property isMadeByMe True if the user promised/committed; false if the contact promised
 * @property rawSnippet Exact message quote containing the commitment
 * @property timestamp Message timestamp in milliseconds
 * @property dueDate Optional detected due date or time description (e.g. "Tomorrow 6 PM", "Friday")
 * @property category Categorization of the commitment
 * @property status Workflow status (PENDING, COMPLETED, DISMISSED)
 */
data class DetectedCommitment(
    val id: String = UUID.randomUUID().toString(),
    val messageId: Long = 0L,
    val threadId: Long = 0L,
    val contactName: String = "",
    val summary: String = "",
    val isMadeByMe: Boolean = true,
    val rawSnippet: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val dueDate: String? = null,
    val category: CommitmentCategory = CommitmentCategory.TASK,
    val status: CommitmentStatus = CommitmentStatus.PENDING
)
