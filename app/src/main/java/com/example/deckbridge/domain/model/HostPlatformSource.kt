package com.example.deckbridge.domain.model

/**
 * Whether [AppState.hostPlatform] came from user selection or automatic (heuristic) detection.
 */
enum class HostPlatformSource {
    MANUAL,
    AUTOMATIC,
}
