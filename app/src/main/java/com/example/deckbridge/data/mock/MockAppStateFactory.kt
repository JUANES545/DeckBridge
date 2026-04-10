package com.example.deckbridge.data.mock

import android.view.KeyEvent
import com.example.deckbridge.data.deck.DeckCatalog
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.InputDiagnostics
import com.example.deckbridge.domain.model.InputEventSource
import com.example.deckbridge.domain.model.KeyMotion
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.RecentInputEvent

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

    fun runtimeBootstrap(): AppState {
        return DeckCatalog.baseDeckAppState(HostPlatform.WINDOWS).copy(
            recentInputEvents = emptyList(),
            physicalKeyboard = PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = "Esperando enumeración de InputManager…",
            ),
            inputDiagnostics = InputDiagnostics(
                lastEventDevice = null,
                lastEventAtEpochMs = null,
                lastMotion = null,
                lastKeyCode = null,
                lastClassification = null,
                detectedExternalKeyboards = emptyList(),
                hintLine = "Abre la app en primer plano y conecta el teclado; usa F1–F9 para probar.",
            ),
            deckHighlight = null,
            recentDeckActivations = emptyList(),
            systemStatusLine = "MVP etapa 4 · Plataforma host + atajos resueltos + DataStore",
        )
    }

    fun initial(nowEpochMs: Long = System.currentTimeMillis()): AppState {
        val events = listOf(
            RecentInputEvent(
                id = "ev_1",
                occurredAtEpochMs = nowEpochMs - 4_200,
                motion = KeyMotion.DOWN,
                keyCode = KeyEvent.KEYCODE_F2,
                keyLabel = "F2",
                keyCodeName = "KEYCODE_F2",
                device = simulatedKeyboard,
                classification = KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD,
                source = InputEventSource.SIMULATED,
            ),
            RecentInputEvent(
                id = "ev_2",
                occurredAtEpochMs = nowEpochMs - 8_900,
                motion = KeyMotion.DOWN,
                keyCode = KeyEvent.KEYCODE_F1,
                keyLabel = "F1",
                keyCodeName = "KEYCODE_F1",
                device = simulatedKeyboard,
                classification = KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD,
                source = InputEventSource.SIMULATED,
            ),
            RecentInputEvent(
                id = "ev_3",
                occurredAtEpochMs = nowEpochMs - 9_050,
                motion = KeyMotion.UP,
                keyCode = KeyEvent.KEYCODE_F1,
                keyLabel = "F1",
                keyCodeName = "KEYCODE_F1",
                device = simulatedKeyboard,
                classification = KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD,
                source = InputEventSource.SIMULATED,
            ),
            RecentInputEvent(
                id = "ev_4",
                occurredAtEpochMs = nowEpochMs - 21_400,
                motion = KeyMotion.DOWN,
                keyCode = KeyEvent.KEYCODE_F3,
                keyLabel = "F3",
                keyCodeName = "KEYCODE_F3",
                device = simulatedKeyboard,
                classification = KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD,
                source = InputEventSource.SIMULATED,
            ),
        )

        return DeckCatalog.baseDeckAppState(HostPlatform.WINDOWS).copy(
            recentInputEvents = events,
            physicalKeyboard = PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.CONNECTED,
                deviceName = simulatedKeyboard.name,
                detail = "Datos simulados para preview",
            ),
            inputDiagnostics = InputDiagnostics(
                lastEventDevice = simulatedKeyboard,
                lastEventAtEpochMs = nowEpochMs - 4_200,
                lastMotion = KeyMotion.DOWN,
                lastKeyCode = KeyEvent.KEYCODE_F2,
                lastClassification = KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD,
                detectedExternalKeyboards = listOf(simulatedKeyboard),
                hintLine = "Vista previa: teclado macro simulado conectado.",
            ),
            deckHighlight = null,
            recentDeckActivations = emptyList(),
            systemStatusLine = "Vista previa · Datos simulados",
        )
    }
}
