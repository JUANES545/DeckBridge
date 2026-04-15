package com.example.deckbridge.domain.pairing

import kotlin.random.Random

/**
 * Human-friendly codes for the pairing modal. PC agent will validate in a later phase.
 */
object PairingCodeFactory {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun newSixChar(): String = buildString(6) {
        repeat(6) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    }
}
