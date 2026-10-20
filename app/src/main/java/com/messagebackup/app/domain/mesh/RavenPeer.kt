package com.messagebackup.app.domain.mesh

/**
 * Represents a trusted contact in the Raven Mesh peer-to-peer network.
 */
data class RavenPeer(
    val peerId: String,               // e.g. "raven://valkyrie-7a3f"
    val displayName: String,
    val publicKey: String,            // Base64-encoded X.509 EC Public Key
    val lastKnownIp: String?,
    val lastSeenTimestamp: Long,
    val isTrusted: Boolean = true,
    val avatarColor: String = "#0284c7"
) {
    /**
     * Short visual fingerprint of the peer's public key (e.g. 4 hex characters).
     */
    fun fingerprint(): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(publicKey.toByteArray(Charsets.UTF_8))
            String.format("%02X%02X", digest[0], digest[1])
        } catch (e: Exception) {
            "????"
        }
    }
}
