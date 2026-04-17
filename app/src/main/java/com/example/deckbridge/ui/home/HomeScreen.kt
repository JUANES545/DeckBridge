@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.home

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.PlatformSlotState
import com.example.deckbridge.update.UpdateState
import com.example.deckbridge.ui.hardware.HardwareMirrorPanel
import com.example.deckbridge.ui.hardware.MirrorLayoutDensity
import com.example.deckbridge.ui.hardware.MirrorPadSlot
import com.example.deckbridge.ui.hardware.MirrorPanelChrome
import com.example.deckbridge.ui.theme.DeckBridgeTheme
@Composable
fun HomeScreen(
    state: AppState,
    onDeckButtonTapped: (String) -> Unit,
    onDeckButtonLongPress: (String) -> Unit = {},
    onMirrorKnobTouchRotate: (Int, Boolean) -> Unit = { _, _ -> },
    /** Long press on a mirror knob (index 0..2) opens its editor. */
    onMirrorKnobLongPress: (Int) -> Unit = {},
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenSettings: () -> Unit = {},
    onGoToConnect: () -> Unit = {},
    updateState: UpdateState = UpdateState.Idle,
    onUpdateTapped: () -> Unit = {},
    onUpdatePermissionTapped: () -> Unit = {},
    onInstallTapped: (android.net.Uri) -> Unit = {},
    onUpdateDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val surface = Color(0xFF050508)
    val padSlots = remember(state.macroButtons) { mirrorPadSlots(state) }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    val showBanner = !bannerDismissed &&
        state.hostDeliveryChannel == HostDeliveryChannel.LAN && (
            state.lanServerHost.isBlank() ||
            !state.lanTrustOk ||
            state.lanHealthOk == false
        )
    val showUpdateBanner = updateState !is UpdateState.Idle && updateState !is UpdateState.Dismissed
    val charging = rememberDeviceCharging(state.animatedBackgroundMode == AnimatedBackgroundMode.WHEN_CHARGING)
    val showEnergyPulses = when (state.animatedBackgroundMode) {
        AnimatedBackgroundMode.ALWAYS -> true
        AnimatedBackgroundMode.WHEN_CHARGING -> charging
        AnimatedBackgroundMode.OFF -> false
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = surface,
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            DashboardHexBackground(Modifier.fillMaxSize())
            // Animated overlay at the root Box level → covers the full screen including
            // the landscape side-chrome panel.  All UI layers above are transparent or
            // semi-transparent so the animation shows through everywhere.
            if (showEnergyPulses) {
                DashboardAnimatedOverlay(
                    theme = state.animatedBackgroundTheme,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HardwareMirrorPanel(
                            calibration = state.hardwareCalibration,
                            highlight = state.hardwareMirrorHighlight,
                            knobMirrorRotation = state.knobMirrorRotation,
                            diagSummary = state.hardwareDiagSummary,
                            padSlots = padSlots,
                            deckKnobs = state.deckKnobs,
                            hostPlatform = state.hostPlatform,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            maxContentWidth = null,
                            showKnobRoleHints = false,
                            layoutDensity = MirrorLayoutDensity.DashboardLandscape,
                            chrome = MirrorPanelChrome.Dashboard,
                            deckHighlight = state.deckHighlight,
                            onPadCellTapped = onDeckButtonTapped,
                            onPadCellLongPress = onDeckButtonLongPress,
                            onMirrorKnobTouchRotate = onMirrorKnobTouchRotate,
                            onMirrorKnobLongPress = onMirrorKnobLongPress,
                        )
                        if (showUpdateBanner) {
                            UpdateBanner(
                                updateState = updateState,
                                onUpdate = onUpdateTapped,
                                onPermission = onUpdatePermissionTapped,
                                onInstall = onInstallTapped,
                                onDismiss = onUpdateDismissed,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                    DashboardLandscapeChrome(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onHostPlatformSelected = onHostPlatformSelected,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                ) {
                    DashboardTopChrome(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showUpdateBanner) {
                        UpdateBanner(
                            updateState = updateState,
                            onUpdate = onUpdateTapped,
                            onPermission = onUpdatePermissionTapped,
                            onInstall = onInstallTapped,
                            onDismiss = onUpdateDismissed,
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HardwareMirrorPanel(
                            calibration = state.hardwareCalibration,
                            highlight = state.hardwareMirrorHighlight,
                            knobMirrorRotation = state.knobMirrorRotation,
                            diagSummary = state.hardwareDiagSummary,
                            padSlots = padSlots,
                            deckKnobs = state.deckKnobs,
                            hostPlatform = state.hostPlatform,
                            modifier = Modifier.fillMaxWidth(),
                            maxContentWidth = null,
                            showKnobRoleHints = false,
                            layoutDensity = MirrorLayoutDensity.DashboardPortrait,
                            chrome = MirrorPanelChrome.Dashboard,
                            deckHighlight = state.deckHighlight,
                            onPadCellTapped = onDeckButtonTapped,
                            onPadCellLongPress = onDeckButtonLongPress,
                            onMirrorKnobTouchRotate = onMirrorKnobTouchRotate,
                            onMirrorKnobLongPress = onMirrorKnobLongPress,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    PlatformSegmentedControl(
                        selected = state.hostPlatform,
                        onSelect = onHostPlatformSelected,
                        compact = true,
                        windowsSlot = state.windowsSlot,
                        macSlot = state.macSlot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
            }
            if (showBanner) {
                NoConnectionBanner(
                    state = state,
                    onGoToConnect = onGoToConnect,
                    onDismiss = { bannerDismissed = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun NoConnectionBanner(
    state: AppState,
    onGoToConnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noHost = state.lanServerHost.isBlank()
    val trustFailed = !state.lanTrustOk
    val (dotColor, titleText, subText) = when {
        noHost -> Triple(
            Color(0xFF5A5A68),
            stringResource(R.string.banner_no_pc_title_no_host),
            stringResource(R.string.banner_no_pc_body_no_host),
        )
        trustFailed -> Triple(
            Color(0xFFFF6B5A),
            stringResource(R.string.banner_no_pc_title_trust),
            stringResource(R.string.banner_no_pc_body_trust),
        )
        state.lanHealthRetrying -> Triple(
            Color(0xFFFFB020),
            stringResource(R.string.banner_no_pc_title_retrying),
            state.lanServerHost,
        )
        else -> Triple(
            Color(0xFFFF6B5A),
            stringResource(R.string.banner_no_pc_title_offline),
            state.lanServerHost,
        )
    }
    val bannerShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(bannerShape)
            .background(Color(0xFF13131F).copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), bannerShape)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onGoToConnect,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                stringResource(R.string.banner_no_pc_connect_cta),
                color = Color(0xFF3D62FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.banner_no_pc_dismiss),
                tint = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun mirrorPadSlots(state: AppState) =
    state.macroButtons
        .sortedBy { it.sortIndex }
        .map { btn ->
            MirrorPadSlot(
                title = if (btn.visible) btn.label else "—",
                shortcutHint = if (btn.visible) btn.resolvedShortcut else "",
                iconToken = if (btn.visible) btn.iconToken else null,
                deckButtonId = if (btn.visible && btn.enabled) btn.id else "",
                editTargetButtonId = if (btn.visible) btn.id else null,
            )
        }

@Composable
private fun PlatformSegmentedControl(
    selected: HostPlatform,
    onSelect: (HostPlatform) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    windowsSlot: PlatformSlotState? = null,
    macSlot: PlatformSlotState? = null,
) {
    val effective = when (selected) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> selected
    }
    val trackShape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    val controlHeight = if (compact) 36.dp else 44.dp
    val inset = if (compact) 3.dp else 4.dp
    val gap = if (compact) 3.dp else 4.dp

    Row(
        modifier = modifier
            .height(controlHeight)
            .border(1.dp, outline, trackShape)
            .clip(trackShape)
            .background(track)
            .padding(inset),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        PlatformSegment(
            iconPainter = painterResource(R.drawable.ic_platform_windows),
            label = stringResource(R.string.platform_chip_windows),
            selected = effective == HostPlatform.WINDOWS,
            onClick = { onSelect(HostPlatform.WINDOWS) },
            compact = compact,
            healthDotColor = windowsSlot?.let { slotHealthDotColor(it) },
            modifier = Modifier.weight(1f),
        )
        PlatformSegment(
            iconVector = Icons.Outlined.LaptopMac,
            label = stringResource(R.string.platform_chip_mac),
            selected = effective == HostPlatform.MAC,
            onClick = { onSelect(HostPlatform.MAC) },
            compact = compact,
            healthDotColor = macSlot?.let { slotHealthDotColor(it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlatformSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    healthDotColor: Color? = null,
) {
    val segShape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val stroke = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    check(iconPainter != null || iconVector != null)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(segShape)
            .then(
                if (selected) {
                    Modifier.border(1.dp, stroke, segShape)
                } else {
                    Modifier
                },
            )
            .background(bg)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val iconSize = if (compact) 17.dp else 20.dp
        when {
            iconVector != null -> Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
            else -> Icon(
                painter = checkNotNull(iconPainter),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 5.dp else 6.dp))
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (healthDotColor != null) {
            Spacer(Modifier.width(if (compact) 4.dp else 5.dp))
            Box(
                Modifier
                    .size(if (compact) 6.dp else 7.dp)
                    .clip(CircleShape)
                    .background(healthDotColor),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    DeckBridgeTheme(dynamicColor = false) {
        val res = LocalContext.current.resources
        HomeScreen(
            state = MockAppStateFactory.initial(1_700_000_000_000L, res),
            onDeckButtonTapped = {},
            onHostPlatformSelected = {},
        )
    }
}


@Composable
private fun DashboardAnimatedOverlay(theme: AnimatedBackgroundTheme, modifier: Modifier = Modifier) {
    when (theme) {
        AnimatedBackgroundTheme.GRID_PULSE -> DashboardEnergyPulsesOverlay(modifier)
        AnimatedBackgroundTheme.PARTICLES  -> DashboardParticlesOverlay(modifier)
        AnimatedBackgroundTheme.AURORA     -> DashboardAuroraOverlay(modifier)
    }
}
