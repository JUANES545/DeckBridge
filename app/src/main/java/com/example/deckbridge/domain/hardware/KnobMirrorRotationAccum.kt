package com.example.deckbridge.domain.hardware

private const val KNOB_MIRROR_TRIM_AFTER: Float = 7200f
private const val KNOB_MIRROR_TRIM_STEP: Float = 3600f

/**
 * Per-knob accumulated rotation (degrees) for the hardware mirror UI.
 * Values may grow in either direction; [withDelta] trims occasionally to avoid huge floats.
 */
data class KnobMirrorRotationAccum(
    val deg0: Float = 0f,
    val deg1: Float = 0f,
    val deg2: Float = 0f,
) {
    fun degreesFor(index: Int): Float = when (index) {
        0 -> deg0
        1 -> deg1
        2 -> deg2
        else -> 0f
    }

    fun withDelta(index: Int, deltaDeg: Float): KnobMirrorRotationAccum {
        if (index !in 0..2 || deltaDeg == 0f) return this
        return when (index) {
            0 -> copy(deg0 = trim(deg0 + deltaDeg))
            1 -> copy(deg1 = trim(deg1 + deltaDeg))
            2 -> copy(deg2 = trim(deg2 + deltaDeg))
            else -> this
        }
    }

    private fun trim(v: Float): Float = when {
        v > KNOB_MIRROR_TRIM_AFTER -> v - KNOB_MIRROR_TRIM_STEP
        v < -KNOB_MIRROR_TRIM_AFTER -> v + KNOB_MIRROR_TRIM_STEP
        else -> v
    }
}
