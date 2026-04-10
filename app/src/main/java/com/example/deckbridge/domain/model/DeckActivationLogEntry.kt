package com.example.deckbridge.domain.model

/**
 * Human-readable trace of deck activations (distinct from raw [RecentInputEvent] key stream).
 */
data class DeckActivationLogEntry(
    val id: String,
    val occurredAtEpochMs: Long,
    val buttonId: String,
    val buttonLabel: String,
    /** Host OS profile used when resolving the action. */
    val hostPlatform: HostPlatform,
    /** Intent name, e.g. Copy. */
    val intentLabel: String,
    /** Resolved shortcut / media line for that platform. */
    val resolvedShortcut: String,
    /** Bound physical key label, e.g. F1, if any. */
    val physicalKeyLabel: String?,
    val source: ButtonTriggerSource,
)
