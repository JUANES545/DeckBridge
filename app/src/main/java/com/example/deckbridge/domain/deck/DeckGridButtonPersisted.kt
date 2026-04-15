package com.example.deckbridge.domain.deck

/**
 * One persisted cell in the 3×3 macro grid (future: same file may host knob rows).
 *
 * @param intentId Stable id matching [com.example.deckbridge.domain.model.DeckButtonIntent.intentId] where applicable.
 * @param payload String map (e.g. `literal` for inject text); interpreted when loading into [com.example.deckbridge.domain.model.DeckButtonIntent].
 */
data class DeckGridButtonPersisted(
    val id: String,
    val sortIndex: Int,
    val label: String,
    val subtitle: String,
    val kind: DeckGridActionKind,
    val intentId: String,
    val payload: Map<String, String> = emptyMap(),
    val iconToken: String? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
)
