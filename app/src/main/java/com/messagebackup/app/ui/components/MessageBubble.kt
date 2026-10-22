package com.messagebackup.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.remember

private val messageTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    .withZone(ZoneId.systemDefault())

/**
 * Chat bubble component displaying a single SMS or MMS message within a conversation view,
 * with selective message exclusion checkbox for custom exports.
 */
@Composable
fun MessageBubble(
    message: MessageItem,
    isExcluded: Boolean = false,
    showExclusionToggle: Boolean = false,
    onToggleExclude: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOutgoing = (message.direction == MessageDirection.OUTGOING)
    val timeStr = remember(message.timestamp) {
        messageTimeFormatter.format(Instant.ofEpochMilli(message.timestamp))
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    val bubbleColor = if (isExcluded) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else if (isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isExcluded) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showExclusionToggle && !isOutgoing) {
            Checkbox(
                checked = !isExcluded,
                onCheckedChange = { onToggleExclude() },
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
        ) {
            if (!isOutgoing) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.body.isNotBlank()) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }

                    for (att in message.attachments) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.3f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "📷 [Attachment: ${att.fileName}]",
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        if (showExclusionToggle && isOutgoing) {
            Checkbox(
                checked = !isExcluded,
                onCheckedChange = { onToggleExclude() },
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
