package com.messagebackup.app.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.messagebackup.app.data.local.RavenChatMessage
import com.messagebackup.app.data.local.RavenDatabaseHelper
import com.messagebackup.app.domain.mesh.RavenPeer
import com.messagebackup.app.domain.mesh.RavenSendResult
import com.messagebackup.app.domain.mesh.RavenSocketTransport
import com.messagebackup.app.domain.mesh.RavenVoicePttEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated End-to-End Encrypted Raven Chat Thread Screen for a connected peer.
 * Displays persistent message bubbles (sent and received), timestamps, E2EE badges,
 * and allows real-time instant whisper texting and PTT voice bursts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RavenChatDialog(
    peer: RavenPeer,
    socketTransport: RavenSocketTransport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbHelper = remember { RavenDatabaseHelper(context) }
    val pttEngine = remember { RavenVoicePttEngine(context) }

    var messagesList by remember { mutableStateOf<List<RavenChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRecordingPtt by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var playingMessageId by remember { mutableStateOf<Long?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    fun loadMessages() {
        scope.launch(Dispatchers.IO) {
            val msgs = dbHelper.getMessagesForPeer(peer.peerId)
            withContext(Dispatchers.Main) {
                messagesList = msgs
            }
        }
    }

    LaunchedEffect(isRecordingPtt) {
        if (isRecordingPtt) {
            val startTs = System.currentTimeMillis()
            while (isRecordingPtt) {
                recordingElapsedMs = System.currentTimeMillis() - startTs
                delay(100)
            }
        } else {
            recordingElapsedMs = 0L
        }
    }

    fun startVoiceRecording() {
        if (isRecordingPtt) return
        val started = pttEngine.startRecording()
        if (started) {
            isRecordingPtt = true
        } else {
            Toast.makeText(context, "Microphone unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoiceRecording() {
        if (!isRecordingPtt) return
        isRecordingPtt = false
        scope.launch(Dispatchers.IO) {
            val snippet = pttEngine.stopRecording()
            if (snippet != null) {
                val res = socketTransport.sendVoiceSnippetToPeer(peer, snippet)
                withContext(Dispatchers.Main) {
                    if (res is RavenSendResult.Success) {
                        loadMessages()
                        Toast.makeText(context, "🎙️ Voice note delivered!", Toast.LENGTH_SHORT).show()
                    } else if (res is RavenSendResult.Error) {
                        Toast.makeText(context, "⚠️ " + res.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Voice note too short (hold or speak longer)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        if (!isRecordingPtt) return
        isRecordingPtt = false
        pttEngine.cancelRecording()
        Toast.makeText(context, "Recording cancelled", Toast.LENGTH_SHORT).show()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone enabled for PTT", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission required for PTT", Toast.LENGTH_LONG).show()
        }
    }

    // Load initial messages and refresh when socket emits new events
    LaunchedEffect(peer.peerId) {
        loadMessages()
    }

    LaunchedEffect(Unit) {
        socketTransport.incomingMessages.collect { incoming ->
            val cleanIncoming = incoming.senderCallsign.removePrefix("raven://").trim()
            val cleanPeer = peer.peerId.removePrefix("raven://").trim()
            if (cleanIncoming.equals(cleanPeer, ignoreCase = true)) {
                loadMessages()
            }
        }
    }

    LaunchedEffect(Unit) {
        socketTransport.incomingVoiceStreams.collect { incoming ->
            val cleanIncoming = incoming.senderCallsign.removePrefix("raven://").trim()
            val cleanPeer = peer.peerId.removePrefix("raven://").trim()
            if (cleanIncoming.equals(cleanPeer, ignoreCase = true)) {
                loadMessages()
            }
        }
    }

    // Scroll to bottom whenever messagesList changes
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Peer Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peer.displayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = peer.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "E2EE AES-256 • ${peer.peerId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { showClearHistoryDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Messages Thread
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    if (messagesList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "End-to-End Encrypted Channel",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Messages and walkie-talkie audio are encrypted directly on-device using hardware EC P-256 keys. No server can read your conversations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Say hello to ${peer.displayName} below!",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(messagesList, key = { it.id }) { msg ->
                                RavenMessageBubble(
                                    msg = msg,
                                    isPlaying = playingMessageId == msg.id,
                                    onPlayAudio = { audioB64 ->
                                        if (playingMessageId == msg.id) return@RavenMessageBubble
                                        playingMessageId = msg.id
                                        scope.launch(Dispatchers.IO) {
                                            pttEngine.playInboundVoice(audioB64)
                                            withContext(Dispatchers.Main) {
                                                playingMessageId = null
                                            }
                                        }
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Recording Status Bar with Live Timer & Stop/Send + Cancel Controls
                AnimatedVisibility(visible = isRecordingPtt) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val sec = String.format(Locale.US, "%.1fs", recordingElapsedMs / 1000f)
                                Text(
                                    text = "Recording $sec • Release or tap Send",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { cancelVoiceRecording() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Cancel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                }

                                Button(
                                    onClick = { stopAndSendVoiceRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "Stop and Send",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Send", fontSize = 12.sp, color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bottom Composer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // PTT Mic Button (Supports both Hold-to-Talk AND Tap-to-Toggle!)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecordingPtt) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                return@detectTapGestures
                                            }
                                            if (isRecordingPtt) {
                                                // Already recording in toggle mode -> tap stops & sends
                                                stopAndSendVoiceRecording()
                                            } else {
                                                val pressStart = System.currentTimeMillis()
                                                startVoiceRecording()
                                                val released = tryAwaitRelease()
                                                val heldDuration = System.currentTimeMillis() - pressStart
                                                // If user held the button for > 400ms, treat as press-and-hold and send on release!
                                                // If it was a quick tap (< 400ms), stay recording in hands-free toggle mode until they tap again or hit Send.
                                                if (released && heldDuration >= 400L && isRecordingPtt) {
                                                    stopAndSendVoiceRecording()
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = if (isRecordingPtt) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Hold or Tap PTT",
                                tint = if (isRecordingPtt) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Type encrypted whisper...", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button
                        IconButton(
                            onClick = {
                                val textToSend = inputText.trim()
                                if (textToSend.isBlank() || isSending) return@IconButton
                                isSending = true
                                scope.launch(Dispatchers.IO) {
                                    val res = socketTransport.sendMessageToPeer(peer, textToSend)
                                    withContext(Dispatchers.Main) {
                                        isSending = false
                                        when (res) {
                                            is RavenSendResult.Success -> {
                                                inputText = ""
                                                loadMessages()
                                            }
                                            is RavenSendResult.Error -> {
                                                Toast.makeText(context, "⚠️ " + res.errorMessage, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !isSending,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This will delete all saved messages with ${peer.displayName} from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        dbHelper.deleteMessagesForPeer(peer.peerId)
                        loadMessages()
                        withContext(Dispatchers.Main) {
                            showClearHistoryDialog = false
                            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RavenMessageBubble(
    msg: RavenChatMessage,
    isPlaying: Boolean = false,
    onPlayAudio: (String) -> Unit = {}
) {
    val isOutbound = msg.isOutbound
    val alignment = if (isOutbound) Alignment.End else Alignment.Start
    val bubbleColor = if (isOutbound) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOutbound) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = if (isOutbound) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val timeString = remember(msg.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(min = 60.dp, max = 290.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isOutbound) {
                    Text(
                        text = msg.senderDisplayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (msg.messageType == "VOICE") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !msg.audioBase64.isNullOrBlank()) {
                                msg.audioBase64?.let { onPlayAudio(it) }
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isPlaying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Voice Note",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = msg.messageText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Text(
                                text = if (isPlaying) "🔊 Playing audio..."
                                else if (!msg.audioBase64.isNullOrBlank()) "Tap to play voice message"
                                else "Voice transmission",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = msg.messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.7f)
                    )
                    if (isOutbound) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "✓ E2EE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
