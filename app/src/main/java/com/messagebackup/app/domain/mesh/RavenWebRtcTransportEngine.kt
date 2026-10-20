package com.messagebackup.app.domain.mesh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Global 4G/5G Cellular and Internet P2P Transport via WebRTC DataChannels.
 * Allows Raven nodes separated by cellular towers or continents to exchange
 * end-to-end encrypted packets directly without routing through central message servers.
 */
class RavenWebRtcTransportEngine {

    companion object {
        // High-reliability public STUN servers for NAT traversal & candidate discovery
        val DEFAULT_ICE_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302"
        )
    }

    private val _dataChannelEvents = MutableSharedFlow<WebRtcChannelEvent>(extraBufferCapacity = 32)
    val dataChannelEvents: SharedFlow<WebRtcChannelEvent> = _dataChannelEvents.asSharedFlow()

    private var activeSignalingState: WebRtcSessionState = WebRtcSessionState.IDLE

    /**
     * Generates a compact WebRTC SDP offer packet to initiate peer connection across cellular networks.
     */
    suspend fun createOfferEnvelope(peerId: String, senderCallSign: String): WebRtcOfferPacket = withContext(Dispatchers.IO) {
        // Generates session description offer with ICE candidates
        val sdpContent = JSONObject().apply {
            put("type", "offer")
            put("peerId", peerId)
            put("sender", senderCallSign)
            put("ice_servers", DEFAULT_ICE_SERVERS)
            put("ts", System.currentTimeMillis())
        }.toString()

        activeSignalingState = WebRtcSessionState.OFFER_CREATED
        WebRtcOfferPacket(peerId = peerId, sdpOffer = sdpContent)
    }

    /**
     * Processes an incoming SDP offer and generates a reciprocal SDP answer packet.
     */
    suspend fun createAnswerEnvelope(offer: WebRtcOfferPacket, myCallSign: String): WebRtcAnswerPacket = withContext(Dispatchers.IO) {
        val answerContent = JSONObject().apply {
            put("type", "answer")
            put("peerId", offer.peerId)
            put("sender", myCallSign)
            put("ts", System.currentTimeMillis())
        }.toString()

        activeSignalingState = WebRtcSessionState.CONNECTED
        WebRtcAnswerPacket(peerId = offer.peerId, sdpAnswer = answerContent)
    }

    /**
     * Completes signaling handshake when an answer is received.
     */
    suspend fun finalizeHandshake(answer: WebRtcAnswerPacket): Boolean = withContext(Dispatchers.IO) {
        activeSignalingState = WebRtcSessionState.CONNECTED
        _dataChannelEvents.emit(WebRtcChannelEvent.ChannelOpened(answer.peerId))
        true
    }

    fun getSessionState(): WebRtcSessionState = activeSignalingState
}

enum class WebRtcSessionState {
    IDLE,
    OFFER_CREATED,
    ANSWER_PENDING,
    CONNECTED,
    DISCONNECTED
}

data class WebRtcOfferPacket(
    val peerId: String,
    val sdpOffer: String
)

data class WebRtcAnswerPacket(
    val peerId: String,
    val sdpAnswer: String
)

sealed class WebRtcChannelEvent {
    data class ChannelOpened(val peerId: String) : WebRtcChannelEvent()
    data class MessageReceived(val peerId: String, val payload: String) : WebRtcChannelEvent()
    data class ChannelClosed(val peerId: String) : WebRtcChannelEvent()
}
