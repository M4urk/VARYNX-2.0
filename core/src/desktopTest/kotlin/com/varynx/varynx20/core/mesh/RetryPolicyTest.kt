/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import kotlin.test.*

class RetryPolicyTest {

    // ── Default Behavior ──

    @Test
    fun freshPolicyCanRetry() {
        val policy = RetryPolicy()
        assertTrue(policy.canRetryNow())
        assertFalse(policy.isExhausted)
        assertEquals(0, policy.currentAttempt)
    }

    @Test
    fun recordFailureReturnsPositiveDelay() {
        val policy = RetryPolicy(maxRetries = 5, baseDelayMs = 1000, jitterFraction = 0.0)
        val delay = policy.recordFailure("test", "error")
        assertTrue(delay > 0, "First failure should return positive delay")
        assertEquals(1, policy.currentAttempt)
    }

    @Test
    fun exponentialBackoffGrows() {
        val policy = RetryPolicy(
            maxRetries = 5, baseDelayMs = 100, maxDelayMs = 100_000,
            backoffMultiplier = 2.0, jitterFraction = 0.0
        )

        val d1 = policy.recordFailure("t", "e") // attempt 1: 100 * 2^0 = 100
        val d2 = policy.recordFailure("t", "e") // attempt 2: 100 * 2^1 = 200
        val d3 = policy.recordFailure("t", "e") // attempt 3: 100 * 2^2 = 400

        assertEquals(100, d1)
        assertEquals(200, d2)
        assertEquals(400, d3)
    }

    @Test
    fun delayCappedAtMax() {
        val policy = RetryPolicy(
            maxRetries = 10, baseDelayMs = 10_000, maxDelayMs = 15_000,
            backoffMultiplier = 3.0, jitterFraction = 0.0
        )

        policy.recordFailure("t", "e") // 10000 * 3^0 = 10000
        val d2 = policy.recordFailure("t", "e") // 10000 * 3^1 = 30000 → capped at 15000

        assertEquals(15_000, d2)
    }

    // ── Exhaustion ──

    @Test
    fun exhaustedAfterMaxRetries() {
        val policy = RetryPolicy(maxRetries = 3)
        policy.recordFailure("t", "e")
        policy.recordFailure("t", "e")
        val d = policy.recordFailure("t", "e") // 3rd = exhausted

        assertEquals(-1, d)
        assertTrue(policy.isExhausted)
        assertFalse(policy.canRetryNow())
    }

    // ── Success ──

    @Test
    fun recordSuccessResetsAttempts() {
        val policy = RetryPolicy(maxRetries = 3)
        policy.recordFailure("t", "e")
        policy.recordFailure("t", "e")
        policy.recordSuccess()

        assertEquals(0, policy.currentAttempt)
        assertFalse(policy.isExhausted)
        assertTrue(policy.canRetryNow())
    }

    // ── Reset ──

    @Test
    fun resetClearsEverything() {
        val policy = RetryPolicy(maxRetries = 2)
        policy.recordFailure("t", "e")
        policy.recordFailure("t", "e") // exhausted
        assertTrue(policy.isExhausted)

        policy.reset()
        assertFalse(policy.isExhausted)
        assertEquals(0, policy.currentAttempt)
        assertTrue(policy.canRetryNow())
    }

    // ── Factory Methods ──

    @Test
    fun forSyncIsAggressive() {
        val policy = RetryPolicy.forSync()
        assertEquals(3, policy.maxRetries)
        assertEquals(500L, policy.baseDelayMs)
    }

    @Test
    fun forPairingIsStandard() {
        val policy = RetryPolicy.forPairing()
        assertEquals(3, policy.maxRetries)
        assertEquals(2_000L, policy.baseDelayMs)
    }

    @Test
    fun forTransportIsPatient() {
        val policy = RetryPolicy.forTransport()
        assertEquals(10, policy.maxRetries)
        assertEquals(2_000L, policy.baseDelayMs)
        assertEquals(120_000L, policy.maxDelayMs)
    }

    @Test
    fun forIntelDistributionIsConservative() {
        val policy = RetryPolicy.forIntelDistribution()
        assertEquals(5, policy.maxRetries)
        assertEquals(5_000L, policy.baseDelayMs)
    }

    // ── OperationTimeout ──

    @Test
    fun freshTimeoutIsNotExpired() {
        val timeout = OperationTimeout(30_000L)
        assertFalse(timeout.isExpired)
        assertTrue(timeout.remainingMs > 0)
    }

    @Test
    fun expiredTimeoutReportsExpired() {
        val timeout = OperationTimeout(0L, startTime = 0)
        assertTrue(timeout.isExpired)
        assertEquals(0L, timeout.remainingMs)
    }

    @Test
    fun timeoutFactoriesCreateCorrectValues() {
        assertEquals(30_000L, OperationTimeout.forSync().timeoutMs)
        assertEquals(120_000L, OperationTimeout.forPairing().timeoutMs)
        assertEquals(10_000L, OperationTimeout.forHeartbeat().timeoutMs)
        assertEquals(300_000L, OperationTimeout.forIntelTransfer().timeoutMs)
    }
}
