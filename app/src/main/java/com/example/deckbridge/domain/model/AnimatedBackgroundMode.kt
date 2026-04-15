package com.example.deckbridge.domain.model

/**
 * User preference for the subtle animated grid background on the home dashboard.
 * Persisted as a short string in DataStore.
 */
enum class AnimatedBackgroundMode {
    /** Pulse overlay runs whenever the home screen is visible. */
    ALWAYS,

    /** Pulse overlay only while the device reports charging (saves idle battery). */
    WHEN_CHARGING,

    /** Static grid background only. */
    OFF,
    ;

    fun persisted(): String = when (this) {
        ALWAYS -> "ALWAYS"
        WHEN_CHARGING -> "WHEN_CHARGING"
        OFF -> "OFF"
    }

    companion object {
        fun fromPersisted(raw: String?): AnimatedBackgroundMode {
            if (raw.isNullOrBlank()) return WHEN_CHARGING
            return when (raw.trim()) {
                "ALWAYS" -> ALWAYS
                "WHEN_CHARGING" -> WHEN_CHARGING
                "OFF" -> OFF
                else -> WHEN_CHARGING
            }
        }
    }
}
