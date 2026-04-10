package com.example.deckbridge.input

import com.example.deckbridge.domain.model.RecentInputEvent

/**
 * Future hook: subscribe to normalized hardware events.
 * Stage 2 routes real keys through [android.app.Activity.dispatchKeyEvent] into [com.example.deckbridge.data.repository.DeckBridgeRepository].
 */
fun interface InputEventSink {
    fun onEvent(event: RecentInputEvent)
}

/**
 * Placeholder for Bluetooth/USB keyboard observers. Implementations will feed [InputEventSink].
 */
interface PhysicalKeyboardPipeline {
    fun start(sink: InputEventSink)
    fun stop()
}
