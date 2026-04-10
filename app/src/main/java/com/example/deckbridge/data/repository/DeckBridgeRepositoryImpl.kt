package com.example.deckbridge.data.repository

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.deckbridge.R
import com.example.deckbridge.actions.ActionDispatcher
import com.example.deckbridge.data.deck.DeckCatalog
import com.example.deckbridge.data.hardware.HardwareBridge
import com.example.deckbridge.data.hardware.HardwareCalibrationJson
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.data.preferences.readHardwareCalibrationJson
import com.example.deckbridge.data.preferences.readPersistedHostPlatform
import com.example.deckbridge.data.preferences.writeHardwareCalibrationJson
import com.example.deckbridge.data.preferences.writePersistedHostPlatform
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.domain.hardware.CalibrationSessionUi
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.DECK_HIGHLIGHT_DURATION_MS
import com.example.deckbridge.domain.model.DeckActivationLogEntry
import com.example.deckbridge.domain.model.DeckButtonHighlight
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.macroButtonIdForKeyCode
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardCaptureRules
import com.example.deckbridge.input.KeyboardKeyFormatter
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DeckBridgeRepositoryImpl(
    appContext: Context,
    private val externalScope: CoroutineScope,
    private val actionDispatcher: ActionDispatcher,
    private val dataStore: DataStore<Preferences>,
) : DeckBridgeRepository {

    private val app = appContext.applicationContext
    private val res = app.resources

    private val inputManager: InputManager =
        app.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _appState = MutableStateFlow(MockAppStateFactory.runtimeBootstrap(res))

    private var clearHighlightJob: Job? = null
    private var mirrorClearJob: Job? = null
    private var keyboardScanJob: Job? = null
    private var deviceRefreshJob: Job? = null

    @Volatile
    private var lastKeyboardScanElapsedRealtime: Long = 0L

    private val lastKnobMotionRealtime = LongArray(3) { 0L }

    private val hardwareBridge = HardwareBridge(app, externalScope) { cfg ->
        DeckBridgeLog.state("calibration persisted complete=${cfg.isComplete} pads=${cfg.padKeyCodes.size}")
        withContext(Dispatchers.IO) {
            dataStore.writeHardwareCalibrationJson(HardwareCalibrationJson.encode(cfg))
        }
        _appState.update { it.copy(hardwareCalibration = cfg) }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            val dev = InputDevice.getDevice(deviceId) ?: return
            DeckBridgeLog.state(
                "inputDevice added id=$deviceId name=\"${dev.name}\"",
            )
            scheduleDebouncedKeyboardRefresh()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            DeckBridgeLog.state("inputDevice removed id=$deviceId")
            scheduleDebouncedKeyboardRefresh()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            DeckBridgeLog.inputVerbose("inputDevice changed id=$deviceId")
        }
    }

    init {
        inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler)

        externalScope.launch {
            val stored = dataStore.readPersistedHostPlatform()
            DeckBridgeLog.state("loaded hostPlatform=$stored (applying deck profile)")
            _appState.update { prev -> DeckCatalog.withHostPlatform(prev, stored, res) }
        }
        externalScope.launch {
            val json = dataStore.readHardwareCalibrationJson()
            val cfg = HardwareCalibrationJson.decode(json)
            hardwareBridge.setLoadedConfig(cfg)
            if (cfg != null) {
                DeckBridgeLog.state(
                    "loaded calibration pads=${cfg.padKeyCodes.size} knobs=${cfg.knobs.size} complete=${cfg.isComplete}",
                )
                _appState.update { it.copy(hardwareCalibration = cfg) }
            } else {
                DeckBridgeLog.state("no saved calibration")
            }
        }
    }

    private fun scheduleDebouncedKeyboardRefresh() {
        deviceRefreshJob?.cancel()
        deviceRefreshJob = externalScope.launch {
            delay(DEVICE_REFRESH_DEBOUNCE_MS)
            refreshAttachedKeyboards()
        }
    }

    override val appState: StateFlow<AppState> = _appState.asStateFlow()

    override val calibrationSession: StateFlow<CalibrationSessionUi?> =
        hardwareBridge.calibrationSession

    override fun notifyKeyEvent(event: KeyEvent) {
        val t0 = SystemClock.elapsedRealtime()
        val hw = hardwareBridge.handleKeyEvent(event)
        val tMatch = SystemClock.elapsedRealtime()

        val recordRecent = KeyboardCaptureRules.shouldRecord(event)

        DeckBridgeLog.inputVerbose(
            "KeyEvent action=${event.action} keyCode=${event.keyCode} deviceId=${event.deviceId} record=$recordRecent",
        )

        hw.diagSummary?.let { d ->
            DeckBridgeLog.matchResolved(d, event.keyCode, hw.consumeDeckKeyRouting)
        }
        hw.mirrorHighlight?.let { DeckBridgeLog.uiMirror(it) }

        // Fast path: mirror + raw + diag + lastKeyCode only (no InputDevice snapshot / keyboard merge).
        _appState.update { prev ->
            val mergedRaw = listOf(hw.rawLine) + prev.rawInputDiagnostics.take(MAX_RAW_DIAGNOSTICS - 1)
            prev.copy(
                rawInputDiagnostics = mergedRaw,
                hardwareDiagSummary = hw.diagSummary ?: prev.hardwareDiagSummary,
                hardwareMirrorHighlight = hw.mirrorHighlight ?: prev.hardwareMirrorHighlight,
                inputDiagnostics = prev.inputDiagnostics.copy(lastKeyCode = event.keyCode),
            )
        }
        val tFast = SystemClock.elapsedRealtime()

        if (hw.mirrorHighlight != null || hw.diagSummary != null) {
            DeckBridgeLog.perfPipeline(matchMs = tMatch - t0, stateApplyMs = tFast - t0)
        }

        if (recordRecent) {
            externalScope.launch {
                val device = event.device ?: InputDevice.getDevice(event.deviceId)
                val snap = withContext(Dispatchers.Default) {
                    var s = InputDeviceSnapshotFactory.from(device)
                    if (s.deviceId < 0 && event.deviceId >= 0) {
                        s = s.copy(deviceId = event.deviceId)
                    }
                    val classification = InputDeviceSnapshotFactory.classify(s)
                    val motion = KeyboardKeyFormatter.motionOf(event.action)
                    Triple(s, classification, motion)
                }
                val (snapshot, classification, motion) = snap
                val nowMs = System.currentTimeMillis()
                val detectedStale = _appState.value.inputDiagnostics.detectedExternalKeyboards
                _appState.update { prev ->
                    val keyboard = resolveKeyboardStatus(
                        detectedStale,
                        classification,
                        snapshot,
                        prev.physicalKeyboard,
                    )
                    val inputDiag = if (motion != null) {
                        prev.inputDiagnostics.copy(
                            lastEventDevice = snapshot,
                            lastEventAtEpochMs = nowMs,
                            lastMotion = motion,
                            lastKeyCode = event.keyCode,
                            lastClassification = classification,
                            detectedExternalKeyboards = detectedStale,
                            hintLine = hintFor(detectedStale),
                        )
                    } else {
                        prev.inputDiagnostics
                    }
                    prev.copy(
                        physicalKeyboard = keyboard,
                        inputDiagnostics = inputDiag,
                        systemStatusLine = app.getString(R.string.status_capture_active),
                    )
                }

                runThrottledKeyboardScan(classification, snapshot)
            }
        }

        if (hw.mirrorHighlight != null) {
            scheduleHardwareMirrorClear(hw.mirrorHighlight.untilEpochMs)
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val pad = hw.mirrorHighlight?.control as? HardwareControlId.PadKey
            when {
                pad != null -> {
                    val snap = _appState.value
                    val cfg = snap.hardwareCalibration
                    if (cfg != null && cfg.isComplete) {
                        val buttons = snap.macroButtons.sortedBy { it.sortIndex }
                        val idx = pad.row * 3 + pad.col
                        if (idx in buttons.indices) {
                            triggerDeckButton(buttons[idx].id, ButtonTriggerSource.HARDWARE_KEY)
                        }
                    }
                }
                hw.knobDeckIntent != null -> {
                    val kIdx = (hw.diagSummary?.control as? HardwareControlId.Knob)?.index
                    tryDispatchKnobFromHardware(
                        intent = hw.knobDeckIntent,
                        keyEvent = event,
                        motionKnobIndex = null,
                        interactionKind = hw.diagSummary?.kind ?: "knob",
                        knobIndexForLog = kIdx,
                    )
                }
                !hw.consumeDeckKeyRouting -> {
                    val buttonId = _appState.value.physicalBindingsPreview.macroButtonIdForKeyCode(event.keyCode)
                    if (buttonId != null) {
                        triggerDeckButton(buttonId, ButtonTriggerSource.HARDWARE_KEY)
                    }
                }
            }
        }
    }

    private fun tryDispatchKnobFromHardware(
        intent: DeckButtonIntent?,
        keyEvent: KeyEvent?,
        motionKnobIndex: Int?,
        interactionKind: String,
        knobIndexForLog: Int?,
    ) {
        if (intent == null) return
        if (keyEvent != null && keyEvent.repeatCount != 0) return
        if (motionKnobIndex != null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastKnobMotionRealtime[motionKnobIndex] < KNOB_MOTION_THROTTLE_MS) return
            lastKnobMotionRealtime[motionKnobIndex] = now
        }
        val snapshot = _appState.value
        val resolved = PlatformActionResolver.resolve(intent, snapshot.hostPlatform)
        val button = snapshot.macroButtons.find { it.intent == intent }
        DeckBridgeLog.knob(
            "match=$interactionKind idx=${knobIndexForLog ?: motionKnobIndex} " +
                "intent=${resolved.intentDisplayName} shortcut=${resolved.shortcutDisplay} tile=${button?.id ?: "none"}",
        )
        if (button != null) {
            triggerDeckButton(button.id, ButtonTriggerSource.HARDWARE_KNOB)
        } else {
            externalScope.launch {
                runCatching { actionDispatcher.dispatch(resolved) }
            }
        }
    }

    private fun runThrottledKeyboardScan(
        classification: KeyboardInputClassification,
        snapshot: InputDeviceSnapshot,
    ) {
        keyboardScanJob?.cancel()
        keyboardScanJob = externalScope.launch {
            val nowRt = SystemClock.elapsedRealtime()
            val elapsed = nowRt - lastKeyboardScanElapsedRealtime
            if (elapsed < KEYBOARD_SCAN_MIN_INTERVAL_MS && lastKeyboardScanElapsedRealtime != 0L) {
                DeckBridgeLog.inputVerbose("keyboardScan skipped throttle ${elapsed}ms")
                return@launch
            }
            lastKeyboardScanElapsedRealtime = nowRt
            val tScan = SystemClock.elapsedRealtime()
            val detected = scanExternalKeyboards()
            DeckBridgeLog.state("keyboardScan externalCount=${detected.size}")
            DeckBridgeLog.perf("scanExternalKeyboards ${SystemClock.elapsedRealtime() - tScan}ms")
            _appState.update { prev ->
                prev.copy(
                    physicalKeyboard = resolveKeyboardStatus(
                        detected,
                        classification,
                        snapshot,
                        prev.physicalKeyboard,
                    ),
                    inputDiagnostics = prev.inputDiagnostics.copy(
                        detectedExternalKeyboards = detected,
                        hintLine = hintFor(detected),
                    ),
                )
            }
        }
    }

    override fun notifyGenericMotionEvent(event: MotionEvent) {
        val t0 = SystemClock.elapsedRealtime()
        DeckBridgeLog.inputVerbose(
            "MotionEvent action=${event.actionMasked} source=0x${event.source.toString(16)} id=${event.deviceId}",
        )
        val hw = hardwareBridge.handleGenericMotionEvent(event)
        val tMatch = SystemClock.elapsedRealtime()

        hw.diagSummary?.let { d ->
            DeckBridgeLog.matchResolved(d, keyCode = null, consumeDeckRouting = false)
        }
        hw.mirrorHighlight?.let { DeckBridgeLog.uiMirror(it) }

        _appState.update { prev ->
            val mergedRaw = listOf(hw.rawLine) + prev.rawInputDiagnostics.take(MAX_RAW_DIAGNOSTICS - 1)
            prev.copy(
                rawInputDiagnostics = mergedRaw,
                hardwareDiagSummary = hw.diagSummary ?: prev.hardwareDiagSummary,
                hardwareMirrorHighlight = hw.mirrorHighlight ?: prev.hardwareMirrorHighlight,
            )
        }
        val tFast = SystemClock.elapsedRealtime()

        if (hw.mirrorHighlight != null || hw.diagSummary != null) {
            DeckBridgeLog.perfMotionPipeline(matchMs = tMatch - t0, stateApplyMs = tFast - t0)
        }

        if (hw.mirrorHighlight != null) {
            scheduleHardwareMirrorClear(hw.mirrorHighlight.untilEpochMs)
        }

        val knobControl = hw.diagSummary?.control as? HardwareControlId.Knob
        if (hw.knobDeckIntent != null && knobControl != null) {
            tryDispatchKnobFromHardware(
                intent = hw.knobDeckIntent,
                keyEvent = null,
                motionKnobIndex = knobControl.index,
                interactionKind = hw.diagSummary?.kind ?: "motion",
                knobIndexForLog = knobControl.index,
            )
        }
    }

    override fun startHardwareCalibration() {
        hardwareBridge.startCalibration()
    }

    override fun cancelHardwareCalibration() {
        hardwareBridge.cancelCalibration()
    }

    override fun skipKnobPressCalibrationStep(): Boolean =
        hardwareBridge.skipCurrentKnobPressIfApplicable()

    override fun refreshAttachedKeyboards() {
        val scanStart = SystemClock.elapsedRealtime()
        val detected = scanExternalKeyboards()
        lastKeyboardScanElapsedRealtime = scanStart
        DeckBridgeLog.state("refreshAttachedKeyboards count=${detected.size}")
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
            DeckBridgeLog.state("hostPlatform set=$target (deck profile + shortcuts)")
            _appState.update { prev -> DeckCatalog.withHostPlatform(prev, target, res) }
        }
    }

    override fun triggerDeckButton(buttonId: String, source: ButtonTriggerSource) {
        val snapshot = _appState.value
        val button = snapshot.macroButtons.find { it.id == buttonId } ?: return
        val physicalKeyLabel =
            snapshot.physicalBindingsPreview.firstOrNull { it.macroButtonId == buttonId }?.keyLabel

        val resolved = PlatformActionResolver.resolve(button.intent, snapshot.hostPlatform)

        DeckBridgeLog.ui(
            "deckTile id=$buttonId label=\"${button.label}\" source=$source shortcut=${resolved.shortcutDisplay}",
        )

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

    private fun scheduleHardwareMirrorClear(untilEpochMs: Long) {
        mirrorClearJob?.cancel()
        mirrorClearJob = externalScope.launch {
            val wait = (untilEpochMs - System.currentTimeMillis()).coerceIn(0L, MIRROR_CLEAR_MAX_WAIT_MS)
            delay(wait)
            _appState.update { prev ->
                if (prev.hardwareMirrorHighlight?.untilEpochMs == untilEpochMs) {
                    prev.copy(hardwareMirrorHighlight = null)
                } else {
                    prev
                }
            }
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
                detail = app.getString(
                    R.string.physical_kb_detail_connected_scan,
                    detected.size,
                    first.deviceId,
                ),
            )
        } else {
            PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = app.getString(R.string.physical_kb_detail_disconnected),
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
                detail = app.getString(
                    R.string.physical_kb_detail_from_event,
                    snapshot.deviceId,
                ),
            )
        } else {
            previous
        }
    }

    private fun hintFor(detected: List<InputDeviceSnapshot>): String {
        return if (detected.isEmpty()) {
            app.getString(R.string.input_hint_no_keyboards)
        } else {
            val d0 = detected.first()
            app.getString(
                R.string.input_hint_keyboards_summary,
                detected.size,
                d0.name,
                d0.deviceId,
            )
        }
    }

    companion object {
        private const val MAX_ACTIVATIONS = 25
        private const val MAX_RAW_DIAGNOSTICS = 60
        private const val KEYBOARD_SCAN_MIN_INTERVAL_MS = 900L
        private const val MIRROR_CLEAR_MAX_WAIT_MS = 400L
        private const val DEVICE_REFRESH_DEBOUNCE_MS = 400L
        private const val KNOB_MOTION_THROTTLE_MS = 220L
    }
}
