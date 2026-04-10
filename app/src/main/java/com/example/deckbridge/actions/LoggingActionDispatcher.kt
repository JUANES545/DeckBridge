package com.example.deckbridge.actions

import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.domain.model.ResolvedActionKind
import com.example.deckbridge.logging.DeckBridgeLog

/**
 * Stand-in until HID/host transport exists: logs the **resolved** chord/media line per platform.
 */
class LoggingActionDispatcher : ActionDispatcher {
    override suspend fun dispatch(resolved: ResolvedAction) {
        val kind = when (resolved.kind) {
            ResolvedActionKind.KEY_CHORD -> "CHORD"
            ResolvedActionKind.SYSTEM_MEDIA -> "MEDIA"
        }
        DeckBridgeLog.action(
            "[${resolved.platform.name}|$kind] ${resolved.intentDisplayName} → ${resolved.shortcutDisplay} (id=${resolved.intentId})",
        )
    }
}
