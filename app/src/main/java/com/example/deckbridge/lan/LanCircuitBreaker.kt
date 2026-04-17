package com.example.deckbridge.lan

/**
 * Simple fail-fast circuit breaker for LAN actions.
 *
 * After [failThreshold] consecutive failures the circuit opens: [isOpen] returns true
 * and callers should skip the real network call.  The circuit half-closes automatically
 * after [openWindowMs] so a single probe attempt is allowed through.  A successful probe
 * resets the breaker fully via [recordSuccess].
 *
 * Not thread-safe — all mutations happen on the same coroutine dispatcher in the repository.
 */
class LanCircuitBreaker(
    private val failThreshold: Int = 3,
    private val openWindowMs: Long = 30_000L,
) {
    private var consecutiveFailures = 0
    private var openedAtMs = 0L

    /**
     * True when the breaker is open (consecutive failures ≥ threshold) AND the open window
     * has not yet expired (i.e. a retry probe should not be attempted yet).
     */
    val isOpen: Boolean
        get() = consecutiveFailures >= failThreshold &&
            System.currentTimeMillis() - openedAtMs < openWindowMs

    fun recordSuccess() {
        consecutiveFailures = 0
        openedAtMs = 0L
    }

    fun recordFailure() {
        consecutiveFailures++
        if (consecutiveFailures == failThreshold) {
            openedAtMs = System.currentTimeMillis()
        }
    }

    fun reset() {
        consecutiveFailures = 0
        openedAtMs = 0L
    }
}
