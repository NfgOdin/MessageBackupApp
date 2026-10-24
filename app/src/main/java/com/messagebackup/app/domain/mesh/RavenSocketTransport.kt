package com.messagebackup.app.domain.mesh

import android.content.Context
import android.os.Build
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.data.local.RavenChatMessage
import com.messagebackup.app.data.local.RavenDatabaseHelper
import com.messagebackup.app.domain.migration.DirectLocalMigrationManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Peer-to-peer transport manager for Raven Mesh.
 * Operates an embedded background TCP socket server on local Wi-Fi / Hotspot networks
 * and coordinates seamlessly with the Global Cellular / Internet Relay for zero-delay
 * cross-state communication, live PTT walkie-talkie streaming, encrypted parcels, and Loki's Mist.
 */
class RavenSocketTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        const val DEFAULT_MESH_PORT = 8744
        const val SOCKET_TIMEOUT_MS = 10_000

        @Volatile
        private var instance: RavenSocketTransport? = null

        fun getInstance(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
        ): RavenSocketTransport {
            return instance ?: synchronized(this) {
                instance ?: RavenSocketTransport(context.applicationContext, scope).also { instance = it }
            }
        }
    }

    private val cryptoEngine = RavenCryptoEngine(context)
    private val dbHelper = RavenDatabaseHelper(context)
    private val voicePttEngine = RavenVoicePttEngine.getInstance(context)
    private val globalRelay = RavenGlobalRelayTransport(context, scope)

    init {
        val enabled = try {
            MessageBackupApp.instance.appPreferences.ravenMeshEnabled.value
        } catch (_: Exception) {
            true
        }
        if (enabled) {
            globalRelay.startGlobalRelay()
        }
        scope.launch {
            globalRelay.incomingMessages.collect { _incomingMessages.emit(it) }
        }
        scope.launch {
            globalRelay.incomingVoiceStreams.collect { _incomingVoiceStreams.emit(it) }
        }
        scope.launch {
            globalRelay.peerLinkedEvents.collect { _peerLinkedEvents.emit(it) }
        }
    }

    fun setMeshEnabled(enabled: Boolean) {
        if (enabled) {
            startServer()
            globalRelay.startGlobalRelay()
        } else {
            stopServer()
            globalRelay.stopGlobalRelay()
        }
    }

    fun pollRelayImmediately() {
        globalRelay.pollImmediately()
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val streamedAudioSessions = HashSet<Long>()

    private val _incomingMessages = MutableSharedFlow<RavenIncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<RavenIncomingMessage> = _incomingMessages.asSharedFlow()

    private val _incomingVoiceStreams = MutableSharedFlow<RavenIncomingVoice>(extraBufferCapacity = 32)
    val incomingVoiceStreams: SharedFlow<RavenIncomingVoice> = _incomingVoiceStreams.asSharedFlow()

    private val _peerLinkedEvents = MutableSharedFlow<RavenPeer>(extraBufferCapacity = 16)
    val peerLinkedEvents: SharedFlow<RavenPeer> = _peerLinkedEvents.asSharedFlow()

    /**
     * Starts listening for incoming Raven Mesh connections on the local network.
     */
    @Synchronized
    fun startServer(port: Int = DEFAULT_MESH_PORT) {
        val enabled = try {
            MessageBackupApp.instance.appPreferences.ravenMeshEnabled.value
        } catch (_: Exception) {
            true
        }
        if (!enabled) return
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }

                while (isActive) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }

                    launch {
                        handleClientConnection(clientSocket)
                    }
                }
            } catch (e: Exception) {
                // Server stopped or socket bound error
            } finally {
                stopServer()
            }
        }
    }

    /**
     * Stops the background listening server.
     */
    @Synchronized
    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    /**
     * Checks if a target IP is on the same local Wi-Fi subnet as our phone right now.
     * Prevents waiting 3 seconds for a unreachable local IP when peers are across states!
     */
    private fun isReachableLocalSubnet(targetIp: String?): Boolean {
        if (targetIp.isNullOrBlank() || targetIp == "CELLULAR_RELAY") return false
        val myIp = DirectLocalMigrationManager.getLocalIpAddress() ?: return false
        val myPrefix = myIp.substringBeforeLast('.', "")
        val targetPrefix = targetIp.substringBeforeLast('.', "")
        return myPrefix.isNotEmpty() && myPrefix == targetPrefix
    }

    /**
     * Handles an incoming connection from a local Wi-Fi peer node.
     */
    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val rawLine = reader.readLine() ?: return@withContext
            val packet = JSONObject(rawLine)

            val protocol = packet.getString("protocol")
            if (protocol != "RAVEN_MESH_v1") {
                writer.println(JSONObject().apply { put("status", "ERR_INVALID_PROTOCOL") })
                return@withContext
            }

            val rootPacketType = packet.optString("type", "ENVELOPE")
            if (rootPacketType == "PEER_ACCEPT_CONFIRMATION") {
                val senderCallSign = packet.getString("sender")
                val existing = dbHelper.getAllPeers().find {
                    it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
                }
                if (existing != null) {
                    val updated = existing.copy(
                        status = RavenPeerStatus.CONNECTED,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    dbHelper.upsertPeer(updated)
                    _peerLinkedEvents.emit(updated)
                }
                writer.println(JSONObject().apply { put("status", "OK_ACCEPTED") })
                return@withContext
            }

            if (rootPacketType == "MUTUAL_HANDSHAKE") {
                val senderCallSign = packet.getString("sender")
                val displayName = packet.optString("displayName", senderCallSign)
                val publicKeyBase64 = packet.getString("publicKey")
                val senderLocalIp = packet.optString("localIp", socket.inetAddress?.hostAddress ?: "")
                val signature = packet.getString("sig")
                val timestamp = packet.getLong("ts")

                val peerPubKey = cryptoEngine.decodePublicKey(publicKeyBase64)
                val claim = "MUTUAL_HANDSHAKE|$senderCallSign|$publicKeyBase64|$timestamp"
                val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)

                if (!isValid) {
                    writer.println(JSONObject().apply { put("status", "ERR_INVALID_SIGNATURE") })
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

                val resolvedIp = if (senderLocalIp.isNotBlank()) senderLocalIp else (socket.inetAddress?.hostAddress ?: "")
                val mutualPeer = RavenPeer(
                    peerId = RavenDatabaseHelper.normalizePeerId(senderCallSign),
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = resolvedIp,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true,
                    status = currentStatus
                )
                dbHelper.upsertPeer(mutualPeer)

                val myCallSign = cryptoEngine.getRavenCallSign()
                val myPubKey = cryptoEngine.getPublicKeyBase64()
                val myName = cryptoEngine.getRavenCallSign().removePrefix("raven://")
                val myIp = DirectLocalMigrationManager.getLocalIpAddress() ?: ""
                val ackTs = System.currentTimeMillis()
                val ackClaim = "MUTUAL_ACK|$myCallSign|$myPubKey|$ackTs"
                val ackSig = cryptoEngine.signData(ackClaim.toByteArray(Charsets.UTF_8))

                writer.println(JSONObject().apply {
                    put("status", "OK_MUTUAL_LINK_ESTABLISHED")
                    put("peerId", myCallSign)
                    put("displayName", myName)
                    put("publicKey", myPubKey)
                    put("localIp", myIp)
                    put("sig", ackSig)
                    put("ts", ackTs)
                })

                _peerLinkedEvents.emit(mutualPeer)
                return@withContext
            }

            val senderCallSign = packet.getString("sender")
            val signature = packet.getString("sig")
            val encryptedEnvelope = packet.getString("envelope")
            val timestamp = packet.getLong("ts")

            val peers = dbHelper.getAllPeers()
            val senderPeer = peers.find {
                it.peerId.removePrefix("raven://").equals(senderCallSign.removePrefix("raven://"), ignoreCase = true)
            }
            if (senderPeer == null || !senderPeer.isTrusted) {
                writer.println(JSONObject().apply { put("status", "ERR_UNTRUSTED_PEER") })
                return@withContext
            }

            val peerPubKey = cryptoEngine.decodePublicKey(senderPeer.publicKey)
            val claim = "$senderCallSign|$encryptedEnvelope|$timestamp"
            val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)
            if (!isValid) {
                writer.println(JSONObject().apply { put("status", "ERR_INVALID_SIGNATURE") })
                return@withContext
            }

            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPubKey)
            val decryptedJson = RavenPacketCipher.decryptPayload(encryptedEnvelope, sharedSecret)
            if (decryptedJson == null) {
                writer.println(JSONObject().apply { put("status", "ERR_DECRYPTION_FAILED") })
                return@withContext
            }

            val innerMsg = JSONObject(decryptedJson)
            val packetType = innerMsg.optString("type", "TEXT")
            val ttlSeconds = innerMsg.optInt("ttl_sec", 0)
            val burnOnRead = innerMsg.optBoolean("burn_on_read", false)
            val expiresAt = if (ttlSeconds > 0) System.currentTimeMillis() + (ttlSeconds * 1000L) else 0L

            val clientIp = socket.inetAddress.hostAddress
            dbHelper.upsertPeer(
                senderPeer.copy(
                    status = RavenPeerStatus.CONNECTED,
                    lastKnownIp = clientIp,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )

            if (packetType == "READ_RECEIPT") {
                val upToTs = innerMsg.optLong("up_to_ts", timestamp)
                dbHelper.markOutboundMessagesAsRead(senderPeer.peerId, upToTs)
                writer.println(JSONObject().apply { put("status", "OK_ACK") })
                return@withContext
            }

            if (packetType == "MESSAGE_REACTION") {
                val targetMsgTs = innerMsg.optLong("msg_timestamp", 0L)
                val emoji = innerMsg.optString("reaction", "").ifBlank { null }
                dbHelper.updateMessageReactionByTimestamp(senderCallSign, targetMsgTs, emoji)
                writer.println(JSONObject().apply { put("status", "OK_ACK") })
                return@withContext
            }

            if (packetType == "EMERGENCY_BEACON") {
                val packetChannel = innerMsg.optInt("channel", 1)
                val gps = innerMsg.optString("gps", "")
                voicePttEngine.playGjallarhornSiren()

                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                val sosPattern = longArrayOf(0, 150, 100, 150, 100, 150, 200, 400, 150, 400, 150, 400, 200, 150, 100, 150, 100, 150)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(sosPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(sosPattern, -1)
                }

                val body = if (gps.isNotBlank()) "🚨 EMERGENCY GJALLARHORN BEACON on CH $packetChannel! Location: $gps" else "🚨 EMERGENCY GJALLARHORN BEACON on CH $packetChannel!"
                RavenNotificationHelper.getInstance(context).postIncomingMessageNotification(
                    senderCallsign = senderCallSign,
                    senderDisplayName = "🚨 S.O.S.: ${senderPeer.displayName}",
                    messageText = body
                )
                writer.println(JSONObject().apply { put("status", "OK_ACK") })
                return@withContext
            }

            if (packetType == "PTT_STREAM_CHUNK") {
                val packetChannel = innerMsg.optInt("channel", 1)
                val myChannel = try { MessageBackupApp.instance.appPreferences.ravenRadioChannel.value } catch (_: Exception) { 1 }
                val isSquelchMuted = try { MessageBackupApp.instance.appPreferences.ravenRadioSquelchMute.value } catch (_: Exception) { false }
                val isChannelMatch = (packetChannel == myChannel || packetChannel == 1)

                val isAutoPlay = try {
                    MessageBackupApp.instance.appPreferences.ravenPttAutoPlay.value
                } catch (_: Exception) {
                    true
                }
                if (isAutoPlay && isChannelMatch && !isSquelchMuted) {
                    val chunkB64 = innerMsg.optString("chunk_b64", "")
                    val sessionId = innerMsg.optLong("session_id", 0L)
                    if (sessionId != 0L) {
                        synchronized(streamedAudioSessions) {
                            if (streamedAudioSessions.size > 100) streamedAudioSessions.clear()
                            streamedAudioSessions.add(sessionId)
                        }
                    }
                    if (chunkB64.isNotBlank()) {
                        voicePttEngine.playLiveStreamChunk(chunkB64)
                    }
                }
                writer.println(JSONObject().apply { put("status", "OK_DELIVERED") })
                return@withContext
            }

            if (packetType == "PTT_VOICE") {
                val audioBase64 = innerMsg.getString("audio_base64")
                val durationMs = innerMsg.optLong("duration_ms", 0L)
                val packetChannel = innerMsg.optInt("channel", 1)
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
                    burnOnRead = burnOnRead,
                    deliveryStatus = "READ"
                )
                dbHelper.insertMessage(chatMsg)
                dbHelper.incrementUnreadCount(senderPeer.peerId)

                RavenNotificationHelper.getInstance(context).postIncomingMessageNotification(
                    senderCallsign = senderCallSign,
                    senderDisplayName = senderPeer.displayName,
                    messageText = "Voice Transmission ($durationSec)",
                    messageType = "VOICE"
                )

                _incomingVoiceStreams.emit(
                    RavenIncomingVoice(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        audioBase64 = audioBase64,
                        durationMs = durationMs,
                        timestamp = timestamp
                    )
                )

                val myChannel = try { MessageBackupApp.instance.appPreferences.ravenRadioChannel.value } catch (_: Exception) { 1 }
                val isSquelchMuted = try { MessageBackupApp.instance.appPreferences.ravenRadioSquelchMute.value } catch (_: Exception) { false }
                val isChannelMatch = (packetChannel == myChannel || packetChannel == 1)

                val isAutoPlay = try {
                    MessageBackupApp.instance.appPreferences.ravenPttAutoPlay.value
                } catch (_: Exception) {
                    true
                }
                val sessionId = innerMsg.optLong("session_id", 0L)
                val isFresh = kotlin.math.abs(System.currentTimeMillis() - timestamp) < 20_000L
                val alreadyStreamed = sessionId != 0L && synchronized(streamedAudioSessions) {
                    streamedAudioSessions.contains(sessionId)
                }
                if (isFresh && isAutoPlay && !alreadyStreamed && isChannelMatch && !isSquelchMuted) {
                    scope.launch(Dispatchers.IO) {
                        voicePttEngine.playInboundVoice(
                            audioBase64 = audioBase64,
                            senderCallsign = senderCallSign,
                            senderName = senderPeer.displayName,
                            channel = packetChannel,
                            durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
                        )
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
                    burnOnRead = burnOnRead,
                    deliveryStatus = "DELIVERED"
                )
                dbHelper.insertMessage(chatMsg)
                dbHelper.incrementUnreadCount(senderPeer.peerId)

                RavenNotificationHelper.getInstance(context).postIncomingMessageNotification(
                    senderCallsign = senderCallSign,
                    senderDisplayName = senderPeer.displayName,
                    messageText = if (!attName.isNullOrBlank()) attName else messageText,
                    messageType = packetType
                )

                _incomingMessages.emit(
                    RavenIncomingMessage(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        messageText = messageText,
                        timestamp = timestamp
                    )
                )
            }

            writer.println(JSONObject().apply {
                put("status", "OK_DELIVERED")
                put("ack_ts", System.currentTimeMillis())
            })
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Sends an encrypted Raven text or special message (e.g. LOCATION, SMS_EXCERPT) to a peer.
     */
    suspend fun sendMessageToPeer(
        peer: RavenPeer,
        text: String,
        targetIp: String? = peer.lastKnownIp,
        port: Int = DEFAULT_MESH_PORT,
        messageType: String = "TEXT"
    ): RavenSendResult = withContext(Dispatchers.IO) {
        val ttlSec = dbHelper.getEphemeralTtlForPeer(peer.peerId)
        val burnOnRead = ttlSec == -1 // -1 means Burn immediately after reading
        val effectiveTtl = if (ttlSec > 0) ttlSec else 0
        val expiresAt = if (effectiveTtl > 0) System.currentTimeMillis() + (effectiveTtl * 1000L) else 0L

        val payload = JSONObject().apply {
            put("type", messageType)
            put("text", text)
            put("ttl_sec", effectiveTtl)
            put("burn_on_read", burnOnRead)
            put("ts", System.currentTimeMillis())
        }.toString()

        val result = sendRawPayloadToPeer(peer, payload, targetIp, port)
        if (result is RavenSendResult.Success) {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myName = myCallSign.removePrefix("raven://")
            val chatMsg = RavenChatMessage(
                peerId = peer.peerId,
                senderCallsign = myCallSign,
                senderDisplayName = myName,
                isOutbound = true,
                messageText = text,
                messageType = messageType,
                timestamp = System.currentTimeMillis(),
                expiresAtTimestamp = expiresAt,
                burnOnRead = burnOnRead,
                deliveryStatus = "DELIVERED"
            )
            dbHelper.insertMessage(chatMsg)
            RavenNotificationHelper.getInstance(context).playSentTick()
        }
        result
    }

    /**
     * Sends an encrypted Raven Parcel (Image or File attachment) to a peer.
     */
    suspend fun sendParcelToPeer(
        peer: RavenPeer,
        captionText: String,
        attachmentBase64: String,
        attachmentName: String,
        attachmentMimeType: String,
        isImage: Boolean
    ): RavenSendResult = withContext(Dispatchers.IO) {
        val ttlSec = dbHelper.getEphemeralTtlForPeer(peer.peerId)
        val burnOnRead = ttlSec == -1
        val effectiveTtl = if (ttlSec > 0) ttlSec else 0
        val expiresAt = if (effectiveTtl > 0) System.currentTimeMillis() + (effectiveTtl * 1000L) else 0L
        val msgType = if (isImage) "IMAGE" else "FILE"

        val payload = JSONObject().apply {
            put("type", msgType)
            put("text", captionText)
            put("attachment_base64", attachmentBase64)
            put("attachment_name", attachmentName)
            put("attachment_mime", attachmentMimeType)
            put("ttl_sec", effectiveTtl)
            put("burn_on_read", burnOnRead)
            put("ts", System.currentTimeMillis())
        }.toString()

        val result = sendRawPayloadToPeer(peer, payload, peer.lastKnownIp, DEFAULT_MESH_PORT)
        if (result is RavenSendResult.Success) {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myName = myCallSign.removePrefix("raven://")
            val chatMsg = RavenChatMessage(
                peerId = peer.peerId,
                senderCallsign = myCallSign,
                senderDisplayName = myName,
                isOutbound = true,
                messageText = captionText,
                messageType = msgType,
                timestamp = System.currentTimeMillis(),
                attachmentBase64 = attachmentBase64,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType,
                expiresAtTimestamp = expiresAt,
                burnOnRead = burnOnRead,
                deliveryStatus = "DELIVERED"
            )
            dbHelper.insertMessage(chatMsg)
            RavenNotificationHelper.getInstance(context).playSentTick()
        }
        result
    }

    /**
     * Dispatches an authenticated Read Receipt packet to notify the peer that their message was viewed.
     */
    suspend fun sendReadReceipt(peer: RavenPeer, upToTimestamp: Long) = withContext(Dispatchers.IO) {
        val sendReceipts = try {
            MessageBackupApp.instance.appPreferences.ravenSendReadReceipts.value
        } catch (_: Exception) { true }
        if (!sendReceipts) return@withContext

        val payload = JSONObject().apply {
            put("type", "READ_RECEIPT")
            put("up_to_ts", upToTimestamp)
            put("ts", System.currentTimeMillis())
        }.toString()

        sendRawPayloadToPeer(peer, payload, peer.lastKnownIp, DEFAULT_MESH_PORT)
    }

    suspend fun sendReaction(peer: RavenPeer, messageTimestamp: Long, emoji: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", "MESSAGE_REACTION")
            put("msg_timestamp", messageTimestamp)
            put("reaction", emoji)
            put("ts", System.currentTimeMillis())
        }.toString()

        if (isReachableLocalSubnet(peer.lastKnownIp)) {
            sendRawPayloadToPeer(peer, payload, peer.lastKnownIp, DEFAULT_MESH_PORT)
        } else {
            globalRelay.sendPacketToRemotePeer(peer, payload)
        }
    }

    suspend fun broadcastEmergencyBeacon(channel: Int, gpsCoords: String = "") = withContext(Dispatchers.IO) {
        val myCallsign = cryptoEngine.getRavenCallSign()
        val peers = dbHelper.getAllPeers().filter { it.isTrusted }
        val payload = JSONObject().apply {
            put("type", "EMERGENCY_BEACON")
            put("channel", channel)
            put("gps", gpsCoords)
            put("sender", myCallsign)
            put("ts", System.currentTimeMillis())
        }.toString()

        for (peer in peers) {
            launch {
                if (isReachableLocalSubnet(peer.lastKnownIp)) {
                    sendRawPayloadToPeer(peer, payload, peer.lastKnownIp, DEFAULT_MESH_PORT)
                } else {
                    globalRelay.sendPacketToRemotePeer(peer, payload)
                }
            }
        }
    }

    /**
     * Dispatches a single real-time 450ms walkie-talkie audio chunk while the user is holding the PTT button.
     * Live PCM chunks are streamed only over direct local Wi-Fi sockets so the public HTTP relay is never rate-limited.
     */
    suspend fun sendLiveVoiceStreamChunk(
        peer: RavenPeer,
        chunkBase64: String,
        sessionId: Long,
        seq: Int
    ) = withContext(Dispatchers.IO) {
        if (!isReachableLocalSubnet(peer.lastKnownIp)) return@withContext
        val payload = JSONObject().apply {
            put("type", "PTT_STREAM_CHUNK")
            put("chunk_b64", chunkBase64)
            put("session_id", sessionId)
            put("seq", seq)
            put("ts", System.currentTimeMillis())
        }.toString()
        sendRawPayloadToPeer(peer, payload, peer.lastKnownIp, DEFAULT_MESH_PORT)
    }

    /**
     * Sends an encrypted "Mind-to-Mind" PTT voice snippet directly to a peer.
     */
    suspend fun sendVoiceSnippetToPeer(
        peer: RavenPeer,
        voiceSnippet: VoiceSnippet,
        sessionId: Long = 0L,
        targetIp: String? = peer.lastKnownIp,
        port: Int = DEFAULT_MESH_PORT
    ): RavenSendResult = withContext(Dispatchers.IO) {
        val ttlSec = dbHelper.getEphemeralTtlForPeer(peer.peerId)
        val burnOnRead = ttlSec == -1
        val effectiveTtl = if (ttlSec > 0) ttlSec else 0
        val expiresAt = if (effectiveTtl > 0) System.currentTimeMillis() + (effectiveTtl * 1000L) else 0L

        val payload = JSONObject().apply {
            put("type", "PTT_VOICE")
            put("audio_base64", voiceSnippet.audioBase64)
            put("duration_ms", voiceSnippet.durationMs)
            put("session_id", sessionId)
            if (!voiceSnippet.transcription.isNullOrBlank()) {
                put("transcription", voiceSnippet.transcription)
            }
            put("ttl_sec", effectiveTtl)
            put("burn_on_read", burnOnRead)
            put("ts", System.currentTimeMillis())
        }.toString()

        val result = sendRawPayloadToPeer(peer, payload, targetIp, port)
        if (result is RavenSendResult.Success) {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myName = myCallSign.removePrefix("raven://")
            val durationSec = String.format(java.util.Locale.US, "%.1fs", voiceSnippet.durationMs / 1000f)
            val chatMsg = RavenChatMessage(
                peerId = peer.peerId,
                senderCallsign = myCallSign,
                senderDisplayName = myName,
                isOutbound = true,
                messageText = "🎙️ Voice Note ($durationSec)",
                messageType = "VOICE",
                timestamp = System.currentTimeMillis(),
                audioBase64 = voiceSnippet.audioBase64,
                transcription = voiceSnippet.transcription,
                expiresAtTimestamp = expiresAt,
                burnOnRead = burnOnRead,
                deliveryStatus = "DELIVERED"
            )
            dbHelper.insertMessage(chatMsg)
            RavenNotificationHelper.getInstance(context).playSentTick()
        }
        result
    }

    /**
     * Automatically completes 2-way mutual link with a peer over local Wi-Fi socket (if on same subnet)
     * and across the Global Cellular Relay so remote peers connect simultaneously.
     */
    suspend fun sendMutualHandshake(
        targetIp: String,
        targetPeerId: String = "",
        targetPublicKey: String = "",
        port: Int = DEFAULT_MESH_PORT
    ): Result<RavenPeer> = withContext(Dispatchers.IO) {
        var relaySuccess = false
        if (targetPeerId.isNotBlank()) {
            relaySuccess = globalRelay.sendCellularMutualHandshake(targetPeerId, targetPublicKey)
        }

        if (!isReachableLocalSubnet(targetIp)) {
            val peerObj = RavenPeer(
                peerId = RavenDatabaseHelper.normalizePeerId(targetPeerId),
                displayName = targetPeerId.removePrefix("raven://"),
                publicKey = targetPublicKey,
                lastKnownIp = "CELLULAR_RELAY",
                lastSeenTimestamp = System.currentTimeMillis(),
                isTrusted = true,
                status = if (relaySuccess) RavenPeerStatus.PENDING_APPROVAL else RavenPeerStatus.CONNECTED
            )
            dbHelper.upsertPeer(peerObj)
            return@withContext Result.success(peerObj)
        }

        try {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myPubKey = cryptoEngine.getPublicKeyBase64()
            val myName = cryptoEngine.getRavenCallSign().removePrefix("raven://")
            val myIp = DirectLocalMigrationManager.getLocalIpAddress() ?: ""
            val timestamp = System.currentTimeMillis()

            val claim = "MUTUAL_HANDSHAKE|$myCallSign|$myPubKey|$timestamp"
            val signature = cryptoEngine.signData(claim.toByteArray(Charsets.UTF_8))

            val packet = JSONObject().apply {
                put("protocol", "RAVEN_MESH_v1")
                put("type", "MUTUAL_HANDSHAKE")
                put("sender", myCallSign)
                put("displayName", myName)
                put("publicKey", myPubKey)
                put("localIp", myIp)
                put("sig", signature)
                put("ts", timestamp)
            }

            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, port), 1_200)
            socket.soTimeout = 2_000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.println(packet.toString())
            val responseLine = reader.readLine()
            socket.close()

            if (responseLine != null) {
                val resp = JSONObject(responseLine)
                if (resp.optString("status") == "OK_MUTUAL_LINK_ESTABLISHED") {
                    val peerId = RavenDatabaseHelper.normalizePeerId(resp.getString("peerId"))
                    val displayName = resp.optString("displayName", peerId)
                    val publicKey = resp.getString("publicKey")
                    val remoteIp = resp.optString("localIp", targetIp).ifBlank { targetIp }

                    val confirmedPeer = RavenPeer(
                        peerId = peerId,
                        displayName = displayName,
                        publicKey = publicKey,
                        lastKnownIp = remoteIp,
                        lastSeenTimestamp = System.currentTimeMillis(),
                        isTrusted = true,
                        status = RavenPeerStatus.CONNECTED
                    )
                    dbHelper.upsertPeer(confirmedPeer)
                    Result.success(confirmedPeer)
                } else {
                    Result.failure(Exception("Peer handshake rejected: ${resp.optString("status")}"))
                }
            } else {
                Result.failure(Exception("No response from peer during mutual handshake."))
            }
        } catch (e: Exception) {
            if (relaySuccess || targetPeerId.isNotBlank()) {
                val cellularPeer = RavenPeer(
                    peerId = RavenDatabaseHelper.normalizePeerId(targetPeerId),
                    displayName = targetPeerId.removePrefix("raven://"),
                    publicKey = targetPublicKey,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true,
                    status = RavenPeerStatus.PENDING_APPROVAL
                )
                dbHelper.upsertPeer(cellularPeer)
                Result.success(cellularPeer)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Sends an acceptance confirmation packet to the peer once the local user clicks 'Accept'.
     */
    suspend fun sendAcceptanceConfirmation(peer: RavenPeer, port: Int = DEFAULT_MESH_PORT): Boolean = withContext(Dispatchers.IO) {
        globalRelay.sendCellularAcceptanceConfirmation(peer)

        val targetIp = peer.lastKnownIp
        if (!isReachableLocalSubnet(targetIp)) return@withContext true

        try {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val packet = JSONObject().apply {
                put("protocol", "RAVEN_MESH_v1")
                put("type", "PEER_ACCEPT_CONFIRMATION")
                put("sender", myCallSign)
                put("ts", System.currentTimeMillis())
            }

            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, port), 1_200)
            socket.soTimeout = 1_500

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.println(packet.toString())
            val responseLine = reader.readLine()
            socket.close()

            responseLine != null && JSONObject(responseLine).optString("status") == "OK_ACCEPTED"
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendRawPayloadToPeer(
        peer: RavenPeer,
        innerPayload: String,
        targetIp: String?,
        port: Int
    ): RavenSendResult = withContext(Dispatchers.IO) {
        // 1. Only attempt local TCP socket if targetIp is on our active local Wi-Fi subnet (zero delay when out of state!)
        if (isReachableLocalSubnet(targetIp)) {
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

                val socket = Socket()
                socket.connect(InetSocketAddress(targetIp, port), 800)
                socket.soTimeout = 1_200

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                writer.println(packet.toString())
                val responseLine = reader.readLine()
                socket.close()

                if (responseLine != null) {
                    val resp = JSONObject(responseLine)
                    if (resp.optString("status") == "OK_DELIVERED") {
                        dbHelper.upsertPeer(peer.copy(lastKnownIp = targetIp, lastSeenTimestamp = System.currentTimeMillis()))
                        return@withContext RavenSendResult.Success
                    }
                }
            } catch (_: Exception) {
                // Local socket unreachable; fall through immediately to Global Cellular Relay
            }
        }

        // 2. Instant Global Cellular / Internet Relay dispatch (0ms wait across states and 4G/5G)
        val relayResult = globalRelay.sendPacketToRemotePeer(peer, innerPayload)
        if (relayResult is RavenSendResult.Success) {
            dbHelper.upsertPeer(peer.copy(lastSeenTimestamp = System.currentTimeMillis()))
        }
        relayResult
    }
}

data class RavenIncomingMessage(
    val senderCallsign: String,
    val senderDisplayName: String,
    val messageText: String,
    val timestamp: Long
)

data class RavenIncomingVoice(
    val senderCallsign: String,
    val senderDisplayName: String,
    val audioBase64: String,
    val durationMs: Long,
    val timestamp: Long
)

sealed class RavenSendResult {
    object Success : RavenSendResult()
    data class Error(val errorMessage: String) : RavenSendResult()
}
