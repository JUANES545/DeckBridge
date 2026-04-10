package com.example.deckbridge.domain.hardware

/**
 * Logical control on the physical macro deck (3 knobs + 3×3 pad). UI and future action mapping use this, not raw keyCodes.
 */
sealed class HardwareControlId {
    data class PadKey(val row: Int, val col: Int) : HardwareControlId() {
        init {
            require(row in 0..2 && col in 0..2)
        }
    }

    /** 0 = top, 1 = middle, 2 = bottom (left column). */
    data class Knob(val index: Int) : HardwareControlId() {
        init {
            require(index in 0..2)
        }
    }
}
