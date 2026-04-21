package com.example.deckbridge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deckbridge.R
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PlatformSlotState

@Composable
fun DashboardTopChrome(
    state: AppState,
    onOpenSettings: () -> Unit,
    onGoToConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        ConnectionStatusPill(state = state, onGoToConnect = onGoToConnect)
        val batteryLevel = state.physicalKeyboard
            .takeIf { it.state == PhysicalKeyboardConnectionState.CONNECTED }
            ?.batteryLevel
        if (batteryLevel != null) {
            Spacer(Modifier.width(6.dp))
            KeyboardBatteryBadge(level = batteryLevel)
        }
        Spacer(Modifier.width(10.dp))
        ChromeIconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.fab_open_settings),
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

internal fun slotHealthDotColor(slot: PlatformSlotState): Color = when {
    slot.channel == HostDeliveryChannel.MAC_BRIDGE && slot.macBridgeClientAlive -> Color(0xFF2EE6A0)
    slot.channel == HostDeliveryChannel.MAC_BRIDGE && slot.macBridgeServerRunning -> Color(0xFFFFB020)
    slot.channel == HostDeliveryChannel.MAC_BRIDGE -> Color(0xFF5A5A68)
    slot.host.isBlank() -> Color(0xFF5A5A68)
    !slot.trustOk -> Color(0xFFFF6B5A)
    slot.healthOk == true -> Color(0xFF2EE6A0)
    slot.healthRetrying -> Color(0xFFFFB020)
    slot.healthOk == false -> Color(0xFFFF6B5A)
    else -> Color(0xFFFFB020)
}

@Composable
fun DashboardLandscapeChrome(
    state: AppState,
    onOpenSettings: () -> Unit,
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onGoToConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val effective = when (state.hostPlatform) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> state.hostPlatform
    }
    Column(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionStatusPill(state = state, onGoToConnect = onGoToConnect, compact = true)
            val batteryLevel = state.physicalKeyboard
                .takeIf { it.state == PhysicalKeyboardConnectionState.CONNECTED }
                ?.batteryLevel
            if (batteryLevel != null) {
                KeyboardBatteryBadge(level = batteryLevel)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LandscapePlatformOrb(
                    selected = effective == HostPlatform.WINDOWS,
                    onClick = { onHostPlatformSelected(HostPlatform.WINDOWS) },
                    healthDotColor = slotHealthDotColor(state.windowsSlot),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_platform_windows),
                        contentDescription = stringResource(R.string.platform_chip_windows),
                        modifier = Modifier.size(20.dp),
                        tint = Color.White.copy(alpha = if (effective == HostPlatform.WINDOWS) 1f else 0.45f),
                    )
                }
                LandscapePlatformOrb(
                    selected = effective == HostPlatform.MAC,
                    onClick = { onHostPlatformSelected(HostPlatform.MAC) },
                    healthDotColor = slotHealthDotColor(state.macSlot),
                ) {
                    Icon(
                        Icons.Outlined.LaptopMac,
                        contentDescription = stringResource(R.string.platform_chip_mac),
                        modifier = Modifier.size(20.dp),
                        tint = Color.White.copy(alpha = if (effective == HostPlatform.MAC) 1f else 0.45f),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        ChromeIconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.fab_open_settings),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LandscapePlatformOrb(
    selected: Boolean,
    onClick: () -> Unit,
    healthDotColor: Color = Color(0xFF5A5A68),
    content: @Composable () -> Unit,
) {
    val shape = CircleShape
    val bg = if (selected) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.04f)
    }
    val ring = if (selected) {
        Color(0xFF3D62FF).copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(bg)
                .border(1.dp, ring, shape),
        ) {
            content()
        }
        // Small health dot in bottom-right corner
        Box(
            modifier = Modifier
                .size(7.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(healthDotColor),
        )
    }
}

@Composable
private fun ChromeIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = CircleShape
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        content()
    }
}

/**
 * Tappable pill showing the current connection state with a colored dot and a text label.
 * Tapping navigates to the connection screen.
 *
 * [compact] = true collapses the text label (landscape side-panel where vertical space is limited).
 */
@Composable
private fun ConnectionStatusPill(
    state: AppState,
    onGoToConnect: () -> Unit,
    compact: Boolean = false,
) {
    val channel = state.hostDeliveryChannel
    val lan = channel == HostDeliveryChannel.LAN
    val macBridge = channel == HostDeliveryChannel.MAC_BRIDGE
    val hostBlank = state.lanServerHost.isBlank()
    val hostLine = state.lanServerHost.trim().takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.dash_host_placeholder)
    val detailShort = state.lanHealthDetail?.trim()?.take(44)?.takeIf { it.isNotEmpty() }

    val (dotColor, statusLabel) = when {
        macBridge && state.macBridgeClientAlive && state.lanPersistedPairActive ->
            Color(0xFF2EE6A0) to stringResource(R.string.dash_mac_bridge_linked)
        macBridge && state.macBridgeClientAlive ->
            Color(0xFF2EE6A0) to stringResource(R.string.dash_mac_bridge_connected)
        macBridge && state.macBridgeServerRunning ->
            Color(0xFFFFB020) to stringResource(R.string.dash_mac_bridge_server_ready)
        macBridge ->
            Color(0xFF5A5A68) to stringResource(R.string.dash_mac_bridge_inactive)
        !lan -> Color(0xFF5A5A68) to stringResource(R.string.dash_link_hid)
        !state.lanTrustOk -> Color(0xFFFF6B5A) to stringResource(R.string.dash_lan_trust)
        lan && hostBlank -> Color(0xFFFFB020) to stringResource(R.string.dash_lan_no_host)
        state.lanHealthOk == true -> Color(0xFF2EE6A0) to stringResource(R.string.dash_link_ready)
        state.lanHealthOk == false && state.lanHealthRetrying ->
            Color(0xFFFFB020) to stringResource(R.string.dash_lan_retrying)
        state.lanHealthOk == false -> Color(0xFFFF6B5A) to stringResource(R.string.dash_lan_offline)
        else -> Color(0xFFFFB020) to stringResource(R.string.dash_link_lan_pending)
    }

    val detailLine = when {
        state.lastActionFailed -> stringResource(R.string.dash_action_not_sent)
        state.macBridgeActionDropped -> stringResource(R.string.mac_bridge_action_dropped)
        macBridge && state.macBridgeClientAlive ->
            state.macBridgeClientIp ?: stringResource(R.string.dash_host_placeholder)
        macBridge && state.macBridgeServerRunning ->
            stringResource(R.string.mac_bridge_server_port_hint)
        macBridge -> stringResource(R.string.dash_mac_bridge_inactive)
        lan && state.lanHealthOk == false && detailShort != null -> detailShort
        else -> hostLine
    }
    val a11yLabel = stringResource(R.string.dash_connection_dot_a11y, statusLabel, detailLine)

    // One-word label shown in the compact (landscape) pill: derived from dot color
    // so it stays in sync without duplicating state logic.
    val shortLabel = when (dotColor) {
        Color(0xFF2EE6A0) -> stringResource(R.string.dash_status_short_online)
        Color(0xFFFFB020) -> stringResource(R.string.dash_status_short_waiting)
        Color(0xFFFF6B5A) -> stringResource(R.string.dash_status_short_offline)
        else              -> stringResource(R.string.dash_status_short_inactive)
    }

    val interaction = remember { MutableInteractionSource() }
    val pillModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Color.White.copy(alpha = 0.07f))
        .clickable(
            interactionSource = interaction,
            indication = ripple(bounded = true),
            onClick = onGoToConnect,
        )
        .semantics { contentDescription = a11yLabel }

    val dot = @Composable {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
    }

    if (compact) {
        // Landscape side-panel: dot stacked above a one-word status word.
        // Panel width ~56 dp → 44 dp content area → fits "Waiting…" at 9 sp.
        Column(
            modifier = pillModifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            dot()
            Spacer(Modifier.height(4.dp))
            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        // Portrait top-bar: dot + full status label in a row.
        Row(
            modifier = pillModifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dot()
            Spacer(Modifier.width(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Circular battery badge shown next to the connection pill when a Bluetooth keyboard is connected
 * and battery level is available (API 31+).
 *
 * Color coding: green ≥ 50 %, amber 20–49 %, red < 20 %.
 */
@Composable
internal fun KeyboardBatteryBadge(level: Int, modifier: Modifier = Modifier) {
    val ringColor = when {
        level >= 50 -> Color(0xFF2EE6A0)
        level >= 20 -> Color(0xFFFFB020)
        else -> Color(0xFFFF6B5A)
    }
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$level",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = ringColor,
            maxLines = 1,
        )
    }
}
