package com.messagebackup.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.messagebackup.app.ads.AdManager
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.domain.ai.AiClientEngine
import com.messagebackup.app.domain.ai.AiProviderType
import com.messagebackup.app.domain.ai.AiQueryResult
import com.messagebackup.app.domain.ai.OdinChatMessage
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.ui.dialogs.OllamaSetupGuideDialog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * O.D.I.N. (Omniscient Document & Insights Navigator) Chat Copilot.
 * Allows users to ask natural questions about their backed-up messages using Gemini or Ollama.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCopilotScreen(
    prefs: AppPreferences,
    adManager: AdManager,
    isAdsRemoved: Boolean,
    threads: List<ConversationThread>,
    currentMessages: List<MessageItem>,
    targetContactName: String,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    initialPrompt: String? = null,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val aiEngine = remember { AiClientEngine() }

    val aiCredits by prefs.aiCredits.collectAsState()
    val aiProviderStr by prefs.aiProvider.collectAsState()
    val geminiKey by prefs.geminiApiKey.collectAsState()
    val ollamaUrl by prefs.ollamaEndpoint.collectAsState()
    val ollamaModel by prefs.ollamaModel.collectAsState()

    val currentProvider = remember(aiProviderStr) {
        if (aiProviderStr == AiProviderType.OLLAMA.name) AiProviderType.OLLAMA else AiProviderType.GEMINI
    }

    var showGuideDialog by remember { mutableStateOf(false) }
    var showArcadeDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val chatMessages = remember {
        mutableStateListOf(
            OdinChatMessage(
                sender = OdinChatMessage.MessageSender.ODIN,
                text = "Greetings! I am O.D.I.N. (Omniscient Document & Insights Navigator). Ask me anything about your conversations with $targetContactName — such as door codes, reservations, past agreements, or summaries."
            )
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val starterPrompts = listOf(
        "⚡ Summarize thread in 3 bullets",
        "📅 Find dates, deadlines & commitments",
        "📍 Find addresses & directions",
        "🔑 Find gate, door, or Wi-Fi codes",
        "✈️ List flights & reservations",
        "✍️ Draft a polite follow-up reply"
    )

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank() || isThinking) return

        // Credit check for free users
        if (!isAdsRemoved && aiCredits <= 0) {
            Toast.makeText(context, "Out of AI credits! Watch an ad to earn +2 credits.", Toast.LENGTH_SHORT).show()
            adManager.showRewardedAd(
                activity = activity,
                isAdsRemoved = false,
                onUserEarnedReward = {
                    prefs.recordRewardAdWatched()
                    prefs.addAiCredits(2)
                    Toast.makeText(context, "Earned +2 AI Credits! Resending...", Toast.LENGTH_SHORT).show()
                    sendPrompt(trimmed)
                }
            )
            return
        }

        // Consume credit for free users
        if (!isAdsRemoved) {
            prefs.consumeAiCredit()
        }

        inputText = ""
        chatMessages.add(OdinChatMessage(sender = OdinChatMessage.MessageSender.USER, text = trimmed))
        isThinking = true

        scope.launch {
            val result = aiEngine.queryOdin(
                userPrompt = trimmed,
                messages = currentMessages,
                targetContactName = targetContactName,
                providerType = currentProvider,
                geminiApiKey = geminiKey,
                ollamaEndpoint = ollamaUrl,
                ollamaModel = ollamaModel
            )

            isThinking = false
            when (result) {
                is AiQueryResult.Success -> {
                    chatMessages.add(
                        OdinChatMessage(sender = OdinChatMessage.MessageSender.ODIN, text = result.responseText)
                    )
                }
                is AiQueryResult.Error -> {
                    chatMessages.add(
                        OdinChatMessage(sender = OdinChatMessage.MessageSender.ODIN, text = "⚠️ ${result.errorMessage}", isError = true)
                    )
                }
            }
        }
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            sendPrompt(initialPrompt)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("O.D.I.N. Copilot", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = if (currentProvider == AiProviderType.OLLAMA) "OLLAMA" else "GEMINI",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Target: $targetContactName (${currentMessages.size} msgs)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Credit Badge or PRO
                    Surface(
                        color = if (isAdsRemoved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable {
                                if (!isAdsRemoved) {
                                    showArcadeDialog = true
                                }
                            }
                            .padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isAdsRemoved) Icons.Default.AutoAwesome else Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isAdsRemoved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAdsRemoved) "PRO" else "$aiCredits Credits",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isAdsRemoved) {
                        IconButton(onClick = { showArcadeDialog = true }) {
                            Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = "Reward Arcade")
                        }
                    }

                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "Setup Guide")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "AI Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Privacy & Choice Indicator Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (currentProvider == AiProviderType.OLLAMA)
                        "🔒 Local Privacy Mode: Queries stay on your private network ($ollamaUrl)"
                    else
                        "☁️ Opt-In Gemini Mode (BYOK): Queries use your personal API key • Switch to Ollama in Settings for 100% local AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Quick Starter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(starterPrompts) { prompt ->
                    FilterChip(
                        selected = false,
                        onClick = { sendPrompt(prompt) },
                        label = { Text(prompt, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Chat Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "O.D.I.N. is analyzing messages...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask O.D.I.N. about messages...") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendPrompt(inputText) },
                        enabled = inputText.isNotBlank() && !isThinking
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    if (showGuideDialog) {
        OllamaSetupGuideDialog(
            currentEndpoint = ollamaUrl,
            currentModel = ollamaModel,
            onSaveEndpoint = { endpoint, model ->
                prefs.setOllamaEndpoint(endpoint)
                prefs.setOllamaModel(model)
                prefs.setAiProvider("LOCAL_OLLAMA")
            },
            onDismiss = { showGuideDialog = false }
        )
    }

    if (showArcadeDialog) {
        com.messagebackup.app.ui.dialogs.CreditArcadeDialog(
            prefs = prefs,
            adManager = adManager,
            onDismiss = { showArcadeDialog = false }
        )
    }
}

@Composable
private fun ChatBubble(message: OdinChatMessage) {
    val isUser = message.sender == OdinChatMessage.MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (message.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (message.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "O.D.I.N.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}
