/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection
import com.varynx.varynx20.core.platform.currentTimeMillis

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock
import kotlin.uuid.Uuid

class RuntimeThreatMonitor : ProtectionModule {
    override val moduleId = "protect_runtime_threat"
    override val moduleName = "Runtime Threat Monitor"
    override var state = ModuleState.IDLE

    private val lock = Any()
    @Volatile private var lastEvent: ThreatEvent? = null
    private val anomalyBuffer = mutableListOf<Anomaly>()

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel {
        evaluateAnomalies()
        return lastEvent?.threatLevel ?: ThreatLevel.NONE
    }

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun reportAnomaly(anomaly: Anomaly) {
        val shouldEvaluate = withLock(lock) {
            anomalyBuffer.add(anomaly)
            anomalyBuffer.size >= 5
        }
        if (shouldEvaluate) evaluateAnomalies()
    }

    private fun evaluateAnomalies() {
        val (recent, score) = withLock(lock) {
            val r = anomalyBuffer.filter { currentTimeMillis() - it.timestamp < 30_000 }
            val s = r.sumOf { it.severity }
            anomalyBuffer.removeAll { currentTimeMillis() - it.timestamp > 60_000 }
            r to s
        }

        val level = when {
            score >= 10 -> ThreatLevel.CRITICAL
            score >= 7 -> ThreatLevel.HIGH
            score >= 4 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Runtime Anomaly Cluster",
                description = "${recent.size} anomalies detected in 30s window (score: $score)"
            )
        }
    }

    data class Anomaly(
        val type: String,
        val severity: Int,
        val detail: String,
        val timestamp: Long = currentTimeMillis()
    )
}
