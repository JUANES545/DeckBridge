package com.example.deckbridge.domain.model

/**
 * Concrete action after applying [HostPlatform], ready for HID/agent execution.
 *
 * - [ResolvedActionKind.KEY_CHORD]: keyboard shortcut (Ctrl+C, ⌘V, …).
 * - [ResolvedActionKind.SYSTEM_MEDIA]: OS media keys (same representation on Win/Mac for this MVP).
 */
enum class ResolvedActionKind {
    KEY_CHORD,
    SYSTEM_MEDIA,
}

data class ResolvedAction(
    val intentId: String,
    val intentDisplayName: String,
    val platform: HostPlatform,
    /** Human line for UI + Logcat, e.g. Ctrl+C or ⌘V. */
    val shortcutDisplay: String,
    val kind: ResolvedActionKind,
)
