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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
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
    isLoadingMessages: Boolean = false,
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
    val geminiModel by prefs.geminiModel.collectAsState()
    val openaiKey by prefs.openaiApiKey.collectAsState()
    val openaiModel by prefs.openaiModel.collectAsState()
    val ollamaUrl by prefs.ollamaEndpoint.collectAsState()
    val ollamaModel by prefs.ollamaModel.collectAsState()
    val odinTemperature by prefs.odinTemperature.collectAsState()
    val odinTopP by prefs.odinTopP.collectAsState()
    val odinNumCtx by prefs.odinNumCtx.collectAsState()
    val customSystemPrompt by prefs.odinCustomSystemPrompt.collectAsState()

    val currentProvider = remember(aiProviderStr) {
        try {
            AiProviderType.valueOf(aiProviderStr)
        } catch (e: Exception) {
            AiProviderType.GEMINI
        }
    }

    var showGuideDialog by remember { mutableStateOf(false) }
    var showArcadeDialog by remember { mutableStateOf(false) }
    var showRadarDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val chatMessages = remember {
        val greeting = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
            "Hail, traveler. I am O.D.I.N. (Omniscient Document & Insights Navigator). My raven Muninn remembers all words, oaths, dates, and secrets in these vaults. What wisdom or forgotten truth dost thou seek?"
        } else {
            "Hail, traveler. I am O.D.I.N. The archives of your words with $targetContactName are open before me. Ask of their oaths, door codes, dates, or hidden truths, and Muninn shall find them."
        }
        mutableStateListOf(
            OdinChatMessage(
                sender = OdinChatMessage.MessageSender.ODIN,
                text = greeting
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

        if (isLoadingMessages) {
            Toast.makeText(context, "Indexing conversations, please wait a moment...", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentMessages.isEmpty()) {
            Toast.makeText(context, "No messages available to analyze in this conversation archive.", Toast.LENGTH_LONG).show()
            return
        }

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
                geminiModel = geminiModel,
                openaiApiKey = openaiKey,
                openaiModel = openaiModel,
                ollamaEndpoint = ollamaUrl,
                ollamaModel = ollamaModel,
                temperature = odinTemperature,
                topP = odinTopP,
                numCtx = odinNumCtx,
                customSystemPrompt = customSystemPrompt
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

    var initialPromptHandled by remember { mutableStateOf(false) }

    LaunchedEffect(initialPrompt, isLoadingMessages, currentMessages.size) {
        if (!initialPrompt.isNullOrBlank() && !initialPromptHandled && !isLoadingMessages && currentMessages.isNotEmpty()) {
            initialPromptHandled = true
            sendPrompt(initialPrompt)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "O.D.I.N. Copilot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (currentProvider == AiProviderType.OLLAMA) "OLLAMA" else "GEMINI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isLoadingMessages) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "• Indexing conversations...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "• $targetContactName (${currentMessages.size} msgs)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
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
                            .padding(end = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            if (isAdsRemoved) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            } else {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.messagebackup.app.R.drawable.ic_rune_raido),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isAdsRemoved) "PRO" else "$aiCredits",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!isAdsRemoved && aiCredits <= 0) {
                                Toast.makeText(context, "Scanning promises requires 1 Raido Rune credit. Watch an ad to earn +2!", Toast.LENGTH_SHORT).show()
                                adManager.showRewardedAd(
                                    activity = activity,
                                    isAdsRemoved = false,
                                    onUserEarnedReward = {
                                        prefs.recordRewardAdWatched()
                                        prefs.addAiCredits(2)
                                        prefs.consumeAiCredit()
                                        showRadarDialog = true
                                    }
                                )
                            } else {
                                if (!isAdsRemoved) {
                                    prefs.consumeAiCredit()
                                }
                                showRadarDialog = true
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Radar, contentDescription = "Commitment Radar", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }

                    if (!isAdsRemoved) {
                        IconButton(
                            onClick = { showArcadeDialog = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = "Reward Arcade", modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "Setup Guide", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "AI Settings", modifier = Modifier.size(20.dp))
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
                    text = when (currentProvider) {
                        AiProviderType.OLLAMA -> "🔒 Local Privacy Mode: Queries stay on your private local network ($ollamaUrl)"
                        AiProviderType.OPENAI -> "☁️ OpenAI ChatGPT Mode (BYOK): Queries use your personal API key ($openaiModel)"
                        AiProviderType.GEMINI -> "☁️ Google Gemini Mode (BYOK): Queries use your personal API key ($geminiModel)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (isLoadingMessages) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
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
                    ChatBubble(
                        message = msg,
                        targetContactName = targetContactName,
                        targetAddress = threads.firstOrNull { it.displayName == targetContactName || it.address == targetContactName }?.address
                    )
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
                        placeholder = {
                            Text(
                                if (isLoadingMessages) "Indexing conversations for O.D.I.N...."
                                else "Ask O.D.I.N. about messages..."
                            )
                        },
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

    if (showRadarDialog) {
        com.messagebackup.app.ui.dialogs.CommitmentRadarDialog(
            messages = currentMessages,
            contactName = targetContactName,
            onDismiss = { showRadarDialog = false }
        )
    }
}

@Composable
private fun ChatBubble(
    message: OdinChatMessage,
    targetContactName: String = "",
    targetAddress: String? = null
) {
    val context = LocalContext.current
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

    // Determine if this ODIN response appears to suggest/draft a reply or contains actionable response text
    val isOdinResponse = !isUser && !message.isError
    val containsDraftOrReply = isOdinResponse && message.text.length > 5 && !message.text.startsWith("Greetings!")

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
            modifier = Modifier.fillMaxWidth(0.88f)
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

                if (containsDraftOrReply) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Copy to Clipboard chip
                        AssistChip(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("ODIN Reply", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Copy", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                        )

                        // Open in Messages chip
                        AssistChip(
                            onClick = {
                                try {
                                    val sendIntent = if (!targetAddress.isNullOrBlank()) {
                                        android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                            data = android.net.Uri.parse("smsto:$targetAddress")
                                            putExtra("sms_body", message.text)
                                        }
                                    } else {
                                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, message.text)
                                        }
                                    }
                                    context.startActivity(sendIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open messaging app: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = {
                                Text(
                                    if (!targetAddress.isNullOrBlank()) "Send to $targetContactName" else "Open in Messages",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "Open in Messages",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }
    }
}
