package com.example.deckbridge.mac

import com.example.deckbridge.actions.ActionDispatcher
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.lan.LanActionJsonFactory
import com.example.deckbridge.logging.DeckBridgeLog

/**
 * Routes resolved deck actions into [MacBridgeServer]'s action queue so the polling Mac agent
 * can pick them up via GET /action/next.  Unsupported action types are silently dropped.
 */
class MacBridgeDispatcher(private val server: MacBridgeServer) : ActionDispatcher {

    override suspend fun dispatch(resolved: ResolvedAction): Result<Unit> {
        val json = LanActionJsonFactory.actionJsonOrNull(resolved)
        if (json == null) {
            DeckBridgeLog.lan("MacBridge skip unsupported intent=${resolved.intentId} kind=${resolved.kind}")
            return Result.success(Unit)
        }
        if (!server.isRunning) {
            DeckBridgeLog.lan("MacBridge skip server not running intent=${resolved.intentId}")
            return Result.failure(IllegalStateException("mac bridge not running"))
        }
        server.enqueueAction(resolved.intentId, json)
        return Result.success(Unit)
    }
}
