package com.messagebackup.app.domain.mesh

import android.content.Context
import android.util.Log
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.data.local.RavenChatMessage
import com.messagebackup.app.data.local.RavenDatabaseHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Global 4G/5G Cellular and Internet P2P Transport Relay.
 * Connects Raven Mesh nodes anywhere on Earth across cellular carriers, states, and firewalls.
 *
 * Packets are always end-to-end encrypted (AES-256-GCM) with hardware EC P-256 keys.
 * Supports:
 * - Instant real-time WebSocket push (<50ms) + delta HTTP polling (since=<last_ts>).
 * - Zero startup chirp: tracks processed message IDs persistently and never auto-plays stale voice notes.
 * - Live Walkie-Talkie 450ms audio chunk streaming (`PTT_STREAM_CHUNK`).
 * - Encrypted Raven Parcels (Photos & Files), Loki's Mist ephemeral timers, Location Pins, and Shared SMS Excerpts.
 */
class RavenGlobalRelayTransport(
    private val context: Context,
    parentScope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "RavenGlobalRelay"
        private const val RELAY_HTTP_BASE = "https://ntfy.sh"
        private const val RELAY_WS_BASE = "wss://ntfy.sh"
        private const val TOPIC_PREFIX = "muninn_raven_"

        /**
         * Sanitizes a Raven Call-sign (e.g. "raven://Odin-7A3F" or "Odin-7A3F")
         * into a valid, unique pub/sub channel topic (e.g. "muninn_raven_odin_7a3f").
         */
        fun getTopicForCallsign(callsign: String): String {
            val clean = callsign.removePrefix("raven://")
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "_")
            return "$TOPIC_PREFIX$clean"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cryptoEngine = RavenCryptoEngine(context)
    private val dbHelper = RavenDatabaseHelper(context)
    private val voicePttEngine = RavenVoicePttEngine.getInstance(context)

    private val _incomingMessages = MutableSharedFlow<RavenIncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<RavenIncomingMessage> = _incomingMessages.asSharedFlow()

    private val _incomingVoiceStreams = MutableSharedFlow<RavenIncomingVoice>(extraBufferCapacity = 32)
    val incomingVoiceStreams: SharedFlow<RavenIncomingVoice> = _incomingVoiceStreams.asSharedFlow()

    private val _peerLinkedEvents = MutableSharedFlow<RavenPeer>(extraBufferCapacity = 16)
    val peerLinkedEvents: SharedFlow<RavenPeer> = _peerLinkedEvents.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val pollClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var activeWebSocket: WebSocket? = null
    @Volatile
    private var isConnected = false
    @Volatile
    private var isConnecting = false

    private var reconnectJob: Job? = null
    private var pollingJob: Job? = null

    private val processedMessageIds = LinkedHashSet<String>()
    private val streamedAudioSessions = HashSet<Long>()

    init {
        try {
            val savedIds = MessageBackupApp.instance.appPreferences.getProcessedRelayIds()
            synchronized(processedMessageIds) {
                processedMessageIds.addAll(savedIds)
            }
        } catch (_: Exception) {}
    }

    private fun isRavenMeshEnabled(): Boolean {
        return try {
            MessageBackupApp.instance.appPreferences.ravenMeshEnabled.value
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Starts the global relay transport: WebSocket real-time stream + adaptive HTTP polling fallback.
     */
    @Synchronized
    fun startGlobalRelay() {
        if (!isRavenMeshEnabled()) return
        startWebSocket()
        startPollingLoop()
    }

    fun stopGlobalRelay() {
        reconnectJob?.cancel()
        pollingJob?.cancel()
        try {
            activeWebSocket?.close(1000, "Raven Mesh disabled or closed")
        } catch (_: Exception) {}
        activeWebSocket = null
        isConnected = false
        isConnecting = false
    }

    /**
     * Immediately fetches pending messages via HTTP, useful on app resume or user refresh.
     */
    fun pollImmediately() {
        if (!isRavenMeshEnabled()) return
        scope.launch(Dispatchers.IO) {
            pollPendingRelayMessages()
        }
    }

    private fun startWebSocket() {
        if (!isRavenMeshEnabled() || isConnected || isConnecting) return
        isConnecting = true

        val sinceSec = try {
            MessageBackupApp.instance.appPreferences.getLastRelayPollTimestampSeconds()
        } catch (_: Exception) {
            (System.currentTimeMillis() / 1000L) - 300L
        }

        val myTopic = getTopicForCallsign(cryptoEngine.getRavenCallSign())
        val wsUrl = "$RELAY_WS_BASE/$myTopic/ws?since=$sinceSec"
        Log.i(TAG, "Starting Global Cellular Relay WebSocket at $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isConnecting = false
                Log.i(TAG, "Global Cellular Relay WebSocket connected for topic: $myTopic")
                pollImmediately()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.IO) {
                    handleIncomingNtfyFrame(text)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                isConnecting = false
                activeWebSocket = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                isConnecting = false
                activeWebSocket = null
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!isRavenMeshEnabled() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(3_000)
            startWebSocket()
        }
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (!isRavenMeshEnabled()) {
                    delay(10_000)
                    continue
                }
                try {
                    pollPendingRelayMessages()
                } catch (e: Exception) {
                    Log.d(TAG, "Polling check: ${e.message}")
                }
                // Adaptive interval: 18 seconds when WebSocket is connected; 3 seconds when WebSocket is reconnecting
                val waitMs = if (isConnected) 18_000L else 3_000L
                delay(waitMs)
            }
        }
    }

    /**
     * Polls the ntfy JSON stream for any messages delivered since our last processed timestamp.
     */
    private suspend fun pollPendingRelayMessages() = withContext(Dispatchers.IO) {
        if (!isRavenMeshEnabled()) return@withContext
        try {
            val sinceSec = try {
                (MessageBackupApp.instance.appPreferences.getLastRelayPollTimestampSeconds() - 15L).coerceAtLeast(0L)
            } catch (_: Exception) {
                (System.currentTimeMillis() / 1000L) - 300L
            }

            val myTopic = getTopicForCallsign(cryptoEngine.getRavenCallSign())
            val url = "$RELAY_HTTP_BASE/$myTopic/json?poll=1&since=$sinceSec"

            val request = Request.Builder().url(url).build()
            pollClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string() ?: return@use
                body.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank()) {
                        handleIncomingNtfyFrame(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "pollPendingRelayMessages error: ${e.message}")
        }
    }

    /**
     * Parses an incoming ntfy envelope, handling both inline messages and larger attachment payloads.
     */
    private suspend fun handleIncomingNtfyFrame(frameJson: String) = withContext(Dispatchers.IO) {
        try {
            val eventObj = JSONObject(frameJson)
            val eventType = eventObj.optString("event")
            if (eventType != "message") return@withContext

            val eventTimeSec = eventObj.optLong("time", 0L)
            if (eventTimeSec > 0L) {
                try {
                    MessageBackupApp.instance.appPreferences.setLastRelayPollTimestampSeconds(eventTimeSec)
                } catch (_: Exception) {}
            }

            val messageId = eventObj.optString("id")
            if (messageId.isNotBlank()) {
                synchronized(processedMessageIds) {
                    if (processedMessageIds.contains(messageId)) return@withContext
                    if (processedMessageIds.size > 500) {
                        val oldest = processedMessageIds.first()
                        processedMessageIds.remove(oldest)
                    }
                    processedMessageIds.add(messageId)
                }
                try {
                    MessageBackupApp.instance.appPreferences.addProcessedRelayId(messageId)
                } catch (_: Exception) {}
            }

            var rawRavenPayload: String? = null

            // Check if payload arrived as an attachment (for larger payloads like voice notes or image/file parcels)
            if (eventObj.has("attachment")) {
                val attObj = eventObj.getJSONObject("attachment")
                val downloadUrl = attObj.optString("url")
                if (downloadUrl.isNotBlank()) {
                    try {
                        val downloadReq = Request.Builder().url(downloadUrl).build()
                        pollClient.newCall(downloadReq).execute().use { downloadResp ->
                            rawRavenPayload = downloadResp.body?.string()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download attachment payload: ${e.message}")
                    }
                }
            }

            val candidate = if (rawRavenPayload.isNullOrBlank()) {
                eventObj.optString("message")
            } else {
                rawRavenPayload
            }

            if (!candidate.isNullOrBlank()) {
                processIncomingRelayPayload(candidate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming ntfy frame: ${e.message}")
        }
    }

    /**
     * Processes an incoming raw Raven JSON packet received from the cellular relay.
     */
    private suspend fun processIncomingRelayPayload(rawJson: String) = withContext(Dispatchers.IO) {
        try {
            val packet = JSONObject(rawJson)
            val protocol = packet.optString("protocol")
            if (protocol != "RAVEN_MESH_v1") return@withContext

            val type = packet.optString("type", "ENVELOPE")
            val senderCallSign = packet.getString("sender").trim()

            // Don't process our own broadcast packets if reflected
            val myCallSign = cryptoEngine.getRavenCallSign().trim()
            if (senderCallSign.removePrefix("raven://").equals(myCallSign.removePrefix("raven://"), ignoreCase = true)) {
                return@withContext
            }

            if (type == "MUTUAL_HANDSHAKE") {
                val displayName = packet.optString("displayName", senderCallSign)
                val publicKeyBase64 = packet.getString("publicKey")
                val signature = packet.getString("sig")
                val timestamp = packet.getLong("ts")

                val peerPubKey = cryptoEngine.decodePublicKey(publicKeyBase64)
                val claim = "MUTUAL_HANDSHAKE|$senderCallSign|$publicKeyBase64|$timestamp"
                val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)
                if (!isValid) return@withContext

                val existingPeer = dbHelper.getAllPeers().find {
                    it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
                }
                val currentStatus = if (existingPeer != null && existingPeer.status == RavenPeerStatus.CONNECTED) {
                    RavenPeerStatus.CONNECTED
                } else {
                    RavenPeerStatus.PENDING_ACCEPTANCE
                }

                val mutualPeer = RavenPeer(
                    peerId = RavenDatabaseHelper.normalizePeerId(senderCallSign),
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true,
                    status = currentStatus
                )
                dbHelper.upsertPeer(mutualPeer)
                _peerLinkedEvents.emit(mutualPeer)

                if (existingPeer?.status != RavenPeerStatus.CONNECTED) {
                    val ackTs = System.currentTimeMillis()
                    val myPubKey = cryptoEngine.getPublicKeyBase64()
                    val myName = myCallSign.removePrefix("raven://")
                    val ackClaim = "MUTUAL_ACK|$myCallSign|$myPubKey|$ackTs"
                    val ackSig = cryptoEngine.signData(ackClaim.toByteArray(Charsets.UTF_8))

                    val ackPacket = JSONObject().apply {
                        put("protocol", "RAVEN_MESH_v1")
                        put("type", "MUTUAL_ACK")
                        put("sender", myCallSign)
                        put("recipient", senderCallSign)
                        put("displayName", myName)
                        put("publicKey", myPubKey)
                        put("sig", ackSig)
                        put("ts", ackTs)
                    }
                    sendOverRelay(senderCallSign, ackPacket.toString())
                }
                return@withContext
            }

            if (type == "MUTUAL_ACK") {
                val displayName = packet.optString("displayName", senderCallSign)
                val publicKeyBase64 = packet.getString("publicKey")
                val signature = packet.getString("sig")
                val timestamp = packet.getLong("ts")

                val peerPubKey = cryptoEngine.decodePublicKey(publicKeyBase64)
                val claim = "MUTUAL_ACK|$senderCallSign|$publicKeyBase64|$timestamp"
                val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)
                if (!isValid) return@withContext

                val existing = dbHelper.getAllPeers().find {
                    it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
                }

                val updated = (existing ?: RavenPeer(
                    peerId = RavenDatabaseHelper.normalizePeerId(senderCallSign),
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true
                )).copy(
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    status = RavenPeerStatus.CONNECTED,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis()
                )

                dbHelper.upsertPeer(updated)
                _peerLinkedEvents.emit(updated)
                return@withContext
            }

            if (type == "PEER_ACCEPT_CONFIRMATION") {
                val existing = dbHelper.getAllPeers().find {
                    it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
                }
                if (existing != null) {
                    val updated = existing.copy(
                        status = RavenPeerStatus.CONNECTED,
                        lastKnownIp = "CELLULAR_RELAY",
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    dbHelper.upsertPeer(updated)
                    _peerLinkedEvents.emit(updated)
                }
                return@withContext
            }

            // Normal encrypted envelope (whisper text, live walkie-talkie chunk, voice note, parcel, location, SMS excerpt)
            val encryptedEnvelope = packet.getString("envelope")
            val signature = packet.getString("sig")
            val timestamp = packet.getLong("ts")

            val peers = dbHelper.getAllPeers()
            val senderPeer = peers.find {
                it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
            }
            if (senderPeer == null || !senderPeer.isTrusted) {
                return@withContext
            }

            val peerPubKey = cryptoEngine.decodePublicKey(senderPeer.publicKey)
            val claim = "$senderCallSign|$encryptedEnvelope|$timestamp"
            val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)
            if (!isValid) return@withContext

            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPubKey)
            val decryptedJson = RavenPacketCipher.decryptPayload(encryptedEnvelope, sharedSecret) ?: return@withContext

            val innerMsg = JSONObject(decryptedJson)
            val packetType = innerMsg.optString("type", "TEXT")
            val ttlSeconds = innerMsg.optInt("ttl_sec", 0)
            val burnOnRead = innerMsg.optBoolean("burn_on_read", false)
            val expiresAt = if (ttlSeconds > 0) System.currentTimeMillis() + (ttlSeconds * 1000L) else 0L

            // Always update peer to CONNECTED upon receiving authenticated message
            dbHelper.upsertPeer(
                senderPeer.copy(
                    status = RavenPeerStatus.CONNECTED,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    lastKnownIp = "CELLULAR_RELAY"
                )
            )

            // Handle Live Sub-Second Walkie-Talkie Audio Stream Chunk
            if (packetType == "PTT_STREAM_CHUNK") {
                val isFresh = kotlin.math.abs(System.currentTimeMillis() - timestamp) < 15_000L
                val isAutoPlayEnabled = try {
                    MessageBackupApp.instance.appPreferences.ravenPttAutoPlay.value
                } catch (_: Exception) {
                    true
                }
                if (isFresh && isAutoPlayEnabled) {
                    val chunkB64 = innerMsg.optString("chunk_b64", "")
                    val sessionId = innerMsg.optLong("session_id", 0L)
                    if (chunkB64.isNotBlank()) {
                        synchronized(streamedAudioSessions) {
                            if (streamedAudioSessions.size > 100) streamedAudioSessions.clear()
                            streamedAudioSessions.add(sessionId)
                        }
                        voicePttEngine.playLiveStreamChunk(chunkB64)
                    }
                }
                return@withContext
            }

            if (packetType == "PTT_VOICE") {
                val audioBase64 = innerMsg.getString("audio_base64")
                val durationMs = innerMsg.optLong("duration_ms", 0L)
                val sessionId = innerMsg.optLong("session_id", 0L)
                val transcription = innerMsg.optString("transcription", "").ifBlank { null }
                val durationSec = String.format(java.util.Locale.US, "%.1fs", durationMs / 1000f)

                val chatMsg = RavenChatMessage(
                    peerId = senderPeer.peerId,
                    senderCallsign = senderCallSign,
                    senderDisplayName = senderPeer.displayName,
                    isOutbound = false,
                    messageText = "🎙️ Voice Note ($durationSec)",
                    messageType = "VOICE",
                    timestamp = timestamp,
                    audioBase64 = audioBase64,
                    transcription = transcription,
                    expiresAtTimestamp = expiresAt,
                    burnOnRead = burnOnRead
                )
                dbHelper.insertMessage(chatMsg)
                dbHelper.incrementUnreadCount(senderPeer.peerId)

                _incomingVoiceStreams.emit(
                    RavenIncomingVoice(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        audioBase64 = audioBase64,
                        durationMs = durationMs,
                        timestamp = timestamp
                    )
                )

                // Only auto-play if packet is fresh (<20s old), Walkie-Talkie Auto-Play is ON,
                // and it wasn't already played live via PTT_STREAM_CHUNK!
                val isFresh = kotlin.math.abs(System.currentTimeMillis() - timestamp) < 20_000L
                val isAutoPlay = try {
                    MessageBackupApp.instance.appPreferences.ravenPttAutoPlay.value
                } catch (_: Exception) {
                    true
                }
                val alreadyStreamed = sessionId != 0L && synchronized(streamedAudioSessions) {
                    streamedAudioSessions.contains(sessionId)
                }

                if (isFresh && isAutoPlay && !alreadyStreamed) {
                    scope.launch(Dispatchers.IO) {
                        voicePttEngine.playInboundVoice(audioBase64)
                    }
                }
            } else {
                val messageText = innerMsg.optString("text", "")
                val attB64 = innerMsg.optString("attachment_base64", "").ifBlank { null }
                val attName = innerMsg.optString("attachment_name", "").ifBlank { null }
                val attMime = innerMsg.optString("attachment_mime", "").ifBlank { null }

                val chatMsg = RavenChatMessage(
                    peerId = senderPeer.peerId,
                    senderCallsign = senderCallSign,
                    senderDisplayName = senderPeer.displayName,
                    isOutbound = false,
                    messageText = messageText,
                    messageType = packetType,
                    timestamp = timestamp,
                    attachmentBase64 = attB64,
                    attachmentName = attName,
                    attachmentMimeType = attMime,
                    expiresAtTimestamp = expiresAt,
                    burnOnRead = burnOnRead
                )
                dbHelper.insertMessage(chatMsg)
                dbHelper.incrementUnreadCount(senderPeer.peerId)

                _incomingMessages.emit(
                    RavenIncomingMessage(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        messageText = messageText,
                        timestamp = timestamp
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processIncomingRelayPayload: ${e.message}")
        }
    }

    /**
     * Dispatches an encrypted packet across the global cellular relay to a remote peer.
     */
    suspend fun sendPacketToRemotePeer(peer: RavenPeer, innerPayload: String): RavenSendResult = withContext(Dispatchers.IO) {
        try {
            val peerPubKey = cryptoEngine.decodePublicKey(peer.publicKey)
            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPubKey)

            val envelope = RavenPacketCipher.encryptPayload(innerPayload, sharedSecret)
            val timestamp = System.currentTimeMillis()
            val myCallSign = cryptoEngine.getRavenCallSign()

            val claim = "$myCallSign|$envelope|$timestamp"
            val signature = cryptoEngine.signData(claim.toByteArray(Charsets.UTF_8))

            val packet = JSONObject().apply {
                put("protocol", "RAVEN_MESH_v1")
                put("sender", myCallSign)
                put("recipient", peer.peerId)
                put("envelope", envelope)
                put("ts", timestamp)
                put("sig", signature)
            }

            val sent = sendOverRelay(peer.peerId, packet.toString())
            if (sent) {
                RavenSendResult.Success
            } else {
                RavenSendResult.Error("Relay delivery failed. Check internet/cellular connection.")
            }
        } catch (e: Exception) {
            RavenSendResult.Error("Cellular delivery failed: ${e.message}")
        }
    }

    /**
     * Sends a mutual handshake packet over cellular relay when adding a friend's code out of state.
     */
    suspend fun sendCellularMutualHandshake(peerCallsign: String, peerPublicKeyBase64: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myPubKey = cryptoEngine.getPublicKeyBase64()
            val myName = myCallSign.removePrefix("raven://")
            val timestamp = System.currentTimeMillis()

            val claim = "MUTUAL_HANDSHAKE|$myCallSign|$myPubKey|$timestamp"
            val signature = cryptoEngine.signData(claim.toByteArray(Charsets.UTF_8))

            val packet = JSONObject().apply {
                put("protocol", "RAVEN_MESH_v1")
                put("type", "MUTUAL_HANDSHAKE")
                put("sender", myCallSign)
                put("recipient", peerCallsign)
                put("displayName", myName)
                put("publicKey", myPubKey)
                put("sig", signature)
                put("ts", timestamp)
            }

            sendOverRelay(peerCallsign, packet.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendCellularMutualHandshake error: ${e.message}")
            false
        }
    }

    /**
     * Dispatches an acceptance confirmation packet across cellular relay.
     */
    suspend fun sendCellularAcceptanceConfirmation(peer: RavenPeer): Boolean = withContext(Dispatchers.IO) {
        try {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val packet = JSONObject().apply {
                put("protocol", "RAVEN_MESH_v1")
                put("type", "PEER_ACCEPT_CONFIRMATION")
                put("sender", myCallSign)
                put("recipient", peer.peerId)
                put("ts", System.currentTimeMillis())
            }
            sendOverRelay(peer.peerId, packet.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendCellularAcceptanceConfirmation error: ${e.message}")
            false
        }
    }

    /**
     * High-speed, atomic HTTP POST/PUT dispatch to peer's global relay inbox.
     * Supports both compact inline JSON packets (<3.8KB) and larger attachment packets (Voice Notes, Encrypted Images & Files).
     */
    private fun sendOverRelay(targetPeerId: String, payload: String): Boolean {
        val targetTopic = getTopicForCallsign(targetPeerId)
        val url = "$RELAY_HTTP_BASE/$targetTopic"

        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Title", "MUNINN")
            .header("Priority", "high")

        // If payload is larger than 3,800 bytes (e.g. Voice Note or Encrypted Photo Parcel),
        // add Filename header so ntfy stores it cleanly as an instant binary attachment without truncating!
        if (payloadBytes.size > 3800) {
            requestBuilder.header("Filename", "raven_parcel_${System.currentTimeMillis()}.json")
        }

        val body = payloadBytes.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = requestBuilder.post(body).build()

        for (attempt in 1..3) {
            try {
                pollClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return true
                    }
                    if (response.code == 429) {
                        Thread.sleep(500)
                    }
                }
            } catch (e: Exception) {
                try { Thread.sleep(250) } catch (_: Exception) {}
            }
        }
        return false
    }
}
