package com.example.deckbridge.data.mock

import android.content.res.Resources
import android.view.KeyEvent
import com.example.deckbridge.R
import com.example.deckbridge.data.deck.DeckCatalog
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.InputDiagnostics
import com.example.deckbridge.domain.model.KeyMotion
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus

/**
 * Preview/demo overlays. Base deck layout lives in [DeckCatalog].
 */
object MockAppStateFactory {

    private val simulatedKeyboard = InputDeviceSnapshot(
        deviceId = 101,
        name = "Simulated Deck KB",
        descriptor = "mock-descriptor",
        vendorId = 0x1a2b,
        productId = 0x3c4d,
        sourcesFlags = android.view.InputDevice.SOURCE_KEYBOARD,
        sourcesLabel = "keyboard",
        isExternal = true,
        isVirtual = false,
    )

    fun runtimeBootstrap(res: Resources): AppState {
        return DeckCatalog.baseDeckAppState(HostPlatform.WINDOWS, res).copy(
            physicalKeyboard = PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = res.getString(R.string.mock_waiting_input_manager),
            ),
            inputDiagnostics = InputDiagnostics(
                lastEventDevice = null,
                lastEventAtEpochMs = null,
                lastMotion = null,
                lastKeyCode = null,
                lastClassification = null,
                detectedExternalKeyboards = emptyList(),
                hintLine = res.getString(R.string.input_hint_no_keyboards),
            ),
            deckHighlight = null,
            recentDeckActivations = emptyList(),
            systemStatusLine = res.getString(R.string.mock_runtime_bootstrap_status),
            hardwareCalibration = null,
            hardwareMirrorHighlight = null,
            hardwareDiagSummary = null,
            rawInputDiagnostics = emptyList(),
        )
    }

    fun initial(nowEpochMs: Long = System.currentTimeMillis(), res: Resources): AppState {
        return DeckCatalog.baseDeckAppState(HostPlatform.WINDOWS, res).copy(
            physicalKeyboard = PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.CONNECTED,
                deviceName = simulatedKeyboard.name,
                detail = res.getString(R.string.mock_preview_keyboard_detail),
            ),
            inputDiagnostics = InputDiagnostics(
                lastEventDevice = simulatedKeyboard,
                lastEventAtEpochMs = nowEpochMs - 4_200,
                lastMotion = KeyMotion.DOWN,
                lastKeyCode = KeyEvent.KEYCODE_F2,
                lastClassification = KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD,
                detectedExternalKeyboards = listOf(simulatedKeyboard),
                hintLine = res.getString(R.string.mock_preview_input_hint),
            ),
            deckHighlight = null,
            recentDeckActivations = emptyList(),
            systemStatusLine = res.getString(R.string.mock_preview_screen_status),
            hardwareCalibration = null,
            hardwareMirrorHighlight = null,
            hardwareDiagSummary = null,
            rawInputDiagnostics = emptyList(),
        )
    }
}
