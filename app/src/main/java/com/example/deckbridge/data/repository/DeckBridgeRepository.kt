package com.example.deckbridge.data.repository

import android.view.KeyEvent
import android.view.MotionEvent
import com.example.deckbridge.domain.hardware.CalibrationSessionUi
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry for app-wide snapshot; later splits into profile/input/usb sources.
 */
interface DeckBridgeRepository {
    val appState: StateFlow<AppState>

    /** Wizard session while learning pad + knobs; null when idle. */
    val calibrationSession: StateFlow<CalibrationSessionUi?>

    /** Called from [android.app.Activity.dispatchKeyEvent] while the app is foreground. */
    fun notifyKeyEvent(event: KeyEvent)

    /** Called from [android.app.Activity.dispatchGenericMotionEvent] for scroll / rotary diagnostics. */
    fun notifyGenericMotionEvent(event: MotionEvent)

    /** Refresh enumerated keyboards; invoke on resume and after hot-plug (future). */
    fun refreshAttachedKeyboards()

    /**
     * Single activation path for deck tiles (hardware binding, touch, or simulated).
     * Updates highlight, activation log, and dispatches [com.example.deckbridge.actions.ActionDispatcher].
     */
    fun triggerDeckButton(buttonId: String, source: ButtonTriggerSource)

    /** Persists and reapplies deck layout for the selected host OS (Windows / macOS). */
    fun setHostPlatform(platform: HostPlatform)

    /** When true, host platform follows [HostOsDetector] (usually UNKNOWN + manual hint). */
    fun setHostAutoDetect(enabled: Boolean)

    /** Re-probes USB gadget HID nodes and host USB stickiness; call on resume / USB events. */
    fun refreshHostAndTransport()

    /** Starts the guided hardware calibration wizard (foreground + physical device). */
    fun startHardwareCalibration()

    /** Abandons in-progress calibration without changing saved mapping. */
    fun cancelHardwareCalibration()

    /**
     * If the wizard is on a **knob press** step and the hardware sends no key, advance without a press mapping.
     * @return true if a step was skipped.
     */
    fun skipKnobPressCalibrationStep(): Boolean
}
