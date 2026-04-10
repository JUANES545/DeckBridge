package com.example.deckbridge.domain.model

/**
 * One configurable tile on the Stream Deck–style surface.
 * [intent] is stable; [resolvedShortcut] reflects the active [HostPlatform] (filled in the data layer).
 */
data class MacroButton(
    val id: String,
    val label: String,
    val intent: DeckButtonIntent,
    /** Reading order in the grid (0-based). */
    val sortIndex: Int,
    /** Optional icon name for future asset loading; null uses a default glyph. */
    val iconToken: String? = null,
    /** Shortcut line for current platform, e.g. Ctrl+C / ⌘C. */
    val resolvedShortcut: String,
)
