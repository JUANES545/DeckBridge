package com.example.deckbridge.profiles

/**
 * Named layout + binding set for deck buttons and physical keys. Persistence comes later.
 */
data class Profile(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
)
