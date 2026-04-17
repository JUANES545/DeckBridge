@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.connect

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.deckbridge.R
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.LanAgentListScanState
import com.example.deckbridge.domain.model.LanDiscoveredAgent
import com.example.deckbridge.lan.LanDiscovery
import com.example.deckbridge.ui.onboarding.OnboardingSecondaryCta
import com.example.deckbridge.ui.onboarding.OnboardingTheme
import kotlinx.coroutines.delay

@Composable
private fun LanDiscoveredAgentRowTitle(
    agent: LanDiscoveredAgent,
    healthAgentOs: String?,
    color: Color,
) {
    val name = agent.displayName?.trim()?.takeIf { it.isNotEmpty() }
    val os = healthAgentOs?.trim()?.takeIf { it.isNotEmpty() }
        ?: agent.agentOs?.trim()?.takeIf { it.isNotEmpty() }
    val text = name ?: when (os?.lowercase()) {
        "darwin", "macos", "osx", "mac" ->
            stringResource(R.string.lan_discovered_agent_label_mac, agent.address)
        "windows", "win32" ->
            stringResource(R.string.lan_discovered_agent_label_windows, agent.address)
        else -> agent.address
    }
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
}

private val FlowDialogShape = RoundedCornerShape(20.dp)

@Composable
fun PcConnectionHomeScreen(
    viewModel: PcConnectionViewModel,
    onBack: () -> Unit,
    onOpenQr: () -> Unit,
    onContinueWithoutPcLink: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val agents by viewModel.displayAgents.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val reconnectSlow by viewModel.reconnectSlow.collectAsStateWithLifecycle()
    val helpVisible by viewModel.helpVisible.collectAsStateWithLifecycle()
    val app by viewModel.appState.collectAsStateWithLifecycle()
    val inlineMsg by viewModel.inlineMessageRes.collectAsStateWithLifecycle()
    val addAnotherHost = viewModel.isAddAnotherHostContext
    val probeOs by viewModel.probeAgentOs.collectAsStateWithLifecycle()

    LaunchedEffect(inlineMsg) {
        if (inlineMsg != null) {
            delay(4_000L)
            viewModel.consumeInlineMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAgentList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingTheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.connect_back),
                        tint = OnboardingTheme.textPrimary,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refreshAgentList() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.connect_rescan),
                        tint = OnboardingTheme.textSecondary,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.hostDeliveryChannel == HostDeliveryChannel.MAC_BRIDGE) {
                        CircleIcon(Icons.Outlined.LaptopMac)
                    } else {
                        CircleIcon(Icons.Default.Wifi)
                        CircleIcon(Icons.Outlined.Usb)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(
                        when {
                            app.hostDeliveryChannel == HostDeliveryChannel.MAC_BRIDGE ->
                                R.string.connect_title_mac_bridge
                            addAnotherHost -> R.string.connect_title_add_host
                            else -> R.string.connect_title
                        },
                    ),
                    color = OnboardingTheme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        when {
                            app.hostDeliveryChannel == HostDeliveryChannel.MAC_BRIDGE ->
                                R.string.connect_subtitle_mac_bridge
                            addAnotherHost -> R.string.connect_subtitle_add_host
                            else -> R.string.connect_subtitle
                        },
                    ),
                    color = OnboardingTheme.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                if (app.hostDeliveryChannel == HostDeliveryChannel.MAC_BRIDGE) {
                    MacBridgeStatusSection(app = app)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.mac_bridge_qr_hint),
                        color = OnboardingTheme.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = onOpenQr,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OnboardingTheme.listRow),
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = OnboardingTheme.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.mac_bridge_scan_qr_cta),
                            color = OnboardingTheme.textPrimary,
                            fontSize = 15.sp,
                        )
                    }
                } else if (app.lanPersistedPairActive && pairing == null) {
                    Text(
                        text = stringResource(
                            if (addAnotherHost) {
                                R.string.connect_trust_banner_add_host
                            } else {
                                R.string.connect_trust_banner
                            },
                        ),
                        color = OnboardingTheme.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }
                inlineMsg?.let { resId ->
                    Text(
                        text = stringResource(resId),
                        color = Color(0xFFE57373),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    )
                }
                if (app.hostDeliveryChannel != HostDeliveryChannel.MAC_BRIDGE) {
                if (scan is LanAgentListScanState.Failed) {
                    Text(
                        text = stringResource(
                            R.string.connect_scan_error,
                            (scan as LanAgentListScanState.Failed).message,
                        ),
                        color = OnboardingTheme.textMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.connect_list_header),
                        color = OnboardingTheme.textMuted,
                        fontSize = 13.sp,
                    )
                    if (scan is LanAgentListScanState.Scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = OnboardingTheme.textMuted,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OnboardingTheme.listRow),
                ) {
                    when {
                        scan is LanAgentListScanState.Scanning -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = OnboardingTheme.accent,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = stringResource(
                                        R.string.connect_searching_hint,
                                        (LanDiscovery.SCAN_TOTAL_MS_DEFAULT / 1000L).toInt(),
                                    ),
                                    color = OnboardingTheme.textSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        agents.isNotEmpty() -> {
                        agents.forEachIndexed { index, agent ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = OnboardingTheme.background,
                                    thickness = 1.dp,
                                )
                            }
                            val row = viewModel.rowStateFor(agent)
                            val blocked = row == LanAgentRowUiState.LinkedOtherPhone
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (blocked) {
                                            Modifier
                                        } else {
                                            Modifier.clickable { viewModel.selectDiscoveredAgent(agent) }
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    LanDiscoveredAgentRowTitle(
                                        agent = agent,
                                        healthAgentOs = probeOs[viewModel.agentKey(agent)],
                                        color = if (blocked) OnboardingTheme.textMuted else OnboardingTheme.textPrimary,
                                    )
                                    LanAgentStatusSubtitle(row)
                                }
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (blocked) OnboardingTheme.textMuted else OnboardingTheme.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (addAnotherHost) {
                                            R.string.connect_no_hosts_yet_add_host
                                        } else {
                                            R.string.connect_no_hosts_yet
                                        },
                                    ),
                                    color = OnboardingTheme.textMuted,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (scan is LanAgentListScanState.Ready) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.connect_network_isolation_hint),
                                        color = OnboardingTheme.textMuted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = OnboardingTheme.background, thickness = 1.dp)
                    TextButton(
                        onClick = onOpenQr,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = OnboardingTheme.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                if (addAnotherHost) {
                                    R.string.connect_add_computer_add_host
                                } else {
                                    R.string.connect_add_computer
                                },
                            ),
                            color = OnboardingTheme.textPrimary,
                            fontSize = 15.sp,
                        )
                    }
                }
                } // end MAC_BRIDGE guard
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(
                        when {
                            app.hostDeliveryChannel == HostDeliveryChannel.MAC_BRIDGE ->
                                R.string.mac_bridge_connect_hint
                            addAnotherHost -> R.string.connect_hint_add_host
                            else -> R.string.connect_hint
                        },
                    ),
                    color = OnboardingTheme.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                onContinueWithoutPcLink?.let { skip ->
                    OnboardingSecondaryCta(
                        text = stringResource(R.string.connect_gate_continue),
                        onClick = skip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    )
                }
                OnboardingSecondaryCta(
                    text = stringResource(R.string.connect_get_help),
                    onClick = { viewModel.openHelp() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (helpVisible) {
            ConnectHelpBottomSheet(
                onDismiss = { viewModel.closeHelp() },
                addAnotherHostContext = addAnotherHost,
            )
        }

        pairing?.let { p ->
            PairingWaitDialog(
                state = p,
                addAnotherHostContext = addAnotherHost,
                onDismiss = { viewModel.dismissPairing() },
            )
        }

        if (reconnectSlow) {
            ReconnectSlowDialog(
                onDismiss = { viewModel.dismissReconnectSlow() },
            )
        }
    }
}

// ── Mac Bridge status section ─────────────────────────────────────────────────

@Composable
private fun MacBridgeStatusSection(app: AppState) {
    val alive = app.macBridgeClientAlive
    val paired = app.lanPersistedPairActive
    val clientIp = app.macBridgeClientIp
    val serverRunning = app.macBridgeServerRunning
    val actionDropped = app.macBridgeActionDropped

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OnboardingTheme.listRow)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacBridgeStatusDot(alive = alive, serverRunning = serverRunning)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(
                        when {
                            alive && paired -> R.string.mac_bridge_status_linked
                            alive -> R.string.mac_bridge_status_connected
                            else -> R.string.mac_bridge_status_waiting
                        },
                    ),
                    color = OnboardingTheme.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        actionDropped ->
                            stringResource(R.string.mac_bridge_action_dropped)
                        alive && clientIp != null ->
                            stringResource(R.string.mac_bridge_client_polling, clientIp)
                        serverRunning && !alive ->
                            stringResource(R.string.mac_bridge_server_port_hint)
                        paired -> stringResource(R.string.mac_bridge_paired_hint)
                        else -> stringResource(R.string.mac_bridge_unpaired_hint)
                    },
                    color = if (actionDropped) Color(0xFFFFB020) else OnboardingTheme.textMuted,
                    fontSize = 12.sp,
                )
            }
        }
        if (!alive) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.mac_bridge_setup_instructions),
                color = OnboardingTheme.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun MacBridgeStatusDot(alive: Boolean, serverRunning: Boolean) {
    when {
        alive -> {
            // Green: client connected
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2EE6A0)),
            )
        }
        serverRunning -> {
            // Amber pulsing: server up, waiting for Mac agent
            val pulse = rememberInfiniteTransition(label = "bridge_pulse")
            val scale by pulse.animateFloat(
                initialValue = 0.75f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bridge_dot_scale",
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB020)),
            )
        }
        else -> {
            // Grey: server not yet running
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5A5A68)),
            )
        }
    }
}

// ── LAN agent helpers ─────────────────────────────────────────────────────────

@Composable
private fun LanAgentStatusSubtitle(row: LanAgentRowUiState) {
    if (row == LanAgentRowUiState.Unknown) return
    val res = when (row) {
        LanAgentRowUiState.Probing -> R.string.connect_agent_probing
        LanAgentRowUiState.Offline -> R.string.connect_agent_offline
        LanAgentRowUiState.ReadyToLink -> R.string.connect_agent_ready
        LanAgentRowUiState.LinkedThisPhone -> R.string.connect_agent_linked_here
        LanAgentRowUiState.LinkedOtherPhone -> R.string.connect_agent_linked_other
        LanAgentRowUiState.Unknown -> return
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(res),
        color = OnboardingTheme.textMuted,
        fontSize = 12.sp,
    )
}

@Composable
private fun CircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(OnboardingTheme.cardElevated),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = OnboardingTheme.accent, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun PairingWaitDialog(
    state: PairingUiState,
    addAnotherHostContext: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = FlowDialogShape,
        containerColor = OnboardingTheme.card,
        titleContentColor = OnboardingTheme.textPrimary,
        textContentColor = OnboardingTheme.textSecondary,
        title = {
            Text(
                text = if (state.userErrorRes != null) {
                    stringResource(R.string.connect_pairing_title_failed)
                } else {
                    stringResource(R.string.connect_pairing_title, state.targetLabel)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                if (state.userErrorRes != null) {
                    Text(
                        text = stringResource(state.userErrorRes),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFE57373),
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (addAnotherHostContext) {
                                R.string.connect_pairing_body_add_host
                            } else {
                                R.string.connect_pairing_body
                            },
                        ),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.connect_pairing_waiting_hint),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = OnboardingTheme.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.pairingCode.toCharArray().joinToString("  "),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        color = OnboardingTheme.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.connect_pairing_cancel),
                    color = OnboardingTheme.accent,
                )
            }
        },
    )
}

@Composable
private fun ReconnectSlowDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = FlowDialogShape,
        containerColor = OnboardingTheme.card,
        titleContentColor = OnboardingTheme.textPrimary,
        textContentColor = OnboardingTheme.textSecondary,
        title = {
            Text(
                text = stringResource(R.string.connect_reconnect_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.connect_reconnect_body),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.connect_reconnect_cancel), color = OnboardingTheme.accent)
            }
        },
    )
}
