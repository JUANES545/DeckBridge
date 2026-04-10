package com.example.deckbridge.ui.hardware

/**
 * Display model for one cell of the 3×3 hardware mirror pad.
 * The [media] area is reserved for future images / GIFs / animated icons; [title] and [shortcutHint] are secondary.
 */
data class MirrorPadSlot(
    val title: String,
    val shortcutHint: String,
    val iconToken: String?,
)
