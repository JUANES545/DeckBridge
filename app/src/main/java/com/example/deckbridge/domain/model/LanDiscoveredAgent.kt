package com.example.deckbridge.domain.model

/**
 * One PC agent answering UDP discovery (or persisted endpoint shown in the list).
 */
data class LanDiscoveredAgent(
    val address: String,
    val httpPort: Int,
    /** Optional display name when the agent sends one in JSON; else UI uses [address]. */
    val displayName: String? = null,
    /** From UDP/HTTP JSON `agent_os` when present (e.g. `windows`, `darwin`). */
    val agentOs: String? = null,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: address
}

sealed interface LanAgentListScanState {
    data object Idle : LanAgentListScanState
    data object Scanning : LanAgentListScanState
    data class Ready(val agents: List<LanDiscoveredAgent>) : LanAgentListScanState
    data class Failed(val message: String) : LanAgentListScanState
}
