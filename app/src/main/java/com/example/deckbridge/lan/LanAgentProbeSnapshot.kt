package com.example.deckbridge.lan

/**
 * Result of a lightweight GET /health + optional GET /v1/pairing/host/status against a candidate host:port
 * (does not depend on [LanHostClient]’s current endpoint).
 */
data class LanAgentProbeSnapshot(
    val healthOk: Boolean,
    val healthDetail: String?,
    /** From GET /health → `pairing.paired`. */
    val serverReportsPaired: Boolean,
    val actionRequiresPairToken: Boolean,
    /** From GET /v1/pairing/host/status when paired; null if unknown or not paired. */
    val pairedDeviceIdOnHost: String?,
    /**
     * From GET /health → `pairing.pair_token_valid` when the probe sent `X-DeckBridge-Pair-Token`
     * (same saved host + persisted token); null if not sent or absent in JSON.
     */
    val pairTokenValid: Boolean? = null,
    /** From GET /health root `agent_os` when present (e.g. `darwin`, `windows`). */
    val agentOs: String? = null,
)
