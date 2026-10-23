package com.messagebackup.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagebackup.app.model.MediaAttachment
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val messageTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    .withZone(ZoneId.systemDefault())

/**
 * Chat bubble component displaying a single SMS or MMS message within a conversation view,
 * with selective message exclusion checkbox for custom exports, rich clickable attachment previews,
 * and support for photos, APKs, ZIP archives, and documents.
 */
@Composable
fun MessageBubble(
    message: MessageItem,
    isExcluded: Boolean = false,
    showExclusionToggle: Boolean = false,
    onToggleExclude: () -> Unit = {},
    onAttachmentClick: ((MediaAttachment) -> Unit)? = null,
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
                modifier = Modifier.widthIn(max = 290.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Message text body
                    if (message.body.isNotBlank()) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }

                    // Attachments list (Photos, APKs, ZIP archives, Video, Audio, Docs)
                    if (message.attachments.isNotEmpty()) {
                        for (att in message.attachments) {
                            if (message.body.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            AttachmentPreviewCard(
                                attachment = att,
                                textColor = textColor,
                                onClick = { onAttachmentClick?.invoke(att) }
                            )
                        }
                    } else if (message.body.isBlank() && message.type == MessageType.MMS) {
                        // Fallback indicator so empty MMS bodies never produce blank message bubbles
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Attachment,
                                contentDescription = "MMS Attachment",
                                tint = textColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📎 Multimedia Attachment",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.85f)
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

/**
 * Clickable attachment preview component supporting rich asynchronous photo previews
 * and distinct cards for APKs, ZIP archives, video, audio, and documents.
 */
@Composable
private fun AttachmentPreviewCard(
    attachment: MediaAttachment,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val ctLower = attachment.contentType.lowercase()
    val nameLower = attachment.fileName.lowercase()

    val isImage = ctLower.startsWith("image/") || nameLower.let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".gif")
    }
    val isApk = ctLower.contains("android.package-archive") || nameLower.endsWith(".apk")
    val isZip = ctLower.contains("zip") || nameLower.let { it.endsWith(".zip") || it.endsWith(".rar") || it.endsWith(".7z") }
    val isAudio = ctLower.startsWith("audio/") || nameLower.let { it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".wav") || it.endsWith(".aac") }
    val isVideo = ctLower.startsWith("video/") || nameLower.let { it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".3gp") }

    if (isImage) {
        val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = attachment.uriString) {
            value = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(attachment.uriString)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        val bitmap = bitmapState.value
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClick() }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 190.dp)
                )
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "🔍 Zoom",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            // Loading / fallback image card
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.fileName.ifBlank { "Photo Attachment" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Tap to view photo", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
                    }
                }
            }
        }
    } else {
        // Non-image file card (APK, ZIP, Video, Audio, Document)
        val icon = when {
            isApk -> Icons.Default.Android
            isZip -> Icons.Default.FolderZip
            isAudio -> Icons.Default.VolumeUp
            isVideo -> Icons.Default.Videocam
            else -> Icons.Default.Description
        }

        val typeBadge = when {
            isApk -> "APK Package"
            isZip -> "ZIP Archive"
            isAudio -> "Audio Note"
            isVideo -> "Video Clip"
            else -> "Document / File"
        }

        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = typeBadge,
                    tint = textColor,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.fileName.ifBlank { typeBadge },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$typeBadge • Tap to open",
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
