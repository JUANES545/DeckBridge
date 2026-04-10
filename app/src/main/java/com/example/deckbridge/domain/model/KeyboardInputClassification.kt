package com.example.deckbridge.domain.model

/**
 * Coarse bucket for deciding how much we trust an event for “macro keyboard” workflows.
 * This is heuristic — OEMs differ on reporting for BT vs USB and built-in controllers.
 */
enum class KeyboardInputClassification {
    /** Likely BT/USB keyboard or keypad marked external. */
    EXTERNAL_HARDWARE_KEYBOARD,

    /** Laptop-style integrated keyboard or dock marked non-external. */
    BUILT_IN_HARDWARE_KEYBOARD,

    /** Virtual input (often IME / software); may still claim keyboard source. */
    SOFTWARE_OR_VIRTUAL_KEYBOARD,

    /** Missing device, or sources do not include keyboard. */
    NON_KEYBOARD_OR_UNKNOWN,
}
