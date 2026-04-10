package com.example.deckbridge.actions

import com.example.deckbridge.domain.model.ResolvedAction

/**
 * Executes a [ResolvedAction] on the host (HID, agent, ADB, etc.). Implementations receive
 * platform-specific, display-ready payloads produced by [com.example.deckbridge.domain.PlatformActionResolver].
 */
fun interface ActionDispatcher {
    suspend fun dispatch(resolved: ResolvedAction)
}

class NoOpActionDispatcher : ActionDispatcher {
    override suspend fun dispatch(resolved: ResolvedAction) = Unit
}
