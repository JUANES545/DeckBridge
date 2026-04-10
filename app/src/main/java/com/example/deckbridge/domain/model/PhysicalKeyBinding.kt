package com.example.deckbridge.domain.model

/**
 * Maps a physical key (from the external BT/USB keyboard) to a deck button.
 * [keyCode] matches [android.view.KeyEvent.getKeyCode] (e.g. [android.view.KeyEvent.KEYCODE_F1]).
 */
data class PhysicalKeyBinding(
    val id: String,
    val keyCode: Int,
    val keyLabel: String,
    val macroButtonId: String?,
    val profileId: String,
    val modifierLabels: List<String> = emptyList(),
)

fun List<PhysicalKeyBinding>.macroButtonIdForKeyCode(keyCode: Int): String? =
    firstOrNull { it.keyCode == keyCode && it.macroButtonId != null }?.macroButtonId
