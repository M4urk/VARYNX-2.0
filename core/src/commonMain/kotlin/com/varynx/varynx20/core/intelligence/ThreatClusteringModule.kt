/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.domain.DetectionSignal
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Threat Clustering — groups related threat events into clusters.
 *
 * When multiple threats arrive within a time window from related sources,
 * they form a cluster. Clusters represent coordinated attacks or cascading
 * failures. A cluster's severity is elevated above individual events.
 */
class ThreatClusteringModule : IntelligenceModule {

    override val moduleId = "intel_threat_clustering"
    override val moduleName = "Threat Clustering"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val activeClusters = mutableListOf<ThreatCluster>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Threat clustering initialized")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val now = currentTimeMillis()
        val threats = signals.filter { it.severity > ThreatLevel.NONE }

        return withLock(lock) {
            activeClusters.removeAll { now - it.lastUpdated > CLUSTER_TIMEOUT_MS }

            for (signal in threats) {
                val matched = activeClusters.find { it.canAbsorb(signal, now) }
                if (matched != null) {
                    matched.absorb(signal, now)
                } else {
                    activeClusters.add(ThreatCluster(signal, now))
                }
            }

            val maturedCluster = activeClusters.find {
                it.memberCount >= MIN_CLUSTER_SIZE && !it.reported
            }
            if (maturedCluster != null) {
                maturedCluster.reported = true
                val clusterLevel = ThreatLevel.fromScore(
                    (maturedCluster.maxSeverity.score + 1).coerceAtMost(ThreatLevel.CRITICAL.score)
                )
                IntelligenceInsight(
                    sourceModuleId = moduleId,
                    insightType = InsightType.CLUSTER_FORMED,
                    adjustedLevel = clusterLevel,
                    confidence = (maturedCluster.memberCount.toFloat() / (MIN_CLUSTER_SIZE * 3)).coerceIn(0.5f, 1.0f),
                    detail = "Threat cluster formed: ${maturedCluster.memberCount} events from " +
                        "${maturedCluster.sourceModules.size} modules in ${(now - maturedCluster.startTime) / 1000}s"
                )
            } else null
        }
    }

    override fun adapt(feedback: AdaptationFeedback) {
        // No adaptation needed — clustering is observation-based
    }

    override fun reset() {
        withLock(lock) { activeClusters.clear() }
    }

    val clusterCount: Int get() = withLock(lock) { activeClusters.size }

    companion object {
        private const val CLUSTER_TIMEOUT_MS = 60_000L     // clusters expire after 1 minute of inactivity
        private const val CLUSTER_WINDOW_MS = 10_000L      // signals within 10s can cluster together
        private const val MIN_CLUSTER_SIZE = 3
    }
}

internal class ThreatCluster(initial: DetectionSignal, now: Long) {
    val sourceModules = mutableSetOf(initial.sourceModuleId)
    var memberCount = 1
        private set
    var maxSeverity = initial.severity
        private set
    val startTime = now
    var lastUpdated = now
        private set
    var reported = false

    fun canAbsorb(signal: DetectionSignal, now: Long): Boolean {
        return now - lastUpdated < 10_000L // within cluster window
    }

    fun absorb(signal: DetectionSignal, now: Long) {
        sourceModules.add(signal.sourceModuleId)
        memberCount++
        if (signal.severity > maxSeverity) maxSeverity = signal.severity
        lastUpdated = now
    }
}
