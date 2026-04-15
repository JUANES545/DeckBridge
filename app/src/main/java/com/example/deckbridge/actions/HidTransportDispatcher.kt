package com.example.deckbridge.actions

import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.domain.model.ResolvedActionKind
import com.example.deckbridge.hid.HidGadgetSession
import com.example.deckbridge.hid.HidKeyboardSender
import com.example.deckbridge.hid.HidMediaSender
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sends [ResolvedAction] to Linux gadget HID nodes when [HidGadgetSession] probe succeeded.
 * Falls back to [loggingFallback] when transport is unavailable or send fails.
 */
class HidTransportDispatcher(
    private val gadgetSession: HidGadgetSession,
    private val loggingFallback: LoggingActionDispatcher,
) : ActionDispatcher {

    @Volatile
    private var keyboardReady: Boolean = false

    @Volatile
    private var consumerReady: Boolean = false

    /** When false, never touch `/dev/hidg*` (Settings “Modo HID al PC”). */
    @Volatile
    private var hidPcModeEnabled: Boolean = false

    fun updateTransportState(keyboard: Boolean, consumer: Boolean) {
        keyboardReady = keyboard
        consumerReady = consumer
    }

    fun setHidPcModeEnabled(enabled: Boolean) {
        hidPcModeEnabled = enabled
    }

    override suspend fun dispatch(resolved: ResolvedAction): Result<Unit> = withContext(Dispatchers.IO) {
        when (resolved.kind) {
            ResolvedActionKind.KEY_CHORD -> dispatchKeyboardChord(resolved)
            ResolvedActionKind.SYSTEM_MEDIA -> dispatchMedia(resolved)
            ResolvedActionKind.TEXT,
            ResolvedActionKind.KEY,
            -> {
                DeckBridgeLog.hid("skip HID for ${resolved.kind} intent=${resolved.intentId} → log fallback")
                loggingFallback.dispatch(resolved)
            }
            ResolvedActionKind.NOOP -> {
                DeckBridgeLog.hid("noop intent=${resolved.intentId}")
            }
        }
        Result.success(Unit)
    }

    private suspend fun dispatchKeyboardChord(resolved: ResolvedAction) {
        if (!hidPcModeEnabled) {
            DeckBridgeLog.hid("skip hidPcMode=off intent=${resolved.intentId} shortcut=${resolved.shortcutDisplay}")
            loggingFallback.dispatch(resolved)
            return
        }
        if (!keyboardReady) {
            DeckBridgeLog.hid(
                "skip keyboard not ready path=${gadgetSession.keyboardDevicePath} intent=${resolved.intentId} shortcut=${resolved.shortcutDisplay} → PC gets nothing without gadget HID; see prior [HID] probe/diagnosis lines",
            )
            loggingFallback.dispatch(resolved)
            return
        }
        val pair = HidKeyboardSender.chordPressRelease(resolved.intentId, resolved.platform)
        if (pair == null) {
            DeckBridgeLog.hid("no mapping intent=${resolved.intentId}")
            loggingFallback.dispatch(resolved)
            return
        }
        val (down, up) = pair
        gadgetSession.sendKeyboardReport(down).fold(
            onSuccess = {
                delay(KEY_FRAME_GAP_MS)
                gadgetSession.sendKeyboardReport(up).fold(
                    onSuccess = {
                        DeckBridgeLog.hid("keyboard OK ${resolved.intentId} ${resolved.shortcutDisplay} bytes=${down.joinToString { b -> "%02x".format(b) }}")
                    },
                    onFailure = { e ->
                        DeckBridgeLog.hid("keyboard release failed ${e.message}")
                        loggingFallback.dispatch(resolved)
                    },
                )
            },
            onFailure = { e ->
                DeckBridgeLog.hid("keyboard press failed ${e.message}")
                loggingFallback.dispatch(resolved)
            },
        )
    }

    private suspend fun dispatchMedia(resolved: ResolvedAction) {
        if (!hidPcModeEnabled) {
            DeckBridgeLog.hid("skip hidPcMode=off intent=${resolved.intentId}")
            loggingFallback.dispatch(resolved)
            return
        }
        if (!consumerReady) {
            DeckBridgeLog.hid(
                "skip media (no consumer gadget) path=${gadgetSession.consumerDevicePath} intent=${resolved.intentId} → volume/play need /dev/hidg1 (or configured consumer path)",
            )
            loggingFallback.dispatch(resolved)
            return
        }
        val pair = HidMediaSender.reportForIntentId(resolved.intentId)
        if (pair == null) {
            loggingFallback.dispatch(resolved)
            return
        }
        val (down, up) = pair
        gadgetSession.sendConsumerReport(down).fold(
            onSuccess = {
                delay(KEY_FRAME_GAP_MS)
                gadgetSession.sendConsumerReport(up).fold(
                    onSuccess = {
                        DeckBridgeLog.hid("consumer OK ${resolved.intentId} ${resolved.shortcutDisplay}")
                    },
                    onFailure = { e ->
                        DeckBridgeLog.hid("consumer release failed ${e.message}")
                        loggingFallback.dispatch(resolved)
                    },
                )
            },
            onFailure = { e ->
                DeckBridgeLog.hid("consumer press failed ${e.message}")
                loggingFallback.dispatch(resolved)
            },
        )
    }

    companion object {
        private const val KEY_FRAME_GAP_MS = 16L
    }
}
