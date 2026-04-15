package com.example.deckbridge.ui.hardware

/**
 * Display model for one cell of the 3×3 hardware mirror pad.
 * The [media] area is reserved for future images / GIFs / animated icons; [title] and [shortcutHint] are secondary.
 */
data class MirrorPadSlot(
    val title: String,
    val shortcutHint: String,
    val iconToken: String?,
    /** Deck tile id for touch + highlight; empty when slot is filler only. */
    val deckButtonId: String = "",
    /**
     * Long-press editor target when the tile is visible but [deckButtonId] is empty (e.g. disabled tile).
     * Defaults to [deckButtonId] when null.
     */
    val editTargetButtonId: String? = null,
)
