package com.example.deckbridge.logging

import android.util.Log
import com.example.deckbridge.BuildConfig
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight

/**
 * **Single Logcat tag:** `DeckBridge` — filter in Android Studio or `adb logcat -s DeckBridge`.
 *
 * Also mirrors structured lines to a **per-process session file** under [SessionFileLog] when initialized.
 *
 * Categories (prefix only; same tag for easy filtering):
 * - `[STATE]` — lifecycle, platform, calibration load/save, keyboards/devices
 * - `[INPUT]` — verbose key/motion lines (**debug APK only**; suppressed in release)
 * - `[MATCH]` — logical control resolved from calibration (pad / knob rotate / knob press / release)
 * - `[UI]` — mirror highlight + on-screen deck highlight
 * - `[PERF]` — pipeline timings (match → state apply), throttled
 * - `[CAL]` — calibration wizard steps and skips
 * - `[KNOB]` — knob matched → logical intent / tile / dispatch
 * - `[ACTION]` — resolved shortcut dispatch (logging fallback / audit)
 * - `[HID]` — USB gadget HID transport (probe with paths exists/writable, diagnosis when NO_NODES,
 *   `[DBG]` one-line snapshot = same as Settings HID debug strip, skip reasons, successful sends)
 * - `[LAN]` — HTTP agent on the PC (UDP discovery, health probe, POST /action, failures)
 * - `[QR]` — pairing QR / deeplink scan, claim, and poll milestones (filter: `adb logcat -s DeckBridge | grep QR`)
 */
object DeckBridgeLog {
    const val TAG = "DeckBridge"

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        else -> "INFO"
    }

    private fun emit(priority: Int, category: String, message: String) {
        val line = "[$category] $message"
        Log.println(priority, TAG, line)
        SessionFileLog.append(priorityLabel(priority), category, message)
    }

    fun state(message: String) {
        emit(Log.INFO, "STATE", message)
    }

    fun cal(message: String) {
        emit(Log.INFO, "CAL", message)
    }

    /** Knob → deck intent dispatch (distinct from generic [INPUT] noise). */
    fun knob(message: String) {
        emit(Log.INFO, "KNOB", message)
    }

    /** One structured line after hardware matching (replaces scattered duplicates). */
    fun matchResolved(
        diag: HardwareDiagSummary,
        keyCode: Int?,
        consumeDeckRouting: Boolean,
    ) {
        val control = diag.control?.let { formatControl(it) } ?: diag.controlLabel
        val keyPart = keyCode?.takeIf { it >= 0 }?.toString() ?: "—"
        emit(
            Log.DEBUG,
            "MATCH",
            "kind=${diag.kind} control=$control key=$keyPart via=${diag.matchedAs} consumeRouting=$consumeDeckRouting",
        )
    }

    fun ui(message: String) {
        emit(Log.DEBUG, "UI", message)
    }

    fun uiMirror(h: HardwareMirrorHighlight) {
        emit(
            Log.DEBUG,
            "UI",
            "mirror kind=${h.kind} control=${formatControl(h.control)} untilMs=${h.untilEpochMs}",
        )
    }

    fun action(message: String) {
        emit(Log.INFO, "ACTION", message)
    }

    fun hid(message: String) {
        emit(Log.INFO, "HID", message)
    }

    fun lan(message: String) {
        emit(Log.INFO, "LAN", message)
    }

    /** Pairing QR / deeplink flow (Android scanner → LAN claim → poll). */
    fun qr(message: String) {
        emit(Log.INFO, "QR", message)
    }

    /** Verbose per-key / motion line — DEBUG builds only (avoids Logcat spam). */
    fun inputVerbose(message: String) {
        if (BuildConfig.DEBUG) {
            emit(Log.DEBUG, "INPUT", message)
        }
    }

    fun perf(message: String) {
        emit(Log.DEBUG, "PERF", message)
    }

    fun perfPipeline(matchMs: Long, stateApplyMs: Long) {
        emit(Log.DEBUG, "PERF", "keyPipeline match=${matchMs}ms stateApply=${stateApplyMs}ms")
    }

    fun perfMotionPipeline(matchMs: Long, stateApplyMs: Long) {
        emit(Log.DEBUG, "PERF", "motionPipeline match=${matchMs}ms stateApply=${stateApplyMs}ms")
    }

    private fun formatControl(c: HardwareControlId?): String = when (c) {
        is HardwareControlId.PadKey -> "pad r=${c.row} c=${c.col}"
        is HardwareControlId.Knob -> "knob idx=${c.index}"
        null -> "—"
    }
}
