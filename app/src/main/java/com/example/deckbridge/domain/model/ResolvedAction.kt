package com.example.deckbridge.domain.model

/**
 * Concrete action after applying [HostPlatform], ready for HID/agent execution.
 *
 * - [KEY_CHORD]: keyboard shortcut (Ctrl+C, ⌘V, …).
 * - [SYSTEM_MEDIA]: OS media keys (volume, transport, mute).
 * - [TEXT]: literal text for LAN `type=text` (HID may fall back to log until mapped).
 * - [KEY]: single logical key for LAN `type=key` (e.g. enter, escape).
 */
enum class ResolvedActionKind {
    KEY_CHORD,
    SYSTEM_MEDIA,
    TEXT,
    KEY,
    /** Switch Mac default audio output — carries uid in [ResolvedAction.textPayload]. */
    AUDIO_OUTPUT_SELECT,
    /** No host payload; transports skip silently. */
    NOOP,
}

data class ResolvedAction(
    val intentId: String,
    val intentDisplayName: String,
    val platform: HostPlatform,
    /** Human line for UI + Logcat, e.g. Ctrl+C or ⌘V. */
    val shortcutDisplay: String,
    val kind: ResolvedActionKind,
    /** When [kind] == [TEXT], UTF-8 text to send to the LAN agent. */
    val textPayload: String? = null,
    /** When [kind] == [KEY], token understood by the PC agent (e.g. `enter`, `escape`). */
    val keyToken: String? = null,
)
