package com.example.deckbridge.domain.model

/**
 * Where a deck button activation was initiated. Extensible for host agent / automation later.
 */
enum class ButtonTriggerSource {
    /** User tapped the Compose tile. */
    TOUCH,

    /** Matched [PhysicalKeyBinding] from a hardware [android.view.KeyEvent]. */
    HARDWARE_KEY,

    /** Synthetic / tests / future in-app replay. */
    SIMULATED,
}
