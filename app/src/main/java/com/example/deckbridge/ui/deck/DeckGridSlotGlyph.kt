package com.example.deckbridge.ui.deck

/** Glyph shown on deck tiles / editor preview for a persisted [iconToken]. */
fun deckGridSlotGlyph(iconToken: String?): String = when (iconToken) {
    "content_copy" -> "⎘"
    "content_paste" -> "📋"
    "volume_up" -> "🔊"
    "play_pause" -> "⏯"
    "search" -> "🔍"
    "content_cut" -> "✂"
    "undo" -> "↩"
    "redo" -> "↪"
    "volume_mute" -> "🔇"
    "text_jamon" -> "⌨"
    "show_desktop" -> "🖥"
    "keyboard_enter" -> "⏎"
    "screen_snip" -> "⎙"
    "previous_track" -> "⏮"
    "next_track" -> "⏭"
    else -> "◇"
}
