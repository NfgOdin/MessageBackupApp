package com.messagebackup.app.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.messagebackup.app.R
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.domain.ai.AiClientEngine
import com.messagebackup.app.domain.ai.AiQueryResult
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.ui.dialogs.RuneSpendConfirmationDialog
import com.messagebackup.app.worker.ScheduledSmsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * Huginn Live Chat Composer & O.D.I.N. Thought Assistant Bar.
 * Anchored to the bottom of the conversation thread preview.
 *
 * Capabilities:
 * 1. Direct SMS sending (with multi-SIM selection fallback).
 * 2. ᚱ O.D.I.N. Thought Rune: Instant intelligent rewrite / polish / context lookup.
 * 3. Swipe-to-reply quoted message preview banner.
 * 4. Voice Notes (record audio and send via MMS).
 * 5. Scheduled Text Messages ("Send Later" via WorkManager).
 */
@Composable
fun HuginnMessageComposerBar(
    thread: ConversationThread,
    recentMessages: List<MessageItem>,
    prefs: AppPreferences,
    aiEngine: AiClientEngine,
    isAdsRemoved: Boolean,
    onRequireCredits: () -> Unit,
    quotedMessage: MessageItem? = null,
    onClearQuotedMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val ansuzCredits by prefs.ansuzCredits.collectAsState()
    val huginnLeaseExpiryMs by prefs.huginnLeaseExpiryMs.collectAsState()
    val isHuginnPermanentUnlocked by prefs.isHuginnPermanentUnlocked.collectAsState()

    val isHuginnActive = isAdsRemoved || isHuginnPermanentUnlocked || (System.currentTimeMillis() < huginnLeaseExpiryMs)
    val remainingHours = if (huginnLeaseExpiryMs > System.currentTimeMillis()) {
        (huginnLeaseExpiryMs - System.currentTimeMillis()) / (1000L * 60L * 60L)
    } else 0L

    val confirmRuneSpend by prefs.confirmRuneSpend.collectAsState()
    var pendingThoughtAction by remember { mutableStateOf<String?>(null) }
    var showConfirmRuneSpend by remember { mutableStateOf(false) }

    var textInput by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var thoughtSuggestion by remember { mutableStateOf<String?>(null) }
    var showThoughtSheet by remember { mutableStateOf(false) }

    // Multi-SIM selection state
    var activeSims by remember { mutableStateOf<List<SubscriptionInfo>>(emptyList()) }
    var selectedSimIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val hasPhonePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasPhonePermission) {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val list = subManager?.activeSubscriptionInfoList ?: emptyList()
                activeSims = list
            }
        } catch (_: Exception) {}
    }

    // Voice Notes state
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }

    // Schedule Dialog state
    var showScheduleDialog by remember { mutableStateOf(false) }

    // Audio recording timer loop
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingDurationSeconds = 0
            while (isActive && isRecordingVoice) {
                delay(1000L)
                recordingDurationSeconds++
            }
        }
    }

    // Ensure recorder is released if composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            try {
                voiceRecorder?.stop()
                voiceRecorder?.release()
            } catch (_: Exception) {}
        }
    }

    fun executeOdinThought(actionType: String) {
        isThinking = true
        showThoughtSheet = true

        val prompt = when (actionType) {
            "POLISH" -> "The user is drafting a reply to ${thread.displayName}. Refine this draft to be articulate, warm, and clear while retaining their voice: \"$textInput\""
            "DIPLOMATIC" -> "The user is replying to ${thread.displayName}. Rewrite this draft to be highly diplomatic, de-escalating, and professional: \"$textInput\""
            "ODIN_STYLE" -> "The user is replying to ${thread.displayName}. Rewrite this draft in the poetic, dignified wisdom of the Allfather Odin: \"$textInput\""
            "CONTEXT_SUGGEST" -> "Based on recent messages in this conversation, suggest a thoughtful, natural next reply for the user to send to ${thread.displayName}."
            else -> "Improve this draft: \"$textInput\""
        }

        val provider = try {
            com.messagebackup.app.domain.ai.AiProviderType.valueOf(prefs.aiProvider.value)
        } catch (e: Exception) {
            com.messagebackup.app.domain.ai.AiProviderType.GEMINI
        }

        scope.launch(Dispatchers.IO) {
            val result = try {
                kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                    aiEngine.queryOdin(
                        userPrompt = prompt,
                        messages = recentMessages.takeLast(25),
                        targetContactName = thread.displayName,
                        providerType = provider,
                        geminiApiKey = prefs.geminiApiKey.value,
                        geminiModel = prefs.geminiModel.value,
                        openaiApiKey = prefs.openaiApiKey.value,
                        openaiModel = prefs.openaiModel.value,
                        ollamaEndpoint = prefs.ollamaEndpoint.value,
                        ollamaModel = prefs.ollamaModel.value,
                        temperature = prefs.odinTemperature.value,
                        topP = prefs.odinTopP.value,
                        numCtx = prefs.odinNumCtx.value,
                        customSystemPrompt = prefs.odinCustomSystemPrompt.value,
                        context = context
                    )
                } ?: AiQueryResult.Error("O.D.I.N. draft suggestion timed out.")
            } catch (e: Exception) {
                AiQueryResult.Error("O.D.I.N. encountered an error: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                withContext(Dispatchers.Main) {
                    isThinking = false
                }
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    is AiQueryResult.Success -> {
                        if (!isAdsRemoved) {
                            prefs.consumeAnsuzCredit()
                        }
                        thoughtSuggestion = result.responseText.trim().removeSurrounding("\"")
                    }
                    is AiQueryResult.Error -> {
                        Toast.makeText(context, "⚠️ ${result.errorMessage} (No credits consumed)", Toast.LENGTH_LONG).show()
                        showThoughtSheet = false
                    }
                }
            }
        }
    }

    fun triggerOdinThought(actionType: String) {
        if (!isHuginnActive) {
            onRequireCredits()
            return
        }
        val currentCredits = prefs.ansuzCredits.value
        if (!isAdsRemoved && currentCredits <= 0) {
            onRequireCredits()
            return
        }

        if (!isAdsRemoved && confirmRuneSpend) {
            pendingThoughtAction = actionType
            showConfirmRuneSpend = true
        } else {
            executeOdinThought(actionType)
        }
    }

    var isHuginnFlying by remember { mutableStateOf(false) }
    var pendingDirectSmsText by remember { mutableStateOf("") }

    fun sendSmsViaIntentFallback(textToSend: String) {
        val recipientAddress = thread.address
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(recipientAddress)}")
                putExtra("sms_body", textToSend)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            isHuginnFlying = true
            textInput = ""
            thoughtSuggestion = null
            showThoughtSheet = false
            onClearQuotedMessage()
            focusManager.clearFocus()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open messaging app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendSmsDirectly(textToSend: String) {
        if (textToSend.isBlank()) return
        val recipientAddress = thread.address
        try {
            val selectedSubId = activeSims.getOrNull(selectedSimIndex)?.subscriptionId ?: -1
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (selectedSubId != -1) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(selectedSubId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                }
            } else {
                @Suppress("DEPRECATION")
                if (selectedSubId != -1) {
                    SmsManager.getSmsManagerForSubscriptionId(selectedSubId)
                } else {
                    SmsManager.getDefault()
                }
            }

            val parts = smsManager.divideMessage(textToSend)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipientAddress, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipientAddress, null, textToSend, null, null)
            }

            // Immediately register into Telephony Sent table so the message shows in the conversation thread
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.Telephony.Sms.ADDRESS, recipientAddress)
                    put(android.provider.Telephony.Sms.BODY, textToSend)
                    put(android.provider.Telephony.Sms.DATE, System.currentTimeMillis())
                    put(android.provider.Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                    put(android.provider.Telephony.Sms.READ, 1)
                    put(android.provider.Telephony.Sms.SEEN, 1)
                    put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_SENT)
                }
                context.contentResolver.insert(android.provider.Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (_: Exception) {}

            isHuginnFlying = true
            textInput = ""
            thoughtSuggestion = null
            showThoughtSheet = false
            onClearQuotedMessage()
            focusManager.clearFocus()
            Toast.makeText(context, "🦅 Huginn dispatched text directly", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            sendSmsViaIntentFallback(textToSend)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sendSmsDirectly(pendingDirectSmsText)
        } else {
            sendSmsViaIntentFallback(pendingDirectSmsText)
        }
    }

    fun dispatchSms(textToSend: String) {
        if (textToSend.isBlank()) return
        val finalBody = if (quotedMessage != null) {
            "“${quotedMessage.body.take(40)}...”\n\n$textToSend"
        } else {
            textToSend
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            sendSmsDirectly(finalBody)
        } else {
            pendingDirectSmsText = finalBody
            smsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS)
        }
    }

    // Voice Note helper functions
    fun startVoiceRecording() {
        try {
            val outputDir = File(context.cacheDir, "huginn_voice_notes").apply { mkdirs() }
            val audioFile = File(outputDir, "huginn_note_${System.currentTimeMillis()}.m4a")
            voiceRecordingFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(audioFile.absolutePath)
            recorder.prepare()
            recorder.start()

            voiceRecorder = recorder
            isRecordingVoice = true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start audio recorder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoiceRecording() {
        try {
            voiceRecorder?.stop()
            voiceRecorder?.release()
            voiceRecorder = null
            isRecordingVoice = false

            val file = voiceRecordingFile ?: return
            if (file.exists() && file.length() > 0) {
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mmsIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra("address", thread.address)
                    putExtra("sms_body", "Voice Note")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(mmsIntent, "Send Voice Note via MMS"))
                Toast.makeText(context, "🎤 Voice note ready to dispatch", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Voice note dispatch error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelVoiceRecording() {
        try {
            voiceRecorder?.stop()
            voiceRecorder?.release()
        } catch (_: Exception) {}
        voiceRecorder = null
        isRecordingVoice = false
        voiceRecordingFile?.delete()
        voiceRecordingFile = null
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Quoted Message Banner (Swipe-to-Reply)
        AnimatedVisibility(visible = quotedMessage != null) {
            if (quotedMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${if (quotedMessage.direction == MessageDirection.OUTGOING) "Yourself" else quotedMessage.sender}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = quotedMessage.body.ifBlank { "Attachment" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        IconButton(
                            onClick = onClearQuotedMessage,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // O.D.I.N. Thought Suggestion Banner
        AnimatedVisibility(visible = showThoughtSheet) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_rune_raido),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "O.D.I.N. Thought Counsel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        IconButton(
                            onClick = { showThoughtSheet = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isThinking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Muninn recalls memory... O.D.I.N. ponders thought...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    } else if (thoughtSuggestion != null) {
                        Text(
                            text = thoughtSuggestion!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    textInput = thoughtSuggestion!!
                                    showThoughtSheet = false
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Use Draft")
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            TextButton(
                                onClick = { dispatchSms(thoughtSuggestion!!) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Directly")
                            }
                        }
                    } else {
                        // Options Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { triggerOdinThought("POLISH") }
                            ) {
                                Text(
                                    text = "✨ Polish",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clickable { triggerOdinThought("DIPLOMATIC") }
                            ) {
                                Text(
                                    text = "🕊️ Diplomatic",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { triggerOdinThought("ODIN_STYLE") }
                            ) {
                                Text(
                                    text = "ᚱ Allfather",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voice Recording Active Bar
        AnimatedVisibility(visible = isRecordingVoice) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val mins = recordingDurationSeconds / 60
                        val secs = recordingDurationSeconds % 60
                        Text(
                            text = "Recording Voice Note... %02d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { cancelVoiceRecording() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { stopAndSendVoiceRecording() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Send", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Huginn Brand & Dual-SIM Indicator above text enter box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🪶 HUGINN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isAdsRemoved) {
                    "• PRO Unlimited"
                } else if (isHuginnPermanentUnlocked) {
                    "• Unlocked • $ansuzCredits ᚫ"
                } else if (isHuginnActive) {
                    val days = remainingHours / 24
                    val hrs = remainingHours % 24
                    val timeStr = if (days > 0) "${days}d ${hrs}h" else "${hrs}h"
                    "• ⏱️ $timeStr left • $ansuzCredits ᚫ"
                } else {
                    "• 🔒 Lease Expired"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isHuginnActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // Dual-SIM Selector Toggle Chip
            if (activeSims.size > 1) {
                Spacer(modifier = Modifier.width(8.dp))
                val currentSim = activeSims.getOrNull(selectedSimIndex)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        selectedSimIndex = (selectedSimIndex + 1) % activeSims.size
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "SIM ${currentSim?.simSlotIndex?.plus(1) ?: 1} ▾",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Schedule Button
            IconButton(
                onClick = { showScheduleDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Schedule SMS",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Main Composer Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ᚫ Ansuz Thought Rune Launcher Button
            Surface(
                shape = CircleShape,
                color = if (showThoughtSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        showThoughtSheet = !showThoughtSheet
                        if (showThoughtSheet && textInput.isBlank() && thoughtSuggestion == null) {
                            triggerOdinThought("CONTEXT_SUGGEST")
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rune_ansuz),
                        contentDescription = "O.D.I.N. Thought",
                        tint = if (showThoughtSheet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Text Input Box
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        text = "Reply to ${thread.displayName}...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines = 4,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                        if (isHuginnActive && textInput.isNotBlank()) {
                            dispatchSms(textInput)
                        }
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )

            // Voice Note Mic Button (if text is blank) OR Send Button (if text is present)
            if (textInput.isBlank()) {
                Surface(
                    shape = CircleShape,
                    color = if (isRecordingVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            val hasAudio = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (!hasAudio) {
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else if (!isRecordingVoice) {
                                startVoiceRecording()
                            } else {
                                stopAndSendVoiceRecording()
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = if (isRecordingVoice) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = if (!isHuginnActive) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            if (!isHuginnActive) {
                                onRequireCredits()
                            } else {
                                dispatchSms(textInput)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (!isHuginnActive) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (!isHuginnActive) "Unlock Huginn" else "Send",
                            tint = if (!isHuginnActive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Overlay animation: Soaring raven flight from send button
        HuginnFlightOverlay(
            isFlying = isHuginnFlying,
            onFlightCompleted = { isHuginnFlying = false }
        )

        // Schedule Send Dialog
        if (showScheduleDialog) {
            AlertDialog(
                onDismissRequest = { showScheduleDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Schedule Message", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select when Huginn should dispatch this text to ${thread.displayName}:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val options = listOf(
                            "In 15 minutes" to 15 * 60 * 1000L,
                            "In 1 hour" to 60 * 60 * 1000L,
                            "In 3 hours" to 3 * 60 * 60 * 1000L,
                            "Tomorrow morning (9:00 AM)" to run {
                                val cal = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                    set(Calendar.HOUR_OF_DAY, 9)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }
                                cal.timeInMillis - System.currentTimeMillis()
                            }
                        )

                        for ((label, delayMs) in options) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showScheduleDialog = false
                                        val text = textInput.ifBlank { "Checking in!" }
                                        val subId = activeSims.getOrNull(selectedSimIndex)?.subscriptionId ?: -1
                                        ScheduledSmsWorker.scheduleSms(
                                            context = context,
                                            recipientAddress = thread.address,
                                            displayName = thread.displayName,
                                            body = text,
                                            delayMillis = delayMs,
                                            subscriptionId = subId
                                        )
                                        textInput = ""
                                        Toast.makeText(context, "⏳ Message scheduled: $label", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showScheduleDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 100% Transparency Rune Spend Confirmation Dialog
        if (showConfirmRuneSpend && pendingThoughtAction != null) {
            val actionDesc = when (pendingThoughtAction) {
                "POLISH" -> "Consult O.D.I.N. to polish and articulate your draft reply."
                "DIPLOMATIC" -> "Consult O.D.I.N. to rewrite your draft with diplomatic tact."
                "ODIN_STYLE" -> "Consult O.D.I.N. to infuse your draft with ancient Norse wisdom."
                "CONTEXT_SUGGEST" -> "Consult O.D.I.N. to analyze recent chat history and synthesize a thoughtful reply."
                else -> "Consult O.D.I.N. to counsel your message draft."
            }
            RuneSpendConfirmationDialog(
                runeName = "Ansuz (Thought & Voice)",
                runeDrawableId = R.drawable.ic_rune_ansuz,
                runeSymbol = "ᚫ",
                costAmount = 1,
                currentBalance = ansuzCredits,
                actionDescription = actionDesc,
                prefs = prefs,
                onConfirm = {
                    val act = pendingThoughtAction ?: "CONTEXT_SUGGEST"
                    showConfirmRuneSpend = false
                    pendingThoughtAction = null
                    executeOdinThought(act)
                },
                onDismiss = {
                    showConfirmRuneSpend = false
                    pendingThoughtAction = null
                }
            )
        }
    }
}
