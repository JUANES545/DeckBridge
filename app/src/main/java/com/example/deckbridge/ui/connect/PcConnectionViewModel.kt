package com.example.deckbridge.ui.connect

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import com.example.deckbridge.R
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.LanAgentListScanState
import com.example.deckbridge.domain.model.LanDiscoveredAgent
import com.example.deckbridge.domain.pairing.DeckbridgePairingPayload
import com.example.deckbridge.lan.LanAgentProbeSnapshot
import com.example.deckbridge.lan.QR_INVITE_DEVICE_ID
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PairingUiState(
    val sessionId: String,
    val pairingCode: String,
    val targetLabel: String,
    val host: String,
    val port: Int,
    val userErrorRes: Int? = null,
)

/** Row status for a discovered / saved LAN agent (from live HTTP probes). */
sealed class LanAgentRowUiState {
    data object Unknown : LanAgentRowUiState()
    data object Probing : LanAgentRowUiState()
    data object Offline : LanAgentRowUiState()
    data object ReadyToLink : LanAgentRowUiState()
    data object LinkedThisPhone : LanAgentRowUiState()
    data object LinkedOtherPhone : LanAgentRowUiState()
}

class PcConnectionViewModel(
    private val repository: DeckBridgeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** `true` when opened from Settings to add another Windows/Mac host (not first-run onboarding). */
    val isAddAnotherHostContext: Boolean =
        savedStateHandle.get<Int>("addHostFlow")?.let { it == 1 } ?: false

    val appState: StateFlow<AppState> = repository.appState
    val scanState: StateFlow<LanAgentListScanState> = repository.lanAgentListScanState

    val displayAgents: StateFlow<List<LanDiscoveredAgent>> = combine(
        repository.lanAgentListScanState,
        repository.appState,
    ) { scan, app -> Companion.buildAgentList(scan, app) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _agentRowUi = MutableStateFlow<Map<String, LanAgentRowUiState>>(emptyMap())
    val agentRowUi: StateFlow<Map<String, LanAgentRowUiState>> = _agentRowUi.asStateFlow()

    /** `agentKey` → `agent_os` from last GET /health probe (fills gaps when UDP JSON omits `agent_os`). */
    private val _probeAgentOs = MutableStateFlow<Map<String, String>>(emptyMap())
    val probeAgentOs: StateFlow<Map<String, String>> = _probeAgentOs.asStateFlow()

    private val _helpVisible = MutableStateFlow(false)
    val helpVisible: StateFlow<Boolean> = _helpVisible.asStateFlow()

    private val _pairing = MutableStateFlow<PairingUiState?>(null)
    val pairing: StateFlow<PairingUiState?> = _pairing.asStateFlow()

    private val _reconnectSlow = MutableStateFlow(false)
    val reconnectSlow: StateFlow<Boolean> = _reconnectSlow.asStateFlow()

    private val _qrScanMessageRes = MutableStateFlow<Int?>(null)
    val qrScanMessageRes: StateFlow<Int?> = _qrScanMessageRes.asStateFlow()

    /** Non-null while a QR-driven pairing step is in progress (string resource id). */
    private val _qrPhaseMessageRes = MutableStateFlow<Int?>(null)
    val qrPhaseMessageRes: StateFlow<Int?> = _qrPhaseMessageRes.asStateFlow()

    private val _qrNavOut = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val qrNavOut: SharedFlow<Unit> = _qrNavOut.asSharedFlow()

    private val _qrScanReset = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val qrScanReset: SharedFlow<Unit> = _qrScanReset.asSharedFlow()

    private var emitQrNavigateOnPairingSuccess: Boolean = false

    private val _inlineMessageRes = MutableStateFlow<Int?>(null)
    val inlineMessageRes: StateFlow<Int?> = _inlineMessageRes.asStateFlow()

    private var pairingPollJob: Job? = null

    init {
        if (isAddAnotherHostContext) {
            DeckBridgeLog.state("PcConnection opened from Settings (add another computer)")
        }
        // LAN list scan is started from PcConnectionHomeScreen (LaunchedEffect) so each visit gets
        // a full window and we avoid double-scan vs repository job cancel/restart races.
        viewModelScope.launch {
            combine(repository.lanAgentListScanState, repository.appState) { scan, app ->
                Triple(scan, Companion.buildAgentList(scan, app), app)
            }.collect { (scan, agents, app) ->
                if (agents.isEmpty()) {
                    _agentRowUi.value = emptyMap()
                    _probeAgentOs.value = emptyMap()
                    return@collect
                }
                if (scan is LanAgentListScanState.Scanning) {
                    _agentRowUi.value = agents.associate { agentKey(it) to LanAgentRowUiState.Probing }
                    _probeAgentOs.value = emptyMap()
                    return@collect
                }
                val canProbe = scan is LanAgentListScanState.Ready ||
                    (scan is LanAgentListScanState.Idle && agents.isNotEmpty())
                if (!canProbe) {
                    return@collect
                }
                delay(280L)
                coroutineScope {
                    _probeAgentOs.value = emptyMap()
                    agents.forEach { agent ->
                        launch {
                            val key = agentKey(agent)
                            _agentRowUi.update { it + (key to LanAgentRowUiState.Probing) }
                            val snap = repository.probeLanAgent(agent.address, agent.httpPort)
                            DeckBridgeLog.lan(
                                "connect probe ${agent.address}:${agent.httpPort} healthOk=${snap.healthOk} " +
                                    "detail=${snap.healthDetail} paired=${snap.serverReportsPaired} " +
                                    "actionRequiresToken=${snap.actionRequiresPairToken} " +
                                    "remoteDevPrefix=${snap.pairedDeviceIdOnHost?.take(8) ?: "—"} pairTokValid=${snap.pairTokenValid} " +
                                    "agent_os=${snap.agentOs ?: "—"}",
                            )
                            val os = snap.agentOs?.trim()?.takeIf { it.isNotEmpty() }
                            if (os != null) {
                                _probeAgentOs.update { it + (key to os) }
                            }
                            val myId = repository.getOrCreateLanMobileDeviceId()
                            val row = mapProbeToRow(
                                snap = snap,
                                agent = agent,
                                myDeviceId = myId,
                                savedHost = app.lanServerHost.trim(),
                                persistedPairActive = app.lanPersistedPairActive,
                            )
                            _agentRowUi.update { it + (key to row) }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            delay(2_800)
            val s = repository.appState.value
            if (s.hostDeliveryChannel == HostDeliveryChannel.LAN &&
                s.lanServerHost.isNotBlank() &&
                s.lanHealthOk != true
            ) {
                _reconnectSlow.value = true
            }
        }
        viewModelScope.launch {
            repository.appState.collect { s ->
                if (s.lanHealthOk == true) {
                    _reconnectSlow.value = false
                }
            }
        }
    }

    fun consumeQrScanMessage() {
        _qrScanMessageRes.value = null
    }

    fun clearQrPhaseMessage() {
        _qrPhaseMessageRes.value = null
    }

    private fun unlockQrCameraGateForRetry() {
        _qrScanReset.tryEmit(Unit)
    }

    /** While on [QrScanScreen], surface failures here (not only [_pairing], which Connect home reads). */
    private fun surfaceQrScanFailureRes(messageRes: Int) {
        _qrPhaseMessageRes.value = null
        _qrScanMessageRes.value = messageRes
        unlockQrCameraGateForRetry()
    }

    private fun surfaceQrScanFailureFromHost(message: String?) {
        surfaceQrScanFailureRes(mapPairingFailureToRes(message))
    }

    fun consumeInlineMessage() {
        _inlineMessageRes.value = null
    }

    fun refreshAgentList() {
        repository.refreshLanAgentListScan()
    }

    fun openHelp() {
        _helpVisible.value = true
    }

    fun closeHelp() {
        _helpVisible.value = false
    }

    fun dismissReconnectSlow() {
        _reconnectSlow.value = false
        refreshAgentList()
    }

    fun agentKey(agent: LanDiscoveredAgent): String = "${agent.address}:${agent.httpPort}"

    fun rowStateFor(agent: LanDiscoveredAgent): LanAgentRowUiState =
        _agentRowUi.value[agentKey(agent)] ?: LanAgentRowUiState.Unknown

    fun selectDiscoveredAgent(agent: LanDiscoveredAgent) {
        when (val row = rowStateFor(agent)) {
            LanAgentRowUiState.LinkedOtherPhone -> {
                _inlineMessageRes.value = R.string.connect_select_blocked_other
                viewModelScope.launch {
                    delay(4_000L)
                    if (_inlineMessageRes.value == R.string.connect_select_blocked_other) {
                        _inlineMessageRes.value = null
                    }
                }
                return
            }
            LanAgentRowUiState.LinkedThisPhone -> {
                pairingPollJob?.cancel()
                viewModelScope.launch {
                    runCatching {
                        repository.applyLanEndpointPreservingPairToken(agent.address, agent.httpPort)
                        repository.setHostDeliveryChannel(HostDeliveryChannel.LAN, skipLanDiscovery = true)
                        repository.probeLanHealthNow()
                        if (repository.appState.value.lanHealthOk == true) {
                            DeckBridgeLog.lan("quick reconnect same PC ${agent.address}")
                            return@launch
                        }
                        startFullPairingAfterEndpoint(agent)
                    }.onFailure { DeckBridgeLog.lan("quick reconnect failed: ${it.message}") }
                }
                return
            }
            else -> {
                pairingPollJob?.cancel()
                viewModelScope.launch {
                    runCatching {
                        startFullPairingAfterEndpoint(agent)
                    }.onFailure { DeckBridgeLog.lan("pairing flow error: ${it.message}") }
                }
            }
        }
    }

    private suspend fun startFullPairingAfterEndpoint(agent: LanDiscoveredAgent) {
        emitQrNavigateOnPairingSuccess = false
        _qrPhaseMessageRes.value = null
        repository.syncLanEndpointForPairing(agent.address, agent.httpPort, clearPairToken = true)
        repository.setHostDeliveryChannel(HostDeliveryChannel.LAN, skipLanDiscovery = true)
        repository.probeLanHealthNow()
        val displayName = "DeckBridge · ${agent.label}"
        val created = repository.startLanPairingSession(displayName).getOrElse { e ->
            DeckBridgeLog.lan("pairing start failed: ${e.message}")
            _pairing.value = PairingUiState(
                sessionId = "",
                pairingCode = "—",
                targetLabel = agent.label,
                host = agent.address,
                port = agent.httpPort,
                userErrorRes = mapPairingFailureToRes(e.message),
            )
            return
        }
        _pairing.value = PairingUiState(
            sessionId = created.sessionId,
            pairingCode = created.pairingCode,
            targetLabel = agent.label,
            host = agent.address,
            port = agent.httpPort,
        )
        DeckBridgeLog.lan("pairing session=${created.sessionId} code=${created.pairingCode}")
        pollPairingUntilTerminal(created.sessionId, agent.label, agent.address, agent.httpPort)
    }

    /**
     * Starts LAN bootstrap + pairing after a valid QR payload. Invalid payloads set [qrScanMessageRes].
     * On success, emits [qrNavOut] so the scanner can close (see [emitQrNavigateOnPairingSuccess]).
     * @return true if [raw] was a valid DeckBridge payload (caller should stop scanning this frame).
     */
    fun submitQrScan(raw: String): Boolean {
        val ep = DeckbridgePairingPayload.parse(raw.trim())
        if (ep == null) {
            surfaceQrScanFailureRes(R.string.connect_qr_invalid)
            DeckBridgeLog.qr("scan parse_rejected raw_len=${raw.length}")
            return false
        }
        pairingPollJob?.cancel()
        emitQrNavigateOnPairingSuccess = true
        DeckBridgeLog.qr(
            "scan accepted host=${ep.host} port=${ep.port} sid=${ep.sessionId} " +
                "osHint=${ep.suggestedHostPlatform} hostDisplay=${ep.hostDisplayName} raw_len=${raw.length}",
        )
        viewModelScope.launch {
            runCatching {
                beginQrPairing(ep)
            }.onFailure { e ->
                emitQrNavigateOnPairingSuccess = false
                surfaceQrScanFailureFromHost(e.message)
                DeckBridgeLog.qr("pairing exception: ${e.message}")
            }
        }
        return true
    }

    private suspend fun beginQrPairing(ep: DeckbridgePairingPayload.Bootstrap) {
        ep.suggestedHostPlatform?.let { plat ->
            DeckBridgeLog.qr("deeplink os=… applying hostPlatform=$plat before LAN bootstrap")
            repository.syncHostPlatformForPairing(plat)
        }
        _qrPhaseMessageRes.value = R.string.connect_qr_phase_bootstrapping
        repository.syncLanEndpointForPairing(ep.host, ep.port, clearPairToken = true)
        repository.setHostDeliveryChannel(HostDeliveryChannel.LAN, skipLanDiscovery = true)
        repository.probeLanHealthNow()
        val healthOk = repository.appState.value.lanHealthOk
        DeckBridgeLog.qr("bootstrap host=${ep.host} port=${ep.port} lanHealthOk=$healthOk")
        val label = ep.hostDisplayName?.takeIf { it.isNotBlank() } ?: ep.host
        val displayName = "DeckBridge · QR"
        val myId = repository.getOrCreateLanMobileDeviceId()
        if (ep.sessionId != null) {
            _qrPhaseMessageRes.value = R.string.connect_qr_phase_loading_session
            val st0 = repository.getLanPairingSessionStatus(ep.sessionId).getOrElse { e ->
                DeckBridgeLog.qr("GET session failed sid=${ep.sessionId} err=${e.message}")
                emitQrNavigateOnPairingSuccess = false
                _pairing.value = null
                surfaceQrScanFailureFromHost(e.message)
                return
            }
            var session = st0
            val mid = st0.mobileDeviceId
            if (mid != null && mid != QR_INVITE_DEVICE_ID && mid != myId) {
                DeckBridgeLog.qr("session owned by other device mid_prefix=${mid.take(12)}")
                emitQrNavigateOnPairingSuccess = false
                _pairing.value = null
                surfaceQrScanFailureRes(R.string.connect_qr_session_other_device)
                return
            }
            if (mid == QR_INVITE_DEVICE_ID) {
                _qrPhaseMessageRes.value = R.string.connect_qr_phase_claiming
                DeckBridgeLog.qr("POST claim sid=${ep.sessionId}")
                repository.claimLanPairingSession(ep.sessionId, displayName).getOrElse { e ->
                    DeckBridgeLog.qr("claim failed: ${e.message}")
                    emitQrNavigateOnPairingSuccess = false
                    _pairing.value = null
                    surfaceQrScanFailureFromHost(e.message)
                    return
                }
                session = repository.getLanPairingSessionStatus(ep.sessionId).getOrElse { e ->
                    DeckBridgeLog.qr("GET session after claim failed: ${e.message}")
                    emitQrNavigateOnPairingSuccess = false
                    _pairing.value = null
                    surfaceQrScanFailureFromHost(e.message)
                    return
                }
                DeckBridgeLog.qr(
                    "claimed session=${session.sessionId} status=${session.status} mobile=${session.mobileDeviceId}",
                )
            }
            _pairing.value = PairingUiState(
                sessionId = session.sessionId,
                pairingCode = session.pairingCode ?: "—",
                targetLabel = label,
                host = ep.host,
                port = ep.port,
            )
            _qrPhaseMessageRes.value = R.string.connect_qr_phase_waiting_pc
            DeckBridgeLog.qr("poll start session=${session.sessionId} code=${session.pairingCode}")
            pollPairingUntilTerminal(session.sessionId, label, ep.host, ep.port)
        } else {
            val created = repository.startLanPairingSession(displayName).getOrElse { e ->
                emitQrNavigateOnPairingSuccess = false
                _pairing.value = null
                surfaceQrScanFailureFromHost(e.message)
                DeckBridgeLog.qr("start session (no sid in QR) failed: ${e.message}")
                return
            }
            _pairing.value = PairingUiState(
                sessionId = created.sessionId,
                pairingCode = created.pairingCode,
                targetLabel = label,
                host = ep.host,
                port = ep.port,
            )
            _qrPhaseMessageRes.value = R.string.connect_qr_phase_waiting_pc
            DeckBridgeLog.qr("new session from QR host-only payload session=${created.sessionId} code=${created.pairingCode}")
            pollPairingUntilTerminal(created.sessionId, label, ep.host, ep.port)
        }
    }

    fun dismissPairing() {
        if (emitQrNavigateOnPairingSuccess) {
            unlockQrCameraGateForRetry()
        }
        emitQrNavigateOnPairingSuccess = false
        _qrPhaseMessageRes.value = null
        val sid = _pairing.value?.sessionId
        pairingPollJob?.cancel()
        pairingPollJob = null
        if (!sid.isNullOrBlank()) {
            viewModelScope.launch {
                repository.cancelLanPairingSession(sid).onFailure {
                    DeckBridgeLog.lan("pairing cancel failed: ${it.message}")
                }
                _pairing.value = null
            }
        } else {
            _pairing.value = null
        }
    }

    private fun pollPairingUntilTerminal(
        sessionId: String,
        targetLabel: String,
        host: String,
        port: Int,
    ) {
        pairingPollJob?.cancel()
        pairingPollJob = launchPairingPoll(sessionId, targetLabel, host, port)
    }

    private fun launchPairingPoll(
        sessionId: String,
        targetLabel: String,
        host: String,
        port: Int,
    ): Job = viewModelScope.launch {
        var lastLoggedStatus: String? = null
        while (isActive) {
            delay(1_400L)
            val st = repository.getLanPairingSessionStatus(sessionId).getOrElse { e ->
                DeckBridgeLog.qr("poll GET session error session=$sessionId err=${e.message}")
                if (emitQrNavigateOnPairingSuccess) {
                    emitQrNavigateOnPairingSuccess = false
                    _pairing.value = null
                    surfaceQrScanFailureFromHost(e.message)
                } else {
                    emitQrNavigateOnPairingSuccess = false
                    _qrPhaseMessageRes.value = null
                    _pairing.update { p ->
                        p?.copy(userErrorRes = mapPairingFailureToRes(e.message))
                    }
                }
                return@launch
            }
            if (st.status != lastLoggedStatus) {
                DeckBridgeLog.qr(
                    "poll session=$sessionId status=${st.status} code=${st.pairingCode ?: "—"} hasToken=${!st.pairToken.isNullOrBlank()}",
                )
                lastLoggedStatus = st.status
            }
            when (st.status) {
                "consumed", "paired" -> {
                    val token = st.pairToken
                    if (token.isNullOrBlank()) {
                        DeckBridgeLog.qr("poll terminal=${st.status} session=$sessionId missing pair_token")
                        if (emitQrNavigateOnPairingSuccess) {
                            emitQrNavigateOnPairingSuccess = false
                            _pairing.value = null
                            surfaceQrScanFailureRes(R.string.pairing_err_generic)
                        } else {
                            emitQrNavigateOnPairingSuccess = false
                            _qrPhaseMessageRes.value = null
                            _pairing.update { p -> p?.copy(userErrorRes = R.string.pairing_err_generic) }
                        }
                        return@launch
                    }
                    repository.persistLanPairToken(token)
                    repository.testLanHealth()
                    DeckBridgeLog.qr("poll terminal=${st.status} session=$sessionId token persisted → home")
                    val popQr = emitQrNavigateOnPairingSuccess
                    emitQrNavigateOnPairingSuccess = false
                    _qrPhaseMessageRes.value = null
                    _pairing.value = null
                    if (popQr) {
                        _qrNavOut.tryEmit(Unit)
                    }
                    return@launch
                }
                "rejected" -> {
                    DeckBridgeLog.qr("poll terminal=rejected session=$sessionId")
                    if (emitQrNavigateOnPairingSuccess) {
                        emitQrNavigateOnPairingSuccess = false
                        _pairing.value = null
                        surfaceQrScanFailureRes(R.string.pairing_err_rejected)
                    } else {
                        emitQrNavigateOnPairingSuccess = false
                        _qrPhaseMessageRes.value = null
                        _pairing.update { p -> p?.copy(userErrorRes = R.string.pairing_err_rejected) }
                    }
                    return@launch
                }
                "expired" -> {
                    DeckBridgeLog.qr("poll terminal=expired session=$sessionId")
                    if (emitQrNavigateOnPairingSuccess) {
                        emitQrNavigateOnPairingSuccess = false
                        _pairing.value = null
                        surfaceQrScanFailureRes(R.string.pairing_err_expired)
                    } else {
                        emitQrNavigateOnPairingSuccess = false
                        _qrPhaseMessageRes.value = null
                        _pairing.update { p -> p?.copy(userErrorRes = R.string.pairing_err_expired) }
                    }
                    return@launch
                }
                "cancelled" -> {
                    DeckBridgeLog.qr("poll terminal=cancelled session=$sessionId")
                    if (emitQrNavigateOnPairingSuccess) {
                        emitQrNavigateOnPairingSuccess = false
                        _pairing.value = null
                        surfaceQrScanFailureRes(R.string.pairing_err_cancelled)
                    } else {
                        emitQrNavigateOnPairingSuccess = false
                        _qrPhaseMessageRes.value = null
                        _pairing.update { p -> p?.copy(userErrorRes = R.string.pairing_err_cancelled) }
                    }
                    return@launch
                }
                "superseded" -> {
                    DeckBridgeLog.qr("poll terminal=superseded session=$sessionId")
                    if (emitQrNavigateOnPairingSuccess) {
                        emitQrNavigateOnPairingSuccess = false
                        _pairing.value = null
                        surfaceQrScanFailureRes(R.string.pairing_err_superseded)
                    } else {
                        emitQrNavigateOnPairingSuccess = false
                        _qrPhaseMessageRes.value = null
                        _pairing.update { p -> p?.copy(userErrorRes = R.string.pairing_err_superseded) }
                    }
                    return@launch
                }
                "pending_approval", "pending" -> {
                    st.pairingCode?.let { c ->
                        _pairing.update { p -> if (p != null && p.pairingCode != c) p.copy(pairingCode = c) else p }
                    }
                }
                else -> {
                    DeckBridgeLog.qr("poll unknown status=${st.status} session=$sessionId")
                    st.pairingCode?.let { c ->
                        _pairing.update { p -> if (p != null && p.pairingCode != c) p.copy(pairingCode = c) else p }
                    }
                }
            }
        }
    }

    companion object {
        fun factory(
            repository: DeckBridgeRepository,
            navBackStackEntry: NavBackStackEntry,
        ): AbstractSavedStateViewModelFactory =
            object : AbstractSavedStateViewModelFactory(
                navBackStackEntry,
                navBackStackEntry.arguments,
            ) {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    key: String,
                    modelClass: Class<T>,
                    handle: SavedStateHandle,
                ): T {
                    require(modelClass.isAssignableFrom(PcConnectionViewModel::class.java))
                    return PcConnectionViewModel(repository, handle) as T
                }
            }

        fun buildAgentList(scan: LanAgentListScanState, app: AppState): List<LanDiscoveredAgent> {
            val fromScan = when (scan) {
                is LanAgentListScanState.Ready -> scan.agents
                else -> emptyList()
            }
            val merged = fromScan.toMutableList()
            // While UDP scan runs, do not inject the saved host — it made the UI skip the "searching"
            // state (list non-empty) and showed a stale row as "Probing" for the whole scan window.
            // Only surface a persisted host row when there is an active trusted link — avoids
            // showing stale IPs or old lab hosts from DataStore after token was cleared.
            if (scan !is LanAgentListScanState.Scanning) {
                val saved = app.lanServerHost.trim()
                val showSaved =
                    app.lanPersistedPairActive &&
                        saved.isNotEmpty() &&
                        merged.none { it.address.equals(saved, ignoreCase = true) }
                if (showSaved) {
                    merged.add(LanDiscoveredAgent(address = saved, httpPort = app.lanServerPort))
                }
            }
            return merged.distinctBy { it.address }
        }

        fun mapProbeToRow(
            snap: LanAgentProbeSnapshot,
            agent: LanDiscoveredAgent,
            myDeviceId: String,
            savedHost: String,
            persistedPairActive: Boolean,
        ): LanAgentRowUiState {
            val sameSavedHost = agent.address.trim().equals(savedHost, ignoreCase = true)
            if (!snap.healthOk) return LanAgentRowUiState.Offline
            if (snap.pairTokenValid == true && sameSavedHost && persistedPairActive) {
                return LanAgentRowUiState.LinkedThisPhone
            }
            if (!snap.serverReportsPaired && !snap.actionRequiresPairToken) {
                return LanAgentRowUiState.ReadyToLink
            }
            val remoteId = snap.pairedDeviceIdOnHost ?: return LanAgentRowUiState.ReadyToLink
            if (remoteId == myDeviceId) {
                return if (sameSavedHost && persistedPairActive) {
                    LanAgentRowUiState.LinkedThisPhone
                } else {
                    LanAgentRowUiState.ReadyToLink
                }
            }
            return LanAgentRowUiState.LinkedOtherPhone
        }

        fun mapPairingFailureToRes(message: String?): Int {
            val m = message.orEmpty().lowercase()
            return when {
                m.contains("error=expired") || m.contains("\"error\":\"expired\"") || m == "expired" ->
                    R.string.pairing_err_expired
                m.contains("session_not_found") || m.contains("error=session_not_found") ->
                    R.string.pairing_err_session_invalid
                m.contains("device_mismatch") || m.contains("error=device_mismatch") ->
                    R.string.connect_qr_session_other_device
                m.contains("invalid_status") -> when {
                    m.contains("expired") -> R.string.pairing_err_expired
                    m.contains("rejected") -> R.string.pairing_err_rejected
                    m.contains("cancelled") -> R.string.pairing_err_cancelled
                    m.contains("consumed") || m.contains("paired") -> R.string.pairing_err_session_invalid
                    else -> R.string.pairing_err_session_invalid
                }
                m.contains("404") -> R.string.pairing_err_session_invalid
                m.contains("401") -> R.string.pairing_err_host_unreachable
                m.isBlank() -> R.string.pairing_err_generic
                m.contains("failed to connect") || m.contains("timeout") || m.contains("unreachable") ->
                    R.string.pairing_err_host_unreachable
                else -> R.string.pairing_err_generic
            }
        }
    }
}
