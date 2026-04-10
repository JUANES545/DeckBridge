package com.example.deckbridge.ui.hardware

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.domain.knob.KnobIntentMapper
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareHighlightKind
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight

@Composable
fun HardwareMirrorPanel(
    calibration: HardwareCalibrationConfig?,
    highlight: HardwareMirrorHighlight?,
    diagSummary: HardwareDiagSummary?,
    padSlots: List<MirrorPadSlot>,
    hostPlatform: HostPlatform,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val activeHighlight = highlight?.takeIf { it.untilEpochMs > now }
    val slots = if (padSlots.size >= 9) padSlots.take(9) else {
        padSlots + List(9 - padSlots.size) {
            MirrorPadSlot("", "—", null)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp, pressedElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                    ),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LastInteractionLine(
                calibration = calibration,
                diagSummary = diagSummary,
                padSlots = slots,
                hostPlatform = hostPlatform,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.26f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(3) { index ->
                        KnobVisual(
                            index = index,
                            label = knobUserLabel(index),
                            roleHint = knobRoleHint(index),
                            activeHighlight = activeHighlight,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(0.74f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (r in 0..2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            for (c in 0..2) {
                                val idx = r * 3 + c
                                PadKeyTile(
                                    slot = slots[idx],
                                    padId = HardwareControlId.PadKey(r, c),
                                    activeHighlight = activeHighlight,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LastInteractionLine(
    calibration: HardwareCalibrationConfig?,
    diagSummary: HardwareDiagSummary?,
    padSlots: List<MirrorPadSlot>,
    hostPlatform: HostPlatform,
) {
    val calibOk = calibration != null && calibration.isComplete
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (calibOk) {
                stringResource(R.string.mirror_status_ready)
            } else {
                stringResource(R.string.mirror_status_setup)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (calibOk) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        diagSummary?.let { s ->
            Text(
                text = userFacingInteractionLine(s, padSlots, hostPlatform),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun userFacingInteractionLine(
    summary: HardwareDiagSummary,
    padSlots: List<MirrorPadSlot>,
    hostPlatform: HostPlatform,
): String {
    val controlName = when (val c = summary.control) {
        is HardwareControlId.PadKey -> {
            val i = c.row * 3 + c.col
            padSlots.getOrNull(i)?.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.mirror_pad_fallback)
        }
        is HardwareControlId.Knob -> knobUserLabel(c.index)
        null -> summary.controlLabel
    }
    val action = when (val c = summary.control) {
        is HardwareControlId.Knob -> {
            val intent = when (summary.kind) {
                "KNOB_ROTATE_CCW", "KNOB_CCW" -> KnobIntentMapper.intentForRotate(c.index, ccw = true)
                "KNOB_ROTATE_CW", "KNOB_CW" -> KnobIntentMapper.intentForRotate(c.index, ccw = false)
                "KNOB_PRESS_DOWN" -> KnobIntentMapper.intentForPress(c.index)
                else -> null
            }
            when {
                intent != null -> PlatformActionResolver.resolve(intent, hostPlatform).shortcutDisplay
                summary.kind == "KNOB_PRESS_UP" -> stringResource(R.string.mirror_action_knob_release)
                else -> when (summary.kind) {
                    "KNOB_ROTATE_CW", "KNOB_CW" -> stringResource(R.string.mirror_action_knob_cw)
                    "KNOB_ROTATE_CCW", "KNOB_CCW" -> stringResource(R.string.mirror_action_knob_ccw)
                    else -> summary.kind
                }
            }
        }
        else -> when (summary.kind) {
            "PAD_DOWN" -> stringResource(R.string.mirror_action_pad_press)
            "KNOB_PRESS_DOWN" -> stringResource(R.string.mirror_action_knob_press)
            "KNOB_PRESS_UP" -> stringResource(R.string.mirror_action_knob_release)
            "KNOB_ROTATE_CW", "KNOB_CW" -> stringResource(R.string.mirror_action_knob_cw)
            "KNOB_ROTATE_CCW", "KNOB_CCW" -> stringResource(R.string.mirror_action_knob_ccw)
            else -> summary.kind
        }
    }
    return stringResource(R.string.mirror_last_interaction, controlName, action)
}

@Composable
private fun knobUserLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.knob_top)
    1 -> stringResource(R.string.knob_middle)
    2 -> stringResource(R.string.knob_bottom)
    else -> stringResource(R.string.knob_generic, index + 1)
}

@Composable
private fun knobRoleHint(index: Int): String = when (index) {
    0 -> stringResource(R.string.mirror_knob_role_top)
    1 -> stringResource(R.string.mirror_knob_role_middle)
    2 -> stringResource(R.string.mirror_knob_role_bottom)
    else -> ""
}

@Composable
private fun KnobVisual(
    index: Int,
    label: String,
    roleHint: String,
    activeHighlight: HardwareMirrorHighlight?,
) {
    val knobId = HardwareControlId.Knob(index)
    val h = activeHighlight
    val isOn = h?.control == knobId
    val kind = if (isOn) h?.kind else null
    val isPressDown = kind == HardwareHighlightKind.KNOB_PRESS_DOWN
    val isPressUp = kind == HardwareHighlightKind.KNOB_PRESS_UP
    val isRotate = isOn && !isPressDown && !isPressUp

    val scale by animateFloatAsState(
        targetValue = when {
            isPressDown -> 0.90f
            isPressUp -> 0.98f
            isRotate -> 1.07f
            else -> 1f
        },
        animationSpec = when {
            isRotate -> spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
            isPressUp -> tween(durationMillis = 95, easing = FastOutSlowInEasing)
            else -> spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            )
        },
        label = "knobScale$index",
    )
    val rotationDeg by animateFloatAsState(
        targetValue = if (isRotate) (h?.rotationVisual ?: 0f) * 24f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "knobRot$index",
    )
    val innerDotScale by animateFloatAsState(
        targetValue = when {
            isPressDown -> 1.15f
            isPressUp -> 1.22f
            isRotate -> 1f
            else -> 1f
        },
        animationSpec = tween(durationMillis = if (isPressUp) 110 else 140, easing = FastOutSlowInEasing),
        label = "knobInner$index",
    )

    val ringColor = when {
        !isOn -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        isPressDown -> MaterialTheme.colorScheme.tertiary
        isPressUp -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp),
        ) {
            if (isRotate) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val r = size.minDimension / 2f - stroke
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val start = if ((h?.rotationVisual ?: 0f) >= 0) -140f else -40f
                    val sweep = if ((h?.rotationVisual ?: 0f) >= 0) 80f else -80f
                    drawArc(
                        color = Color.White.copy(alpha = 0.35f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = stroke),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .scale(scale)
                    .rotate(rotationDeg)
                    .shadow(
                        elevation = when {
                            isPressDown -> 14.dp
                            isPressUp -> 9.dp
                            isRotate -> 13.dp
                            isOn -> 11.dp
                            else -> 4.dp
                        },
                        shape = CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(
                            alpha = when {
                                isPressDown -> 0.5f
                                isPressUp -> 0.28f
                                isRotate -> 0.42f
                                isOn -> 0.35f
                                else -> 0.15f
                            },
                        ),
                    )
                    .clip(CircleShape)
                    .background(
                        brush = if (isOn) {
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                ),
                            )
                        },
                    )
                    .border(2.5.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val innerDotColor = when {
                    isPressUp -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                    isPressDown -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f)
                    isOn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .scale(innerDotScale)
                        .clip(CircleShape)
                        .background(color = innerDotColor),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (roleHint.isNotBlank()) {
            Text(
                text = roleHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 92.dp),
            )
        }
    }
}

@Composable
private fun PadKeyTile(
    slot: MirrorPadSlot,
    padId: HardwareControlId.PadKey,
    activeHighlight: HardwareMirrorHighlight?,
    modifier: Modifier = Modifier,
) {
    val isOn = activeHighlight?.control == padId
    val scale by animateFloatAsState(
        targetValue = if (isOn) 0.94f else 1f,
        animationSpec = tween(
            durationMillis = if (isOn) 95 else 380,
            easing = FastOutSlowInEasing,
        ),
        label = "padScale${padId.row}${padId.col}",
    )
    val shape = RoundedCornerShape(16.dp)
    val baseBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeBg = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        0.35f,
    )
    val borderActive = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    val borderIdle = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    BoxWithConstraints(modifier = modifier.scale(scale)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (isOn) 14.dp else 3.dp,
                    shape = shape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isOn) 0.5f else 0.08f),
                )
                .clip(shape)
                .background(if (isOn) activeBg else baseBg)
                .border(
                    width = if (isOn) 2.dp else 1.dp,
                    color = if (isOn) borderActive else borderIdle,
                    shape = shape,
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Media / thumbnail region — replace with AsyncImage / SubcomposeAsyncImage later
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isOn) 0.22f else 0.08f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = if (isOn) 0.12f else 0.05f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mirrorSlotGlyph(slot.iconToken),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Text(
                    text = slot.title.ifBlank { "—" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = slot.shortcutHint.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun mirrorSlotGlyph(iconToken: String?): String = when (iconToken) {
    "content_copy" -> "⎘"
    "content_paste" -> "📋"
    "volume_up" -> "🔊"
    "play_pause" -> "⏯"
    "search" -> "🔍"
    "content_cut" -> "✂"
    "undo" -> "↩"
    "redo" -> "↪"
    "volume_mute" -> "🔇"
    else -> "◇"
}
