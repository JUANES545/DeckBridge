package com.example.deckbridge.domain.model

/**
 * A macOS audio output device pushed from the Mac agent via POST /state.
 *
 * @param uid      Stable CoreAudio device UID — use this to identify the device across reboots.
 * @param name     Human-readable name shown in macOS Control Center (e.g. "MacBook Pro Speakers").
 * @param isActive True when this device is the current default macOS audio output.
 */
data class AudioOutputDevice(
    val uid: String,
    val name: String,
    val isActive: Boolean,
)
