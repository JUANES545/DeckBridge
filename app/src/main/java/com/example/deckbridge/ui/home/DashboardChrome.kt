package com.example.deckbridge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform

@Composable
fun DashboardTopChrome(
    state: AppState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        ConnectionStatusDot(state = state)
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

@Composable
fun DashboardLandscapeChrome(
    state: AppState,
    onOpenSettings: () -> Unit,
    onHostPlatformSelected: (HostPlatform) -> Unit,
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
            ConnectionStatusDot(state = state)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LandscapePlatformOrb(
                    selected = effective == HostPlatform.WINDOWS,
                    onClick = { onHostPlatformSelected(HostPlatform.WINDOWS) },
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

@Composable
private fun ConnectionStatusDot(state: AppState) {
    val lan = state.hostDeliveryChannel == HostDeliveryChannel.LAN
    val hostBlank = state.lanServerHost.isBlank()
    val hostLine = state.lanServerHost.trim().takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.dash_host_placeholder)
    val detailShort = state.lanHealthDetail?.trim()?.take(44)?.takeIf { it.isNotEmpty() }

    val (dotColor, statusLabel) = when {
        !lan -> Color(0xFF5A5A68) to stringResource(R.string.dash_link_hid)
        !state.lanTrustOk -> Color(0xFFFF6B5A) to stringResource(R.string.dash_lan_trust)
        lan && hostBlank -> Color(0xFFFFB020) to stringResource(R.string.dash_lan_no_host)
        state.lanHealthOk == true -> Color(0xFF2EE6A0) to stringResource(R.string.dash_link_ready)
        state.lanHealthOk == false -> Color(0xFFFFB020) to stringResource(R.string.dash_lan_offline)
        else -> Color(0xFFFFB020) to stringResource(R.string.dash_link_lan_pending)
    }

    val detailLine = when {
        lan && state.lanHealthOk == false && detailShort != null -> detailShort
        else -> hostLine
    }
    val a11yLabel = stringResource(
        R.string.dash_connection_dot_a11y,
        statusLabel,
        detailLine,
    )

    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(dotColor)
            .semantics { contentDescription = a11yLabel },
    )
}
