package com.example.deckbridge.actions

import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.domain.model.ResolvedActionKind
import com.example.deckbridge.logging.DeckBridgeLog

/**
 * Stand-in until HID/host transport exists: logs the **resolved** chord/media line per platform.
 */
class LoggingActionDispatcher : ActionDispatcher {
    override suspend fun dispatch(resolved: ResolvedAction): Result<Unit> {
        val kind = when (resolved.kind) {
            ResolvedActionKind.KEY_CHORD -> "CHORD"
            ResolvedActionKind.SYSTEM_MEDIA -> "MEDIA"
            ResolvedActionKind.TEXT -> "TEXT"
            ResolvedActionKind.KEY -> "KEY"
            ResolvedActionKind.NOOP -> "NOOP"
            ResolvedActionKind.AUDIO_OUTPUT_SELECT -> "AUDIO"
        }
        DeckBridgeLog.action(
            "[${resolved.platform.name}|$kind] ${resolved.intentDisplayName} → ${resolved.shortcutDisplay} (id=${resolved.intentId})",
        )
        return Result.success(Unit)
    }
}
