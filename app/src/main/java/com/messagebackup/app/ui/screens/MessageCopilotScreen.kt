package com.messagebackup.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.messagebackup.app.model.VaultMediaItem
import com.messagebackup.app.ui.dialogs.MediaLightboxDialog
import com.messagebackup.app.R
import com.messagebackup.app.ads.AdManager
import com.messagebackup.app.data.local.LocalMessageDatabase
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.domain.ai.AiClientEngine
import com.messagebackup.app.domain.ai.AiProviderType
import com.messagebackup.app.ui.dialogs.RuneSpendConfirmationDialog
import com.messagebackup.app.domain.ai.AiQueryResult
import com.messagebackup.app.domain.ai.OdinChatMessage
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.ui.dialogs.OllamaSetupGuideDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity ?: (context as Activity)
    val scope = rememberCoroutineScope()
    val aiEngine = remember { AiClientEngine() }
    val localDb = remember { LocalMessageDatabase.getInstance(context) }

    val aiCredits by prefs.aiCredits.collectAsState()
    val isOdinPermanentUnlocked by prefs.isOdinPermanentUnlocked.collectAsState()
    val isOdinUnlockedByBilling by com.messagebackup.app.MessageBackupApp.instance.billingManager.isOdinUnlocked.collectAsState()
    val isOdinUnlimited = isAdsRemoved || isOdinPermanentUnlocked || isOdinUnlockedByBilling
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
    val odinSaveHistory by prefs.odinSaveHistory.collectAsState()
    val googleAccountEmail by prefs.googleAccountEmail.collectAsState()

    val targetThread = remember(threads, targetContactName) {
        if (targetContactName == "All Conversations" || targetContactName.isBlank()) null
        else threads.firstOrNull { it.displayName == targetContactName || it.address == targetContactName }
    }

    var fullThreadMessages by remember(targetThread?.threadId) { mutableStateOf<List<MessageItem>>(emptyList()) }

    LaunchedEffect(targetThread?.threadId) {
        val tid = targetThread?.threadId ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                com.messagebackup.app.MessageBackupApp.instance.currentTelephonyRepository.getMessagesForThread(
                    threadId = tid,
                    resolveContacts = false,
                    limit = null
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (loaded.isNotEmpty()) {
            fullThreadMessages = loaded
        }
    }

    val effectiveMessages = if (fullThreadMessages.size > currentMessages.size) fullThreadMessages else currentMessages

    val threadKey = remember(targetContactName) {
        if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
            "ALL_CONVERSATIONS"
        } else {
            "THREAD_${targetContactName.trim()}"
        }
    }

    val currentProvider = remember(aiProviderStr) {
        try {
            AiProviderType.valueOf(aiProviderStr)
        } catch (e: Exception) {
            AiProviderType.GEMINI
        }
    }

    val confirmRuneSpend by prefs.confirmRuneSpend.collectAsState()
    var pendingPromptText by remember { mutableStateOf<String?>(null) }
    var showPromptSpendConfirm by remember { mutableStateOf(false) }
    var showRadarSpendConfirm by remember { mutableStateOf(false) }
    var showClearHistoryMenu by remember { mutableStateOf(false) }
    var activeLightboxMedia by remember { mutableStateOf<Pair<List<VaultMediaItem>, Int>?>(null) }

    var showGuideDialog by remember { mutableStateOf(false) }
    var showArcadeDialog by remember { mutableStateOf(false) }
    var showRadarDialog by remember { mutableStateOf(false) }
    var showProviderQuickMenu by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        var localTts: TextToSpeech? = null
        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.language = Locale.getDefault()
                tts = localTts
            }
        }
        onDispose {
            try {
                localTts?.stop()
                localTts?.shutdown()
            } catch (_: Exception) {}
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = spoken?.firstOrNull()
            if (!text.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) text else "$inputText $text"
            }
        }
    }

    val greetingMessage = remember(targetContactName) {
        val greeting = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
            "Hail, traveler. I am O.D.I.N. (Omniscient Document & Insights Navigator). My raven Muninn remembers all words, oaths, dates, and secrets in these vaults. What wisdom or forgotten truth dost thou seek?"
        } else {
            "Hail, traveler. I am O.D.I.N. The archives of your words with $targetContactName are open before me. Ask of their oaths, door codes, dates, or hidden truths, and Muninn shall find them."
        }
        OdinChatMessage(
            sender = OdinChatMessage.MessageSender.ODIN,
            text = greeting
        )
    }

    val chatMessages = remember(threadKey) {
        mutableStateListOf(greetingMessage)
    }

    // Load saved O.D.I.N. history for this conversation when opened
    LaunchedEffect(threadKey, odinSaveHistory) {
        if (odinSaveHistory) {
            val savedHistory = withContext(Dispatchers.IO) {
                localDb.getOdinChatHistory(threadKey)
            }
            chatMessages.clear()
            chatMessages.add(greetingMessage)
            chatMessages.addAll(savedHistory)
        }
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

    fun executeSendPrompt(trimmed: String) {
        inputText = ""
        val userMsg = OdinChatMessage(sender = OdinChatMessage.MessageSender.USER, text = trimmed)
        chatMessages.add(userMsg)
        if (odinSaveHistory) {
            scope.launch(Dispatchers.IO) {
                localDb.insertOdinChatMessage(threadKey, userMsg)
            }
        }
        isThinking = true

        scope.launch {
            val result = try {
                // Ensure messages are loaded if the user tapped a prompt while background indexing was still running
                val messagesToAnalyze = if (effectiveMessages.isNotEmpty()) {
                    effectiveMessages
                } else {
                    withContext(Dispatchers.IO) {
                        val repo = com.messagebackup.app.MessageBackupApp.instance.currentTelephonyRepository
                        if (targetThread != null) {
                            repo.getMessagesForThread(targetThread.threadId, resolveContacts = false, limit = null)
                        } else {
                            repo.getRecentMessagesAcrossConversations(
                                maxMessages = 400,
                                resolveContacts = prefs.resolveContacts.value
                            )
                        }
                    }
                }

                kotlinx.coroutines.withTimeoutOrNull(32_000L) {
                    aiEngine.queryOdin(
                        userPrompt = trimmed,
                        messages = messagesToAnalyze,
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
                        customSystemPrompt = customSystemPrompt,
                        context = context
                    )
                } ?: AiQueryResult.Error("O.D.I.N. query timed out. Please try again or switch AI mode in the top bar.")
            } catch (e: Exception) {
                AiQueryResult.Error("O.D.I.N. encountered an unexpected error: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                isThinking = false
            }

            when (result) {
                is AiQueryResult.Success -> {
                    // Only consume the credit when a successful answer is actually delivered!
                    if (!isOdinUnlimited) {
                        prefs.consumeAiCredit()
                    }
                    val odinReply = OdinChatMessage(sender = OdinChatMessage.MessageSender.ODIN, text = result.responseText)
                    chatMessages.add(odinReply)
                    if (odinSaveHistory) {
                        withContext(Dispatchers.IO) {
                            localDb.insertOdinChatMessage(threadKey, odinReply)
                        }
                    }
                }
                is AiQueryResult.Error -> {
                    // Credit is preserved/never taken on error
                    chatMessages.add(
                        OdinChatMessage(sender = OdinChatMessage.MessageSender.ODIN, text = "⚠️ ${result.errorMessage}\n\n(No credit was consumed)", isError = true)
                    )
                }
            }
        }
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank() || isThinking) return

        // Credit check for free users
        if (!isOdinUnlimited && aiCredits <= 0) {
            Toast.makeText(context, "Out of Othala AI credits! Watch an ad or sacrifice an eye for knowledge.", Toast.LENGTH_SHORT).show()
            adManager.showRewardedAd(
                activity = activity,
                isAdsRemoved = false,
                onUserEarnedReward = {
                    prefs.recordRewardAdWatched()
                    prefs.addAiCredits(2)
                    Toast.makeText(context, "Earned +2 ᛟ Othala Runes! Resending...", Toast.LENGTH_SHORT).show()
                    sendPrompt(trimmed)
                }
            )
            return
        }

        if (!isOdinUnlimited && confirmRuneSpend) {
            pendingPromptText = trimmed
            showPromptSpendConfirm = true
        } else {
            executeSendPrompt(trimmed)
        }
    }

    var initialPromptHandled by remember { mutableStateOf(false) }

    LaunchedEffect(initialPrompt, isLoadingMessages, effectiveMessages.size) {
        if (!initialPrompt.isNullOrBlank() && !initialPromptHandled && !isLoadingMessages && effectiveMessages.isNotEmpty()) {
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
                            Box {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.clickable { showProviderQuickMenu = true }
                                ) {
                                    Text(
                                        text = when (currentProvider) {
                                            AiProviderType.ON_DEVICE_NANO -> "⚡ ON-DEVICE AI ▾"
                                            AiProviderType.OPENAI -> "☁️ OPENAI ▾"
                                            AiProviderType.OLLAMA -> "🔒 OLLAMA ▾"
                                            AiProviderType.GEMINI -> if (geminiKey.isBlank()) "☁️ GEMINI (1-TAP) ▾" else "☁️ GEMINI (BYOK) ▾"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showProviderQuickMenu,
                                    onDismissRequest = { showProviderQuickMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("☁️ Google Gemini (1-Tap Cloud AI)", fontWeight = FontWeight.Bold)
                                                Text("Works immediately • No API key needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            prefs.setAiProvider("GEMINI")
                                            showProviderQuickMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("⚡ On-Device AI (Gemini Nano / Local)", fontWeight = FontWeight.Bold)
                                                Text("100% Private on-device • Works offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            prefs.setAiProvider("ON_DEVICE_NANO")
                                            showProviderQuickMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("🧠 OpenAI ChatGPT (BYOK)", fontWeight = FontWeight.Bold)
                                                Text("Uses personal OpenAI API key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            prefs.setAiProvider("OPENAI")
                                            showProviderQuickMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("🔒 Local / Self-Hosted (Ollama)", fontWeight = FontWeight.Bold)
                                                Text("Private LAN / Tailscale host", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            prefs.setAiProvider("OLLAMA")
                                            showProviderQuickMenu = false
                                        }
                                    )
                                }
                            }
                            if (isLoadingMessages && effectiveMessages.isEmpty()) {
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
                                    text = "• $targetContactName (${effectiveMessages.size} msgs)",
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
                        color = if (isOdinUnlimited) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable {
                                if (!isOdinUnlimited) {
                                    showArcadeDialog = true
                                }
                            }
                            .padding(end = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            if (isOdinUnlimited) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_odin_allfather),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = com.messagebackup.app.R.drawable.ic_rune_othala),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isAdsRemoved) "PRO" else if (isOdinPermanentUnlocked || isOdinUnlockedByBilling) "ᛟ 👁️ ∞" else "$aiCredits",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!isOdinUnlimited && aiCredits <= 0) {
                                Toast.makeText(context, "Scanning promises requires 1 Othala Rune credit. Watch an ad to earn +2!", Toast.LENGTH_SHORT).show()
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
                                if (!isOdinUnlimited) {
                                    if (confirmRuneSpend) {
                                        showRadarSpendConfirm = true
                                    } else {
                                        prefs.consumeAiCredit()
                                        showRadarDialog = true
                                    }
                                } else {
                                    showRadarDialog = true
                                }
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

                    // Clear O.D.I.N. History Menu
                    Box {
                        IconButton(
                            onClick = { showClearHistoryMenu = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear O.D.I.N. History", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showClearHistoryMenu,
                            onDismissRequest = { showClearHistoryMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear This Conversation's History") },
                                onClick = {
                                    showClearHistoryMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        localDb.clearOdinChatHistoryForThread(threadKey)
                                        withContext(Dispatchers.Main) {
                                            chatMessages.clear()
                                            chatMessages.add(greetingMessage)
                                            Toast.makeText(context, "Cleared O.D.I.N. history for this conversation", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete All Saved O.D.I.N. History") },
                                onClick = {
                                    showClearHistoryMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        localDb.clearAllOdinChatHistory()
                                        withContext(Dispatchers.Main) {
                                            chatMessages.clear()
                                            chatMessages.add(greetingMessage)
                                            Toast.makeText(context, "Deleted all saved O.D.I.N. chat history", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
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
                        AiProviderType.ON_DEVICE_NANO -> "⚡ On-Device AI Mode: 100% Private, hardware-accelerated & instant, zero data leaves this device"
                        AiProviderType.OLLAMA -> "🔒 Local Privacy Mode: Queries stay on your private local network ($ollamaUrl)"
                        AiProviderType.OPENAI -> "☁️ OpenAI ChatGPT Mode (BYOK): Queries use your personal API key ($openaiModel)"
                        AiProviderType.GEMINI -> if (geminiKey.isBlank() && googleAccountEmail.isNotBlank()) {
                            "✅ Google Account Linked ($googleAccountEmail): 1-Tap Cloud AI Active (No API Key Needed)"
                        } else if (geminiKey.isBlank()) {
                            "☁️ 1-Tap Cloud AI Active: Ready to answer (Sign in with Google or add optional BYOK key in Settings)"
                        } else {
                            "☁️ Google Gemini Mode (BYOK): Queries use your personal API key ($geminiModel)"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (isLoadingMessages && effectiveMessages.isEmpty()) {
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
                        targetAddress = threads.firstOrNull { it.displayName == targetContactName || it.address == targetContactName }?.address,
                        isSpeaking = (speakingMessageId == msg.id),
                        onSpeakToggle = {
                            if (speakingMessageId == msg.id) {
                                tts?.stop()
                                speakingMessageId = null
                            } else {
                                tts?.stop()
                                speakingMessageId = msg.id
                                tts?.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, msg.id)
                            }
                        },
                        onOpenMedia = { items, index ->
                            activeLightboxMedia = Pair(items, index)
                        }
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

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to O.D.I.N. Allfather...")
                                }
                                speechRecognizerLauncher.launch(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak to Odin",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

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
                prefs.setAiProvider("OLLAMA")
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
            messages = effectiveMessages,
            contactName = targetContactName,
            onDismiss = { showRadarDialog = false }
        )
    }

    // 100% Transparency Rune Spend Confirmation Dialogs
    if (showPromptSpendConfirm && pendingPromptText != null) {
        RuneSpendConfirmationDialog(
            runeName = "Othala (Forensic Intelligence)",
            runeDrawableId = R.drawable.ic_rune_othala,
            runeSymbol = "ᛟ",
            costAmount = 1,
            currentBalance = aiCredits,
            actionDescription = "Consult O.D.I.N. to search and synthesize your conversation archive for forgotten details.",
            prefs = prefs,
            onConfirm = {
                val promptToExecute = pendingPromptText ?: ""
                showPromptSpendConfirm = false
                pendingPromptText = null
                executeSendPrompt(promptToExecute)
            },
            onDismiss = {
                showPromptSpendConfirm = false
                pendingPromptText = null
            }
        )
    }

    if (showRadarSpendConfirm) {
        RuneSpendConfirmationDialog(
            runeName = "Othala (Commitment Radar)",
            runeDrawableId = R.drawable.ic_rune_othala,
            runeSymbol = "ᛟ",
            costAmount = 1,
            currentBalance = aiCredits,
            actionDescription = "Scan conversation archive for unfulfilled commitments, promised dates, and obligations.",
            prefs = prefs,
            onConfirm = {
                showRadarSpendConfirm = false
                prefs.consumeAiCredit()
                showRadarDialog = true
            },
            onDismiss = {
                showRadarSpendConfirm = false
            }
        )
    }

    activeLightboxMedia?.let { (mediaList, idx) ->
        MediaLightboxDialog(
            items = mediaList,
            initialIndex = idx,
            onDismiss = { activeLightboxMedia = null }
        )
    }
}

@Composable
private fun ChatBubble(
    message: OdinChatMessage,
    targetContactName: String = "",
    targetAddress: String? = null,
    isSpeaking: Boolean = false,
    onSpeakToggle: (() -> Unit)? = null,
    onOpenMedia: (List<VaultMediaItem>, Int) -> Unit = { _, _ -> }
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

    val isOdinResponse = !isUser && !message.isError
    val containsDraftOrReply = isOdinResponse && message.text.length > 5 &&
        !message.text.startsWith("Greetings!") &&
        !message.text.startsWith("Hail, traveler.")

    val optionRegex = remember {
        Regex("""(?:\*\*|###\s*)?Option\s*(\d+)[^:\n]*:?\s*(?:\*\*)?\s*\n?\s*"?([^"]+?)"?(?=\n\s*(?:\*\*|###\s*)?Option\s*\d+|\s*\Z)""", RegexOption.DOT_MATCHES_ALL)
    }
    val parsedOptions = remember(message.text) {
        if (isOdinResponse && (message.text.contains("Option 1") || message.text.contains("Option 2"))) {
            val matches = optionRegex.findAll(message.text).map { matchResult ->
                val optNum = matchResult.groupValues[1]
                val optContent = matchResult.groupValues[2].trim().removeSurrounding("\"").trim()
                Pair(optNum, optContent)
            }.filter { it.second.isNotBlank() }.toList()
            if (matches.size >= 2) matches else emptyList()
        } else {
            emptyList()
        }
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
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_odin_allfather),
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

                        if (!message.isError && onSpeakToggle != null) {
                            IconButton(
                                onClick = onSpeakToggle,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop Speaking" else "Speak Aloud",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Interactive Media Attachment Carousel for search results / attachments
                if (message.mediaItems.isNotEmpty()) {
                    Text(
                        text = "📎 Matched Photos & Media (${message.mediaItems.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        items(message.mediaItems.size) { idx ->
                            val item = message.mediaItems[idx]
                            var bitmap by remember(item.uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
                            LaunchedEffect(item.uriString) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.openInputStream(Uri.parse(item.uriString))?.use { stream ->
                                            val options = BitmapFactory.Options().apply {
                                                inSampleSize = 2 // Optimized thumbnail downsampling for 60fps smooth scrolling
                                            }
                                            bitmap = BitmapFactory.decodeStream(stream, null, options)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .size(width = 110.dp, height = 110.dp)
                                    .clickable { onOpenMedia(message.mediaItems, idx) }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val loadedBitmap = bitmap
                                    if (loadedBitmap != null) {
                                        Image(
                                            bitmap = loadedBitmap.asImageBitmap(),
                                            contentDescription = item.fileName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(topStart = 8.dp),
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    ) {
                                        Text(
                                            text = "🔍 View",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Render Message Text with Clickable Web Links
                ClickableMessageText(
                    text = message.text,
                    textColor = textColor
                )

                // If multiple reply options are found, render per-option action cards
                if (parsedOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚡ Select Reply Option:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        parsedOptions.forEach { (num, optionText) ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Option $num",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"$optionText\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Copy Just This Option
                                        AssistChip(
                                            onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("ODIN Option $num", optionText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Copied Option $num to clipboard! 📋", Toast.LENGTH_SHORT).show()
                                            },
                                            label = { Text("Copy Option $num", style = MaterialTheme.typography.labelSmall) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )

                                        // Send Just This Option
                                        AssistChip(
                                            onClick = {
                                                try {
                                                    val sendIntent = if (!targetAddress.isNullOrBlank()) {
                                                        android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                            data = Uri.parse("smsto:$targetAddress")
                                                            putExtra("sms_body", optionText)
                                                        }
                                                    } else {
                                                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(android.content.Intent.EXTRA_TEXT, optionText)
                                                        }
                                                    }
                                                    context.startActivity(sendIntent)
                                                } catch (ex: Exception) {
                                                    Toast.makeText(context, "Could not open messaging app: ${ex.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            label = {
                                                Text(
                                                    if (!targetAddress.isNullOrBlank()) "Send Option $num" else "SMS Option $num",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Sms,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (containsDraftOrReply) {
                    val scope = rememberCoroutineScope()
                    val localDb = remember { LocalMessageDatabase.getInstance(context) }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Copy All / Full text to Clipboard chip
                        AssistChip(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("ODIN Full Reply", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Copy All", style = MaterialTheme.typography.labelSmall) },
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

                        // Pin to Starred Vault Memories chip
                        AssistChip(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    localDb.addStarredMemory(
                                        messageId = message.timestamp,
                                        threadId = 0L,
                                        contactName = if (targetContactName.isBlank()) "O.D.I.N. Insight" else "O.D.I.N. ($targetContactName)",
                                        sender = "O.D.I.N.",
                                        body = message.text,
                                        timestamp = message.timestamp,
                                        sourceType = "ODIN"
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "⭐ Pinned to Vault Memories!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            label = { Text("Pin", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Pin to Vault",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                        )

                        // Open in Messages chip (full text)
                        AssistChip(
                            onClick = {
                                try {
                                    val sendIntent = if (!targetAddress.isNullOrBlank()) {
                                        android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("smsto:$targetAddress")
                                            putExtra("sms_body", message.text)
                                        }
                                    } else {
                                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, message.text)
                                        }
                                    }
                                    context.startActivity(sendIntent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Could not open messaging app: ${ex.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = {
                                Text(
                                    if (!targetAddress.isNullOrBlank()) "Send All" else "SMS All",
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

@Composable
private fun ClickableMessageText(
    text: String,
    textColor: androidx.compose.ui.graphics.Color
) {
    val context = LocalContext.current
    val urlPattern = remember {
        Regex("https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?")
    }

    val matches = remember(text) { urlPattern.findAll(text).toList() }

    if (matches.isEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    } else {
        val primaryColor = MaterialTheme.colorScheme.primary
        val bodyStyle = MaterialTheme.typography.bodyMedium
        val annotatedString = remember(text, primaryColor) {
            buildAnnotatedString {
                var lastIdx = 0
                for (match in matches) {
                    val start = match.range.first
                    val end = match.range.last + 1
                    if (start > lastIdx) {
                        append(text.substring(lastIdx, start))
                    }
                    val url = match.value
                    val linkStart = length
                    append(url)
                    val linkEnd = length
                    addStyle(
                        style = SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = linkStart,
                        end = linkEnd
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = url,
                        start = linkStart,
                        end = linkEnd
                    )
                    lastIdx = end
                }
                if (lastIdx < text.length) {
                    append(text.substring(lastIdx))
                }
            }
        }

        ClickableText(
            text = annotatedString,
            style = bodyStyle.copy(color = textColor),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "Could not open link: ${ex.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        )
    }
}

