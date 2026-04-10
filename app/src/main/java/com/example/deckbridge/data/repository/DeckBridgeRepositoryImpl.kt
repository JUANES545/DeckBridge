package com.example.deckbridge.data.repository

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.deckbridge.actions.ActionDispatcher
import com.example.deckbridge.data.deck.DeckCatalog
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.data.preferences.readPersistedHostPlatform
import com.example.deckbridge.data.preferences.writePersistedHostPlatform
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.DECK_HIGHLIGHT_DURATION_MS
import com.example.deckbridge.domain.model.DeckActivationLogEntry
import com.example.deckbridge.domain.model.DeckButtonHighlight
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.InputEventSource
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.RecentInputEvent
import com.example.deckbridge.domain.model.macroButtonIdForKeyCode
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardCaptureRules
import com.example.deckbridge.input.KeyboardKeyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class DeckBridgeRepositoryImpl(
    appContext: Context,
    private val externalScope: CoroutineScope,
    private val actionDispatcher: ActionDispatcher,
    private val dataStore: DataStore<Preferences>,
) : DeckBridgeRepository {

    private val inputManager: InputManager =
        appContext.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val _appState = MutableStateFlow(MockAppStateFactory.runtimeBootstrap())

    private var clearHighlightJob: Job? = null

    init {
        externalScope.launch {
            val stored = dataStore.readPersistedHostPlatform()
            _appState.update { prev -> DeckCatalog.withHostPlatform(prev, stored) }
        }
    }

    override val appState: StateFlow<AppState> = _appState.asStateFlow()

    override fun notifyKeyEvent(event: KeyEvent) {
        if (!KeyboardCaptureRules.shouldRecord(event)) return

        val device = event.device ?: InputDevice.getDevice(event.deviceId)
        var snapshot = InputDeviceSnapshotFactory.from(device)
        if (snapshot.deviceId < 0 && event.deviceId >= 0) {
            snapshot = snapshot.copy(deviceId = event.deviceId)
        }
        val classification = InputDeviceSnapshotFactory.classify(snapshot)
        val motion = KeyboardKeyFormatter.motionOf(event.action) ?: return

        val newEvent = RecentInputEvent(
            id = UUID.randomUUID().toString(),
            occurredAtEpochMs = System.currentTimeMillis(),
            motion = motion,
            keyCode = event.keyCode,
            keyLabel = KeyboardKeyFormatter.friendlyLabel(event.keyCode),
            keyCodeName = KeyboardKeyFormatter.keyCodeName(event.keyCode),
            device = snapshot,
            classification = classification,
            source = InputEventSource.HARDWARE,
        )

        val detected = scanExternalKeyboards()

        _appState.update { prev ->
            val merged = listOf(newEvent) + prev.recentInputEvents.take(MAX_RECENT_EVENTS - 1)
            val keyboard = resolveKeyboardStatus(detected, classification, snapshot, prev.physicalKeyboard)
            prev.copy(
                recentInputEvents = merged,
                physicalKeyboard = keyboard,
                inputDiagnostics = prev.inputDiagnostics.copy(
                    lastEventDevice = snapshot,
                    lastEventAtEpochMs = newEvent.occurredAtEpochMs,
                    lastMotion = motion,
                    lastKeyCode = event.keyCode,
                    lastClassification = classification,
                    detectedExternalKeyboards = detected,
                    hintLine = hintFor(detected),
                ),
                systemStatusLine = STATUS_CAPTURE_ACTIVE,
            )
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val buttonId = _appState.value.physicalBindingsPreview.macroButtonIdForKeyCode(event.keyCode)
            if (buttonId != null) {
                triggerDeckButton(buttonId, ButtonTriggerSource.HARDWARE_KEY)
            }
        }
    }

    override fun refreshAttachedKeyboards() {
        val detected = scanExternalKeyboards()
        _appState.update { prev ->
            prev.copy(
                physicalKeyboard = resolveKeyboardStatusFromScan(detected),
                inputDiagnostics = prev.inputDiagnostics.copy(
                    detectedExternalKeyboards = detected,
                    hintLine = hintFor(detected),
                ),
            )
        }
    }

    override fun setHostPlatform(platform: HostPlatform) {
        val target = when (platform) {
            HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
            else -> platform
        }
        externalScope.launch {
            dataStore.writePersistedHostPlatform(target)
            _appState.update { prev -> DeckCatalog.withHostPlatform(prev, target) }
        }
    }

    override fun triggerDeckButton(buttonId: String, source: ButtonTriggerSource) {
        val snapshot = _appState.value
        val button = snapshot.macroButtons.find { it.id == buttonId } ?: return
        val physicalKeyLabel =
            snapshot.physicalBindingsPreview.firstOrNull { it.macroButtonId == buttonId }?.keyLabel

        val resolved = PlatformActionResolver.resolve(button.intent, snapshot.hostPlatform)

        val entry = DeckActivationLogEntry(
            id = UUID.randomUUID().toString(),
            occurredAtEpochMs = System.currentTimeMillis(),
            buttonId = buttonId,
            buttonLabel = button.label,
            hostPlatform = resolved.platform,
            intentLabel = resolved.intentDisplayName,
            resolvedShortcut = resolved.shortcutDisplay,
            physicalKeyLabel = physicalKeyLabel,
            source = source,
        )

        clearHighlightJob?.cancel()
        _appState.update { prev ->
            prev.copy(
                deckHighlight = DeckButtonHighlight(buttonId),
                recentDeckActivations = listOf(entry) + prev.recentDeckActivations.take(MAX_ACTIVATIONS - 1),
            )
        }

        clearHighlightJob = externalScope.launch {
            delay(DECK_HIGHLIGHT_DURATION_MS)
            _appState.update { prev ->
                if (prev.deckHighlight?.buttonId == buttonId) {
                    prev.copy(deckHighlight = null)
                } else {
                    prev
                }
            }
        }

        externalScope.launch {
            runCatching { actionDispatcher.dispatch(resolved) }
        }
    }

    private fun scanExternalKeyboards(): List<InputDeviceSnapshot> {
        return inputManager.inputDeviceIds
            .asSequence()
            .mapNotNull { InputDevice.getDevice(it) }
            .map { InputDeviceSnapshotFactory.from(it) }
            .filter { InputDeviceSnapshotFactory.classify(it) == KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD }
            .distinctBy { it.deviceId }
            .toList()
    }

    private fun resolveKeyboardStatusFromScan(
        detected: List<InputDeviceSnapshot>,
    ): PhysicalKeyboardStatus {
        val first = detected.firstOrNull()
        return if (first != null) {
            PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.CONNECTED,
                deviceName = first.name,
                detail = "Teclado externo detectado (${detected.size}). Último id=${first.deviceId}",
            )
        } else {
            PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = "Sin teclado externo con fuente keyboard. Conecta USB o Bluetooth y vuelve a abrir la app.",
            )
        }
    }

    private fun resolveKeyboardStatus(
        detected: List<InputDeviceSnapshot>,
        classification: KeyboardInputClassification,
        snapshot: InputDeviceSnapshot,
        previous: PhysicalKeyboardStatus,
    ): PhysicalKeyboardStatus {
        val fromScan = resolveKeyboardStatusFromScan(detected)
        if (fromScan.state == PhysicalKeyboardConnectionState.CONNECTED) return fromScan
        return if (classification == KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD) {
            PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.CONNECTED,
                deviceName = snapshot.name,
                detail = "Evento desde teclado externo (deviceId=${snapshot.deviceId})",
            )
        } else {
            previous
        }
    }

    private fun hintFor(detected: List<InputDeviceSnapshot>): String {
        return if (detected.isEmpty()) {
            "Ningún teclado externo enumerado todavía. Deja la app en primer plano y pulsa F1–F9."
        } else {
            "Teclados externos: ${detected.size}. Último visto: ${detected.first().name} (id=${detected.first().deviceId})."
        }
    }

    companion object {
        private const val MAX_RECENT_EVENTS = 25
        private const val MAX_ACTIVATIONS = 25
        private const val STATUS_CAPTURE_ACTIVE =
            "Captura hardware activa · Mantén la app en primer plano para ver KeyEvents"
    }
}
