package com.example.deckbridge.logging

import android.util.Log
import com.example.deckbridge.BuildConfig
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight

/**
 * **Single Logcat tag:** `DeckBridge` — filter in Android Studio or `adb logcat -s DeckBridge`.
 *
 * Categories (prefix only; same tag for easy filtering):
 * - `[STATE]` — lifecycle, platform, calibration load/save, keyboards/devices
 * - `[INPUT]` — verbose key/motion lines (**debug APK only**; suppressed in release)
 * - `[MATCH]` — logical control resolved from calibration (pad / knob rotate / knob press / release)
 * - `[UI]` — mirror highlight + on-screen deck highlight
 * - `[PERF]` — pipeline timings (match → state apply), throttled
 * - `[CAL]` — calibration wizard steps and skips
 * - `[KNOB]` — knob matched → logical intent / tile / dispatch
 * - `[ACTION]` — resolved shortcut dispatch (no HID yet)
 */
object DeckBridgeLog {
    const val TAG = "DeckBridge"

    fun state(message: String) {
        Log.i(TAG, "[STATE] $message")
    }

    fun cal(message: String) {
        Log.i(TAG, "[CAL] $message")
    }

    /** Knob → deck intent dispatch (distinct from generic [INPUT] noise). */
    fun knob(message: String) {
        Log.i(TAG, "[KNOB] $message")
    }

    /** One structured line after hardware matching (replaces scattered duplicates). */
    fun matchResolved(
        diag: HardwareDiagSummary,
        keyCode: Int?,
        consumeDeckRouting: Boolean,
    ) {
        val control = diag.control?.let { formatControl(it) } ?: diag.controlLabel
        val keyPart = keyCode?.takeIf { it >= 0 }?.toString() ?: "—"
        Log.d(
            TAG,
            "[MATCH] kind=${diag.kind} control=$control key=$keyPart via=${diag.matchedAs} consumeRouting=$consumeDeckRouting",
        )
    }

    fun ui(message: String) {
        Log.d(TAG, "[UI] $message")
    }

    fun uiMirror(h: HardwareMirrorHighlight) {
        Log.d(
            TAG,
            "[UI] mirror kind=${h.kind} control=${formatControl(h.control)} untilMs=${h.untilEpochMs}",
        )
    }

    fun action(message: String) {
        Log.i(TAG, "[ACTION] $message")
    }

    /** Verbose per-key / motion line — DEBUG builds only (avoids Logcat spam). */
    fun inputVerbose(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[INPUT] $message")
        }
    }

    fun perf(message: String) {
        Log.d(TAG, "[PERF] $message")
    }

    fun perfPipeline(matchMs: Long, stateApplyMs: Long) {
        Log.d(TAG, "[PERF] keyPipeline match=${matchMs}ms stateApply=${stateApplyMs}ms")
    }

    fun perfMotionPipeline(matchMs: Long, stateApplyMs: Long) {
        Log.d(TAG, "[PERF] motionPipeline match=${matchMs}ms stateApply=${stateApplyMs}ms")
    }

    private fun formatControl(c: HardwareControlId?): String = when (c) {
        is HardwareControlId.PadKey -> "pad r=${c.row} c=${c.col}"
        is HardwareControlId.Knob -> "knob idx=${c.index}"
        null -> "—"
    }
}
