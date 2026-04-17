package com.example.deckbridge.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.isActive

private data class PulseTrack(
    /** Fraction of the track period (0..1) at which this track's head starts. */
    val phase: Float,
    /** Relative speed multiplier — higher = faster pulse on this track. */
    val speedFactor: Float,
    val start: Offset,
    val end: Offset,
)

/**
 * Teal energy pulses travelling along full-screen grid segments.
 *
 * Fixed vs original:
 * - All tracks span the **full** canvas width or height (no more partial segments).
 * - 10 tracks with independent speed factors so pulses feel alive at different rates.
 * - A single wall-clock tick drives all phases, no per-track coroutines.
 */
@Composable
fun DashboardEnergyPulsesOverlay(modifier: Modifier = Modifier) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    // Sync to Vsync via withFrameMillis — no delay(), no over-drawing, no under-drawing.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameMs -> nowMs = frameMs }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = if (constraints.maxWidth == Constraints.Infinity) 0f
                else constraints.maxWidth.toFloat()
        val h = if (constraints.maxHeight == Constraints.Infinity) 0f
                else constraints.maxHeight.toFloat()

        val step = with(density) { 32.dp.toPx() }
        val tracks = remember(w, h, step, landscape) { buildPulseTracks(w, h, step, landscape) }

        // Visual constants
        val strokeCore   = with(density) { (if (landscape) 2.8f else 3.2f).dp.toPx() }
        val strokeHalo   = with(density) { (if (landscape) 7.0f else 8.5f).dp.toPx() }
        val headR        = with(density) { (if (landscape) 10.0f else 12.0f).dp.toPx() }
        val tailFraction = if (landscape) 0.18f else 0.22f
        val basePeriod   = if (landscape) 14_000L else 12_000L  // ms for speedFactor=1

        Canvas(Modifier.fillMaxSize()) {
            if (tracks.isEmpty() || w == 0f || h == 0f) return@Canvas

            val coreAlpha   = if (landscape) 0.48f else 0.56f
            val haloAlpha   = if (landscape) 0.10f else 0.13f
            val trailAlpha  = if (landscape) 0.38f else 0.44f

            tracks.forEach { tr ->
                val period = (basePeriod / tr.speedFactor).toLong().coerceAtLeast(3_000L)
                val raw    = (nowMs % period) / period.toFloat()
                val v      = ((raw + tr.phase) % 1f + 1f) % 1f

                val hx   = tr.start.x + (tr.end.x - tr.start.x) * v
                val hy   = tr.start.y + (tr.end.y - tr.start.y) * v
                val head = Offset(hx, hy)

                val v0       = (v - tailFraction).coerceAtLeast(0f)
                val tailStart = Offset(
                    tr.start.x + (tr.end.x - tr.start.x) * v0,
                    tr.start.y + (tr.end.y - tr.start.y) * v0,
                )

                drawLine(
                    color       = accent.copy(alpha = haloAlpha),
                    start       = tailStart,
                    end         = head,
                    strokeWidth = strokeHalo,
                    cap         = StrokeCap.Round,
                )
                drawLine(
                    color       = accent.copy(alpha = trailAlpha),
                    start       = tailStart,
                    end         = head,
                    strokeWidth = strokeCore,
                    cap         = StrokeCap.Round,
                )
                drawCircle(
                    color  = accent.copy(alpha = if (landscape) 0.07f else 0.09f),
                    radius = headR * 1.5f,
                    center = head,
                )
                drawCircle(
                    color  = accent.copy(alpha = coreAlpha),
                    radius = headR * 0.55f,
                    center = head,
                )
            }
        }
    }
}

private fun buildPulseTracks(w: Float, h: Float, step: Float, landscape: Boolean): List<PulseTrack> {
    if (w < step * 3 || h < step * 3 || step <= 0f) return emptyList()

    val cols = (w / step).toInt().coerceAtLeast(2)
    val rows = (h / step).toInt().coerceAtLeast(2)

    // Grid snap helpers — now spanning all the way to the canvas edges
    fun snapX(col: Int) = (col.coerceIn(0, cols) * step).coerceIn(0f, w)
    fun snapY(row: Int) = (row.coerceIn(0, rows) * step).coerceIn(0f, h)

    // Evenly spread row/col positions across the canvas
    val rowPositions = if (landscape) {
        listOf(rows / 5, rows * 2 / 5, rows * 3 / 5, rows * 4 / 5)
    } else {
        listOf(rows / 6, rows / 3, rows / 2, rows * 2 / 3, rows * 5 / 6)
    }
    val colPositions = if (landscape) {
        listOf(cols / 4, cols / 2, cols * 3 / 4)
    } else {
        listOf(cols / 3, cols * 2 / 3)
    }

    val tracks = mutableListOf<PulseTrack>()

    // Horizontal tracks — full width, left edge to right edge
    rowPositions.forEachIndexed { i, row ->
        tracks += PulseTrack(
            phase       = i * 0.17f,
            speedFactor = 0.75f + i * 0.10f,        // 0.75, 0.85, 0.95, 1.05, 1.15
            start       = Offset(0f, snapY(row)),
            end         = Offset(w,  snapY(row)),
        )
        // Second pulse on same row, opposite direction (reverse phase by 0.5)
        if (i % 2 == 0) {
            tracks += PulseTrack(
                phase       = i * 0.17f + 0.5f,
                speedFactor = 0.70f + i * 0.12f,
                start       = Offset(w,  snapY(row)),   // end → start for right-to-left feel
                end         = Offset(0f, snapY(row)),
            )
        }
    }

    // Vertical tracks — full height, top to bottom
    colPositions.forEachIndexed { i, col ->
        tracks += PulseTrack(
            phase       = 0.33f + i * 0.21f,
            speedFactor = 0.80f + i * 0.15f,
            start       = Offset(snapX(col), 0f),
            end         = Offset(snapX(col), h),
        )
    }

    return tracks
}
