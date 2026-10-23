package com.messagebackup.app.ui.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.data.local.RavenChatMessage
import com.messagebackup.app.data.local.RavenDatabaseHelper
import com.messagebackup.app.domain.mesh.RavenPeer
import com.messagebackup.app.domain.mesh.RavenSendResult
import com.messagebackup.app.domain.mesh.RavenSocketTransport
import com.messagebackup.app.domain.mesh.RavenVoicePttEngine
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen encrypted chat & walkie-talkie window for a specific Raven Mesh peer.
 * Supports:
 * - Instant E2EE text whispers
 * - Live Walkie-Talkie PTT streaming (Hold-to-Talk & Tap-to-Toggle) + On-device speech transcription
 * - Loudspeaker vs. Private Earpiece 1-tap toggle & Walkie-Talkie Auto-Play vs. Silent Voice Note toggle
 * - Raven Parcels (Encrypted Camera Photos, Gallery Images, and File attachments)
 * - Loki's Mist (Disappearing / Burn-After-Reading timers)
 * - Dead Reckoning (1-tap Encrypted GPS Coordinates & Battery Pin Drop)
 */
@Composable
fun RavenChatDialog(
    peer: RavenPeer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { MessageBackupApp.instance.appPreferences }

    val dbHelper = remember { RavenDatabaseHelper(context) }
    val socketTransport = remember { RavenSocketTransport.getInstance(context, scope) }
    val pttEngine = remember { RavenVoicePttEngine.getInstance(context) }

    val useLoudspeaker by prefs.ravenAudioLoudspeaker.collectAsState()
    val pttAutoPlay by prefs.ravenPttAutoPlay.collectAsState()

    var messagesList by remember { mutableStateOf<List<RavenChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRecordingPtt by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var activeStreamSessionId by remember { mutableLongStateOf(0L) }
    var liveTranscriptPreview by remember { mutableStateOf("") }
    var playingMessageId by remember { mutableStateOf<Long?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showLokisMistMenu by remember { mutableStateOf(false) }
    var ephemeralTtlSec by remember { mutableIntStateOf(dbHelper.getEphemeralTtlForPeer(peer.peerId)) }
    var fullScreenImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            pttEngine.cancelRecording()
            pttEngine.stopPlayback()
        }
    }

    fun loadMessages() {
        scope.launch(Dispatchers.IO) {
            dbHelper.clearUnreadCount(peer.peerId)
            val msgs = dbHelper.getMessagesForPeer(peer.peerId)
            withContext(Dispatchers.Main) {
                messagesList = msgs
            }
            val latestInbound = msgs.lastOrNull { !it.isOutbound }
            if (latestInbound != null) {
                socketTransport.sendReadReceipt(peer, latestInbound.timestamp)
            }
        }
    }

    // Periodically purge any Loki's Mist expired messages while chat is open
    LaunchedEffect(peer.peerId) {
        loadMessages()
        while (true) {
            delay(5_000)
            val purged = withContext(Dispatchers.IO) { dbHelper.purgeExpiredMessages() }
            if (purged > 0) {
                loadMessages()
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
        val sessionId = System.currentTimeMillis()
        activeStreamSessionId = sessionId
        liveTranscriptPreview = ""

        val started = pttEngine.startRecording(
            scope = scope,
            onLiveChunkReady = { chunkB64, seq ->
                socketTransport.sendLiveVoiceStreamChunk(
                    peer = peer,
                    chunkBase64 = chunkB64,
                    sessionId = sessionId,
                    seq = seq
                )
            }
        )
        if (started) {
            isRecordingPtt = true
            pttEngine.transcribeLiveSpeech { partial ->
                liveTranscriptPreview = partial
            }
        } else {
            Toast.makeText(context, "Microphone unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoiceRecording() {
        if (!isRecordingPtt) return
        isRecordingPtt = false
        val sessionId = activeStreamSessionId
        scope.launch(Dispatchers.IO) {
            val snippet = pttEngine.stopRecording()
            if (snippet != null) {
                val res = socketTransport.sendVoiceSnippetToPeer(
                    peer = peer,
                    voiceSnippet = snippet,
                    sessionId = sessionId
                )
                withContext(Dispatchers.Main) {
                    liveTranscriptPreview = ""
                    if (res is RavenSendResult.Success) {
                        loadMessages()
                        Toast.makeText(context, "🎙️ Voice transmission delivered!", Toast.LENGTH_SHORT).show()
                    } else if (res is RavenSendResult.Error) {
                        Toast.makeText(context, "⚠️ " + res.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    liveTranscriptPreview = ""
                    Toast.makeText(context, "Voice note too short (hold or speak longer)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        if (!isRecordingPtt) return
        isRecordingPtt = false
        liveTranscriptPreview = ""
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

    // Camera Snapshot Launcher (Take Photo Parcel)
    val cameraSnapshotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            isSending = true
            scope.launch(Dispatchers.IO) {
                try {
                    val scaled = scaleBitmapDown(bitmap, 1024)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 76, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    val fileName = "raven_photo_${System.currentTimeMillis()}.jpg"
                    val res = socketTransport.sendParcelToPeer(
                        peer = peer,
                        captionText = "📸 Encrypted Camera Photo",
                        attachmentBase64 = b64,
                        attachmentName = fileName,
                        attachmentMimeType = "image/jpeg",
                        isImage = true
                    )
                    withContext(Dispatchers.Main) {
                        isSending = false
                        if (res is RavenSendResult.Success) {
                            loadMessages()
                            Toast.makeText(context, "📸 Encrypted photo parcel sent!", Toast.LENGTH_SHORT).show()
                        } else if (res is RavenSendResult.Error) {
                            Toast.makeText(context, "⚠️ ${res.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isSending = false
                        Toast.makeText(context, "Failed to encode photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Gallery Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isSending = true
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (originalBitmap != null) {
                        val scaled = scaleBitmapDown(originalBitmap, 1080)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        val name = getFileNameFromUri(context, uri) ?: "raven_image.jpg"
                        val res = socketTransport.sendParcelToPeer(
                            peer = peer,
                            captionText = "🖼️ Encrypted Image ($name)",
                            attachmentBase64 = b64,
                            attachmentName = name,
                            attachmentMimeType = "image/jpeg",
                            isImage = true
                        )
                        withContext(Dispatchers.Main) {
                            isSending = false
                            if (res is RavenSendResult.Success) {
                                loadMessages()
                                Toast.makeText(context, "🖼️ Encrypted image parcel sent!", Toast.LENGTH_SHORT).show()
                            } else if (res is RavenSendResult.Error) {
                                Toast.makeText(context, "⚠️ ${res.errorMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isSending = false
                            Toast.makeText(context, "Could not read selected image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isSending = false
                        Toast.makeText(context, "Error sending image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // File Parcel Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isSending = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            isSending = false
                            Toast.makeText(context, "Selected file is empty", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    if (bytes.size > 25 * 1024 * 1024) {
                        withContext(Dispatchers.Main) {
                            isSending = false
                            Toast.makeText(context, "File exceeds 25 MB Raven Parcel limit", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val fileName = getFileNameFromUri(context, uri) ?: "parcel_${System.currentTimeMillis()}.bin"
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val sizeKb = String.format(Locale.US, "%.1f KB", bytes.size / 1024f)

                    val res = socketTransport.sendParcelToPeer(
                        peer = peer,
                        captionText = "📦 Raven Parcel: $fileName ($sizeKb)",
                        attachmentBase64 = b64,
                        attachmentName = fileName,
                        attachmentMimeType = mime,
                        isImage = mime.startsWith("image/")
                    )
                    withContext(Dispatchers.Main) {
                        isSending = false
                        if (res is RavenSendResult.Success) {
                            loadMessages()
                            Toast.makeText(context, "📦 Encrypted file parcel delivered!", Toast.LENGTH_SHORT).show()
                        } else if (res is RavenSendResult.Error) {
                            Toast.makeText(context, "⚠️ ${res.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isSending = false
                        Toast.makeText(context, "Error sending file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendDeadReckoningLocationPin() {
        isSending = true
        scope.launch(Dispatchers.IO) {
            try {
                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                var bestLoc: android.location.Location? = null
                for (p in providers) {
                    try {
                        val l = locManager.getLastKnownLocation(p)
                        if (l != null && (bestLoc == null || l.accuracy < bestLoc.accuracy)) {
                            bestLoc = l
                        }
                    } catch (_: Exception) {}
                }

                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val batTag = if (batteryPct in 0..100) " • 🔋 $batteryPct%" else ""

                val locationMsg = if (bestLoc != null) {
                    val lat = String.format(Locale.US, "%.5f", bestLoc.latitude)
                    val lon = String.format(Locale.US, "%.5f", bestLoc.longitude)
                    val alt = if (bestLoc.hasAltitude()) " • Alt: ${bestLoc.altitude.toInt()}m" else ""
                    "🧭 Dead Reckoning Coordinates:\n📍 Lat: $lat, Lon: $lon$alt$batTag\nhttps://maps.google.com/?q=$lat,$lon"
                } else {
                    "🧭 Dead Reckoning Signal Beacon$batTag\n(GPS lock unavailable indoors — enable GPS for exact coordinates)"
                }

                val res = socketTransport.sendMessageToPeer(
                    peer = peer,
                    text = locationMsg,
                    messageType = "LOCATION"
                )
                withContext(Dispatchers.Main) {
                    isSending = false
                    if (res is RavenSendResult.Success) {
                        loadMessages()
                        Toast.makeText(context, "🧭 Coordinates dispatched to ${peer.displayName}!", Toast.LENGTH_SHORT).show()
                    } else if (res is RavenSendResult.Error) {
                        Toast.makeText(context, "⚠️ ${res.errorMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSending = false
                    Toast.makeText(context, "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            sendDeadReckoningLocationPin()
        } else {
            Toast.makeText(context, "Location permission needed to drop GPS coordinates", Toast.LENGTH_SHORT).show()
        }
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
                .padding(10.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top Header Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val transportTag = if (peer.lastKnownIp == "CELLULAR_RELAY" || peer.lastKnownIp.isNullOrBlank()) {
                                        "4G/5G Relay"
                                    } else {
                                        "Mesh Link"
                                    }
                                    Text(
                                        text = "E2EE • $transportTag",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 1-Tap Audio Output Route Toggle: Loudspeaker (default) vs. Private Earpiece
                            IconButton(
                                onClick = {
                                    val next = !useLoudspeaker
                                    prefs.setRavenAudioLoudspeaker(next)
                                    val modeLabel = if (next) "🔊 Speakerphone (Loud)" else "👂 Private Earpiece (Hold to ear)"
                                    Toast.makeText(context, "Audio Output: $modeLabel", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = if (useLoudspeaker) Icons.Default.VolumeUp else Icons.Default.Hearing,
                                    contentDescription = "Audio Output Mode",
                                    tint = if (useLoudspeaker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }

                            // 1-Tap Walkie-Talkie Live Auto-Play vs. Silent Voice Note Toggle
                            IconButton(
                                onClick = {
                                    val next = !pttAutoPlay
                                    prefs.setRavenPttAutoPlay(next)
                                    val label = if (next) "📻 Walkie-Talkie Live Auto-Play ON" else "🔕 Silent Mode (Play Button Only)"
                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = if (pttAutoPlay) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = "PTT Reception Mode",
                                    tint = if (pttAutoPlay) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Loki's Mist (Disappearing / Burn-After-Reading) Menu
                            Box {
                                IconButton(onClick = { showLokisMistMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.AutoDelete,
                                        contentDescription = "Loki's Mist Ephemeral Timer",
                                        tint = if (ephemeralTtlSec != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showLokisMistMenu,
                                    onDismissRequest = { showLokisMistMenu = false }
                                ) {
                                    val options = listOf(
                                        0 to "🛡️ Off (Keep Messages)",
                                        -1 to "🔥 Burn After Reading / Playing",
                                        60 to "🌫️ 1 Minute (Loki's Mist)",
                                        3600 to "⏳ 1 Hour (Loki's Mist)",
                                        86400 to "🌙 24 Hours (Loki's Mist)"
                                    )
                                    options.forEach { (ttl, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (ephemeralTtlSec == ttl) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (ephemeralTtlSec == ttl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                ephemeralTtlSec = ttl
                                                dbHelper.setEphemeralTtlForPeer(peer.peerId, ttl)
                                                showLokisMistMenu = false
                                                Toast.makeText(context, "Loki's Mist: $label", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
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

                        // Quick Status Strip showing active Audio Routing & Loki's Mist status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.clickable {
                                    prefs.setRavenAudioLoudspeaker(!useLoudspeaker)
                                }
                            ) {
                                Text(
                                    text = if (useLoudspeaker) "🔊 Loudspeaker" else "👂 Private Ear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.clickable {
                                    prefs.setRavenPttAutoPlay(!pttAutoPlay)
                                }
                            ) {
                                Text(
                                    text = if (pttAutoPlay) "📻 Live Walkie-Talkie" else "🔕 Silent (Play Button)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            if (ephemeralTtlSec != 0) {
                                val mistTag = when (ephemeralTtlSec) {
                                    -1 -> "🔥 Burn on Read"
                                    60 -> "🌫️ 1m Mist"
                                    3600 -> "⏳ 1h Mist"
                                    else -> "🌙 24h Mist"
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = mistTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
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
                                text = "Walkie-talkie voice streams, text whispers, photos, files, and GPS pins are encrypted on-device with EC P-256 + AES-256-GCM.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Hold 🎙️ to walkie-talkie or tap 📎 to send a Raven Parcel!",
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
                                        if (playingMessageId == msg.id) {
                                            pttEngine.stopPlayback()
                                            playingMessageId = null
                                            return@RavenMessageBubble
                                        }
                                        playingMessageId = msg.id
                                        scope.launch(Dispatchers.IO) {
                                            pttEngine.playInboundVoice(
                                                audioBase64 = audioB64,
                                                useLoudspeaker = useLoudspeaker
                                            )
                                            // If Burn-on-Read is enabled for this inbound message, delete after playing!
                                            if (!msg.isOutbound && msg.burnOnRead) {
                                                dbHelper.deleteMessageById(msg.id)
                                                loadMessages()
                                            }
                                            withContext(Dispatchers.Main) {
                                                playingMessageId = null
                                            }
                                        }
                                    },
                                    onImageClick = { bmp ->
                                        fullScreenImageBitmap = bmp
                                        if (!msg.isOutbound && msg.burnOnRead) {
                                            scope.launch(Dispatchers.IO) {
                                                dbHelper.deleteMessageById(msg.id)
                                                loadMessages()
                                            }
                                        }
                                    },
                                    onBurnNow = {
                                        scope.launch(Dispatchers.IO) {
                                            dbHelper.deleteMessageById(msg.id)
                                            loadMessages()
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

                // Recording Status Bar with Live Timer, Live Streaming Indicator & Transcription Preview
                AnimatedVisibility(visible = isRecordingPtt) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        text = "📻 LIVE PTT $sec • Streaming to ${peer.displayName}",
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
                            if (liveTranscriptPreview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "📝 \"$liveTranscriptPreview\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
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
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Raven Parcel & Dead Reckoning Attachment Menu Button
                        Box {
                            IconButton(
                                onClick = { showAttachMenu = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Attach Raven Parcel or Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Take Camera Photo", fontWeight = FontWeight.SemiBold)
                                            Text("Snap & encrypt photo parcel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showAttachMenu = false
                                        cameraSnapshotLauncher.launch(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Attach Image from Gallery", fontWeight = FontWeight.SemiBold)
                                            Text("Send encrypted picture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showAttachMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Send File Parcel", fontWeight = FontWeight.SemiBold)
                                            Text("Any document, ZIP, or audio (<25 MB)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showAttachMenu = false
                                        filePickerLauncher.launch("*/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Drop Dead Reckoning Pin", fontWeight = FontWeight.SemiBold)
                                            Text("Encrypted GPS coordinates & battery %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showAttachMenu = false
                                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        if (hasFine || hasCoarse) {
                                            sendDeadReckoningLocationPin()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // PTT Walkie-Talkie Button (Supports both Hold-to-Talk Live Stream AND Tap-to-Toggle!)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecordingPtt) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primaryContainer
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
                                                stopAndSendVoiceRecording()
                                            } else {
                                                val pressStart = System.currentTimeMillis()
                                                startVoiceRecording()
                                                val released = tryAwaitRelease()
                                                val heldDuration = System.currentTimeMillis() - pressStart
                                                if (released && heldDuration >= 380L && isRecordingPtt) {
                                                    stopAndSendVoiceRecording()
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = if (isRecordingPtt) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Hold or Tap Walkie-Talkie PTT",
                                tint = if (isRecordingPtt) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Encrypted whisper...", fontSize = 14.sp) },
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

                        Spacer(modifier = Modifier.width(6.dp))

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

    // Full-screen Image Parcel Viewer Dialog with Pinch-to-Zoom & Pan
    fullScreenImageBitmap?.let { bmp ->
        Dialog(
            onDismissRequest = { fullScreenImageBitmap = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                offset = if (scale > 1f) {
                    Offset(offset.x + panChange.x, offset.y + panChange.y)
                } else {
                    Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Raven Encrypted Photo Fullscreen",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                // Top Controls: Title & Dismiss Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Raven Photo (${bmp.width}x${bmp.height})",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { fullScreenImageBitmap = null },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                // Reset zoom pill if zoomed in
                if (scale > 1.05f) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                    ) {
                        Text(
                            text = "Reset Zoom (${String.format(Locale.US, "%.1fx", scale)})",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
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
    onPlayAudio: (String) -> Unit = {},
    onImageClick: (Bitmap) -> Unit = {},
    onBurnNow: () -> Unit = {}
) {
    val context = LocalContext.current
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

    val decodedBitmap = androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, msg.id, msg.attachmentBase64, msg.messageType) {
        value = if (msg.messageType == "IMAGE" && !msg.attachmentBase64.isNullOrBlank()) {
            withContext(Dispatchers.Default) {
                try {
                    val bytes = Base64.decode(msg.attachmentBase64, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }.value

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(min = 60.dp, max = 300.dp),
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

                when (msg.messageType) {
                    "VOICE" -> {
                        Column {
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
                                    color = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Stop Voice Note" else "Play Voice Note",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
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
                                        text = if (isPlaying) "🔊 Playing... (Tap to stop)"
                                        else if (!msg.audioBase64.isNullOrBlank()) "▶️ Tap to play voice message"
                                        else "Voice transmission",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!msg.transcription.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "📝 ${msg.transcription}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = textColor.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    "IMAGE" -> {
                        Column {
                            if (decodedBitmap != null) {
                                Image(
                                    bitmap = decodedBitmap.asImageBitmap(),
                                    contentDescription = msg.attachmentName ?: "Encrypted Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp, max = 220.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onImageClick(decodedBitmap) },
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = msg.messageText,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    }

                    "FILE" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                                .clickable(enabled = !msg.attachmentBase64.isNullOrBlank()) {
                                    try {
                                        val bytes = Base64.decode(msg.attachmentBase64, Base64.NO_WRAP)
                                        val dir = File(context.cacheDir, "raven_parcels").apply { mkdirs() }
                                        val safeName = (msg.attachmentName ?: "raven_parcel.bin").replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                        val file = File(dir, safeName).apply { writeBytes(bytes) }
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = msg.attachmentMimeType ?: "*/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Save or Open Raven Parcel"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save Parcel",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = msg.attachmentName ?: "Encrypted Parcel",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = "Tap to save or open file",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    "LOCATION" -> {
                        Column {
                            Text(
                                text = msg.messageText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    val urlMatch = Regex("https://maps\\.google\\.com/\\?q=[0-9.,-]+").find(msg.messageText)?.value
                                    if (urlMatch != null) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlMatch)))
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open Coordinates in Maps", fontSize = 12.sp)
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = msg.messageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (msg.burnOnRead) {
                        Text(
                            text = "🔥 Burn",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { onBurnNow() }
                        )
                    } else if (msg.expiresAtTimestamp > 0L) {
                        val remMin = ((msg.expiresAtTimestamp - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1L)
                        Text(
                            text = "🌫️ ${remMin}m",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.7f)
                    )
                    if (isOutbound) {
                        val (statusText, statusColor) = when (msg.deliveryStatus) {
                            "READ" -> Pair("✓✓ Seen", androidx.compose.ui.graphics.Color(0xFF06B6D4))
                            "DELIVERED" -> Pair("✓✓ Delivered", MaterialTheme.colorScheme.primary)
                            else -> Pair("✓ Sent", textColor.copy(alpha = 0.7f))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) name = cursor.getString(idx)
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}
