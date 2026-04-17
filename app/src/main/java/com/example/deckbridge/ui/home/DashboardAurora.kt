package com.example.deckbridge.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import android.graphics.LinearGradient
import android.graphics.Matrix as NativeMatrix
import android.graphics.Paint as NativePaint
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Each band is a horizontal strip of colour whose vertical centre position ripples
 * using a combination of sine waves — giving the illusion of aurora-like movement
 * without any texture assets or shaders.
 */
private data class AuroraBand(
    /** Normalised vertical centre at rest (0=top, 1=bottom). */
    val centerY: Float,
    /** Half-height of the band as a fraction of canvas height. */
    val halfHeight: Float,
    val color: Color,
    /** Primary wave: amplitude as fraction of height, period in seconds. */
    val amp1: Float, val period1: Float, val phase1: Float,
    /** Secondary wave (adds texture). */
    val amp2: Float, val period2: Float, val phase2: Float,
    /** Overall alpha at peak brightness. */
    val peakAlpha: Float,
    /** Alpha pulse amplitude (band breathes in/out). */
    val breathAmp: Float,
    val breathPeriod: Float,
)

/**
 * Pre-allocated shader + paint per band — avoids creating a new [LinearGradient] and
 * [Brush] every draw call (was 6 × 7 object allocations / frame at 30 fps).
 * Each frame only updates the matrix (translate/scale) and paint alpha.
 */
private class BandRenderer(band: AuroraBand) {
    val shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(
            android.graphics.Color.TRANSPARENT,
            band.color.copy(alpha = 0.6f).toArgb(),
            band.color.toArgb(),
            band.color.copy(alpha = 0.6f).toArgb(),
            android.graphics.Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
        Shader.TileMode.CLAMP,
    )
    val matrix = NativeMatrix()
    val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        this.shader = this@BandRenderer.shader
    }
}

@Composable
fun DashboardAuroraOverlay(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary  // teal

    // Companion colours — deep indigo and electric violet — blended with the accent.
    val indigo  = Color(0xFF3D2DB8)
    val violet  = Color(0xFF7B3FE4)
    val cyan    = Color(
        red   = (accent.red   * 0.6f + 0.0f).coerceIn(0f, 1f),
        green = (accent.green * 0.9f + 0.1f).coerceIn(0f, 1f),
        blue  = (accent.blue  * 0.7f + 0.3f).coerceIn(0f, 1f),
    )

    val bands = remember(accent) {
        listOf(
            AuroraBand(
                centerY = 0.22f, halfHeight = 0.18f,
                color = indigo,
                amp1 = 0.06f, period1 = 11f, phase1 = 0.0f,
                amp2 = 0.03f, period2 = 6.3f, phase2 = 1.1f,
                peakAlpha = 0.28f, breathAmp = 0.10f, breathPeriod = 7.5f,
            ),
            AuroraBand(
                centerY = 0.40f, halfHeight = 0.20f,
                color = cyan,
                amp1 = 0.07f, period1 = 9f,  phase1 = 2.4f,
                amp2 = 0.035f, period2 = 5.1f, phase2 = 0.7f,
                peakAlpha = 0.22f, breathAmp = 0.12f, breathPeriod = 9.0f,
            ),
            AuroraBand(
                centerY = 0.58f, halfHeight = 0.22f,
                color = violet,
                amp1 = 0.08f, period1 = 13f, phase1 = 1.6f,
                amp2 = 0.04f, period2 = 7.7f, phase2 = 3.0f,
                peakAlpha = 0.24f, breathAmp = 0.09f, breathPeriod = 11.0f,
            ),
            AuroraBand(
                centerY = 0.76f, halfHeight = 0.19f,
                color = accent,
                amp1 = 0.06f, period1 = 10f, phase1 = 4.2f,
                amp2 = 0.03f, period2 = 6.0f, phase2 = 2.3f,
                peakAlpha = 0.20f, breathAmp = 0.11f, breathPeriod = 8.0f,
            ),
            // Extra thin band across the very top for edge coverage
            AuroraBand(
                centerY = 0.06f, halfHeight = 0.10f,
                color = cyan,
                amp1 = 0.03f, period1 = 8f,  phase1 = 3.5f,
                amp2 = 0.015f, period2 = 4.5f, phase2 = 1.8f,
                peakAlpha = 0.15f, breathAmp = 0.08f, breathPeriod = 6.5f,
            ),
            // Extra thin band at the very bottom
            AuroraBand(
                centerY = 0.94f, halfHeight = 0.10f,
                color = indigo,
                amp1 = 0.04f, period1 = 7f,  phase1 = 5.1f,
                amp2 = 0.02f, period2 = 4.0f, phase2 = 0.4f,
                peakAlpha = 0.14f, breathAmp = 0.07f, breathPeriod = 7.0f,
            ),
        )
    }

    // Pre-allocated per-band shaders — reused every frame, only matrix + alpha updated.
    val bandRenderers = remember(bands) { bands.map { BandRenderer(it) } }

    // Aurora moves slowly — skip every other Vsync to keep GPU load low (~30 fps effective).
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        var frameCount = 0L
        while (isActive) {
            withFrameMillis { frameMs ->
                if (frameCount % 2L == 0L) nowMs = frameMs   // ~30 fps from 60 fps Vsync
                frameCount++
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas

        val tSec = nowMs / 1_000.0

        // No per-frame allocations: matrix + alpha updated in-place on pre-allocated objects.
        val native = drawContext.canvas.nativeCanvas
        bands.forEachIndexed { i, band ->
            // Vertical centre of this band at current time
            val wave1      = sin(2.0 * PI * (tSec / band.period1) + band.phase1).toFloat()
            val wave2      = sin(2.0 * PI * (tSec / band.period2) + band.phase2).toFloat()
            val centerFrac = band.centerY + band.amp1 * wave1 + band.amp2 * wave2
            val cy         = centerFrac * h
            val halfH      = band.halfHeight * h

            val breath = sin(2.0 * PI * (tSec / band.breathPeriod)).toFloat()
            val alpha  = (band.peakAlpha + band.breathAmp * breath * 0.5f).coerceIn(0f, 1f)

            val top    = (cy - halfH).coerceAtLeast(0f)
            val bottom = (cy + halfH).coerceAtMost(h)
            if (bottom <= top) return@forEachIndexed

            val br = bandRenderers[i]
            // Reposition the unit-space shader to cover [top, bottom] on screen
            br.matrix.setScale(w, bottom - top)
            br.matrix.postTranslate(0f, top)
            br.shader.setLocalMatrix(br.matrix)
            // Breathing: scale Paint alpha — shader bakes relative shape (0→0.6→1→0.6→0)
            br.paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            native.drawRect(0f, top, w, bottom, br.paint)
        }
    }
}
