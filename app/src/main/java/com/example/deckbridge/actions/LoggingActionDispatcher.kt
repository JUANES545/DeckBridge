package com.example.deckbridge.actions

import android.util.Log
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.domain.model.ResolvedActionKind

/**
 * Stand-in until HID/host transport exists: logs the **resolved** chord/media line per platform.
 */
class LoggingActionDispatcher(
    private val tag: String = "DeckBridgeAction",
) : ActionDispatcher {
    override suspend fun dispatch(resolved: ResolvedAction) {
        val kind = when (resolved.kind) {
            ResolvedActionKind.KEY_CHORD -> "CHORD"
            ResolvedActionKind.SYSTEM_MEDIA -> "MEDIA"
        }
        Log.i(
            tag,
            "[${resolved.platform.name}|$kind] ${resolved.intentDisplayName} -> ${resolved.shortcutDisplay} (id=${resolved.intentId})",
        )
    }
}
