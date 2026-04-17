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
import com.example.deckbridge.actions.HostDeliveryRouter
import com.example.deckbridge.actions.HidTransportDispatcher
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
import com.example.deckbridge.data.preferences.readAnimatedBackgroundMode
import com.example.deckbridge.data.preferences.readAnimatedBackgroundTheme
import com.example.deckbridge.data.preferences.readDeckGridLayoutJson
import com.example.deckbridge.data.preferences.readHardwareCalibrationJson
import com.example.deckbridge.data.preferences.readHidPcModeOrNull
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
import com.example.deckbridge.data.preferences.writeHidPcMode
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
import com.example.deckbridge.domain.model.HostConnectionStatus
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.LanAgentListScanState
import com.example.deckbridge.domain.model.LanDiscoveredAgent
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.macroButtonIdForKeyCode
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardCaptureRules
import com.example.deckbridge.input.KeyboardKeyFormatter
import com.example.deckbridge.device.EmulatorDetector
import com.example.deckbridge.device.PrivilegedShellProbe
import com.example.deckbridge.hid.HidGadgetSession
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
import com.example.deckbridge.transport.HidDebugLineFormatter
import com.example.deckbridge.transport.HidTransportUiMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val hidTransportDispatcher: HidTransportDispatcher,
    private val hidGadgetSession: HidGadgetSession,
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

    private val _skipInitialPcConnect = MutableStateFlow<Boolean?>(null)

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
            _skipInitialPcConnect.value = dataStore.migrateAndReadSkipInitialPcConnect()
        }

        externalScope.launch {
            _appState.collect { s ->
                if (_skipInitialPcConnect.value != true &&
                    s.hostDeliveryChannel == HostDeliveryChannel.LAN &&
                    s.lanServerHost.isNotBlank() &&
                    s.lanHealthOk == true &&
                    s.lanTrustOk
                ) {
                    dataStore.writeSkipInitialPcConnect(true)
                    _skipInitialPcConnect.value = true
                    DeckBridgeLog.state("skip initial PC connect (LAN health OK + trust OK)")
                }
            }
        }

        externalScope.launch {
            // Load persisted deck surface first so the UI reflects the user's layout ASAP.
            // Previously done via runBlocking() on the calling thread (ANR risk on slow storage).
            val surface = withContext(Dispatchers.IO) { loadPersistedSurfaceOrSeed() }
            applyPersistedSurfaceToAppState(surface)
            bootstrapHidPcMode()
            bootstrapLanSettings()
            bootstrapAnimatedBackground()
            attemptLanUdpDiscoveryAndHealth()
            scheduleHostAndTransportRefresh()
        }

        // USB cable watchdog — reacts immediately when the cable is removed instead of
        // waiting for the next onResume / manual refresh cycle.
        externalScope.launch {
            var prevUsbState: HostUsbConnectionState? = null
            _appState.collect { state ->
                val current = state.hostConnection.usbState
                if (prevUsbState == HostUsbConnectionState.READY
                    && current == HostUsbConnectionState.NOT_CONNECTED
                ) {
                    autoFallbackOnUsbDrop(state)
                }
                prevUsbState = current
            }
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

    /**
     * Called by the USB watchdog when the cable transitions READY → NOT_CONNECTED.
     *
     * Strategy per active channel:
     * - **MAC_BRIDGE**: server keeps running; Mac agent must reconnect via the phone's WiFi IP
     *   (same path it would use without a cable). State polling is restarted to surface
     *   the reconnect status in the UI promptly.
     * - **LAN**: WiFi is likely still reachable — fire an immediate health probe without
     *   waiting for the next scheduled refresh (debounce window is up to 800 ms).
     * - Regardless of channel, [scheduleHostAndTransportRefresh] is called so the
     *   platform detection and HID state are re-evaluated with the cable gone.
     */
    private fun autoFallbackOnUsbDrop(state: AppState) {
        val platform = state.hostPlatform.normalizeForLanPersistence()
        val channel  = state.hostDeliveryChannel
        DeckBridgeLog.state(
            "connection-watchdog: USB cable dropped · slot=$platform · channel=$channel · " +
                "macBridgeRunning=${macBridgeServer.isRunning} · macBridgeAlive=${macBridgeServer.isClientAlive()}"
        )
        // Always re-evaluate platform + HID state (detects USB gone, may change auto-detected OS).
        scheduleHostAndTransportRefresh()
        when (channel) {
            HostDeliveryChannel.MAC_BRIDGE -> {
                // Mac Bridge server stays alive — Mac agent reconnects via WiFi automatically.
                // Restart polling so the UI reflects the client-alive / client-dropped transition.
                startMacBridgeStatePolling()
                DeckBridgeLog.state(
                    "connection-watchdog: MAC_BRIDGE server running · " +
                        "Mac agent should reconnect via phone WiFi IP"
                )
            }
            HostDeliveryChannel.LAN -> {
                // WiFi may still be reachable without the cable — probe immediately.
                externalScope.launch { runLanHealthProbeForPlatform(platform) }
                DeckBridgeLog.state("connection-watchdog: LAN re-probe triggered after USB drop")
            }
            else -> Unit   // USB_HID or any future channel: scheduleHostAndTransportRefresh handles it
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
        externalScope.launch {
            dataStore.writeSkipInitialPcConnect(skip)
            _skipInitialPcConnect.value = skip
            DeckBridgeLog.state("skip initial PC connect set=$skip")
        }
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

    private suspend fun bootstrapHidPcMode() {
        val root = PrivilegedShellProbe.isAvailable()
        val existing = dataStore.readHidPcModeOrNull()
        val mode = existing ?: root
        if (existing == null) {
            dataStore.writeHidPcMode(mode)
        }
        hidTransportDispatcher.setHidPcModeEnabled(mode)
        _appState.update { prev ->
            prev.copy(
                privilegedShellAvailable = root,
                hidPcModeEnabled = mode,
            )
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

    private fun startMacBridgeStatePolling() {
        if (macBridgeStateJob?.isActive == true) return
        macBridgeStateJob = externalScope.launch {
            while (true) {
                val alive = macBridgeServer.isClientAlive()
                val ip = macBridgeServer.peekClientIp()
                val serverRunning = macBridgeServer.isRunning
                val dropped = macBridgeServer.peekAndResetDropCount()
                _appState.updateMacSlot {
                    copy(
                        macBridgeClientAlive = alive,
                        macBridgeClientIp = if (alive) ip else null,
                        macBridgeServerRunning = serverRunning,
                    )
                }
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
        _appState.updateMacSlot { copy(macBridgeServerRunning = false, macBridgeActionDropped = false) }
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
        // Start Mac Bridge if Mac slot uses it
        if (macChannel == HostDeliveryChannel.MAC_BRIDGE) {
            val tok = dataStore.readLanPairTokenForPlatform(HostPlatform.MAC).trim().takeIf { it.isNotEmpty() }
            macBridgeServer.setPairToken(tok)
            macBridgeServer.start(externalScope)
            startMacBridgeStatePolling()
        }
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
            // Mac slot exhausted all LAN retries: if MAC_BRIDGE is still running
            // (cable was providing ADB tunnel and is now gone), the server is already
            // waiting — nothing to do.  If MAC_BRIDGE is NOT running but was previously
            // the configured channel for the Mac slot, auto-activate it so the Mac agent
            // can reconnect via WiFi.
            if (p == HostPlatform.MAC) {
                val snap = _appState.value
                val macBridgeWasPreferred = snap.macSlot.channel == HostDeliveryChannel.MAC_BRIDGE
                    || macBridgeServer.isRunning
                if (macBridgeWasPreferred && !macBridgeServer.isRunning) {
                    DeckBridgeLog.state(
                        "connection-watchdog: LAN retries exhausted on Mac slot · " +
                            "MAC_BRIDGE was preferred — activating server for WiFi reconnect"
                    )
                    setHostDeliveryChannel(HostDeliveryChannel.MAC_BRIDGE)
                } else if (macBridgeServer.isRunning) {
                    DeckBridgeLog.state(
                        "connection-watchdog: LAN retries exhausted on Mac slot · " +
                            "MAC_BRIDGE server already running — Mac agent can reconnect via WiFi"
                    )
                }
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
            // Channel selection is per Mac slot (Windows is always LAN; HID is global)
            val activePlatform = lanPlatformForEndpoints()
            if (activePlatform == HostPlatform.MAC || channel == HostDeliveryChannel.USB_HID) {
                dataStore.writeHostDeliveryChannel(channel)
            }
            if (channel != HostDeliveryChannel.USB_HID) {
                dataStore.writeMacSlotChannel(channel)
                _appState.updateMacSlot { copy(channel = channel) }
            }
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
                stopMacBridgeStatePolling()
                if (macBridgeServer.isRunning) macBridgeServer.stop()
                if (channel == HostDeliveryChannel.LAN && !skipLanDiscovery && activePlatform == HostPlatform.MAC) {
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

    override fun setHidPcModeEnabled(enabled: Boolean) {
        externalScope.launch {
            dataStore.writeHidPcMode(enabled)
            PrivilegedShellProbe.invalidateCache()
            hidTransportDispatcher.setHidPcModeEnabled(enabled)
            _appState.update { prev -> prev.copy(hidPcModeEnabled = enabled) }
            refreshHostAndTransportInternal()
        }
    }

    private suspend fun refreshHostAndTransportInternal(forceAuto: Boolean = false) {
        val prevLanPlatform = _appState.value.hostPlatform.normalizeForLanPersistence()
        val auto = forceAuto || dataStore.readHostAutoDetect()
        val probe = withContext(Dispatchers.IO) { hidGadgetSession.probe() }
        hidTransportDispatcher.updateTransportState(probe.keyboardWritable, probe.consumerWritable)
        DeckBridgeLog.hid(
            "probe phase=${probe.phase} | keyboard path=${probe.keyboardPath} exists=${probe.keyboardExists} writable=${probe.keyboardWritable} | consumer path=${probe.consumerPath} exists=${probe.consumerExists} writable=${probe.consumerWritable} | err=${probe.lastError}",
        )
        val usbConnected = HostOsDetector.peekUsbConnected(app)
        val hidPcMode = dataStore.readHidPcModeOrNull() ?: false
        hidTransportDispatcher.setHidPcModeEnabled(hidPcMode)
        val rootAvail = PrivilegedShellProbe.isAvailable()
        when {
            !probe.keyboardExists && !probe.consumerExists -> {
                DeckBridgeLog.hid(app.getString(R.string.hid_log_diagnose_no_nodes))
                if (rootAvail) {
                    DeckBridgeLog.hid(app.getString(R.string.hid_log_diagnose_root_ok_no_hidg))
                }
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
        val hidUi = HidTransportUiMapper.toUiState(probe, res, rootAvail)
        val hidDebugLine = HidDebugLineFormatter.format(rootAvail, hidPcMode, usbConnected, probe)
        DeckBridgeLog.hid("[DBG] $hidDebugLine")
        if (auto) {
            val det = HostOsDetector.detect(usbConnected)
            val detail = hostAutoDetail(det)
            DeckBridgeLog.state("host auto-detect platform=${det.platform} usb=$usbConnected")
            _appState.update { prev ->
                DeckCatalog.withHostPlatform(prev, det.platform, res).copy(
                    hostPlatformSource = HostPlatformSource.AUTOMATIC,
                    hostDetectionDetail = detail,
                    hidTransport = hidUi,
                    hidPcModeEnabled = hidPcMode,
                    privilegedShellAvailable = rootAvail,
                    hidDebugLine = hidDebugLine,
                    hostConnection = mergeHostUsb(prev.hostConnection, usbConnected, hidUi.canSendKeyboard),
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
                    hidTransport = hidUi,
                    hidPcModeEnabled = hidPcMode,
                    privilegedShellAvailable = rootAvail,
                    hidDebugLine = hidDebugLine,
                    hostConnection = mergeHostUsb(prev.hostConnection, usbConnected, hidUi.canSendKeyboard),
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

    override suspend fun getDeckGridButton(buttonId: String): DeckGridButtonPersisted? = withContext(Dispatchers.IO) {
        val raw = dataStore.readDeckGridLayoutJson() ?: return@withContext null
        DeckGridLayoutJson.decode(raw, res)?.grid?.buttons?.find { it.id == buttonId }
    }

    override suspend fun updateDeckGridButton(cell: DeckGridButtonPersisted): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DeckGridEditValidator.validate(cell)?.let { rid ->
                throw IllegalArgumentException(app.getString(rid))
            }
            val raw = dataStore.readDeckGridLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decode(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.grid.buttons.none { it.id == cell.id }) {
                throw IllegalArgumentException(app.getString(R.string.grid_edit_err_unknown_button))
            }
            val normalizedIcon = cell.iconToken?.trim()?.takeIf { it.isNotEmpty() }
            val toWrite = cell.copy(
                label = cell.label.trim(),
                subtitle = cell.subtitle.trim(),
                iconToken = normalizedIcon,
            )
            val replaced = surface.grid.buttons.map { if (it.id == toWrite.id) toWrite else it }
            val next = surface.copy(grid = DeckGridLayoutPersisted(replaced))
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(next))
            applyPersistedSurfaceToAppState(next)
            DeckBridgeLog.state("deck grid: updated cell id=${cell.id} kind=${cell.kind}")
        }
    }

    override suspend fun resetDeckGridButtonToDefault(buttonId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val presetCell = DeckGridPreset.defaultLayoutFromResources(res).buttons.find { it.id == buttonId }
                ?: throw IllegalArgumentException(app.getString(R.string.grid_edit_err_unknown_button))
            val raw = dataStore.readDeckGridLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decode(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            val current = surface.grid.buttons.find { it.id == buttonId }
                ?: throw IllegalArgumentException(app.getString(R.string.grid_edit_err_unknown_button))
            val merged = presetCell.copy(sortIndex = current.sortIndex)
            val replaced = surface.grid.buttons.map { if (it.id == buttonId) merged else it }
            val next = surface.copy(grid = DeckGridLayoutPersisted(replaced))
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(next))
            applyPersistedSurfaceToAppState(next)
            DeckBridgeLog.state("deck grid: reset cell id=$buttonId to factory preset")
        }
    }

    override suspend fun getDeckKnob(knobId: String): DeckKnobPersisted? = withContext(Dispatchers.IO) {
        val raw = dataStore.readDeckGridLayoutJson() ?: return@withContext null
        DeckGridLayoutJson.decode(raw, res)?.knobs?.knobs?.find { it.id == knobId }
    }

    override suspend fun updateDeckKnob(knob: DeckKnobPersisted): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DeckKnobEditValidator.validate(knob)?.let { rid ->
                throw IllegalArgumentException(app.getString(rid))
            }
            val raw = dataStore.readDeckGridLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decode(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            if (surface.knobs.knobs.none { it.id == knob.id }) {
                throw IllegalArgumentException(app.getString(R.string.knob_edit_err_unknown_knob))
            }
            val toWrite = normalizeKnobForPersist(knob)
            val replaced = surface.knobs.knobs.map { if (it.id == toWrite.id) toWrite else it }
            val next = surface.copy(knobs = DeckKnobsLayoutPersisted(replaced))
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(next))
            applyPersistedSurfaceToAppState(next)
            DeckBridgeLog.state("deck knob: updated id=${knob.id}")
        }
    }

    override suspend fun resetDeckKnobToDefault(knobId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val presetKnob = DeckKnobPreset.defaultKnobsFromResources(res).knobs.find { it.id == knobId }
                ?: throw IllegalArgumentException(app.getString(R.string.knob_edit_err_unknown_knob))
            val raw = dataStore.readDeckGridLayoutJson()
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_no_persisted_grid))
            val surface = DeckGridLayoutJson.decode(raw, res)
                ?: throw IllegalStateException(app.getString(R.string.grid_edit_err_corrupt_grid))
            val current = surface.knobs.knobs.find { it.id == knobId }
                ?: throw IllegalArgumentException(app.getString(R.string.knob_edit_err_unknown_knob))
            val merged = presetKnob.copy(
                sortIndex = current.sortIndex,
                iconToken = current.iconToken,
            )
            val replaced = surface.knobs.knobs.map { if (it.id == knobId) merged else it }
            val next = surface.copy(knobs = DeckKnobsLayoutPersisted(replaced))
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(next))
            applyPersistedSurfaceToAppState(next)
            DeckBridgeLog.state("deck knob: reset id=$knobId to factory preset")
        }
    }

    private fun applyPersistedSurfaceToAppState(surface: DeckPersistedSurface) {
        hardwareBridge.setDeckKnobsLayout(surface.knobs)
        val host = _appState.value.hostPlatform
        val macros = DeckGridDisplay.applyResolvedShortcuts(
            DeckGridMacroMapper.toMacroButtons(surface.grid),
            host,
            res,
        )
        val bindings = DeckCatalog.physicalBindingsForFKeys(host, surface.grid.sortedButtons().map { it.id })
        _appState.update { prev ->
            prev.copy(
                macroButtons = macros,
                physicalBindingsPreview = bindings,
                deckKnobs = surface.knobs,
            )
        }
    }

    private suspend fun loadPersistedSurfaceOrSeed(): DeckPersistedSurface {
        val raw = dataStore.readDeckGridLayoutJson()
        if (raw.isNullOrBlank()) {
            val grid = DeckGridPreset.defaultLayoutFromResources(res)
            val knobs = DeckKnobPreset.defaultKnobsFromResources(res)
            val surface = DeckPersistedSurface(
                schemaVersion = DeckPersistedSurface.CURRENT_SCHEMA_VERSION,
                grid = grid,
                knobs = knobs,
            )
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(surface))
            DeckBridgeLog.state("deck grid: seeded default preset (${grid.buttons.size} cells + knobs)")
            return surface
        }
        val migrateKnobs = DeckGridLayoutJson.surfaceMissingKnobsSection(raw)
        val decoded = DeckGridLayoutJson.decode(raw, res)
        if (decoded == null) {
            DeckBridgeLog.state("deck grid: JSON corrupt or incompatible — reset to default preset")
            val grid = DeckGridPreset.defaultLayoutFromResources(res)
            val knobs = DeckKnobPreset.defaultKnobsFromResources(res)
            val surface = DeckPersistedSurface(
                schemaVersion = DeckPersistedSurface.CURRENT_SCHEMA_VERSION,
                grid = grid,
                knobs = knobs,
            )
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(surface))
            return surface
        }
        if (migrateKnobs) {
            dataStore.writeDeckGridLayoutJson(DeckGridLayoutJson.encode(decoded))
            DeckBridgeLog.state("deck surface: migrated knobs into persisted JSON")
        }
        DeckBridgeLog.state("deck grid: loaded persisted layout schema=${decoded.schemaVersion}")
        return decoded
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
