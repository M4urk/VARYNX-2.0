/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * EngineGuard — fail-closed wrapper around any Engine.
 *
 * Guarantees:
 *   - Engines that throw are disabled, NOT left in an inconsistent state
 *   - Error counts and timestamps tracked for telemetry
 *   - Policy-based disable: engine can be stopped via policy without killing the node
 *   - Consecutive failures trigger automatic disable with backoff
 *   - Engine can be re-enabled after cooldown
 */
class EngineGuard(
    private val delegate: Engine,
    private val maxConsecutiveFailures: Int = 5,
    private val disableCooldownMs: Long = 60_000L
) : Engine {

    override val engineId: String get() = delegate.engineId
    override val engineName: String get() = delegate.engineName
    override var state: ModuleState
        get() = if (policyDisabled) ModuleState.LOCKED else delegate.state
        set(value) { delegate.state = value }

    // Telemetry
    @Volatile var totalErrors: Long = 0L; private set
    @Volatile var consecutiveFailures: Int = 0; private set
    @Volatile var lastErrorMessage: String? = null; private set
    @Volatile var lastErrorTime: Long = 0L; private set
    @Volatile var lastSuccessTime: Long = 0L; private set
    @Volatile var autoDisabled: Boolean = false; private set
    @Volatile var autoDisabledTime: Long = 0L; private set
    @Volatile var policyDisabled: Boolean = false; private set

    override fun initialize() {
        if (policyDisabled) return
        try {
            delegate.initialize()
            consecutiveFailures = 0
            lastSuccessTime = currentTimeMillis()
        } catch (e: Exception) {
            recordFailure(e, "initialize")
            failClosed("initialize")
        }
    }

    override fun process() {
        if (policyDisabled) return
        if (autoDisabled) {
            // Check cooldown expiry
            if (currentTimeMillis() - autoDisabledTime >= disableCooldownMs) {
                autoDisabled = false
                consecutiveFailures = 0
                GuardianLog.logEngine(engineId, "re-enabled",
                    "Engine re-enabled after cooldown")
                try {
                    delegate.initialize()
                } catch (e: Exception) {
                    recordFailure(e, "re-initialize")
                    failClosed("re-initialize")
                    return
                }
            } else {
                return
            }
        }

        try {
            delegate.process()
            consecutiveFailures = 0
            lastSuccessTime = currentTimeMillis()
        } catch (e: Exception) {
            recordFailure(e, "process")
            if (consecutiveFailures >= maxConsecutiveFailures) {
                failClosed("process")
            }
        }
    }

    override fun shutdown() {
        try {
            delegate.shutdown()
        } catch (e: Exception) {
            recordFailure(e, "shutdown")
            // Force state to IDLE even on failure
            delegate.state = ModuleState.IDLE
        }
    }

    /**
     * Policy-based disable. Engine stops processing but node stays alive.
     */
    fun disableByPolicy() {
        policyDisabled = true
        try { delegate.shutdown() } catch (_: Exception) {}
        delegate.state = ModuleState.LOCKED
        GuardianLog.logEngine(engineId, "policy_disabled",
            "Engine disabled by policy")
    }

    /**
     * Re-enable after policy disable.
     */
    fun enableByPolicy() {
        policyDisabled = false
        GuardianLog.logEngine(engineId, "policy_enabled",
            "Engine re-enabled by policy")
    }

    /**
     * Produce a telemetry snapshot for this engine.
     */
    fun metrics(): EngineMetrics = EngineMetrics(
        engineId = engineId,
        engineName = engineName,
        status = when {
            policyDisabled -> EngineStatus.POLICY_DISABLED
            autoDisabled -> EngineStatus.AUTO_DISABLED
            delegate.state == ModuleState.ACTIVE -> EngineStatus.ACTIVE
            delegate.state == ModuleState.IDLE -> EngineStatus.IDLE
            else -> EngineStatus.ERROR
        },
        totalErrors = totalErrors,
        consecutiveFailures = consecutiveFailures,
        lastError = lastErrorMessage,
        lastErrorTime = lastErrorTime,
        lastSuccessTime = lastSuccessTime
    )

    private fun recordFailure(e: Exception, phase: String) {
        totalErrors++
        consecutiveFailures++
        lastErrorMessage = "[$phase] ${e.message}"
        lastErrorTime = currentTimeMillis()
        GuardianLog.logThreat(engineId, "engine_error",
            "Engine failure in $phase: ${e.message}", ThreatLevel.LOW)
    }

    private fun failClosed(phase: String) {
        autoDisabled = true
        autoDisabledTime = currentTimeMillis()
        try { delegate.shutdown() } catch (_: Exception) {}
        delegate.state = ModuleState.IDLE
        GuardianLog.logThreat(engineId, "engine_disabled",
            "Engine auto-disabled after $consecutiveFailures failures in $phase — " +
                "fail-closed for ${disableCooldownMs / 1000}s",
            ThreatLevel.MEDIUM)
    }
}

data class EngineMetrics(
    val engineId: String,
    val engineName: String,
    val status: EngineStatus,
    val totalErrors: Long,
    val consecutiveFailures: Int,
    val lastError: String?,
    val lastErrorTime: Long,
    val lastSuccessTime: Long
)

enum class EngineStatus {
    ACTIVE,
    IDLE,
    ERROR,
    AUTO_DISABLED,
    POLICY_DISABLED
}
