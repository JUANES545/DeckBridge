package com.example.deckbridge.domain.model

/** Mirrors KeyEvent ACTION_DOWN / ACTION_UP without pulling Android into pure domain types. */
enum class KeyMotion {
    DOWN,
    UP,
}
