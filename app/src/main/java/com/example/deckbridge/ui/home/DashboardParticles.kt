package com.example.deckbridge.ui.home

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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 22

/**
 * Mutable particle stored in a plain class.
 * Positions and velocities are updated in-place every frame to avoid per-frame list allocation.
 */
private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val baseRadius: Float,
    val glowRadius: Float,
    val coreAlpha: Float,
    val glowAlpha: Float,
    val pulsePhase: Float,
    val pulseSpeed: Float,
    val color: Color,
) {
    /** Advance position and bounce off canvas edges. Mutates in place. */
    fun step(w: Float, h: Float) {
        x += vx
        y += vy
        if (x < 0f)  { x = 0f;  vx = -vx }
        if (x > w)   { x = w;   vx = -vx }
        if (y < 0f)  { y = 0f;  vy = -vy }
        if (y > h)   { y = h;   vy = -vy }
    }
}

private fun spawnParticles(w: Float, h: Float, accent: Color, rng: Random): Array<Particle> =
    Array(PARTICLE_COUNT) { i ->
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        val speed = rng.nextFloat() * 0.5f + 0.25f
        val r     = rng.nextFloat() * 5f + 3f
        val hueShift = rng.nextFloat() * 0.15f - 0.075f
        val particleColor = Color(
            red   = (accent.red   + hueShift * -0.4f).coerceIn(0f, 1f),
            green = (accent.green + hueShift *  0.2f).coerceIn(0f, 1f),
            blue  = (accent.blue  + hueShift *  0.5f).coerceIn(0f, 1f),
        )
        Particle(
            x          = rng.nextFloat() * w,
            y          = rng.nextFloat() * h,
            vx         = cos(angle) * speed,
            vy         = sin(angle) * speed,
            baseRadius = r,
            glowRadius = r * 4.5f,
            coreAlpha  = rng.nextFloat() * 0.25f + 0.45f,
            glowAlpha  = rng.nextFloat() * 0.04f + 0.03f,
            pulsePhase = (i.toFloat() / PARTICLE_COUNT) * 2f * Math.PI.toFloat(),
            pulseSpeed = rng.nextFloat() * 0.018f + 0.008f,
            color      = particleColor,
        )
    }

/**
 * Soft luminous orbs that drift across the full canvas and bounce off all four edges.
 *
 * Performance notes:
 * - Particle positions are updated **in place** — no per-frame list allocation.
 * - A single [Long] frame counter triggers Canvas redraws; the particle array itself
 *   is not stored in Compose state.
 * - Animation is synced to Vsync via [withFrameMillis].
 */
@Composable
fun DashboardParticlesOverlay(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = if (constraints.maxWidth  == Constraints.Infinity) 0f else constraints.maxWidth.toFloat()
        val h = if (constraints.maxHeight == Constraints.Infinity) 0f else constraints.maxHeight.toFloat()

        // Plain array — mutated in place, NOT stored in Compose state.
        val particles = remember(w, h) {
            spawnParticles(w, h, accent, Random(seed = 0x4D656C69L))
        }
        // One lightweight state value — ticks to trigger Canvas redraws.
        var frameMs by remember { mutableLongStateOf(0L) }

        LaunchedEffect(w, h) {
            if (w == 0f || h == 0f) return@LaunchedEffect
            while (isActive) {
                withFrameMillis { ms ->
                    for (p in particles) p.step(w, h)
                    frameMs = ms   // single state write per Vsync — drives Canvas invalidation
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            if (w == 0f || h == 0f) return@Canvas
            val t = frameMs.toFloat() / 1000f   // time in seconds for pulse math
            for (p in particles) {
                val pulse  = (sin(p.pulsePhase + t * p.pulseSpeed * 2f * Math.PI.toFloat()).toFloat() + 1f) * 0.5f
                val bright = 0.75f + pulse * 0.25f
                val center = Offset(p.x, p.y)
                drawCircle(color = p.color.copy(alpha = p.glowAlpha * bright),     radius = p.glowRadius,        center = center)
                drawCircle(color = p.color.copy(alpha = p.glowAlpha * 2.5f * bright), radius = p.baseRadius * 2.2f, center = center)
                drawCircle(color = p.color.copy(alpha = p.coreAlpha * bright),     radius = p.baseRadius,         center = center)
            }
        }
    }
}
