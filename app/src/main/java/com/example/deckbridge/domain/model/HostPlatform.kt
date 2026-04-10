package com.example.deckbridge.domain.model

/**
 * Target OS for host-side shortcuts and HID mappings. UNKNOWN covers undetected or mixed setups.
 */
enum class HostPlatform {
    WINDOWS,
    MAC,
    UNKNOWN,
    ;

    /**
     * Deck layout, bindings, and chord resolution fallback when the OS is not chosen yet.
     */
    fun coerceForDeckData(): HostPlatform = when (this) {
        UNKNOWN -> WINDOWS
        else -> this
    }
}
