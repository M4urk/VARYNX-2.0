/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.platform.currentTimeMillis
import kotlin.math.pow

/**
 * RetryPolicy — exponential backoff with jitter for mesh operations.
 *
 * Used by sync, pairing, and transport to avoid thundering herd
 * on reconnect and to degrade gracefully under network failures.
 *
 * Fail-closed: operations that exceed max retries return failure,
 * they do NOT silently continue in a degraded state.
 */
class RetryPolicy(
    val maxRetries: Int = 5,
    val baseDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 60_000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFraction: Double = 0.2
) {
    private var attempt = 0
    private var lastAttemptTime = 0L
    private var nextAllowedTime = 0L

    val isExhausted: Boolean get() = attempt >= maxRetries

    val currentAttempt: Int get() = attempt

    /**
     * Check if a retry is allowed now.
     * Returns true if we're within retry budget and past the backoff delay.
     */
    fun canRetryNow(): Boolean {
        if (attempt >= maxRetries) return false
        return currentTimeMillis() >= nextAllowedTime
    }

    /**
     * Record a failure and compute the next backoff delay.
     * Returns the delay in milliseconds before the next attempt is allowed,
     * or -1 if retries are exhausted.
     */
    fun recordFailure(tag: String, error: String): Long {
        attempt++
        lastAttemptTime = currentTimeMillis()

        if (attempt >= maxRetries) {
            GuardianLog.logSystem(tag,
                "Retry budget exhausted ($maxRetries attempts) — fail-closed. Last error: $error")
            nextAllowedTime = Long.MAX_VALUE
            return -1
        }

        val expDelay = (baseDelayMs * backoffMultiplier.pow((attempt - 1).toDouble())).toLong()
        val capped = minOf(expDelay, maxDelayMs)
        val jitter = (capped * jitterFraction * kotlin.random.Random.nextDouble()).toLong()
        val delay = capped + jitter
        nextAllowedTime = lastAttemptTime + delay

        GuardianLog.logSystem(tag,
            "Retry $attempt/$maxRetries — backing off ${delay}ms. Error: $error")
        return delay
    }

    /**
     * Record success — resets the retry counter.
     */
    fun recordSuccess() {
        attempt = 0
        nextAllowedTime = 0
    }

    /**
     * Hard reset — use when manually re-enabling an operation.
     */
    fun reset() {
        attempt = 0
        lastAttemptTime = 0
        nextAllowedTime = 0
    }

    /**
     * Get time remaining before next retry is allowed (0 if ready now).
     */
    fun timeUntilNextRetryMs(): Long {
        val now = currentTimeMillis()
        return if (nextAllowedTime > now) nextAllowedTime - now else 0L
    }

    companion object {
        /** Aggressive retry for real-time sync (fast base, few retries). */
        fun forSync() = RetryPolicy(
            maxRetries = 3,
            baseDelayMs = 500L,
            maxDelayMs = 10_000L
        )

        /** Standard retry for pairing handshake. */
        fun forPairing() = RetryPolicy(
            maxRetries = 3,
            baseDelayMs = 2_000L,
            maxDelayMs = 15_000L
        )

        /** Patient retry for mesh transport reconnect. */
        fun forTransport() = RetryPolicy(
            maxRetries = 10,
            baseDelayMs = 2_000L,
            maxDelayMs = 120_000L,
            backoffMultiplier = 2.0
        )

        /** Conservative retry for intelligence pack distribution. */
        fun forIntelDistribution() = RetryPolicy(
            maxRetries = 5,
            baseDelayMs = 5_000L,
            maxDelayMs = 60_000L
        )
    }
}

/**
 * Timeout wrapper — enforces a deadline on operations.
 */
data class OperationTimeout(
    val timeoutMs: Long,
    val startTime: Long = currentTimeMillis()
) {
    val isExpired: Boolean
        get() = currentTimeMillis() - startTime >= timeoutMs

    val remainingMs: Long
        get() = maxOf(0, timeoutMs - (currentTimeMillis() - startTime))

    companion object {
        fun forSync() = OperationTimeout(30_000L)
        fun forPairing() = OperationTimeout(120_000L)
        fun forHeartbeat() = OperationTimeout(10_000L)
        fun forIntelTransfer() = OperationTimeout(300_000L)
    }
}
