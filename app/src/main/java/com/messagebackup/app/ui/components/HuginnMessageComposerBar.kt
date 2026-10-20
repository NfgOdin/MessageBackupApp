package com.messagebackup.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagebackup.app.R
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.domain.ai.AiClientEngine
import com.messagebackup.app.domain.ai.AiQueryResult
import com.messagebackup.app.ui.dialogs.RuneSpendConfirmationDialog
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Huginn Live Chat Composer & O.D.I.N. Thought Assistant Bar.
 * Anchored to the bottom of the conversation thread preview.
 *
 * Capabilities:
 * 1. Text input for composing replies.
 * 2. ᚱ O.D.I.N. Thought Rune: Offers one-tap intelligent rewrite / polish / context lookup based on conversation history.
 * 3. Send action: Dispatches directly via system SMS intent (`smsto:`) or Raven Mesh.
 */
@Composable
fun HuginnMessageComposerBar(
    thread: ConversationThread,
    recentMessages: List<MessageItem>,
    prefs: AppPreferences,
    aiEngine: AiClientEngine,
    isAdsRemoved: Boolean,
    onRequireCredits: () -> Unit,
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
            val result = aiEngine.queryOdin(
                userPrompt = prompt,
                messages = recentMessages.takeLast(15),
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
                customSystemPrompt = prefs.odinCustomSystemPrompt.value
            )

            withContext(Dispatchers.Main) {
                isThinking = false
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

    fun dispatchSms(textToSend: String) {
        if (textToSend.isBlank()) return
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
            focusManager.clearFocus()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open messaging app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
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

        // Huginn Brand & Mode Indicator above text enter box
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
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "• Live Dispatch",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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

            // Send Button
            Surface(
                shape = CircleShape,
                color = if (!isHuginnActive) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (textInput.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        if (!isHuginnActive) {
                            onRequireCredits()
                        } else if (textInput.isNotBlank()) {
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
                        } else if (textInput.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Overlay animation: Soaring raven flight from send button
        HuginnFlightOverlay(
            isFlying = isHuginnFlying,
            onFlightCompleted = { isHuginnFlying = false }
        )

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
