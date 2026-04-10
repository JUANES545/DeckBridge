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
import com.example.deckbridge.actions.HidTransportDispatcher
import com.example.deckbridge.actions.ActionDispatcher
import com.example.deckbridge.data.deck.DeckCatalog
import com.example.deckbridge.data.hardware.HardwareBridge
import com.example.deckbridge.data.hardware.HardwareCalibrationJson
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.data.preferences.readHardwareCalibrationJson
import com.example.deckbridge.data.preferences.readHostAutoDetect
import com.example.deckbridge.data.preferences.readPersistedHostPlatform
import com.example.deckbridge.data.preferences.writeHardwareCalibrationJson
import com.example.deckbridge.data.preferences.writeHostAutoDetect
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
import com.example.deckbridge.domain.model.HostConnectionStatus
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.macroButtonIdForKeyCode
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardCaptureRules
import com.example.deckbridge.input.KeyboardKeyFormatter
import com.example.deckbridge.hid.HidGadgetSession
import com.example.deckbridge.host.HostOsDetector
import com.example.deckbridge.logging.DeckBridgeLog
import com.example.deckbridge.transport.HidTransportUiMapper
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
    private val hidTransportDispatcher: HidTransportDispatcher,
    private val hidGadgetSession: HidGadgetSession,
    private val dataStore: DataStore<Preferences>,
) : DeckBridgeRepository {

    private val actionDispatcher: ActionDispatcher = hidTransportDispatcher

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
    private var hostTransportRefreshJob: Job? = null

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

        scheduleHostAndTransportRefresh()
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
        externalScope.launch {
            dataStore.writeHostAutoDetect(false)
            dataStore.writePersistedHostPlatform(platform)
            DeckBridgeLog.state("hostPlatform set=$platform source=MANUAL")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, platform, res).copy(
                    hostPlatformSource = HostPlatformSource.MANUAL,
                    hostDetectionDetail = app.getString(R.string.host_detect_manual),
                )
            }
        }
    }

    override fun setHostAutoDetect(enabled: Boolean) {
        externalScope.launch {
            dataStore.writeHostAutoDetect(enabled)
            if (enabled) {
                refreshHostAndTransportInternal(forceAuto = true)
            } else {
                val p = _appState.value.hostPlatform
                dataStore.writePersistedHostPlatform(p)
                _appState.update { prev ->
                    prev.copy(
                        hostPlatformSource = HostPlatformSource.MANUAL,
                        hostDetectionDetail = app.getString(R.string.host_detect_manual),
                    )
                }
            }
        }
    }

    override fun refreshHostAndTransport() {
        scheduleHostAndTransportRefresh()
    }

    /** Coalesces init + onResume + USB_STATE bursts into one probe (same result, less Logcat noise). */
    private fun scheduleHostAndTransportRefresh() {
        hostTransportRefreshJob?.cancel()
        hostTransportRefreshJob = externalScope.launch {
            delay(HOST_TRANSPORT_DEBOUNCE_MS)
            refreshHostAndTransportInternal()
        }
    }

    private suspend fun refreshHostAndTransportInternal(forceAuto: Boolean = false) {
        val auto = forceAuto || dataStore.readHostAutoDetect()
        val probe = withContext(Dispatchers.IO) { hidGadgetSession.probe() }
        hidTransportDispatcher.updateTransportState(probe.keyboardWritable, probe.consumerWritable)
        DeckBridgeLog.hid(
            "probe phase=${probe.phase} | keyboard path=${probe.keyboardPath} exists=${probe.keyboardExists} writable=${probe.keyboardWritable} | consumer path=${probe.consumerPath} exists=${probe.consumerExists} writable=${probe.consumerWritable} | err=${probe.lastError}",
        )
        when {
            !probe.keyboardExists && !probe.consumerExists -> {
                DeckBridgeLog.hid(app.getString(R.string.hid_log_diagnose_no_nodes))
            }
            probe.keyboardExists && !probe.keyboardWritable -> {
                DeckBridgeLog.hid(
                    app.getString(
                        R.string.hid_log_diagnose_keyboard_denied,
                        probe.lastError ?: "—",
                    ),
                )
            }
            probe.keyboardWritable && (!probe.consumerExists || !probe.consumerWritable) -> {
                DeckBridgeLog.hid(
                    app.getString(R.string.hid_log_diagnose_media_only, probe.consumerPath),
                )
            }
        }
        val hidUi = HidTransportUiMapper.toUiState(probe, res)
        val usbConnected = HostOsDetector.peekUsbConnected(app)
        if (auto) {
            val det = HostOsDetector.detect(usbConnected)
            val detail = hostAutoDetail(det)
            DeckBridgeLog.state("host auto-detect platform=${det.platform} usb=$usbConnected")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, det.platform, res).copy(
                    hostPlatformSource = HostPlatformSource.AUTOMATIC,
                    hostDetectionDetail = detail,
                    hidTransport = hidUi,
                    hostConnection = mergeHostUsb(prev.hostConnection, usbConnected, probe.keyboardWritable),
                )
            }
        } else {
            val stored = dataStore.readPersistedHostPlatform()
            DeckBridgeLog.state("loaded hostPlatform=$stored (manual)")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, stored, res).copy(
                    hostPlatformSource = HostPlatformSource.MANUAL,
                    hostDetectionDetail = app.getString(R.string.host_detect_manual),
                    hidTransport = hidUi,
                    hostConnection = mergeHostUsb(prev.hostConnection, usbConnected, probe.keyboardWritable),
                )
            }
        }
    }

    private fun hostAutoDetail(det: HostOsDetector.Result): String = when (det.detailKey) {
        HostOsDetector.DetailKey.USB_NOT_CONNECTED ->
            app.getString(R.string.host_detect_usb_disconnected)
        HostOsDetector.DetailKey.USB_CONNECTED_OS_UNKNOWN ->
            app.getString(R.string.host_detect_usb_unknown_os)
    }

    private fun mergeHostUsb(
        prev: HostConnectionStatus,
        usbConnected: Boolean,
        hidKeyboardReady: Boolean,
    ): HostConnectionStatus {
        val usbState = when {
            !usbConnected -> HostUsbConnectionState.NOT_CONNECTED
            hidKeyboardReady -> HostUsbConnectionState.READY
            else -> HostUsbConnectionState.ATTACHED_IDLE
        }
        val detail = when {
            !usbConnected -> app.getString(R.string.usb_state_not_connected)
            hidKeyboardReady -> app.getString(R.string.host_usb_hid_ready)
            else -> app.getString(R.string.host_usb_attached_no_hid)
        }
        return prev.copy(usbState = usbState, detail = detail)
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
        private const val HOST_TRANSPORT_DEBOUNCE_MS = 280L
        private const val KNOB_MOTION_THROTTLE_MS = 220L
    }
}
