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
    }

    private val cryptoEngine = RavenCryptoEngine(context)
    private val dbHelper = RavenDatabaseHelper(context)
    private val voicePttEngine = RavenVoicePttEngine(context)

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

                // Automatically save the scanning peer as trusted in our database!
                val resolvedIp = if (senderLocalIp.isNotBlank()) senderLocalIp else (socket.inetAddress?.hostAddress ?: "")
                val mutualPeer = RavenPeer(
                    peerId = senderCallSign,
                    displayName = displayName,
                    publicKey = publicKeyBase64,
                    lastKnownIp = resolvedIp,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isTrusted = true
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
            if (senderPeer == null || !senderPeer.isTrusted) {
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
        sendRawPayloadToPeer(peer, payload, targetIp, port)
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
        sendRawPayloadToPeer(peer, payload, targetIp, port)
    }

    /**
     * Automatically completes 2-way mutual link with a scanned peer over local Wi-Fi / hotspot socket.
     * When Phone B scans Phone A, this immediately sends Phone B's identity to Phone A so that
     * Phone A automatically adds Phone B without needing a reciprocal scan!
     */
    suspend fun sendMutualHandshake(
        targetIp: String,
        port: Int = DEFAULT_MESH_PORT
    ): Result<RavenPeer> = withContext(Dispatchers.IO) {
        if (targetIp.isBlank()) {
            return@withContext Result.failure(Exception("Unknown peer IP"))
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
            socket.connect(InetSocketAddress(targetIp, port), 6_000)
            socket.soTimeout = 8_000

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
                        isTrusted = true
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
            Result.failure(e)
        }
    }

    private suspend fun sendRawPayloadToPeer(
        peer: RavenPeer,
        innerPayload: String,
        targetIp: String?,
        port: Int
    ): RavenSendResult = withContext(Dispatchers.IO) {
        if (targetIp.isNullOrBlank()) {
            return@withContext RavenSendResult.Error("Peer IP address is unknown. Link via QR or connect to the same Wi-Fi.")
        }

        try {
            val peerPubKey = cryptoEngine.decodePublicKey(peer.publicKey)
            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPubKey)

            val envelope = RavenPacketCipher.encryptPayload(innerPayload, sharedSecret)
            val timestamp = System.currentTimeMillis()
            val myCallSign = cryptoEngine.getRavenCallSign()

            // Sign claim
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
            socket.connect(InetSocketAddress(targetIp, port), SOCKET_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.println(packet.toString())
            val responseLine = reader.readLine()
            socket.close()

            if (responseLine != null) {
                val resp = JSONObject(responseLine)
                if (resp.optString("status") == "OK_DELIVERED") {
                    // Update last seen
                    dbHelper.upsertPeer(peer.copy(lastKnownIp = targetIp, lastSeenTimestamp = System.currentTimeMillis()))
                    RavenSendResult.Success
                } else {
                    RavenSendResult.Error("Peer rejected message: ${resp.optString("status")}")
                }
            } else {
                RavenSendResult.Error("No response from peer.")
            }
        } catch (e: Exception) {
            RavenSendResult.Error("Delivery failed: ${e.message ?: "Connection timed out"}")
        }
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
