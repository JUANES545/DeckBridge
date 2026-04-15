package com.example.deckbridge.lan

/** Response from POST /v1/pairing/sessions */
data class LanPairingSessionCreated(
    val sessionId: String,
    val pairingCode: String,
    val expiresAtEpochMs: Long,
)

/** Response from GET /v1/pairing/sessions/{id} */
data class LanPairingSessionStatus(
    val sessionId: String,
    val status: String,
    val pairingCode: String?,
    val pairToken: String?,
    val mobileDeviceId: String?,
)
