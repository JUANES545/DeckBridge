package com.example.deckbridge.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
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
import com.example.deckbridge.actions.HostDeliveryRouter
import com.example.deckbridge.data.deck.DeckCatalog
import com.example.deckbridge.data.deck.DeckGridDisplay
import com.example.deckbridge.data.deck.DeckGridLayoutJson
import com.example.deckbridge.data.deck.DeckGridMacroMapper
import com.example.deckbridge.data.deck.DeckGridEditValidator
import com.example.deckbridge.data.deck.DeckGridPreset
import com.example.deckbridge.data.deck.DeckKnobEditValidator
import com.example.deckbridge.data.deck.DeckKnobIntentMapper
import com.example.deckbridge.data.deck.DeckKnobPreset
import com.example.deckbridge.data.hardware.HardwareBridge
import com.example.deckbridge.data.hardware.HardwareCalibrationJson
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.data.preferences.readKeepKeyboardAwake
import com.example.deckbridge.data.preferences.writeKeepKeyboardAwake
import com.example.deckbridge.data.preferences.readAnimatedBackgroundMode
import com.example.deckbridge.data.preferences.readAnimatedBackgroundTheme
import com.example.deckbridge.data.preferences.readDeckGridLayoutJson
import com.example.deckbridge.data.preferences.readDeckPagesLayoutJson
import com.example.deckbridge.data.preferences.writeDeckPagesLayoutJson
import com.example.deckbridge.data.preferences.readHardwareCalibrationJson
import com.example.deckbridge.data.preferences.readHostAutoDetect
import com.example.deckbridge.data.preferences.readHostDeliveryChannel
import com.example.deckbridge.data.preferences.readMacSlotChannel
import com.example.deckbridge.data.preferences.migrateAndReadSkipInitialPcConnect
import com.example.deckbridge.data.preferences.migrateLanPlatformSlotsFromLegacyIfNeeded
import com.example.deckbridge.data.preferences.readLanHostForPlatform
import com.example.deckbridge.data.preferences.readLanMobileDeviceId
import com.example.deckbridge.data.preferences.readLanPairTokenForPlatform
import com.example.deckbridge.data.preferences.readLanPortForPlatform
import com.example.deckbridge.data.preferences.readOnboardingCompleted
import com.example.deckbridge.data.preferences.readPersistedHostPlatform
import com.example.deckbridge.data.preferences.writeAnimatedBackgroundMode
import com.example.deckbridge.data.preferences.writeAnimatedBackgroundTheme
import com.example.deckbridge.data.preferences.writeDeckGridLayoutJson
import com.example.deckbridge.data.preferences.writeHardwareCalibrationJson
import com.example.deckbridge.data.preferences.writeHostAutoDetect
import com.example.deckbridge.data.preferences.writeHostDeliveryChannel
import com.example.deckbridge.data.preferences.writeMacSlotChannel
import com.example.deckbridge.data.preferences.writeLanHostForPlatform
import com.example.deckbridge.data.preferences.writeLanMobileDeviceId
import com.example.deckbridge.data.preferences.writeLanPairTokenForPlatform
import com.example.deckbridge.data.preferences.writeLanPortForPlatform
import com.example.deckbridge.data.preferences.writeOnboardingCompleted
import com.example.deckbridge.data.preferences.writeSkipInitialPcConnect
import com.example.deckbridge.data.preferences.writePersistedHostPlatform
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckGridLayoutPersisted
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted
import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.deck.DeckMultiPageSurface
import com.example.deckbridge.domain.deck.DeckPagesPersisted
import com.example.deckbridge.domain.deck.DeckPersistedSurface
import com.example.deckbridge.domain.hardware.CalibrationSessionUi
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.domain.hardware.HardwareHighlightKind
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight
import com.example.deckbridge.domain.hardware.KnobMirrorRotationAccum
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.PlatformSlotState
import com.example.deckbridge.domain.model.DECK_HIGHLIGHT_DURATION_MS
import com.example.deckbridge.domain.model.DeckActivationLogEntry
import com.example.deckbridge.domain.model.DeckButtonHighlight
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.LanAgentListScanState
import com.example.deckbridge.domain.model.LanDiscoveredAgent
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.macroButtonIdForKeyCode
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardCaptureRules
import com.example.deckbridge.input.KeyboardKeyFormatter
import com.example.deckbridge.device.EmulatorDetector
import com.example.deckbridge.host.HostOsDetector
import com.example.deckbridge.mac.MacBridgeServer
import com.example.deckbridge.lan.LanAgentProbeSnapshot
import com.example.deckbridge.lan.LanCircuitBreaker
import com.example.deckbridge.lan.LanTransportDispatcher
import com.example.deckbridge.lan.LanDiscovery
import com.example.deckbridge.lan.LanHealthResult
import com.example.deckbridge.lan.LanHostClient
import com.example.deckbridge.lan.LanPairingSessionCreated
import com.example.deckbridge.lan.LanPairingSessionStatus
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val KNOB_MIRROR_DEG_PER_TICK = 20f

/** LAN host/port/token are stored per Windows vs Mac agent; UNKNOWN maps to the Windows slot. */
private fun HostPlatform.normalizeForLanPersistence(): HostPlatform = when (this) {
    HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
    else -> this
}

private fun accumulateKnobMirrorRotation(
    prev: KnobMirrorRotationAccum,
    highlight: HardwareMirrorHighlight?,
): KnobMirrorRotationAccum {
    if (highlight == null) return prev
    val knob = highlight.control as? HardwareControlId.Knob ?: return prev
    val delta = when (highlight.kind) {
        HardwareHighlightKind.KNOB_ROTATE_CW -> KNOB_MIRROR_DEG_PER_TICK
        HardwareHighlightKind.KNOB_ROTATE_CCW -> -KNOB_MIRROR_DEG_PER_TICK
        else -> return prev
    }
    return prev.withDelta(knob.index, delta)
}

class DeckBridgeRepositoryImpl(
    appContext: Context,
    private val externalScope: CoroutineScope,
    private val hostDeliveryRouter: HostDeliveryRouter,
    private val winLanClient: LanHostClient,
    private val macLanClient: LanHostClient,
    private val winLanDispatcher: LanTransportDispatcher,
    private val macLanDispatcher: LanTransportDispatcher,
    private val winCircuitBreaker: LanCircuitBreaker,
    private val macCircuitBreaker: LanCircuitBreaker,
    private val macBridgeServer: MacBridgeServer,
    private val dataStore: DataStore<Preferences>,
) : DeckBridgeRepository {

    private val actionDispatcher: ActionDispatcher = hostDeliveryRouter

    private val app = appContext.applicationContext
    private val res = app.resources

    private val inputManager: InputManager =
        app.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val mainHandler = Handler(Looper.getMainLooper())

    // Starts with a pure in-memory default — no IO on the calling thread.
    // The persisted surface (deck grid / knobs) is loaded asynchronously in init { }.
    private val _appState = MutableStateFlow(MockAppStateFactory.runtimeBootstrap(res))

    private val _onboardingComplete = MutableStateFlow<Boolean?>(null)

    // Session-only: always starts as false so the connect gate shows on every launch when offline.
    private val _skipInitialPcConnect = MutableStateFlow<Boolean?>(false)

    private val pendingOpenPcConnect = AtomicBoolean(false)

    private val _lanAgentListScan = MutableStateFlow<LanAgentListScanState>(LanAgentListScanState.Idle)

    private val lanListScanSequence = AtomicInteger(0)
    private var lanAgentListScanJob: Job? = null

    private var clearHighlightJob: Job? = null
    private var mirrorClearJob: Job? = null
    private var keyboardScanJob: Job? = null
    private var deviceRefreshJob: Job? = null
    private var hostTransportRefreshJob: Job? = null
    private var lanForegroundDiscoveryJob: Job? = null
    private var macBridgeStateJob: Job? = null
    private var keyboardBatteryJob: Job? = null
    private var keepKeyboardAliveJob: Job? = null
    @Volatile private var keepAliveGatt: android.bluetooth.BluetoothGatt? = null
    private var winHealthRetryJob: Job? = null
    private var macHealthRetryJob: Job? = null
    private var actionFailedClearJob: Job? = null
    private var macBridgeDropClearJob: Job? = null

    @Volatile
    private var lastKeyboardScanElapsedRealtime: Long = 0L

    private val lastKnobMotionRealtime = LongArray(3) { 0L }
    private val lastMirrorKnobTouchRealtime = LongArray(3) { 0L }

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
        hardwareBridge.setDeckKnobsLayout(_appState.value.deckKnobs)
        inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler)

        externalScope.launch {
            _onboardingComplete.value = dataStore.readOnboardingCompleted()
        }

        externalScope.launch {
            dataStore.migrateAndReadSkipInitialPcConnect() // run migration side-effect only; skip resets each session
        }

        externalScope.launch {
            _appState.collect { s ->
                if (_skipInitialPcConnect.value != true &&
                    s.hostDeliveryChannel == HostDeliveryChannel.LAN &&
                    s.lanServerHost.isNotBlank() &&
                    s.lanHealthOk == true &&
                    s.lanTrustOk
                ) {
                    _skipInitialPcConnect.value = true
                    DeckBridgeLog.state("skip initial PC connect (LAN health OK + trust OK)")
                }
            }
        }

        externalScope.launch {
            // Load persisted deck surface first so the UI reflects the user's layout ASAP.
            // Previously done via runBlocking() on the calling thread (ANR risk on slow storage).
            val surface = withContext(Dispatchers.IO) { loadPersistedMultiPageOrSeed() }
            applyMultiPageSurfaceToAppState(surface)
            bootstrapLanSettings()
            bootstrapAnimatedBackground()
            attemptLanUdpDiscoveryAndHealth()
            scheduleHostAndTransportRefresh()
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
        startKeyboardBatteryPolling()
        externalScope.launch {
            val keepAwake = dataStore.readKeepKeyboardAwake()
            if (keepAwake) {
                _appState.update { it.copy(keepKeyboardAwake = true) }
                maybeStartKeepAlive()
            }
        }
        externalScope.launch {
            _appState.collect { s ->
                if (s.keepKeyboardAwake &&
                    s.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED
                ) {
                    maybeStartKeepAlive()
                } else if (!s.keepKeyboardAwake ||
                    s.physicalKeyboard.state != PhysicalKeyboardConnectionState.CONNECTED
                ) {
                    if (keepKeyboardAliveJob?.isActive == true &&
                        !s.keepKeyboardAwake
                    ) stopKeepAlive()
                }
            }
        }
        // Scan for already-connected keyboards at startup (hot-plug events are not fired for
        // devices that were connected before the app started).
        scheduleDebouncedKeyboardRefresh()
    }

    private fun scheduleDebouncedKeyboardRefresh() {
        deviceRefreshJob?.cancel()
        deviceRefreshJob = externalScope.launch {
            delay(DEVICE_REFRESH_DEBOUNCE_MS)
            refreshAttachedKeyboards()
        }
    }

    override fun cleanup() {
        // Unregister the hot-plug listener registered in init { } to prevent a memory leak
        // if this repository instance is ever garbage-collected before the process exits.
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
    }

    override val appState: StateFlow<AppState> = _appState.asStateFlow()

    override val onboardingComplete: StateFlow<Boolean?> = _onboardingComplete.asStateFlow()

    override val skipInitialPcConnect: StateFlow<Boolean?> = _skipInitialPcConnect.asStateFlow()

    override val initialConnectGateActive: StateFlow<Boolean> = combine(
        _skipInitialPcConnect,
        _appState,
    ) { skip, s ->
        skip == false &&
            s.hostDeliveryChannel == HostDeliveryChannel.LAN &&
            !(
                s.lanServerHost.isNotBlank() &&
                    s.lanHealthOk == true &&
                    s.lanTrustOk
                )
    }.stateIn(externalScope, SharingStarted.Eagerly, false)

    override fun markSkipInitialPcConnect(skip: Boolean) {
        _skipInitialPcConnect.value = skip
        DeckBridgeLog.state("skip initial PC connect set=$skip (session only)")
    }

    override fun markOnboardingFinished() {
        externalScope.launch {
            dataStore.writeOnboardingCompleted(true)
            _onboardingComplete.value = true
            DeckBridgeLog.state("onboarding completed")
        }
    }

    override fun requestPostOnboardingOpenPcConnect() {
        pendingOpenPcConnect.set(true)
    }

    override fun consumePostOnboardingOpenPcConnect(): Boolean = pendingOpenPcConnect.getAndSet(false)

    override val lanAgentListScanState: StateFlow<LanAgentListScanState> = _lanAgentListScan.asStateFlow()

    /**
     * Adds saved Windows / macOS agent endpoints that answer HTTP but did not appear in the UDP scan
     * (e.g. Mac inbound UDP blocked, wrong broadcast interface, or another PC replying first).
     */
    private suspend fun mergePersistedLanEndpointsFromHealth(agentList: MutableList<LanDiscoveredAgent>) {
        fun hasEndpoint(host: String, port: Int): Boolean =
            agentList.any { it.address.equals(host, ignoreCase = true) && it.httpPort == port }
        for (plat in arrayOf(HostPlatform.WINDOWS, HostPlatform.MAC)) {
            val ph = dataStore.readLanHostForPlatform(plat).trim()
            if (ph.isEmpty()) continue
            val pp = dataStore.readLanPortForPlatform(plat).coerceIn(1, 65_535)
            if (hasEndpoint(ph, pp)) {
                DeckBridgeLog.lan("connect scan: HTTP merge skip slot=$plat $ph:$pp (already in list)")
                continue
            }
            val tok = dataStore.readLanPairTokenForPlatform(plat).trim().takeIf { it.isNotEmpty() }
            DeckBridgeLog.lan("connect scan: HTTP merge try slot=$plat $ph:$pp")
            val snap = clientForPlatform(plat).probeAt(ph, pp, tok)
            if (snap.healthOk) {
                agentList.add(
                    LanDiscoveredAgent(
                        address = ph,
                        httpPort = pp,
                        agentOs = snap.agentOs,
                    ),
                )
                DeckBridgeLog.lan(
                    "connect scan: HTTP merge OK slot=$plat $ph:$pp agent_os=${snap.agentOs ?: "—"}",
                )
            } else {
                DeckBridgeLog.lan(
                    "connect scan: HTTP merge FAIL slot=$plat $ph:$pp detail=${snap.healthDetail}",
                )
            }
        }
    }

    override fun refreshLanAgentListScan() {
        val mySeq = lanListScanSequence.incrementAndGet()
        lanAgentListScanJob?.cancel()
        lanAgentListScanJob = externalScope.launch {
            _lanAgentListScan.value = LanAgentListScanState.Scanning
            val t0 = SystemClock.elapsedRealtime()
            DeckBridgeLog.lan(
                "connect scan START seq=$mySeq total_ms=${LanDiscovery.SCAN_TOTAL_MS_DEFAULT} (UDP + HTTP merge for saved Win/Mac hosts)",
            )
            try {
                val plat = _appState.value.hostPlatform.normalizeForLanPersistence()
                val fallbackPort = dataStore.readLanPortForPlatform(plat)
                val agentList = LanDiscovery.scanAgents(
                    context = app,
                    fallbackHttpPort = fallbackPort,
                    totalListenMs = LanDiscovery.SCAN_TOTAL_MS_DEFAULT,
                ).toMutableList()
                mergePersistedLanEndpointsFromHealth(agentList)
                if (agentList.isEmpty()) {
                    DeckBridgeLog.lan(
                        "connect scan: 0 agents after UDP+fallback — if the PC agent is running on LAN, " +
                            "suspect router guest Wi‑Fi / AP client isolation / VLAN (phone may reach the gateway " +
                            "but not other LAN hosts over TCP/UDP).",
                    )
                }
                val elapsed = SystemClock.elapsedRealtime() - t0
                _lanAgentListScan.value = LanAgentListScanState.Ready(agentList)
                DeckBridgeLog.lan("connect scan END seq=$mySeq agents=${agentList.size} elapsed_ms=$elapsed state=Ready")
            } catch (e: CancellationException) {
                val elapsed = SystemClock.elapsedRealtime() - t0
                if (mySeq == lanListScanSequence.get()) {
                    _lanAgentListScan.value = LanAgentListScanState.Idle
                }
                DeckBridgeLog.lan("connect scan CANCELLED seq=$mySeq elapsed_ms=$elapsed")
                throw e
            } catch (e: Exception) {
                val elapsed = SystemClock.elapsedRealtime() - t0
                val msg = e.message ?: "scan failed"
                _lanAgentListScan.value = LanAgentListScanState.Failed(msg)
                DeckBridgeLog.lan("connect scan FAIL seq=$mySeq elapsed_ms=$elapsed err=$msg")
            }
        }
    }

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
                knobMirrorRotation = accumulateKnobMirrorRotation(prev.knobMirrorRotation, hw.mirrorHighlight),
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
                        macroDeckSource = ButtonTriggerSource.HARDWARE_KNOB,
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

    /** Wrapping page advance: delta=+1 for next, -1 for prev. */
    private fun advancePage(delta: Int) {
        val state = _appState.value
        val count = state.deckPageCount.coerceAtLeast(1)
        val next = ((state.activeDeckPageIndex + delta) % count + count) % count
        // Optimistic: flip the index immediately — deckPages is already in memory,
        // so the UI responds in the next frame without waiting for DataStore I/O.
        _appState.update { it.copy(activeDeckPageIndex = next) }
        // Persist async; also updates macroButtons / physicalBindings for the new active page.
        externalScope.launch { setActiveDeckPage(next) }
    }

    private fun tryDispatchKnobFromHardware(
        intent: DeckButtonIntent?,
        keyEvent: KeyEvent?,
        motionKnobIndex: Int?,
        interactionKind: String,
        knobIndexForLog: Int?,
        macroDeckSource: ButtonTriggerSource = ButtonTriggerSource.HARDWARE_KNOB,
    ) {
        if (intent == null) return
        if (keyEvent != null && keyEvent.repeatCount != 0) return
        if (motionKnobIndex != null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastKnobMotionRealtime[motionKnobIndex] < KNOB_MOTION_THROTTLE_MS) return
            lastKnobMotionRealtime[motionKnobIndex] = now
        }
        // Page navigation is local — never sent to the host.
        if (intent is DeckButtonIntent.PageNav) {
            DeckBridgeLog.knob("match=$interactionKind idx=${knobIndexForLog ?: motionKnobIndex} intent=${intent.intentId}")
            advancePage(if (intent is DeckButtonIntent.PageNav.Next) 1 else -1)
            return
        }
        val snapshot = _appState.value
        val resolved = PlatformActionResolver.resolve(intent, snapshot.hostPlatform)
        val button = snapshot.macroButtons.find { it.intent == intent }
        DeckBridgeLog.knob(
            "match=$interactionKind idx=${knobIndexForLog ?: motionKnobIndex} " +
                "intent=${resolved.intentDisplayName} shortcut=${resolved.shortcutDisplay} tile=${button?.id ?: "none"}",
        )
        if (button != null) {
            triggerDeckButton(button.id, macroDeckSource)
        } else {
            externalScope.launch {
                runCatching { actionDispatcher.dispatch(resolved) }
            }
        }
    }

    override fun onMirrorKnobTouchRotate(knobIndex: Int, clockwise: Boolean) {
        if (knobIndex !in 0..2) return
        val nowRt = SystemClock.elapsedRealtime()
        if (nowRt - lastMirrorKnobTouchRealtime[knobIndex] < KNOB_MIRROR_TOUCH_THROTTLE_MS) return
        lastMirrorKnobTouchRealtime[knobIndex] = nowRt

        val kind = if (clockwise) {
            HardwareHighlightKind.KNOB_ROTATE_CW
        } else {
            HardwareHighlightKind.KNOB_ROTATE_CCW
        }
        val until = System.currentTimeMillis() + MIRROR_KNOB_TOUCH_HIGHLIGHT_MS
        val highlight = HardwareMirrorHighlight(
            control = HardwareControlId.Knob(knobIndex),
            kind = kind,
            untilEpochMs = until,
            rotationVisual = if (clockwise) 1f else -1f,
        )
        _appState.update { prev ->
            prev.copy(
                hardwareMirrorHighlight = highlight,
                knobMirrorRotation = accumulateKnobMirrorRotation(prev.knobMirrorRotation, highlight),
            )
        }
        scheduleHardwareMirrorClear(until)

        val intent = DeckKnobIntentMapper.intentForRotate(_appState.value.deckKnobs, knobIndex, ccw = !clockwise)
        tryDispatchKnobFromHardware(
            intent = intent,
            keyEvent = null,
            motionKnobIndex = null,
            interactionKind = "mirror_touch",
            knobIndexForLog = knobIndex,
            macroDeckSource = ButtonTriggerSource.TOUCH_MIRROR_KNOB,
        )
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
                knobMirrorRotation = accumulateKnobMirrorRotation(prev.knobMirrorRotation, hw.mirrorHighlight),
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
                macroDeckSource = ButtonTriggerSource.HARDWARE_KNOB,
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
            // Update router to new slot
            val newSlot = _appState.value.activeSlot
            hostDeliveryRouter.setActiveChannel(newSlot.channel)
            hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(platform))
            if (newSlot.channel == HostDeliveryChannel.LAN) {
                applyLanClientFromPersistedStore(platform, "host platform chip → $platform")
                runLanHealthProbeForPlatform(platform)
            }
        }
    }

    override suspend fun syncHostPlatformForPairing(platform: HostPlatform) =
        withContext(Dispatchers.IO) {
            dataStore.writeHostAutoDetect(false)
            dataStore.writePersistedHostPlatform(platform)
            DeckBridgeLog.state("hostPlatform sync for pairing → $platform (deeplink os=)")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, platform, res).copy(
                    hostPlatformSource = HostPlatformSource.MANUAL,
                    hostDetectionDetail = app.getString(R.string.host_detect_manual),
                )
            }
            hostDeliveryRouter.setActiveChannel(_appState.value.activeSlot.channel)
            hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(platform))
            val newSlot = _appState.value.activeSlot
            if (newSlot.channel == HostDeliveryChannel.LAN) {
                applyLanClientFromPersistedStore(platform, "pairing deeplink os= → $platform")
                runLanHealthProbeForPlatform(platform)
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
                val curPlatform = _appState.value.hostPlatform
                hostDeliveryRouter.setActiveChannel(_appState.value.activeSlot.channel)
                hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(curPlatform))
                if (_appState.value.activeSlot.channel == HostDeliveryChannel.LAN) {
                    applyLanClientFromPersistedStore(curPlatform, "host auto-detect disabled")
                    runLanHealthProbeForPlatform(curPlatform)
                }
            }
        }
    }

    /** Coalesces init + onResume bursts into one probe (same result, less Logcat noise). */
    private fun scheduleHostAndTransportRefresh() {
        hostTransportRefreshJob?.cancel()
        hostTransportRefreshJob = externalScope.launch {
            delay(HOST_TRANSPORT_DEBOUNCE_MS)
            refreshHostAndTransportInternal()
        }
    }

    private suspend fun bootstrapAnimatedBackground() {
        val mode  = dataStore.readAnimatedBackgroundMode()
        val theme = dataStore.readAnimatedBackgroundTheme()
        _appState.update { prev -> prev.copy(animatedBackgroundMode = mode, animatedBackgroundTheme = theme) }
        DeckBridgeLog.state("animated background mode=$mode theme=$theme")
    }

    override fun setAnimatedBackgroundMode(mode: AnimatedBackgroundMode) {
        externalScope.launch(Dispatchers.IO) {
            dataStore.writeAnimatedBackgroundMode(mode)
            _appState.update { prev -> prev.copy(animatedBackgroundMode = mode) }
            DeckBridgeLog.state("animated background mode set to $mode")
        }
    }

    override fun setAnimatedBackgroundTheme(theme: AnimatedBackgroundTheme) {
        externalScope.launch(Dispatchers.IO) {
            dataStore.writeAnimatedBackgroundTheme(theme)
            _appState.update { prev -> prev.copy(animatedBackgroundTheme = theme) }
            DeckBridgeLog.state("animated background theme set to $theme")
        }
    }

    override fun setKeepKeyboardAwake(enabled: Boolean) {
        externalScope.launch(Dispatchers.IO) {
            dataStore.writeKeepKeyboardAwake(enabled)
        }
        _appState.update { it.copy(keepKeyboardAwake = enabled) }
        DeckBridgeLog.state("keepKeyboardAwake=$enabled")
        if (enabled) maybeStartKeepAlive() else stopKeepAlive()
    }

    private fun startMacBridgeStatePolling() {
        if (macBridgeStateJob?.isActive == true) return
        macBridgeStateJob = externalScope.launch {
            var prevAlive = false
            while (true) {
                val alive = macBridgeServer.isClientAlive()
                val ip = macBridgeServer.peekClientIp()
                val serverRunning = macBridgeServer.isRunning
                val dropped = macBridgeServer.peekAndResetDropCount()
                val localIp = macBridgeServer.localWifiIp()
                _appState.updateMacSlot {
                    copy(
                        macBridgeClientAlive = alive,
                        macBridgeClientIp = if (alive) ip else null,
                        macBridgeServerRunning = serverRunning,
                        macBridgeServerLocalIp = localIp,
                    )
                }
                // When the Mac bridge client connects (false→true) and the delivery channel is
                // still LAN (persisted from an older setup), auto-switch to MAC_BRIDGE so actions
                // are routed through the bridge queue instead of failing via the old LAN path.
                if (alive && !prevAlive && _appState.value.macSlot.channel != HostDeliveryChannel.MAC_BRIDGE) {
                    DeckBridgeLog.lan("MacBridge: client connected — auto-switching delivery to MAC_BRIDGE")
                    setHostDeliveryChannel(HostDeliveryChannel.MAC_BRIDGE, skipLanDiscovery = true)
                }
                prevAlive = alive
                if (dropped > 0) {
                    DeckBridgeLog.lan("MacBridge: $dropped action(s) dropped (queue full — Mac agent not polling fast enough)")
                    _appState.updateMacSlot { copy(macBridgeActionDropped = true) }
                    macBridgeDropClearJob?.cancel()
                    macBridgeDropClearJob = launch {
                        delay(4_000)
                        _appState.updateMacSlot { copy(macBridgeActionDropped = false) }
                    }
                }
                delay(3_000)
            }
        }
    }

    private fun stopMacBridgeStatePolling() {
        macBridgeStateJob?.cancel()
        macBridgeStateJob = null
        macBridgeDropClearJob?.cancel()
        macBridgeDropClearJob = null
        _appState.updateMacSlot {
            copy(
                macBridgeServerRunning = false,
                macBridgeActionDropped = false,
                macBridgeClientAlive = false,
                macBridgeClientIp = null,
            )
        }
    }

    private fun maybeStartKeepAlive() {
        if (keepKeyboardAliveJob?.isActive == true) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) return
        if (_appState.value.physicalKeyboard.state != PhysicalKeyboardConnectionState.CONNECTED) return
        startKeepAlive()
    }

    /**
     * Opens a dedicated GATT connection to the keyboard, requests CONNECTION_PRIORITY_HIGH,
     * then reads the Battery Level characteristic every [KEEP_ALIVE_PING_INTERVAL_MS] to
     * generate BLE traffic that resets the keyboard's inactivity timer.
     *
     * This is best-effort: if the keyboard firmware only reacts to HID reports this won't help.
     */
    @Suppress("DiscouragedPrivateApi", "UNCHECKED_CAST")
    private fun startKeepAlive() {
        keepKeyboardAliveJob = externalScope.launch {
            DeckBridgeLog.state("keepAlive: starting GATT keep-alive loop")
            while (isActive && _appState.value.keepKeyboardAwake) {
                val device = findConnectedKeyboardBtDevice()
                if (device == null) {
                    DeckBridgeLog.state("keepAlive: no BT keyboard found, retrying in 10s")
                    delay(10_000)
                    continue
                }
                val connected = kotlinx.coroutines.CompletableDeferred<Boolean>()
                var battChar: android.bluetooth.BluetoothGattCharacteristic? = null

                val callback = object : android.bluetooth.BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: android.bluetooth.BluetoothGatt,
                        status: Int,
                        newState: Int,
                    ) {
                        when (newState) {
                            android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                                DeckBridgeLog.state("keepAlive: GATT connected, requesting HIGH priority")
                                gatt.requestConnectionPriority(android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                                gatt.discoverServices()
                            }
                            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                                DeckBridgeLog.state("keepAlive: GATT disconnected")
                                connected.complete(false)
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt, status: Int) {
                        val svc = gatt.getService(BATTERY_SERVICE_UUID)
                        battChar = svc?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                        DeckBridgeLog.state("keepAlive: services discovered battChar=${battChar != null}")
                        connected.complete(battChar != null)
                    }

                    override fun onCharacteristicRead(
                        gatt: android.bluetooth.BluetoothGatt,
                        characteristic: android.bluetooth.BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int,
                    ) {
                        if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
                            val lvl = value[0].toInt() and 0xFF
                            DeckBridgeLog.state("keepAlive: ping ok battery=$lvl%")
                            _appState.update { it.copy(physicalKeyboard = it.physicalKeyboard.copy(batteryLevel = lvl)) }
                        }
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onCharacteristicRead(
                        gatt: android.bluetooth.BluetoothGatt,
                        characteristic: android.bluetooth.BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        val value = characteristic.value ?: return
                        if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
                            val lvl = value[0].toInt() and 0xFF
                            DeckBridgeLog.state("keepAlive: ping ok battery=$lvl% (compat)")
                            _appState.update { it.copy(physicalKeyboard = it.physicalKeyboard.copy(batteryLevel = lvl)) }
                        }
                    }
                }

                val gatt = device.connectGatt(app, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
                keepAliveGatt = gatt

                val success = kotlinx.coroutines.withTimeoutOrNull(15_000) { connected.await() } ?: false
                if (!success) {
                    DeckBridgeLog.state("keepAlive: GATT connect failed/timeout, retrying in 15s")
                    gatt.close()
                    keepAliveGatt = null
                    delay(15_000)
                    continue
                }

                // Ping loop: read battery characteristic periodically
                while (isActive && _appState.value.keepKeyboardAwake &&
                    _appState.value.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED
                ) {
                    delay(KEEP_ALIVE_PING_INTERVAL_MS)
                    battChar?.let {
                        DeckBridgeLog.state("keepAlive: sending ping read")
                        gatt.readCharacteristic(it)
                    }
                }

                DeckBridgeLog.state("keepAlive: exiting ping loop, disconnecting GATT")
                gatt.disconnect()
                delay(500)
                gatt.close()
                keepAliveGatt = null
                break
            }
            DeckBridgeLog.state("keepAlive: job ended")
        }
    }

    private fun stopKeepAlive() {
        DeckBridgeLog.state("keepAlive: stopping")
        keepKeyboardAliveJob?.cancel()
        keepKeyboardAliveJob = null
        keepAliveGatt?.let {
            try { it.disconnect() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        keepAliveGatt = null
    }

    @Suppress("DiscouragedPrivateApi", "UNCHECKED_CAST")
    private fun findConnectedKeyboardBtDevice(): android.bluetooth.BluetoothDevice? {
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
            val getBonded = adapter.javaClass.getMethod("getBondedDevices")
            val bonded = (getBonded.invoke(adapter) as? Set<android.bluetooth.BluetoothDevice>) ?: return null
            val getBatteryLevel = android.bluetooth.BluetoothDevice::class.java.getMethod("getBatteryLevel")
            // Return the first bonded device with a known (≥0) battery level — the keyboard
            bonded.firstOrNull { d ->
                val lvl = try { getBatteryLevel.invoke(d) as? Int ?: -1 } catch (_: Exception) { -1 }
                lvl >= 0
            }
        } catch (e: Exception) {
            DeckBridgeLog.state("findConnectedKeyboardBtDevice failed: ${e.message}")
            null
        }
    }

    /** Periodically refreshes the keyboard battery level (~every 60 s) while the app is active. */
    private fun startKeyboardBatteryPolling() {
        if (keyboardBatteryJob?.isActive == true) return
        keyboardBatteryJob = externalScope.launch {
            while (true) {
                delay(60_000L)
                val keyboard = _appState.value.physicalKeyboard
                if (keyboard.state == PhysicalKeyboardConnectionState.CONNECTED) {
                    val detected = scanExternalKeyboards()
                    val first = detected.firstOrNull() ?: continue
                    val newLevel = readInputDeviceBatteryLevel(first.deviceId)
                    if (newLevel != keyboard.batteryLevel) {
                        _appState.update { it.copy(physicalKeyboard = it.physicalKeyboard.copy(batteryLevel = newLevel)) }
                    }
                }
            }
        }
    }

    private fun lanPlatformForEndpoints(): HostPlatform =
        _appState.value.hostPlatform.normalizeForLanPersistence()

    private fun clientForPlatform(platform: HostPlatform): LanHostClient = when (platform.normalizeForLanPersistence()) {
        HostPlatform.MAC -> macLanClient
        else -> winLanClient
    }

    private fun circuitBreakerForPlatform(platform: HostPlatform): LanCircuitBreaker = when (platform.normalizeForLanPersistence()) {
        HostPlatform.MAC -> macCircuitBreaker
        else -> winCircuitBreaker
    }

    private fun dispatcherForPlatform(platform: HostPlatform): LanTransportDispatcher = when (platform.normalizeForLanPersistence()) {
        HostPlatform.MAC -> macLanDispatcher
        else -> winLanDispatcher
    }

    private fun MutableStateFlow<AppState>.updateWindowsSlot(t: PlatformSlotState.() -> PlatformSlotState) =
        update { prev -> prev.copy(windowsSlot = prev.windowsSlot.t()) }

    private fun MutableStateFlow<AppState>.updateMacSlot(t: PlatformSlotState.() -> PlatformSlotState) =
        update { prev -> prev.copy(macSlot = prev.macSlot.t()) }

    private fun MutableStateFlow<AppState>.updateSlotForPlatform(
        platform: HostPlatform,
        t: PlatformSlotState.() -> PlatformSlotState,
    ) = when (platform.normalizeForLanPersistence()) {
        HostPlatform.MAC -> updateMacSlot(t)
        else -> updateWindowsSlot(t)
    }

    private suspend fun applyLanClientFromPersistedStore(platform: HostPlatform, reason: String) {
        dataStore.migrateLanPlatformSlotsFromLegacyIfNeeded()
        val p = platform.normalizeForLanPersistence()
        val host = dataStore.readLanHostForPlatform(p)
        val port = dataStore.readLanPortForPlatform(p)
        val pairTok = dataStore.readLanPairTokenForPlatform(p).trim().takeIf { it.isNotEmpty() }
        val client = clientForPlatform(p)
        client.updateEndpoint(host, port)
        client.setPairToken(pairTok)
        _appState.updateSlotForPlatform(p) {
            copy(
                host = host.trim(),
                port = port,
                pairActive = pairTok != null,
                pairTokenValid = null,
                trustOk = true,
                healthOk = null,
                healthDetail = null,
            )
        }
        DeckBridgeLog.lan(
            "LAN rebind ($reason) slot=$p host=${if (host.isBlank()) "—" else host} port=$port pairToken=${if (pairTok != null) "yes" else "no"}",
        )
    }

    private suspend fun bootstrapLanSettings() {
        val macChannel = dataStore.readMacSlotChannel()
        val winPlatform = HostPlatform.WINDOWS
        val macPlatform = HostPlatform.MAC
        val auto = dataStore.readHostAutoDetect()
        if (!auto) {
            val stored = dataStore.readPersistedHostPlatform()
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, stored, res).copy(
                    hostPlatformSource = HostPlatformSource.MANUAL,
                    hostDetectionDetail = app.getString(R.string.host_detect_manual),
                )
            }
        }
        // Load both slots from persisted store
        applyLanClientFromPersistedStore(winPlatform, "bootstrap WIN")
        applyLanClientFromPersistedStore(macPlatform, "bootstrap MAC")
        // Set Mac slot channel from persisted preference
        _appState.updateMacSlot { copy(channel = macChannel) }
        // Configure router for active slot
        val activeSlot = _appState.value.activeSlot
        hostDeliveryRouter.setActiveChannel(activeSlot.channel)
        val activePlatform = _appState.value.hostPlatform.normalizeForLanPersistence()
        hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(activePlatform))
        // Always start the MacBridgeServer so the Mac agent can connect via ADB forward
        // even when the Mac slot channel is LAN. The server is lightweight and the router
        // only dispatches actions through the configured channel.
        val tok = dataStore.readLanPairTokenForPlatform(HostPlatform.MAC).trim().takeIf { it.isNotEmpty() }
        macBridgeServer.setPairToken(tok)
        macBridgeServer.start(externalScope)
        startMacBridgeStatePolling()
        DeckBridgeLog.state(
            "LAN bootstrap macChannel=$macChannel activePlatform=$activePlatform " +
                "winHost=${_appState.value.windowsSlot.host.ifBlank { "—" }} " +
                "macHost=${_appState.value.macSlot.host.ifBlank { "—" }}",
        )
    }

    /**
     * UDP broadcast to [LanDiscovery.DISCOVERY_PORT]; if the Windows agent answers, persist IP/port
     * and probe HTTP `/health`. Safe to call on cold start and on resume when delivery is LAN.
     */
    private suspend fun attemptLanUdpDiscoveryAndHealth(
        platform: HostPlatform = lanPlatformForEndpoints(),
    ) {
        val plat = platform.normalizeForLanPersistence()
        val activeChannel = _appState.value.activeSlot.channel
        if (plat == lanPlatformForEndpoints() && activeChannel != HostDeliveryChannel.LAN) {
            return
        }
        val client = clientForPlatform(plat)
        val savedTok = dataStore.readLanPairTokenForPlatform(plat).trim()
        val savedHost = dataStore.readLanHostForPlatform(plat).trim()
        if (savedTok.isNotEmpty() && savedHost.isNotEmpty()) {
            DeckBridgeLog.lan("LAN reconnect slot=$plat: skip UDP overwrite, probing saved host $savedHost")
            runLanHealthProbeForPlatform(plat)
            return
        }
        val fallbackPort = dataStore.readLanPortForPlatform(plat)
        val discovered = LanDiscovery.tryDiscover(app, fallbackPort)
        if (discovered != null) {
            val ip = discovered.address
            val httpPort = discovered.httpPort
            dataStore.writeLanHostForPlatform(plat, ip)
            dataStore.writeLanPortForPlatform(plat, httpPort)
            client.updateEndpoint(ip, httpPort)
            _appState.updateSlotForPlatform(plat) {
                copy(
                    host = ip,
                    port = httpPort,
                    healthOk = null,
                    healthDetail = null,
                )
            }
            DeckBridgeLog.lan("UDP discover slot=$plat → http://$ip:$httpPort/ agent_os=${discovered.agentOs ?: "—"}")
        } else {
            DeckBridgeLog.lan("UDP discover slot=$plat → no reply (using saved ${client.baseUrlOrNull() ?: "—"})")
        }
        runLanHealthProbeForPlatform(plat)
    }

    private suspend fun invalidateLanLinkTrustAndResetGate(userMessage: String, platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        withContext(Dispatchers.IO) {
            dataStore.writeLanPairTokenForPlatform(p, null)
            dataStore.writeSkipInitialPcConnect(false)
        }
        clientForPlatform(p).setPairToken(null)
        _skipInitialPcConnect.value = false
        _appState.updateSlotForPlatform(p) {
            copy(pairActive = false, pairTokenValid = null, trustOk = false, healthOk = true, healthDetail = userMessage)
        }
        DeckBridgeLog.lan("LAN trust invalidated slot=$p — token cleared, connect gate reset: $userMessage")
    }

    private fun onLanHealthFailed(platform: HostPlatform) = startLanHealthRetryLoop(platform)
    private fun onLanHealthRecovered(platform: HostPlatform) = cancelLanHealthRetryLoop(platform)

    private suspend fun applyLanHealthSuccess(result: LanHealthResult, platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        onLanHealthRecovered(p)
        val persisted = dataStore.readLanPairTokenForPlatform(p).trim().isNotEmpty()
        if (result.pairTokenValid == false && persisted) {
            DeckBridgeLog.lan("LAN /health: pair_token_valid=false with persisted token slot=$p → clearing trust")
            invalidateLanLinkTrustAndResetGate(app.getString(R.string.lan_trust_invalid_summary), p)
            return
        }
        val trustOk = when {
            !persisted -> true
            result.pairTokenValid == true -> true
            result.pairTokenValid == false -> false
            else -> true
        }
        _appState.updateSlotForPlatform(p) {
            copy(
                healthOk = true,
                healthDetail = null,
                pairActive = persisted,
                pairTokenValid = result.pairTokenValid,
                trustOk = trustOk,
            )
        }
        DeckBridgeLog.lan(
            "LAN health OK slot=$p ${clientForPlatform(p).baseUrlOrNull()} pairTokenValid=${result.pairTokenValid} persisted=$persisted trustOk=$trustOk",
        )
    }

    private suspend fun applyLanHealthFailure(detail: String?, platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        _appState.updateSlotForPlatform(p) {
            copy(healthOk = false, healthDetail = detail, pairTokenValid = null)
        }
        DeckBridgeLog.lan("LAN health FAIL slot=$p $detail url=${clientForPlatform(p).baseUrlOrNull() ?: "—"}")
        onLanHealthFailed(p)
    }

    private suspend fun runLanHealthProbeForPlatform(platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        val client = clientForPlatform(p)
        val cb = circuitBreakerForPlatform(p)
        val result = client.getHealthDetailed()
        if (result.httpOk) {
            cb.recordSuccess()
            applyLanHealthSuccess(result, p)
            return
        }
        if (EmulatorDetector.isProbablyEmulator() && client.host != "10.0.2.2") {
            val savedHost = client.host
            val savedPort = client.port
            client.updateEndpoint("10.0.2.2", savedPort)
            val emu = client.getHealthDetailed()
            if (emu.httpOk) {
                dataStore.writeLanHostForPlatform(p, "10.0.2.2")
                dataStore.writeLanPortForPlatform(p, savedPort)
                client.updateEndpoint("10.0.2.2", savedPort)
                cb.recordSuccess()
                applyLanHealthSuccess(emu, p)
                _appState.updateSlotForPlatform(p) { copy(host = "10.0.2.2", port = savedPort) }
                DeckBridgeLog.lan("Emulator: using 10.0.2.2 slot=$p")
                return
            }
            client.updateEndpoint(savedHost, savedPort)
            val merged = listOfNotNull(result.detail, emu.detail).joinToString(" · ").take(220)
            cb.recordFailure()
            applyLanHealthFailure(merged, p)
            return
        }
        cb.recordFailure()
        applyLanHealthFailure(result.detail, p)
    }

    // Keep old name as alias for active slot (many callers)
    private suspend fun runLanHealthProbeWithEmulatorFallback() =
        runLanHealthProbeForPlatform(lanPlatformForEndpoints())

    /**
     * Per-platform exponential backoff retry loop after a failed health probe.
     * Retries at 3 s, 8 s, 20 s, 45 s (total ≈ 76 s) and then stops.
     */
    private fun startLanHealthRetryLoop(platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        val jobIsActive = when (p) {
            HostPlatform.MAC -> macHealthRetryJob?.isActive == true
            else -> winHealthRetryJob?.isActive == true
        }
        if (jobIsActive) return
        val delaysMs = longArrayOf(3_000, 8_000, 20_000, 45_000)
        val job = externalScope.launch {
            _appState.updateSlotForPlatform(p) { copy(healthRetrying = true) }
            DeckBridgeLog.lan("LAN health retry loop started slot=$p (${delaysMs.size} attempts)")
            for ((attempt, delayMs) in delaysMs.withIndex()) {
                delay(delayMs)
                val slotHealthOk = _appState.value.let {
                    if (p == HostPlatform.MAC) it.macSlot.healthOk else it.windowsSlot.healthOk
                }
                if (slotHealthOk == true) break
                DeckBridgeLog.lan("LAN health retry slot=$p attempt=${attempt + 1} delayWas=${delayMs}ms")
                runLanHealthProbeForPlatform(p)
                val slotHealthOkAfter = _appState.value.let {
                    if (p == HostPlatform.MAC) it.macSlot.healthOk else it.windowsSlot.healthOk
                }
                if (slotHealthOkAfter == true) {
                    DeckBridgeLog.lan("LAN health retry recovered slot=$p attempt ${attempt + 1}")
                    break
                }
            }
            _appState.updateSlotForPlatform(p) { copy(healthRetrying = false) }
            when (p) {
                HostPlatform.MAC -> macHealthRetryJob = null
                else -> winHealthRetryJob = null
            }
            if (p == HostPlatform.MAC) {
                // MacBridgeServer is always running — Mac agent will auto-reconnect.
                DeckBridgeLog.state(
                    "connection-watchdog: LAN retries exhausted on Mac slot · " +
                        "MacBridgeServer running=${macBridgeServer.isRunning} — Mac agent can reconnect via ADB/WiFi"
                )
            }
        }
        when (p) {
            HostPlatform.MAC -> macHealthRetryJob = job
            else -> winHealthRetryJob = job
        }
    }

    private fun cancelLanHealthRetryLoop(platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        when (p) {
            HostPlatform.MAC -> { macHealthRetryJob?.cancel(); macHealthRetryJob = null }
            else -> { winHealthRetryJob?.cancel(); winHealthRetryJob = null }
        }
        _appState.updateSlotForPlatform(p) { copy(healthRetrying = false) }
    }

    override fun setHostDeliveryChannel(channel: HostDeliveryChannel, skipLanDiscovery: Boolean) {
        externalScope.launch {
            // Channel selection is per Mac slot (Windows is always LAN)
            val activePlatform = lanPlatformForEndpoints()
            if (activePlatform == HostPlatform.MAC) {
                dataStore.writeHostDeliveryChannel(channel)
            }
            dataStore.writeMacSlotChannel(channel)
            _appState.updateMacSlot { copy(channel = channel) }
            hostDeliveryRouter.setActiveChannel(_appState.value.activeSlot.channel)
            DeckBridgeLog.state("hostDeliveryChannel=$channel slot=$activePlatform")
            if (channel == HostDeliveryChannel.MAC_BRIDGE) {
                if (!macBridgeServer.isRunning) {
                    val tok = dataStore.readLanPairTokenForPlatform(HostPlatform.MAC).trim().takeIf { it.isNotEmpty() }
                    macBridgeServer.setPairToken(tok)
                    macBridgeServer.start(externalScope)
                }
                startMacBridgeStatePolling()
            } else {
                // LAN channel: keep the server and state-polling running so the Mac agent can
                // auto-switch back to MAC_BRIDGE as soon as it reconnects.
                if (!macBridgeServer.isRunning) {
                    val tok = dataStore.readLanPairTokenForPlatform(HostPlatform.MAC).trim().takeIf { it.isNotEmpty() }
                    macBridgeServer.setPairToken(tok)
                    macBridgeServer.start(externalScope)
                }
                startMacBridgeStatePolling()
                if (!skipLanDiscovery && activePlatform == HostPlatform.MAC) {
                    attemptLanUdpDiscoveryAndHealth()
                }
            }
        }
    }

    override fun setMacSlotChannel(channel: HostDeliveryChannel) {
        setHostDeliveryChannel(channel)
    }

    override fun setLanEndpoint(host: String, port: Int) =
        setLanEndpointForPlatform(lanPlatformForEndpoints(), host, port)

    override fun setLanEndpointForPlatform(platform: HostPlatform, host: String, port: Int) {
        val portClamped = port.coerceIn(1, 65_535)
        val p = platform.normalizeForLanPersistence()
        externalScope.launch {
            val prevHost = dataStore.readLanHostForPlatform(p).trim()
            val prevPort = dataStore.readLanPortForPlatform(p)
            if (prevHost != host.trim() || prevPort != portClamped) {
                dataStore.writeLanPairTokenForPlatform(p, null)
                clientForPlatform(p).setPairToken(null)
                DeckBridgeLog.lan("LAN host/port changed slot=$p — pair token cleared")
            }
            dataStore.writeLanHostForPlatform(p, host)
            dataStore.writeLanPortForPlatform(p, portClamped)
            clientForPlatform(p).updateEndpoint(host, portClamped)
            val pairActive = dataStore.readLanPairTokenForPlatform(p).trim().isNotEmpty()
            _appState.updateSlotForPlatform(p) {
                copy(
                    host = host.trim(),
                    port = portClamped,
                    healthOk = null,
                    healthDetail = null,
                    pairActive = pairActive,
                    pairTokenValid = null,
                    trustOk = true,
                )
            }
        }
    }

    override suspend fun syncLanEndpointForPairing(host: String, port: Int, clearPairToken: Boolean) =
        withContext(Dispatchers.IO) {
            val plat = lanPlatformForEndpoints()
            val portClamped = port.coerceIn(1, 65_535)
            val client = clientForPlatform(plat)
            if (clearPairToken) {
                dataStore.writeLanPairTokenForPlatform(plat, null)
                client.setPairToken(null)
            }
            dataStore.writeLanHostForPlatform(plat, host)
            dataStore.writeLanPortForPlatform(plat, portClamped)
            client.updateEndpoint(host, portClamped)
            _appState.updateSlotForPlatform(plat) {
                copy(host = host.trim(), port = portClamped, healthOk = null, healthDetail = null)
            }
            DeckBridgeLog.lan(
                "syncLanEndpointForPairing slot=$plat $host:$port clearToken=$clearPairToken",
            )
        }

    override suspend fun applyLanEndpointPreservingPairToken(host: String, port: Int) =
        withContext(Dispatchers.IO) {
            val plat = lanPlatformForEndpoints()
            val portClamped = port.coerceIn(1, 65_535)
            val client = clientForPlatform(plat)
            dataStore.writeLanHostForPlatform(plat, host)
            dataStore.writeLanPortForPlatform(plat, portClamped)
            client.updateEndpoint(host, portClamped)
            val pairTok = dataStore.readLanPairTokenForPlatform(plat).trim().takeIf { it.isNotEmpty() }
            client.setPairToken(pairTok)
            _appState.updateSlotForPlatform(plat) {
                copy(
                    host = host.trim(),
                    port = portClamped,
                    healthOk = null,
                    healthDetail = null,
                    pairActive = pairTok != null,
                    pairTokenValid = null,
                    trustOk = true,
                )
            }
            DeckBridgeLog.lan("applyLanEndpointPreservingPairToken slot=$plat $host:$port")
        }

    override suspend fun probeLanAgent(host: String, port: Int): LanAgentProbeSnapshot =
        withContext(Dispatchers.IO) {
            val h = host.trim()
            val winHost = dataStore.readLanHostForPlatform(HostPlatform.WINDOWS).trim()
            val macHost = dataStore.readLanHostForPlatform(HostPlatform.MAC).trim()
            val token = when {
                h.equals(winHost, ignoreCase = true) ->
                    dataStore.readLanPairTokenForPlatform(HostPlatform.WINDOWS).trim().takeIf { it.isNotEmpty() }
                h.equals(macHost, ignoreCase = true) ->
                    dataStore.readLanPairTokenForPlatform(HostPlatform.MAC).trim().takeIf { it.isNotEmpty() }
                else -> null
            }
            // Use winLanClient for generic probing (temporary client, doesn't change active endpoint)
            winLanClient.probeAt(h, port, token)
        }

    override suspend fun probeLanHealthNow() {
        runLanHealthProbeWithEmulatorFallback()
    }

    override suspend fun getOrCreateLanMobileDeviceId(): String = withContext(Dispatchers.IO) {
        var id = dataStore.readLanMobileDeviceId().trim()
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString()
            dataStore.writeLanMobileDeviceId(id)
            DeckBridgeLog.lan("assigned new mobile_device_id for pairing")
        }
        id
    }

    override suspend fun startLanPairingSession(mobileDisplayName: String): Result<LanPairingSessionCreated> =
        withContext(Dispatchers.IO) {
            val deviceId = getOrCreateLanMobileDeviceId()
            clientForPlatform(lanPlatformForEndpoints()).createPairingSession(deviceId, mobileDisplayName)
        }

    override suspend fun getLanPairingSessionStatus(sessionId: String): Result<LanPairingSessionStatus> =
        withContext(Dispatchers.IO) {
            clientForPlatform(lanPlatformForEndpoints()).getPairingSession(sessionId)
        }

    override suspend fun cancelLanPairingSession(sessionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val deviceId = dataStore.readLanMobileDeviceId().trim()
            if (deviceId.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("no_mobile_device_id"))
            }
            clientForPlatform(lanPlatformForEndpoints()).cancelPairingSession(sessionId, deviceId)
        }

    override suspend fun claimLanPairingSession(sessionId: String, mobileDisplayName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val deviceId = getOrCreateLanMobileDeviceId()
            clientForPlatform(lanPlatformForEndpoints()).claimPairingSession(sessionId, deviceId, mobileDisplayName)
        }

    override suspend fun persistLanPairToken(pairToken: String) = withContext(Dispatchers.IO) {
        val plat = lanPlatformForEndpoints()
        dataStore.writeLanPairTokenForPlatform(plat, pairToken)
        clientForPlatform(plat).setPairToken(pairToken)
        if (plat == HostPlatform.MAC) macBridgeServer.setPairToken(pairToken)
        _appState.updateSlotForPlatform(plat) { copy(pairActive = true, pairTokenValid = true, trustOk = true) }
        DeckBridgeLog.lan(
            "LAN pair token persisted slot=$plat host=${dataStore.readLanHostForPlatform(plat).trim()} " +
                "(run /health to confirm trust)",
        )
    }

    override suspend fun applyMacBridgeToken(bridgeToken: String) = withContext(Dispatchers.IO) {
        // Persist platform and token
        dataStore.writeHostAutoDetect(false)
        dataStore.writePersistedHostPlatform(HostPlatform.MAC)
        dataStore.writeLanPairTokenForPlatform(HostPlatform.MAC, bridgeToken)
        dataStore.writeMacSlotChannel(HostDeliveryChannel.MAC_BRIDGE)
        // Apply token to server immediately
        macBridgeServer.setPairToken(bridgeToken)
        // Sync in-memory state: platform → MAC, Mac slot → MAC_BRIDGE + paired
        _appState.update { prev ->
            DeckCatalog.withHostPlatform(prev, HostPlatform.MAC, res).let { updated ->
                updated.copy(
                    hostPlatformSource = HostPlatformSource.MANUAL,
                    hostDetectionDetail = app.getString(R.string.host_detect_manual),
                    macSlot = updated.macSlot.copy(
                        channel = HostDeliveryChannel.MAC_BRIDGE,
                        pairActive = true,
                        pairTokenValid = true,
                        trustOk = true,
                    ),
                )
            }
        }
        // Point router to MAC_BRIDGE
        hostDeliveryRouter.setActiveChannel(HostDeliveryChannel.MAC_BRIDGE)
        hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(HostPlatform.MAC))
        // Ensure server is up and polling is active
        if (!macBridgeServer.isRunning) macBridgeServer.start(externalScope)
        startMacBridgeStatePolling()
        DeckBridgeLog.lan("MAC_BRIDGE token applied from QR — server=${macBridgeServer.isRunning} paired=true")
    }

    override suspend fun clearLanPairToken() = withContext(Dispatchers.IO) {
        val plat = lanPlatformForEndpoints()
        dataStore.writeLanPairTokenForPlatform(plat, null)
        clientForPlatform(plat).setPairToken(null)
        if (plat == HostPlatform.MAC) macBridgeServer.setPairToken(null)
        _appState.updateSlotForPlatform(plat) { copy(pairActive = false, pairTokenValid = null) }
    }

    override fun forgetTrustedLanHostLink() = forgetTrustedLanHostLinkForPlatform(lanPlatformForEndpoints())

    override fun forgetTrustedLanHostLinkForPlatform(platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        externalScope.launch {
            withContext(Dispatchers.IO) {
                dataStore.writeLanPairTokenForPlatform(p, null)
                dataStore.writeSkipInitialPcConnect(false)
            }
            clientForPlatform(p).setPairToken(null)
            if (p == HostPlatform.MAC) macBridgeServer.setPairToken(null)
            _skipInitialPcConnect.value = false
            _appState.updateSlotForPlatform(p) {
                copy(pairActive = false, pairTokenValid = null, trustOk = true, healthOk = null, healthDetail = null)
            }
            DeckBridgeLog.lan("Forget LAN link slot=$p: token cleared, skip-connect reset, next open will re-probe")
        }
    }

    override fun testLanHealth() = testLanHealthForPlatform(lanPlatformForEndpoints())

    override fun testLanHealthForPlatform(platform: HostPlatform) {
        val p = platform.normalizeForLanPersistence()
        cancelLanHealthRetryLoop(p)
        externalScope.launch {
            runLanHealthProbeForPlatform(p)
            DeckBridgeLog.lan("manual health probe slot=$p finished ok=${if (p == HostPlatform.MAC) _appState.value.macSlot.healthOk else _appState.value.windowsSlot.healthOk}")
        }
    }

    override fun refreshLanDiscoveryOnForeground() {
        lanForegroundDiscoveryJob?.cancel()
        lanForegroundDiscoveryJob = externalScope.launch {
            delay(LAN_FOREGROUND_DISCOVERY_DEBOUNCE_MS)
            val activePlatform = lanPlatformForEndpoints()
            val inactivePlatform = if (activePlatform == HostPlatform.WINDOWS) HostPlatform.MAC else HostPlatform.WINDOWS
            // Active slot: full discovery + health
            if (_appState.value.activeSlot.channel == HostDeliveryChannel.LAN) {
                attemptLanUdpDiscoveryAndHealth(activePlatform)
            }
            // Inactive slot: light health probe only (no UDP discovery)
            val inactiveSlot = if (inactivePlatform == HostPlatform.MAC) _appState.value.macSlot else _appState.value.windowsSlot
            if (inactiveSlot.host.isNotBlank()) {
                runLanHealthProbeForPlatform(inactivePlatform)
            }
        }
    }

    private suspend fun refreshHostAndTransportInternal(forceAuto: Boolean = false) {
        val prevLanPlatform = _appState.value.hostPlatform.normalizeForLanPersistence()
        val auto = forceAuto || dataStore.readHostAutoDetect()
        val usbConnected = HostOsDetector.peekUsbConnected(app)
        if (auto) {
            val det = HostOsDetector.detect(usbConnected)
            val detail = hostAutoDetail(det)
            DeckBridgeLog.state("host auto-detect platform=${det.platform} usb=$usbConnected")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, det.platform, res).copy(
                    hostPlatformSource = HostPlatformSource.AUTOMATIC,
                    hostDetectionDetail = detail,
                )
            }
            val newPl = det.platform.normalizeForLanPersistence()
            if (prevLanPlatform != newPl) {
                hostDeliveryRouter.setActiveChannel(_appState.value.activeSlot.channel)
                hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(newPl))
                if (_appState.value.activeSlot.channel == HostDeliveryChannel.LAN) {
                    applyLanClientFromPersistedStore(newPl, "auto host OS $prevLanPlatform → $newPl")
                    runLanHealthProbeForPlatform(newPl)
                }
            }
        } else {
            val stored = dataStore.readPersistedHostPlatform()
            DeckBridgeLog.state("loaded hostPlatform=$stored (manual)")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, stored, res).copy(
                    hostPlatformSource = HostPlatformSource.MANUAL,
                    hostDetectionDetail = app.getString(R.string.host_detect_manual),
                )
            }
            val newPl = stored.normalizeForLanPersistence()
            if (prevLanPlatform != newPl) {
                hostDeliveryRouter.setActiveChannel(_appState.value.activeSlot.channel)
                hostDeliveryRouter.setActiveLanDispatcher(dispatcherForPlatform(newPl))
                if (_appState.value.activeSlot.channel == HostDeliveryChannel.LAN) {
                    applyLanClientFromPersistedStore(newPl, "manual host platform $prevLanPlatform → $newPl")
                    runLanHealthProbeForPlatform(newPl)
                }
            }
        }
    }

    private fun hostAutoDetail(det: HostOsDetector.Result): String = when (det.detailKey) {
        HostOsDetector.DetailKey.USB_NOT_CONNECTED ->
            app.getString(R.string.host_detect_usb_disconnected)
        HostOsDetector.DetailKey.USB_CONNECTED_OS_UNKNOWN ->
            app.getString(R.string.host_detect_usb_unknown_os)
    }

    override fun triggerDeckButton(buttonId: String, source: ButtonTriggerSource) {
        val snapshot = _appState.value
        val button = snapshot.macroButtons.find { it.id == buttonId } ?: return
        if (!button.enabled) {
            DeckBridgeLog.ui("deckTile id=$buttonId disabled — ignored")
            return
        }
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

        // Page navigation is local — never sent to the host.
        if (button.intent is DeckButtonIntent.PageNav) {
            advancePage(if (button.intent is DeckButtonIntent.PageNav.Next) 1 else -1)
            return
        }

        externalScope.launch {
            val result = runCatching { actionDispatcher.dispatch(resolved) }
            val failed = result.getOrNull()?.isFailure == true || result.isFailure
            if (failed) {
                _appState.update { it.copy(lastActionFailed = true) }
                actionFailedClearJob?.cancel()
                actionFailedClearJob = launch {
                    delay(2_500)
                    _appState.update { it.copy(lastActionFailed = false) }
                }
            }
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
                batteryLevel = readInputDeviceBatteryLevel(first.deviceId),
            )
        } else {
            PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = app.getString(R.string.physical_kb_detail_disconnected),
            )
        }
    }

    /**
     * Returns battery level 0–100 for the given input device, or null when unavailable.
     *
     * Tries two strategies in order:
     *  1. [InputDevice.getBatteryState] (API 31+) — works when the input framework has a battery
     *     report (BT HID battery characteristic or USB battery status).
     *  2. [android.bluetooth.BluetoothDevice.getBatteryLevel] via reflection — reads the level
     *     cached by the system BLE Battery Service; requires no runtime permission.
     */
    private fun readInputDeviceBatteryLevel(deviceId: Int): Int? {
        // Strategy 1 – InputDevice battery state (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = InputDevice.getDevice(deviceId)
            val battery = device?.batteryState
            DeckBridgeLog.state("batteryState deviceId=$deviceId present=${battery?.isPresent} capacity=${battery?.capacity}")
            if (battery != null && battery.isPresent) {
                val capacity = battery.capacity
                if (!capacity.isNaN() && capacity >= 0f) {
                    return (capacity * 100f).toInt().coerceIn(0, 100)
                }
            }
        }
        // Strategy 2 – BluetoothDevice.getBatteryLevel() (hidden API, no permission needed)
        return readBtDeviceBatteryLevelForInputDevice(deviceId)
    }

    /**
     * Attempts to read the BLE Battery Service level via the BluetoothAdapter hidden API.
     * No permission is required: getDefaultAdapter() and getBondedDevices() (pre-API-31 path)
     * are used to enumerate paired HID devices and call the hidden getBatteryLevel().
     * On API 31+ the bonded set is accessed via reflection to avoid requiring BLUETOOTH_CONNECT.
     */
    @Suppress("DiscouragedPrivateApi", "UNCHECKED_CAST")
    private fun readBtDeviceBatteryLevelForInputDevice(deviceId: Int): Int? {
        // BLUETOOTH_CONNECT is required on API 31+ to enumerate bonded devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
            val getBonded = adapter.javaClass.getMethod("getBondedDevices")
            @Suppress("UNCHECKED_CAST")
            val bonded = (getBonded.invoke(adapter) as? Set<android.bluetooth.BluetoothDevice>)
                ?: return null
            val getBatteryLevel = android.bluetooth.BluetoothDevice::class.java
                .getMethod("getBatteryLevel")
            var bestLevel: Int? = null
            for (btDevice in bonded) {
                val level = getBatteryLevel.invoke(btDevice) as? Int ?: continue
                if (level < 0) continue   // -1 = unknown
                DeckBridgeLog.state("btBattery ${btDevice.name} level=$level")
                bestLevel = level
                break
            }
            bestLevel
        } catch (e: Exception) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            DeckBridgeLog.state("btBattery failed: ${cause::class.simpleName} ${cause.message}")
            null
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

    override suspend fun getDeckGridButton(buttonId: String): DeckGridButtonPersisted? = withContext(Dispatchers.IO) {
        val raw = dataStore.readDeckPagesLayoutJson() ?: return@withContext null
        DeckGridLayoutJson.decodeMultiPage(raw, res)?.pages?.activePage?.buttons?.find { it.id == buttonId }
    }

    override suspend fun updateDeckGridButton(cell: DeckGridButtonPersisted): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DeckGridEditValidator.validate(cell)?.let { rid ->
                throw IllegalArgumentException(app.getString(rid))
            }
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.pages.activePage.buttons.none { it.id == cell.id }) {
                throw IllegalArgumentException(app.getString(R.string.grid_edit_err_unknown_button))
            }
            val normalizedIcon = cell.iconToken?.trim()?.takeIf { it.isNotEmpty() }
            val toWrite = cell.copy(
                label = cell.label.trim(),
                subtitle = cell.subtitle.trim(),
                iconToken = normalizedIcon,
            )
            val updatedGrid = DeckGridLayoutPersisted(
                surface.pages.activePage.buttons.map { if (it.id == toWrite.id) toWrite else it }
            )
            val updatedPages = surface.pages.pages.toMutableList()
                .also { it[surface.pages.activePageIndex] = updatedGrid }
            val next = surface.copy(
                pages = surface.pages.copy(pages = updatedPages)
            )
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck grid: updated cell id=${cell.id} kind=${cell.kind} page=${surface.pages.activePageIndex}")
        }
    }

    override suspend fun resetDeckGridButtonToDefault(buttonId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val presetCell = DeckGridPreset.defaultLayoutFromResources(res).buttons.find { it.id == buttonId }
                ?: throw IllegalArgumentException(app.getString(R.string.grid_edit_err_unknown_button))
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            val current = surface.pages.activePage.buttons.find { it.id == buttonId }
                ?: throw IllegalArgumentException(app.getString(R.string.grid_edit_err_unknown_button))
            val merged = presetCell.copy(sortIndex = current.sortIndex)
            val updatedGrid = DeckGridLayoutPersisted(
                surface.pages.activePage.buttons.map { if (it.id == buttonId) merged else it }
            )
            val updatedPages = surface.pages.pages.toMutableList()
                .also { it[surface.pages.activePageIndex] = updatedGrid }
            val next = surface.copy(pages = surface.pages.copy(pages = updatedPages))
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck grid: reset cell id=$buttonId to factory preset page=${surface.pages.activePageIndex}")
        }
    }

    override suspend fun getDeckKnob(knobId: String): DeckKnobPersisted? = withContext(Dispatchers.IO) {
        val raw = dataStore.readDeckPagesLayoutJson() ?: return@withContext null
        DeckGridLayoutJson.decodeMultiPage(raw, res)?.knobs?.knobs?.find { it.id == knobId }
    }

    override suspend fun updateDeckKnob(knob: DeckKnobPersisted): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DeckKnobEditValidator.validate(knob)?.let { rid ->
                throw IllegalArgumentException(app.getString(rid))
            }
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.knobs.knobs.none { it.id == knob.id }) {
                throw IllegalArgumentException(app.getString(R.string.knob_edit_err_unknown_knob))
            }
            val toWrite = normalizeKnobForPersist(knob)
            val replaced = surface.knobs.knobs.map { if (it.id == toWrite.id) toWrite else it }
            val next = surface.copy(knobs = DeckKnobsLayoutPersisted(replaced))
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck knob: updated id=${knob.id}")
        }
    }

    override suspend fun resetDeckKnobToDefault(knobId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val presetKnob = DeckKnobPreset.defaultKnobsFromResources(res).knobs.find { it.id == knobId }
                ?: throw IllegalArgumentException(app.getString(R.string.knob_edit_err_unknown_knob))
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            val current = surface.knobs.knobs.find { it.id == knobId }
                ?: throw IllegalArgumentException(app.getString(R.string.knob_edit_err_unknown_knob))
            val merged = presetKnob.copy(
                sortIndex = current.sortIndex,
                iconToken = current.iconToken,
            )
            val replaced = surface.knobs.knobs.map { if (it.id == knobId) merged else it }
            val next = surface.copy(knobs = DeckKnobsLayoutPersisted(replaced))
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck knob: reset id=$knobId to factory preset")
        }
    }

    private fun applyMultiPageSurfaceToAppState(surface: DeckMultiPageSurface) {
        hardwareBridge.setDeckKnobsLayout(surface.knobs)
        val host = _appState.value.hostPlatform
        val activeGrid = surface.pages.activePage
        val macros = DeckGridDisplay.applyResolvedShortcuts(
            DeckGridMacroMapper.toMacroButtons(activeGrid),
            host,
            res,
        )
        val allPages = surface.pages.pages.map { grid ->
            DeckGridDisplay.applyResolvedShortcuts(DeckGridMacroMapper.toMacroButtons(grid), host, res)
        }
        val bindings = DeckCatalog.physicalBindingsForFKeys(host, activeGrid.sortedButtons().map { it.id })
        _appState.update { prev ->
            prev.copy(
                macroButtons = macros,
                physicalBindingsPreview = bindings,
                deckKnobs = surface.knobs,
                activeDeckPageIndex = surface.pages.activePageIndex,
                deckPageCount = surface.pages.pages.size,
                deckPages = allPages,
                deckPageNames = surface.pages.pages.map { it.name },
            )
        }
    }

    private suspend fun loadPersistedMultiPageOrSeed(): DeckMultiPageSurface {
        // 1. Try the new multi-page key first.
        val newRaw = dataStore.readDeckPagesLayoutJson()
        if (!newRaw.isNullOrBlank()) {
            val decoded = DeckGridLayoutJson.decodeMultiPage(newRaw, res)
            if (decoded != null) {
                DeckBridgeLog.state("deck pages: loaded ${decoded.pages.pages.size} page(s) schema=${decoded.schemaVersion}")
                return decoded
            }
            DeckBridgeLog.state("deck pages: new key corrupt — falling back to legacy key")
        }

        // 2. Migrate from legacy single-grid key (deck_grid_layout_json).
        val legacyRaw = dataStore.readDeckGridLayoutJson()
        if (!legacyRaw.isNullOrBlank()) {
            val legacySurface = DeckGridLayoutJson.decode(legacyRaw, res)
            if (legacySurface != null) {
                val surface = DeckMultiPageSurface(
                    pages = DeckPagesPersisted(listOf(legacySurface.grid), activePageIndex = 0),
                    knobs = legacySurface.knobs,
                )
                dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(surface))
                DeckBridgeLog.state("deck pages: migrated legacy single-grid to multi-page format")
                return surface
            }
        }

        // 3. Seed factory defaults.
        val grid = DeckGridPreset.defaultLayoutFromResources(res)
        val knobs = DeckKnobPreset.defaultKnobsFromResources(res)
        val surface = DeckMultiPageSurface(
            pages = DeckPagesPersisted(listOf(grid), activePageIndex = 0),
            knobs = knobs,
        )
        dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(surface))
        DeckBridgeLog.state("deck pages: seeded factory default (${grid.buttons.size} cells + knobs)")
        return surface
    }

    // ── Page management ──────────────────────────────────────────────────────

    override suspend fun addDeckPage(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.pages.pages.size >= DeckPagesPersisted.MAX_PAGES) return@runCatching
            val emptyGrid = buildEmptyPageGrid()
            val newPages = surface.pages.pages + emptyGrid
            val newActiveIndex = newPages.size - 1
            val next = surface.copy(
                pages = DeckPagesPersisted(pages = newPages, activePageIndex = newActiveIndex)
            )
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck pages: added page (total=${next.pages.pages.size})")
        }
    }

    override suspend fun duplicateDeckPage(index: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.pages.pages.size >= DeckPagesPersisted.MAX_PAGES) return@runCatching
            val sourcePage = surface.pages.pages.getOrNull(index) ?: return@runCatching
            val prefix = UUID.randomUUID().toString().replace("-", "").take(8)
            val clonedButtons = sourcePage.sortedButtons().mapIndexed { i, btn ->
                btn.copy(id = "btn_${prefix}_$i")
            }
            val clonedPage = DeckGridLayoutPersisted(clonedButtons)
            val newPages = surface.pages.pages.toMutableList().also { it.add(index + 1, clonedPage) }
            val next = surface.copy(
                pages = DeckPagesPersisted(pages = newPages, activePageIndex = index + 1)
            )
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck pages: duplicated page $index → ${index + 1} (total=${next.pages.pages.size})")
        }
    }

    override suspend fun reorderDeckPages(newOrder: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            val pages = surface.pages.pages
            if (newOrder.size != pages.size || newOrder.toSet() != pages.indices.toSet()) return@runCatching
            val reordered = newOrder.map { pages[it] }
            val oldActive = surface.pages.activePageIndex
            val newActive = newOrder.indexOf(oldActive).coerceAtLeast(0)
            val next = surface.copy(
                pages = DeckPagesPersisted(pages = reordered, activePageIndex = newActive)
            )
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck pages: reordered $newOrder (active $oldActive → $newActive)")
        }
    }

    override suspend fun updateDeckPageName(index: Int, name: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (index !in surface.pages.pages.indices) return@runCatching
            val cleanName = name?.ifBlank { null }
            if (surface.pages.pages[index].name == cleanName) return@runCatching
            val updatedPages = surface.pages.pages.mapIndexed { i, grid ->
                if (i == index) grid.copy(name = cleanName) else grid
            }
            val next = surface.copy(pages = surface.pages.copy(pages = updatedPages))
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck pages: renamed page $index → '${cleanName ?: "<unnamed>"}'")
        }
    }

    override suspend fun deleteDeckPage(index: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = dataStore.readDeckPagesLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.pages.pages.size <= 1) return@runCatching
            val newPages = surface.pages.pages.toMutableList().also { it.removeAt(index) }
            val newActiveIndex = surface.pages.activePageIndex.coerceAtMost(newPages.size - 1)
            val next = surface.copy(
                pages = DeckPagesPersisted(pages = newPages, activePageIndex = newActiveIndex)
            )
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck pages: deleted page $index (total=${next.pages.pages.size})")
        }
    }

    override suspend fun setActiveDeckPage(index: Int) {
        withContext(Dispatchers.IO) {
            val raw = dataStore.readDeckPagesLayoutJson() ?: return@withContext
            val surface = DeckGridLayoutJson.decodeMultiPage(raw, res) ?: return@withContext
            if (index !in surface.pages.pages.indices) return@withContext
            val next = surface.copy(pages = surface.pages.copy(activePageIndex = index))
            dataStore.writeDeckPagesLayoutJson(DeckGridLayoutJson.encodeMultiPage(next))
            applyMultiPageSurfaceToAppState(next)
            DeckBridgeLog.state("deck pages: switched to page $index")
        }
    }

    private fun buildEmptyPageGrid(): DeckGridLayoutPersisted {
        val prefix = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        val buttons = (0 until DeckGridLayoutPersisted.GRID_SLOT_COUNT).map { i ->
            DeckGridButtonPersisted(
                id = "btn_${prefix}_$i",
                sortIndex = i,
                label = "",
                subtitle = "",
                kind = DeckGridActionKind.NOOP,
                intentId = "deck.intent.noop",
                payload = emptyMap(),
                iconToken = null,
                enabled = true,
                visible = true,
            )
        }
        return DeckGridLayoutPersisted(buttons)
    }

    private fun normalizeKnobForPersist(knob: DeckKnobPersisted): DeckKnobPersisted {
        fun norm(a: DeckKnobActionPersisted): DeckKnobActionPersisted = when (a.kind) {
            DeckGridActionKind.TEXT ->
                a.copy(payload = mapOf("literal" to a.payload["literal"].orEmpty().trim()))
            else -> a.copy(payload = emptyMap())
        }
        return knob.copy(
            label = knob.label.trim(),
            subtitle = knob.subtitle.trim(),
            rotateCcw = norm(knob.rotateCcw),
            rotateCw = norm(knob.rotateCw),
            press = norm(knob.press),
            iconToken = knob.iconToken?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private const val MAX_ACTIVATIONS = 25
        private const val MAX_RAW_DIAGNOSTICS = 60
        private val BATTERY_SERVICE_UUID = java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID = java.util.UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private const val KEEP_ALIVE_PING_INTERVAL_MS = 20_000L
        private const val KEYBOARD_SCAN_MIN_INTERVAL_MS = 900L
        private const val MIRROR_CLEAR_MAX_WAIT_MS = 400L
        private const val DEVICE_REFRESH_DEBOUNCE_MS = 400L
        private const val HOST_TRANSPORT_DEBOUNCE_MS = 280L
        private const val KNOB_MOTION_THROTTLE_MS = 220L
        private const val KNOB_MIRROR_TOUCH_THROTTLE_MS = 140L
        private const val MIRROR_KNOB_TOUCH_HIGHLIGHT_MS = 240L
        private const val LAN_FOREGROUND_DISCOVERY_DEBOUNCE_MS = 400L
    }
}
