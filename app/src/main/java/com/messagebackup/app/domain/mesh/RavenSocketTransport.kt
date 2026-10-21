package com.messagebackup.app.domain.mesh

import android.content.Context
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
 * to send and receive authenticated, AES-256-GCM encrypted peer messages.
 */
class RavenSocketTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        const val DEFAULT_MESH_PORT = 8744
        const val SOCKET_TIMEOUT_MS = 15_000

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
    private val voicePttEngine = RavenVoicePttEngine(context)
    private val globalRelay = RavenGlobalRelayTransport(context, scope)

    init {
        // Automatically start global cellular / internet relay alongside local socket server
        globalRelay.startGlobalRelay()
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

    fun pollRelayImmediately() {
        globalRelay.pollImmediately()
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

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
     * Handles an incoming connection from a peer node.
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
                val existing = dbHelper.getAllPeers().find { it.peerId == senderCallSign }
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

                // Save the peer with PENDING_ACCEPTANCE if not already accepted/connected
                val existingPeer = dbHelper.getAllPeers().find { it.peerId == senderCallSign }
                val currentStatus = if (existingPeer != null && existingPeer.status == RavenPeerStatus.CONNECTED) {
                    RavenPeerStatus.CONNECTED
                } else {
                    RavenPeerStatus.PENDING_ACCEPTANCE
                }

                val resolvedIp = if (senderLocalIp.isNotBlank()) senderLocalIp else (socket.inetAddress?.hostAddress ?: "")
                val mutualPeer = RavenPeer(
                    peerId = senderCallSign,
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = resolvedIp,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true,
                    status = currentStatus
                )
                dbHelper.upsertPeer(mutualPeer)

                // Return our identity so the scanning phone is 100% confirmed as well
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

            // Look up peer in database
            val peers = dbHelper.getAllPeers()
            val senderPeer = peers.find { it.peerId == senderCallSign }
            if (senderPeer == null || !senderPeer.isTrusted || senderPeer.status != RavenPeerStatus.CONNECTED) {
                writer.println(JSONObject().apply { put("status", "ERR_UNTRUSTED_PEER") })
                return@withContext
            }

            // Verify sender signature over the encrypted envelope + timestamp
            val peerPubKey = cryptoEngine.decodePublicKey(senderPeer.publicKey)
            val claim = "$senderCallSign|$encryptedEnvelope|$timestamp"
            val isValid = cryptoEngine.verifySignature(claim.toByteArray(Charsets.UTF_8), signature, peerPubKey)
            if (!isValid) {
                writer.println(JSONObject().apply { put("status", "ERR_INVALID_SIGNATURE") })
                return@withContext
            }

            // Derive shared secret and decrypt
            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPubKey)
            val decryptedJson = RavenPacketCipher.decryptPayload(encryptedEnvelope, sharedSecret)
            if (decryptedJson == null) {
                writer.println(JSONObject().apply { put("status", "ERR_DECRYPTION_FAILED") })
                return@withContext
            }

            val innerMsg = JSONObject(decryptedJson)
            val packetType = innerMsg.optString("type", "TEXT")

            // Update peer's last seen IP
            val clientIp = socket.inetAddress.hostAddress
            dbHelper.upsertPeer(
                senderPeer.copy(
                    lastKnownIp = clientIp,
                    lastSeenTimestamp = System.currentTimeMillis()
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

                // Emit voice stream event
                _incomingVoiceStreams.emit(
                    RavenIncomingVoice(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        audioBase64 = audioBase64,
                        durationMs = durationMs,
                        timestamp = timestamp
                    )
                )

                // Automatically trigger immediate tactical speaker playback via PTT Engine
                scope.launch(Dispatchers.IO) {
                    voicePttEngine.playInboundVoice(audioBase64)
                }
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

                // Emit incoming message
                _incomingMessages.emit(
                    RavenIncomingMessage(
                        senderCallsign = senderCallSign,
                        senderDisplayName = senderPeer.displayName,
                        messageText = messageText,
                        timestamp = timestamp
                    )
                )
            }

            // Acknowledge receipt
            writer.println(JSONObject().apply {
                put("status", "OK_DELIVERED")
                put("ack_ts", System.currentTimeMillis())
            })

        } catch (e: Exception) {
            // Socket or handshake error
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Sends an encrypted Raven packet directly to a peer's IP address.
     */
    suspend fun sendMessageToPeer(
        peer: RavenPeer,
        text: String,
        targetIp: String? = peer.lastKnownIp,
        port: Int = DEFAULT_MESH_PORT
    ): RavenSendResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", "TEXT")
            put("text", text)
            put("ts", System.currentTimeMillis())
        }.toString()
        val result = sendRawPayloadToPeer(peer, payload, targetIp, port)
        if (result is RavenSendResult.Success) {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myName = myCallSign.removePrefix("raven://")
            val chatMsg = com.messagebackup.app.data.local.RavenChatMessage(
                peerId = peer.peerId,
                senderCallsign = myCallSign,
                senderDisplayName = myName,
                isOutbound = true,
                messageText = text,
                messageType = "TEXT",
                timestamp = System.currentTimeMillis()
            )
            dbHelper.insertMessage(chatMsg)
        }
        result
    }

    /**
     * Sends an encrypted "Mind-to-Mind" PTT voice snippet directly to a peer.
     */
    suspend fun sendVoiceSnippetToPeer(
        peer: RavenPeer,
        voiceSnippet: VoiceSnippet,
        targetIp: String? = peer.lastKnownIp,
        port: Int = DEFAULT_MESH_PORT
    ): RavenSendResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", "PTT_VOICE")
            put("audio_base64", voiceSnippet.audioBase64)
            put("duration_ms", voiceSnippet.durationMs)
            put("ts", System.currentTimeMillis())
        }.toString()
        val result = sendRawPayloadToPeer(peer, payload, targetIp, port)
        if (result is RavenSendResult.Success) {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val myName = myCallSign.removePrefix("raven://")
            val chatMsg = com.messagebackup.app.data.local.RavenChatMessage(
                peerId = peer.peerId,
                senderCallsign = myCallSign,
                senderDisplayName = myName,
                isOutbound = true,
                messageText = "🎙️ PTT Voice Note (${voiceSnippet.durationMs}ms)",
                messageType = "VOICE",
                timestamp = System.currentTimeMillis()
            )
            dbHelper.insertMessage(chatMsg)
        }
        result
    }

    /**
     * Automatically completes 2-way mutual link with a peer over local Wi-Fi socket,
     * and also broadcasts across the Global Cellular Relay so remote peers connect simultaneously!
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

        if (targetIp.isBlank() || targetIp == "CELLULAR_RELAY") {
            return@withContext Result.success(
                RavenPeer(
                    peerId = targetPeerId,
                    displayName = targetPeerId.removePrefix("raven://"),
                    publicKey = targetPublicKey,
                    lastKnownIp = "CELLULAR_RELAY",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true,
                    status = if (relaySuccess) RavenPeerStatus.PENDING_APPROVAL else RavenPeerStatus.CONNECTED
                )
            )
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
            socket.connect(InetSocketAddress(targetIp, port), 2_000)
            socket.soTimeout = 3_000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.println(packet.toString())
            val responseLine = reader.readLine()
            socket.close()

            if (responseLine != null) {
                val resp = JSONObject(responseLine)
                if (resp.optString("status") == "OK_MUTUAL_LINK_ESTABLISHED") {
                    val peerId = resp.getString("peerId")
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
            // Local IP socket unreachable (e.g. out-of-state cellular data test)
            // If cellular relay handshake succeeded or targetPeerId exists, gracefully fall back to cellular relay peer!
            if (relaySuccess || targetPeerId.isNotBlank()) {
                val cellularPeer = RavenPeer(
                    peerId = targetPeerId,
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
        // Also dispatch over cellular relay so out-of-state peers receive it immediately
        globalRelay.sendCellularAcceptanceConfirmation(peer)

        val targetIp = peer.lastKnownIp
        if (targetIp.isNullOrBlank() || targetIp == "CELLULAR_RELAY") return@withContext true

        try {
            val myCallSign = cryptoEngine.getRavenCallSign()
            val packet = JSONObject().apply {
                put("protocol", "RAVEN_MESH_v1")
                put("type", "PEER_ACCEPT_CONFIRMATION")
                put("sender", myCallSign)
                put("ts", System.currentTimeMillis())
            }

            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, port), 4_000)
            socket.soTimeout = 4_000

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
        // 1. If target IP is a local subnet IP, attempt fast local TCP socket first
        if (!targetIp.isNullOrBlank() && targetIp != "CELLULAR_RELAY") {
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
                socket.connect(InetSocketAddress(targetIp, port), 3_000)
                socket.soTimeout = 4_000

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
                // Local socket unreachable; fall through seamlessly to Global Cellular Relay
            }
        }

        // 2. Seamless Global Cellular / Internet Relay fallback (works across states and cell data!)
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
