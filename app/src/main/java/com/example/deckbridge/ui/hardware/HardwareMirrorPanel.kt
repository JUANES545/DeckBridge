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
import androidx.compose.ui.unit.Dp
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
    maxContentWidth: Dp? = 520.dp,
    showKnobRoleHints: Boolean = true,
    layoutDensity: MirrorLayoutDensity = MirrorLayoutDensity.Comfortable,
) {
    val tokens = layoutDensity.toTokens()
    val now = System.currentTimeMillis()
    val activeHighlight = highlight?.takeIf { it.untilEpochMs > now }
    val slots = if (padSlots.size >= 9) padSlots.take(9) else {
        padSlots + List(9 - padSlots.size) {
            MirrorPadSlot("", "—", null)
        }
    }
    val cardShape = RoundedCornerShape(tokens.cardCorner)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (maxContentWidth != null) {
                    Modifier.widthIn(max = maxContentWidth)
                } else {
                    Modifier
                },
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(
            defaultElevation = tokens.cardElevation,
            pressedElevation = tokens.cardElevation,
        ),
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
                    shape = cardShape,
                )
                .padding(
                    horizontal = tokens.paddingHorizontal,
                    vertical = tokens.paddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(tokens.sectionGap),
        ) {
            LastInteractionLine(
                calibration = calibration,
                diagSummary = diagSummary,
                padSlots = slots,
                hostPlatform = hostPlatform,
                compact = !showKnobRoleHints,
                denseTypography = tokens.statusIsDenseTypography,
                headerExtraDense = tokens.headerExtraDense,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.knobPadRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val padWeight = 1f - tokens.knobColumnWeight
                Column(
                    modifier = Modifier
                        .weight(tokens.knobColumnWeight)
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
                            showRoleHint = showKnobRoleHints,
                            tokens = tokens,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(padWeight),
                    verticalArrangement = Arrangement.spacedBy(tokens.padCellGap),
                ) {
                    for (r in 0..2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.padCellGap),
                        ) {
                            for (c in 0..2) {
                                val idx = r * 3 + c
                                PadKeyTile(
                                    slot = slots[idx],
                                    padId = HardwareControlId.PadKey(r, c),
                                    activeHighlight = activeHighlight,
                                    tokens = tokens,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(tokens.padCellAspectRatio),
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
    compact: Boolean = false,
    denseTypography: Boolean = false,
    headerExtraDense: Boolean = false,
) {
    val calibOk = calibration != null && calibration.isComplete
    val statusStyle = when {
        headerExtraDense -> MaterialTheme.typography.labelSmall
        denseTypography -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.labelLarge
    }
    val detailStyle = when {
        headerExtraDense -> MaterialTheme.typography.labelSmall
        denseTypography -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.bodySmall
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            when {
                headerExtraDense -> 6.dp
                denseTypography -> 8.dp
                else -> 12.dp
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (calibOk) {
                stringResource(R.string.mirror_status_ready)
            } else {
                stringResource(R.string.mirror_status_setup)
            },
            style = statusStyle,
            color = if (calibOk) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        diagSummary?.let { s ->
            Text(
                text = userFacingInteractionLine(s, padSlots, hostPlatform),
                style = detailStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 1 else 2,
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
    showRoleHint: Boolean = true,
    tokens: MirrorLayoutTokens,
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
    val denseKnobs = tokens.statusIsDenseTypography
    val primary = tokens.useDistinctPrimaryKnob && index == 0
    val outer = if (primary) tokens.knobPrimaryOuter else tokens.knobOuterBox
    val inner = if (primary) tokens.knobPrimaryInner else tokens.knobInner
    val dot = if (primary) tokens.knobPrimaryDot else tokens.knobDot
    val arcStroke = if (primary) tokens.knobPrimaryArcStroke else tokens.knobArcStroke
    val ringW = if (primary) tokens.knobPrimaryBorder else tokens.knobBorder
    val shDown = when {
        primary -> if (denseKnobs) 10.dp else 14.dp
        denseKnobs -> 6.dp
        else -> 14.dp
    }
    val shUp = when {
        primary -> if (denseKnobs) 6.dp else 9.dp
        denseKnobs -> 4.dp
        else -> 9.dp
    }
    val shRot = when {
        primary -> if (denseKnobs) 9.dp else 13.dp
        denseKnobs -> 5.dp
        else -> 13.dp
    }
    val shOn = when {
        primary -> if (denseKnobs) 8.dp else 11.dp
        denseKnobs -> 5.dp
        else -> 11.dp
    }
    val shIdle = when {
        primary -> if (denseKnobs) 3.dp else 4.dp
        denseKnobs -> 2.dp
        else -> 4.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.knobStackSpacing),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(outer),
        ) {
            if (isRotate) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = arcStroke.toPx()
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
                    .size(inner)
                    .scale(scale)
                    .rotate(rotationDeg)
                    .shadow(
                        elevation = when {
                            isPressDown -> shDown
                            isPressUp -> shUp
                            isRotate -> shRot
                            isOn -> shOn
                            else -> shIdle
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
                    .border(ringW, ringColor, CircleShape),
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
                        .size(dot)
                        .scale(innerDotScale)
                        .clip(CircleShape)
                        .background(color = innerDotColor),
                )
            }
        }
        Text(
            text = label,
            style = if (primary && tokens.useDistinctPrimaryKnob) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelSmall
            },
            fontWeight = if (primary && tokens.useDistinctPrimaryKnob) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showRoleHint && roleHint.isNotBlank()) {
            Text(
                text = roleHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(
                    max = when {
                        primary -> 88.dp
                        denseKnobs -> 72.dp
                        else -> 92.dp
                    },
                ),
            )
        }
    }
}

@Composable
private fun PadKeyTile(
    slot: MirrorPadSlot,
    padId: HardwareControlId.PadKey,
    activeHighlight: HardwareMirrorHighlight?,
    tokens: MirrorLayoutTokens,
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
    val shape = RoundedCornerShape(tokens.padCorner)
    val densePads = tokens.statusIsDenseTypography
    val shadowActive = if (densePads) 8.dp else 14.dp
    val shadowIdle = if (densePads) 2.dp else 3.dp
    val glyphStyle = when {
        tokens.padGlyphProminent && densePads -> MaterialTheme.typography.titleLarge
        tokens.padTitleMinimal && densePads -> MaterialTheme.typography.titleSmall
        densePads -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.headlineSmall
    }
    val titleTypo = if (tokens.padTitleMinimal) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
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
                    elevation = if (isOn) shadowActive else shadowIdle,
                    shape = shape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isOn) 0.5f else 0.08f),
                )
                .clip(shape)
                .background(if (isOn) activeBg else baseBg)
                .border(
                    width = if (isOn) tokens.padBorderActive else tokens.padBorderIdle,
                    color = if (isOn) borderActive else borderIdle,
                    shape = shape,
                )
                .padding(
                    horizontal = tokens.padPaddingH,
                    vertical = tokens.padPaddingV,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(tokens.padColumnGap),
            ) {
                // Media / thumbnail region — replace with AsyncImage / SubcomposeAsyncImage later
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .clip(RoundedCornerShape(tokens.padMediaCorner))
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
                        style = glyphStyle,
                    )
                }
                Text(
                    text = slot.title.ifBlank { "—" },
                    style = titleTypo,
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
