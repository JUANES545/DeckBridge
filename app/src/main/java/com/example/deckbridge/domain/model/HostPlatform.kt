package com.example.deckbridge.domain.model

/**
 * Target OS for host-side shortcuts and HID mappings. UNKNOWN covers undetected or mixed setups.
 */
enum class HostPlatform {
    WINDOWS,
    MAC,
    UNKNOWN,
}
