package com.example.deckbridge.lan

import com.example.deckbridge.actions.ActionDispatcher
import com.example.deckbridge.actions.LoggingActionDispatcher
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.logging.DeckBridgeLog

/**
 * Sends resolved deck actions to the PC LAN agent over HTTP POST `/action`.
 * Unsupported actions or failures delegate to [loggingFallback].
 *
 * Uses [circuitBreaker] to skip calls when the PC is known to be unreachable,
 * avoiding repeated 15-second timeouts on each button press.
 */
class LanTransportDispatcher(
    private val client: LanHostClient,
    private val loggingFallback: LoggingActionDispatcher,
    val circuitBreaker: LanCircuitBreaker = LanCircuitBreaker(),
) : ActionDispatcher {

    override suspend fun dispatch(resolved: ResolvedAction): Result<Unit> {
        val json = LanActionJsonFactory.actionJsonOrNull(resolved)
        if (json == null) {
            DeckBridgeLog.lan("skip unsupported LAN intent=${resolved.intentId} kind=${resolved.kind}")
            loggingFallback.dispatch(resolved)
            return Result.success(Unit)
        }
        if (client.baseUrlOrNull() == null) {
            DeckBridgeLog.lan("skip no LAN host configured intent=${resolved.intentId}")
            loggingFallback.dispatch(resolved)
            return Result.failure(IllegalStateException("no LAN host"))
        }
        if (circuitBreaker.isOpen) {
            DeckBridgeLog.lan("circuit OPEN — skip POST /action intent=${resolved.intentId}")
            loggingFallback.dispatch(resolved)
            return Result.failure(IllegalStateException("circuit open: PC unreachable"))
        }
        val preview = if (json.length > 220) json.take(220) + "…" else json
        DeckBridgeLog.lan("POST /action intent=${resolved.intentId} body=$preview")
        val post = client.postAction(json)
        post.fold(
            onSuccess = {
                DeckBridgeLog.lan("LAN OK intent=${resolved.intentId}")
                circuitBreaker.recordSuccess()
            },
            onFailure = {
                DeckBridgeLog.lan("LAN FAIL intent=${resolved.intentId} ${it.message}")
                circuitBreaker.recordFailure()
                loggingFallback.dispatch(resolved)
            },
        )
        return post
    }
}
