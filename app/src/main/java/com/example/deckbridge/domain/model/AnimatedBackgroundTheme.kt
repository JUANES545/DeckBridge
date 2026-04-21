package com.example.deckbridge.domain.model

/**
 * Visual style of the animated background on the home dashboard.
 * Persisted as a short string in DataStore, independent of [AnimatedBackgroundMode].
 */
enum class AnimatedBackgroundTheme {
    /** Teal energy pulses travelling along the full grid — the original style, improved. */
    GRID_PULSE,

    /** Soft luminous orbs drifting across the screen, bouncing off edges. */
    PARTICLES,

    /** Flowing aurora-plasma gradient bands that ripple slowly across the canvas. */
    AURORA,
    ;

    fun persisted(): String = name

    companion object {
        fun fromPersisted(raw: String?): AnimatedBackgroundTheme {
            if (raw.isNullOrBlank()) return GRID_PULSE
            return entries.firstOrNull { it.name == raw.trim() } ?: GRID_PULSE
        }
    }
}
