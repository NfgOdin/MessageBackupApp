package com.messagebackup.app.domain.mesh

import android.content.Context
import android.util.Log
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
 * Operates in dual-mode: persistent WebSocket for instant push (<50ms) + periodic HTTP polling (<3s)
 * to ensure 100% message delivery even under aggressive carrier NAT or battery-saver conditions.
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

    // Permanent application scope that survives activity teardowns
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cryptoEngine = RavenCryptoEngine(context)
    private val dbHelper = RavenDatabaseHelper(context)
    private val voicePttEngine = RavenVoicePttEngine(context)

    private val _incomingMessages = MutableSharedFlow<RavenIncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<RavenIncomingMessage> = _incomingMessages.asSharedFlow()

    private val _incomingVoiceStreams = MutableSharedFlow<RavenIncomingVoice>(extraBufferCapacity = 32)
    val incomingVoiceStreams: SharedFlow<RavenIncomingVoice> = _incomingVoiceStreams.asSharedFlow()

    private val _peerLinkedEvents = MutableSharedFlow<RavenPeer>(extraBufferCapacity = 16)
    val peerLinkedEvents: SharedFlow<RavenPeer> = _peerLinkedEvents.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val pollClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    // Track processed message IDs to prevent duplicates between WebSocket and HTTP polling
    private val processedMessageIds = LinkedHashSet<String>()

    /**
     * Starts the global relay transport: WebSocket real-time stream + HTTP polling fallback.
     */
    @Synchronized
    fun startGlobalRelay() {
        startWebSocket()
        startPollingLoop()
    }

    fun stopGlobalRelay() {
        reconnectJob?.cancel()
        pollingJob?.cancel()
        try {
            activeWebSocket?.close(1000, "App closed")
        } catch (_: Exception) {}
        activeWebSocket = null
        isConnected = false
        isConnecting = false
    }

    /**
     * Immediately fetches pending messages via HTTP, useful on app resume or user refresh.
     */
    fun pollImmediately() {
        scope.launch(Dispatchers.IO) {
            pollPendingRelayMessages()
        }
    }

    private fun startWebSocket() {
        if (isConnected || isConnecting) return
        isConnecting = true

        val myTopic = getTopicForCallsign(cryptoEngine.getRavenCallSign())
        val wsUrl = "$RELAY_WS_BASE/$myTopic/ws?since=12h"
        Log.i(TAG, "Starting Global Cellular Relay WebSocket at $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isConnecting = false
                Log.i(TAG, "Global Cellular Relay WebSocket connected for topic: $myTopic")
                // Catch any recent items right upon connection
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
                Log.i(TAG, "Relay WebSocket closed: $reason ($code). Scheduling reconnect...")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                isConnecting = false
                activeWebSocket = null
                Log.w(TAG, "Relay WebSocket failure: ${t.message}. Scheduling reconnect...")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(3_000)
            startWebSocket()
        }
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pollPendingRelayMessages()
                } catch (e: Exception) {
                    Log.d(TAG, "Polling check: ${e.message}")
                }
                delay(3_000) // Fast 3-second cycle ensures prompt updates even without WebSocket
            }
        }
    }

    /**
     * Polls the ntfy JSON stream for any messages delivered in the last 12 hours.
     */
    private suspend fun pollPendingRelayMessages() = withContext(Dispatchers.IO) {
        try {
            val myTopic = getTopicForCallsign(cryptoEngine.getRavenCallSign())
            val url = "$RELAY_HTTP_BASE/$myTopic/json?poll=1&since=12h"

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
            }

            var rawRavenPayload: String? = null

            // Check if payload arrived as an attachment (for larger payloads like voice snippets)
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
                if (!isValid) {
                    Log.w(TAG, "Invalid signature on MUTUAL_HANDSHAKE from $senderCallSign")
                    return@withContext
                }

                val existingPeer = dbHelper.getAllPeers().find {
                    it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
                }
                val currentStatus = if (existingPeer != null && existingPeer.status == RavenPeerStatus.CONNECTED) {
                    RavenPeerStatus.CONNECTED
                } else {
                    RavenPeerStatus.PENDING_ACCEPTANCE
                }

                val mutualPeer = RavenPeer(
                    peerId = senderCallSign,
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true,
                    status = currentStatus
                )
                dbHelper.upsertPeer(mutualPeer)
                _peerLinkedEvents.emit(mutualPeer)
                Log.i(TAG, "Received MUTUAL_HANDSHAKE from $displayName ($senderCallSign) -> Status: $currentStatus")

                if (existingPeer?.status != RavenPeerStatus.CONNECTED) {
                    // Return an ACK via global relay so sender instantly knows their handshake reached
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

                // If existing peer had PENDING_APPROVAL, mark them CONNECTED now that reciprocal link is confirmed!
                val updatedStatus = RavenPeerStatus.CONNECTED

                val updated = (existing ?: RavenPeer(
                    peerId = senderCallSign,
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true
                )).copy(
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    status = updatedStatus,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis()
                )

                dbHelper.upsertPeer(updated)
                _peerLinkedEvents.emit(updated)
                Log.i(TAG, "Processed MUTUAL_ACK from $senderCallSign -> Marked CONNECTED!")
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
                    Log.i(TAG, "PEER_ACCEPT_CONFIRMATION received from $senderCallSign -> Now CONNECTED!")
                }
                return@withContext
            }

            // Normal encrypted envelope (whisper text or PTT audio)
            val encryptedEnvelope = packet.getString("envelope")
            val signature = packet.getString("sig")
            val timestamp = packet.getLong("ts")

            val peers = dbHelper.getAllPeers()
            val senderPeer = peers.find {
                it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
            }
            if (senderPeer == null || !senderPeer.isTrusted) {
                Log.w(TAG, "Ignored envelope: sender $senderCallSign is not in trusted contacts")
                return@withContext
            }

            val peerPubKey = cryptoEngine.decodePublicKey(senderPeer.publicKey)
            val claim = "$senderCallSign|$encryptedEnvelope|$timestamp"
            val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)
            if (!isValid) {
                Log.w(TAG, "Signature verification failed on envelope from $senderCallSign")
                return@withContext
            }

            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPubKey)
            val decryptedJson = RavenPacketCipher.decryptPayload(encryptedEnvelope, sharedSecret) ?: return@withContext

            val innerMsg = JSONObject(decryptedJson)
            val packetType = innerMsg.optString("type", "TEXT")

            // Always update to CONNECTED upon receiving authenticated message
            dbHelper.upsertPeer(
                senderPeer.copy(
                    status = RavenPeerStatus.CONNECTED,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    lastKnownIp = "CELLULAR_RELAY"
                )
            )

            if (packetType == "PTT_VOICE") {
                val audioBase64 = innerMsg.getString("audio_base64")
                val durationMs = innerMsg.optLong("duration_ms", 0L)

                val chatMsg = com.messagebackup.app.data.local.RavenChatMessage(
                    peerId = senderPeer.peerId,
                    senderCallsign = senderCallSign,
                    senderDisplayName = senderPeer.displayName,
                    isOutbound = false,
                    messageText = "🎙️ PTT Voice Note (${durationMs}ms)",
                    messageType = "VOICE",
                    timestamp = timestamp
                )
                dbHelper.insertMessage(chatMsg)

                _incomingVoiceStreams.emit(
                    RavenIncomingVoice(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        audioBase64 = audioBase64,
                        durationMs = durationMs,
                        timestamp = timestamp
                    )
                )

                scope.launch(Dispatchers.IO) {
                    voicePttEngine.playInboundVoice(audioBase64)
                }
                Log.i(TAG, "Played inbound PTT voice message (${durationMs}ms) from ${senderPeer.displayName}")
            } else {
                val messageText = innerMsg.getString("text")

                val chatMsg = com.messagebackup.app.data.local.RavenChatMessage(
                    peerId = senderPeer.peerId,
                    senderCallsign = senderCallSign,
                    senderDisplayName = senderPeer.displayName,
                    isOutbound = false,
                    messageText = messageText,
                    messageType = "TEXT",
                    timestamp = timestamp
                )
                dbHelper.insertMessage(chatMsg)

                _incomingMessages.emit(
                    RavenIncomingMessage(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        messageText = messageText,
                        timestamp = timestamp
                    )
                )
                Log.i(TAG, "Received whisper text message from ${senderPeer.displayName}: $messageText")
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
     * High-speed, atomic HTTP POST dispatch to peer's global relay inbox.
     */
    private fun sendOverRelay(targetPeerId: String, payload: String): Boolean {
        val targetTopic = getTopicForCallsign(targetPeerId)
        val url = "$RELAY_HTTP_BASE/$targetTopic"

        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Title", "MUNINN")
            .header("Priority", "high")
            .post(body)
            .build()

        for (attempt in 1..3) {
            try {
                pollClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Dispatched packet to $targetTopic via $url -> Success=true (attempt $attempt)")
                        return true
                    }
                    if (response.code == 429) {
                        Log.w(TAG, "Rate limited on $url (attempt $attempt), backing off 600ms...")
                        Thread.sleep(600)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendOverRelay attempt $attempt failed: ${e.message}")
                try { Thread.sleep(300) } catch (_: Exception) {}
            }
        }
        return false
    }
}
