package com.example.deckbridge.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class PulseTrack(
    val start: Offset,
    val end: Offset,
    val phase: Float,
)

/** Visual tuning: portrait allows stronger pulses; landscape stays readable on a short viewport. */
private data class PulseVisualProfile(
    val durationMillis: Int,
    val coreAlpha: Float,
    val haloAlpha: Float,
    val trailCoreAlpha: Float,
    val strokeCoreDp: Float,
    val strokeHaloDp: Float,
    val headRadiusDp: Float,
    val headOuterAlpha: Float,
    val headInnerAlpha: Float,
    val tailFraction: Float,
)

private fun pulseProfile(landscape: Boolean): PulseVisualProfile =
    if (landscape) {
        PulseVisualProfile(
            durationMillis = 17_500,
            coreAlpha = 0.44f,
            haloAlpha = 0.10f,
            trailCoreAlpha = 0.38f,
            strokeCoreDp = 3.0f,
            strokeHaloDp = 7.8f,
            headRadiusDp = 10.5f,
            headOuterAlpha = 0.068f,
            headInnerAlpha = 0.15f,
            tailFraction = 0.175f,
        )
    } else {
        PulseVisualProfile(
            durationMillis = 15_500,
            coreAlpha = 0.52f,
            haloAlpha = 0.12f,
            trailCoreAlpha = 0.44f,
            strokeCoreDp = 3.4f,
            strokeHaloDp = 9f,
            headRadiusDp = 12.5f,
            headOuterAlpha = 0.085f,
            headInnerAlpha = 0.19f,
            tailFraction = 0.21f,
        )
    }

/**
 * Teal energy pulses along orthogonal grid segments, aligned with the mirror panel bounds.
 *
 * Phase is driven by a lightweight [LaunchedEffect] + [delay] loop so the draw scope always
 * receives fresh values (some device/Compose combinations did not reliably invalidate [Canvas]
 * tied only to [rememberInfiniteTransition] here).
 */
@Composable
fun DashboardEnergyPulsesOverlay(modifier: Modifier = Modifier) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val profile = remember(landscape) { pulseProfile(landscape) }
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    var phase by remember { mutableFloatStateOf(0f) }
    val period = profile.durationMillis.coerceIn(4_000, 120_000)
    LaunchedEffect(period, landscape) {
        while (isActive) {
            val ms = System.currentTimeMillis()
            phase = (ms % period) / period.toFloat()
            delay(32L)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wRaw = constraints.maxWidth.toFloat().let { if (it.isFinite() && it > 0f) it else 0f }
        val hRaw = constraints.maxHeight.toFloat().let { if (it.isFinite() && it > 0f) it else 0f }
        val w = if (constraints.maxWidth == Constraints.Infinity) 0f else wRaw
        val h = if (constraints.maxHeight == Constraints.Infinity) 0f else hRaw
        val step = with(density) { 32.dp.toPx() }
        val tracks = remember(w, h, step, landscape) { buildTracks(w, h, step, landscape) }
        val strokeCore = with(density) { profile.strokeCoreDp.dp.toPx() }
        val strokeHalo = with(density) { profile.strokeHaloDp.dp.toPx() }
        val headR = with(density) { profile.headRadiusDp.dp.toPx() }
        val tail = profile.tailFraction
        val u = phase

        Canvas(Modifier.fillMaxSize()) {
            if (tracks.isEmpty()) return@Canvas
            val coreColor = accent.copy(alpha = profile.coreAlpha)
            val haloColor = accent.copy(alpha = profile.haloAlpha)
            val trailCore = coreColor.copy(alpha = profile.trailCoreAlpha)
            tracks.forEach { tr ->
                val v = ((u + tr.phase) % 1f + 1f) % 1f
                val hx = tr.start.x + (tr.end.x - tr.start.x) * v
                val hy = tr.start.y + (tr.end.y - tr.start.y) * v
                val head = Offset(hx, hy)
                val v0 = (v - tail).coerceAtLeast(0f)
                val tailX = tr.start.x + (tr.end.x - tr.start.x) * v0
                val tailY = tr.start.y + (tr.end.y - tr.start.y) * v0
                val tailStart = Offset(tailX, tailY)
                drawLine(
                    color = haloColor,
                    start = tailStart,
                    end = head,
                    strokeWidth = strokeHalo,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = trailCore,
                    start = tailStart,
                    end = head,
                    strokeWidth = strokeCore,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = accent.copy(alpha = profile.headOuterAlpha),
                    radius = headR * 1.45f,
                    center = head,
                )
                drawCircle(
                    color = accent.copy(alpha = profile.headInnerAlpha),
                    radius = headR * 0.58f,
                    center = head,
                )
            }
        }
    }
}

private fun buildTracks(w: Float, h: Float, step: Float, landscape: Boolean): List<PulseTrack> {
    if (w < step * 4f || h < step * 4f || step <= 0f) return emptyList()
    val cols = kotlin.math.max(2, (w / step).toInt())
    val rows = kotlin.math.max(2, (h / step).toInt())
    fun ox(c: Int) = c.coerceIn(0, cols - 1).toFloat() * step
    fun oy(r: Int) = r.coerceIn(0, rows - 1).toFloat() * step
    val spanH = kotlin.math.max(3, cols * 2 / 5)
    val spanV = kotlin.math.max(3, rows * 2 / 5)
    val spanHLong = kotlin.math.max(spanH, cols * 3 / 5)
    val spanVLong = kotlin.math.max(spanV, rows * 3 / 5)
    val rq = rows / 4
    val rq2 = rows / 2
    val rq34 = rows * 3 / 4
    val cq2 = cols / 2
    val cq14 = cols / 4

    val horizontalSpanEnd = if (landscape) {
        kotlin.math.min(1 + spanH, cols - 1)
    } else {
        kotlin.math.min(1 + spanHLong, cols - 1)
    }
    val verticalSpanEnd = if (landscape) {
        kotlin.math.min(2 + spanV, rows - 1)
    } else {
        kotlin.math.min(2 + spanVLong, rows - 1)
    }

    val base = mutableListOf(
        PulseTrack(
            start = Offset(ox(1), oy(rq)),
            end = Offset(ox(horizontalSpanEnd), oy(rq)),
            phase = 0f,
        ),
        PulseTrack(
            start = Offset(ox(1), oy(rq2)),
            end = Offset(ox(horizontalSpanEnd), oy(rq2)),
            phase = 0.19f,
        ),
        PulseTrack(
            start = Offset(ox(cq2), oy(2)),
            end = Offset(ox(cq2), oy(verticalSpanEnd)),
            phase = 0.46f,
        ),
        PulseTrack(
            start = Offset(ox(kotlin.math.max(0, cols - spanH - 1)), oy(rq34)),
            end = Offset(ox(cols - 1), oy(rq34)),
            phase = 0.72f,
        ),
    )
    if (!landscape && cols >= 6 && rows >= 10) {
        base.add(
            PulseTrack(
                start = Offset(ox(cq14.coerceAtLeast(1)), oy(rows / 5)),
                end = Offset(ox(cq14.coerceAtLeast(1)), oy(kotlin.math.min(rows / 5 + spanVLong, rows - 1))),
                phase = 0.08f,
            ),
        )
    }
    return base
}
