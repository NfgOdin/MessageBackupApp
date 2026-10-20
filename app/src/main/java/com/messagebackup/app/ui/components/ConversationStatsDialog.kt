package com.messagebackup.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.messagebackup.app.model.ConversationStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Modern Material 3 Conversation Analytics & Insights Dialog.
 */
@Composable
fun ConversationStatsDialog(
    stats: ConversationStats?,
    isLoading: Boolean,
    progressPct: Int = 0,
    progressMessage: String = "",
    onRecalculate: () -> Unit,
    onExportReport: () -> Unit,
    onSearchPhrase: ((String, (com.messagebackup.app.model.PhraseFrequencyResult) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmCloseDialog by remember { mutableStateOf(false) }
    var showWrappedStories by remember { mutableStateOf(false) }

    var phraseQuery by remember { mutableStateOf("") }
    var phraseResult by remember { mutableStateOf<com.messagebackup.app.model.PhraseFrequencyResult?>(null) }
    var isSearchingPhrase by remember { mutableStateOf(false) }

    fun handleRequestClose() {
        showConfirmCloseDialog = true
    }

    // Main Dialog — Intercepts both outside taps and system back press with a confirmation dialog
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { handleRequestClose() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BackHandler(enabled = true) {
            handleRequestClose()
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = "Analytics",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "Conversation Insights",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (stats != null && !isLoading) {
                                Text(
                                    text = "Cached stats • Tap 🔄 to recalculate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRecalculate,
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp).width(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Recalculate Stats",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { handleRequestClose() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading || stats == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { progressPct.toFloat() / 100f },
                                    modifier = Modifier.height(72.dp).width(72.dp),
                                    strokeWidth = 6.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "$progressPct%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (progressMessage.isNotBlank()) progressMessage else "Crunching conversation stats...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressPct.toFloat() / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please keep this window open while processing.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                        .withZone(ZoneId.systemDefault())

                    val oldestStr = stats.oldestTimestamp?.let { dateFormatter.format(Instant.ofEpochMilli(it)) } ?: "N/A"
                    val newestStr = stats.newestTimestamp?.let { dateFormatter.format(Instant.ofEpochMilli(it)) } ?: "N/A"

                    // Key Metric Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Total Messages",
                            value = "%,d".format(stats.totalMessages),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Conversations",
                            value = "%,d".format(stats.totalThreads),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "SMS / MMS",
                            value = "${stats.totalSms} / ${stats.totalMms}",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Media Files",
                            value = "%,d".format(stats.totalMediaAttachments),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sent vs Received Ratio
                    Text(
                        text = "Sent vs. Received Ratio",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val totalSentRecv = stats.totalSent + stats.totalReceived
                    val sentPct = if (totalSentRecv > 0) stats.totalSent.toFloat() / totalSentRecv.toFloat() else 0.5f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sent: ${stats.totalSent} (${(sentPct * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Received: ${stats.totalReceived} (${((1f - sentPct) * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { sentPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // Activity Insights
                    Text(
                        text = "Activity Patterns",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    stats.busiestHour?.let { hour ->
                        val amPm = if (hour >= 12) "PM" else "AM"
                        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                        PatternRow(label = "Peak Chat Time", value = "$displayHour:00 $amPm - $displayHour:59 $amPm")
                    }

                    stats.busiestDayOfWeek?.let { day ->
                        PatternRow(label = "Most Active Day", value = day)
                    }

                    PatternRow(label = "Archive Span", value = "$oldestStr — $newestStr")

                    // Dynamics & Response Speed
                    if (stats.avgSentResponseMins != null || stats.avgReceivedResponseMins != null) {
                        val sentSpeed = stats.avgSentResponseMins?.let { "$it mins" } ?: "< 1 min"
                        val recSpeed = stats.avgReceivedResponseMins?.let { "$it mins" } ?: "< 1 min"
                        PatternRow(label = "Your Avg Reply Speed", value = sentSpeed)
                        PatternRow(label = "Their Avg Reply Speed", value = recSpeed)
                    }

                    val initPct = (stats.initiationRatio * 100).toInt()
                    PatternRow(label = "Conversation Starter", value = "$initPct% You • ${100 - initPct}% Them")

                    // Streaks & Rhythm
                    if (stats.longestStreakDays > 0 || stats.longestGapDays > 0) {
                        val currentStreakStr = if (stats.currentStreakDays > 0) "${stats.currentStreakDays} days (Active 🔥)" else "None"
                        PatternRow(label = "Longest Messaging Streak", value = "${stats.longestStreakDays} days")
                        PatternRow(label = "Current Streak", value = currentStreakStr)
                        if (stats.longestGapDays > 0) {
                            PatternRow(label = "Longest Silence", value = "${stats.longestGapDays} days")
                        }
                    }

                    // Night Owl vs Early Bird Day/Night Distribution
                    val totalTimeMsgs = stats.morningCount + stats.afternoonCount + stats.eveningCount + stats.nightCount
                    if (totalTimeMsgs > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Daily Rhythm (When you talk)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TimeHabitCard(
                                label = "Morning",
                                sub = "6am-12pm",
                                count = stats.morningCount,
                                total = totalTimeMsgs,
                                modifier = Modifier.weight(1f)
                            )
                            TimeHabitCard(
                                label = "Afternoon",
                                sub = "12pm-6pm",
                                count = stats.afternoonCount,
                                total = totalTimeMsgs,
                                modifier = Modifier.weight(1f)
                            )
                            TimeHabitCard(
                                label = "Evening",
                                sub = "6pm-12am",
                                count = stats.eveningCount,
                                total = totalTimeMsgs,
                                modifier = Modifier.weight(1f)
                            )
                            TimeHabitCard(
                                label = "Night",
                                sub = "12am-6am",
                                count = stats.nightCount,
                                total = totalTimeMsgs,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Most Used Emojis (Organized into 2 rows for spacious padding)
                    if (stats.topEmojis.isNotEmpty()) {
                        Text(
                            text = "Top Emojis",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val chunkedEmojis = stats.topEmojis.chunked(3)
                        chunkedEmojis.forEach { rowEmojis ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowEmojis.forEach { (emoji, count) ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = emoji, style = MaterialTheme.typography.titleSmall)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                // Fill remaining slots if row has fewer than 3 items
                                for (i in rowEmojis.size until 3) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                    }

                    // Top Words Breakdown with Underlined Section Headers
                    if (stats.topSentWords.isNotEmpty() || stats.topReceivedWords.isNotEmpty()) {
                        Text(
                            text = "Most Frequent Words",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // You sent
                            Column(modifier = Modifier.weight(1f)) {
                                Column {
                                    Text(
                                        text = "You Said Most",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.primary,
                                        thickness = 1.5.dp,
                                        modifier = Modifier
                                            .width(90.dp)
                                            .padding(top = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                stats.topSentWords.forEach { (word, count) ->
                                    Text(
                                        text = "• \"$word\" ($count)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            // They sent
                            Column(modifier = Modifier.weight(1f)) {
                                Column {
                                    Text(
                                        text = "They Said Most",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.secondary,
                                        thickness = 1.5.dp,
                                        modifier = Modifier
                                            .width(95.dp)
                                            .padding(top = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                stats.topReceivedWords.forEach { (word, count) ->
                                    Text(
                                        text = "• \"$word\" ($count)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                    }

                    // Interactive Phrase Frequency Tracker
                    if (onSearchPhrase != null) {
                        Text(
                            text = "Phrase & Word Tracker",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search how many times a word or phrase was spoken and who said it more.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = phraseQuery,
                                onValueChange = { phraseQuery = it },
                                placeholder = { Text("e.g. love you, dinner, miss you", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (phraseQuery.isNotBlank()) {
                                        isSearchingPhrase = true
                                        onSearchPhrase(phraseQuery) { res ->
                                            phraseResult = res
                                            isSearchingPhrase = false
                                        }
                                    }
                                },
                                enabled = phraseQuery.isNotBlank() && !isSearchingPhrase,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSearchingPhrase) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search phrase")
                                }
                            }
                        }

                        // Display Phrase Results Card
                        phraseResult?.let { res ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "\"${res.query}\"",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${res.totalCount} times",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (res.totalCount > 0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val pSentPct = res.sentCount.toFloat() / res.totalCount.toFloat()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "You: ${res.sentCount} (${(pSentPct * 100).toInt()}%)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Them: ${res.receivedCount} (${((1f - pSentPct) * 100).toInt()}%)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { pSentPct },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.secondaryContainer
                                        )

                                        if (res.firstTimestamp != null && res.lastTimestamp != null) {
                                            val firstDate = Instant.ofEpochMilli(res.firstTimestamp)
                                                .atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                            val lastDate = Instant.ofEpochMilli(res.lastTimestamp)
                                                .atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "First used $firstDate • Last used $lastDate",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Never spoken in this conversation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                    }

                    // Top Contacts
                    if (stats.topContacts.isNotEmpty()) {
                        Text(
                            text = "Top Active Contacts",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        stats.topContacts.forEachIndexed { index, contact ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${index + 1}. ${contact.displayName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${contact.count} msgs (${contact.percentage.toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                LinearProgressIndicator(
                                    progress = { (contact.percentage / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Relationship "Wrapped" Story Button
                    Button(
                        onClick = { showWrappedStories = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("✨ View Your Message Wrapped Stories")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onExportReport,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Export Insights Report (.html)")
                    }
                }
            }

            // Relationship "Wrapped" Fullscreen Stories Modal
            if (showWrappedStories && stats != null) {
                com.messagebackup.app.ui.dialogs.RelationshipWrappedDialog(
                    stats = stats,
                    onDismiss = { showWrappedStories = false }
                )
            }

            // In-dialog confirmation overlay
            if (showConfirmCloseDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmCloseDialog = false },
                    title = {
                        Text(if (isLoading) "Cancel Calculation?" else "Close Conversation Insights?")
                    },
                    text = {
                        Text(
                            if (isLoading)
                                "Stats are still being calculated ($progressPct% complete). If you close now, the calculation will stop."
                            else
                                "Are you sure you want to exit Conversation Insights?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmCloseDialog = false
                                onDismiss()
                            }
                        ) {
                            Text(if (isLoading) "Yes, Cancel & Close" else "Close Insights")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showConfirmCloseDialog = false }) {
                            Text(if (isLoading) "Keep Waiting" else "Stay Here")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun PatternRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TimeHabitCard(
    label: String,
    sub: String,
    count: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val pct = if (total > 0) (count.toFloat() / total.toFloat() * 100f).toInt() else 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$count msgs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
