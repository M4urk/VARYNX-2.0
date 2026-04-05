/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Multi-Device Baseline — baseline computed across all mesh devices.
 *
 * Aggregates per-device baselines into a mesh-wide "normal" picture.
 * When one device deviates from the mesh norm, it may indicate
 * a targeted attack on that specific device.
 */
class MultiDeviceBaseline : MeshModule {

    override val moduleId = "mesh_multi_baseline"
    override val moduleName = "Multi-Device Baseline"
    override var state = ModuleState.IDLE

    private val deviceBaselines = mutableMapOf<String, DeviceBaselineSnapshot>()

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Multi-device baseline active")
    }

    override fun process(context: MeshModuleContext) {
        val peerStates = context.meshSync.getPeerStates()
        val now = currentTimeMillis()

        for ((peerId, peerState) in peerStates) {
            val existing = deviceBaselines[peerId]
            if (existing == null) {
                deviceBaselines[peerId] = DeviceBaselineSnapshot(
                    deviceId = peerId,
                    avgThreatScore = peerState.threatLevel.score.toFloat(),
                    avgModuleCount = peerState.activeModuleCount.toFloat(),
                    samples = 1,
                    lastUpdated = now
                )
            } else {
                val newAvgThreat = (existing.avgThreatScore * existing.samples + peerState.threatLevel.score) /
                    (existing.samples + 1)
                val newAvgModules = (existing.avgModuleCount * existing.samples + peerState.activeModuleCount) /
                    (existing.samples + 1)
                deviceBaselines[peerId] = existing.copy(
                    avgThreatScore = newAvgThreat,
                    avgModuleCount = newAvgModules,
                    samples = existing.samples + 1,
                    lastUpdated = now
                )
            }
        }

        // Detect outliers
        if (deviceBaselines.size >= 2) {
            val meshAvg = deviceBaselines.values.map { it.avgThreatScore }.average().toFloat()
            for ((deviceId, baseline) in deviceBaselines) {
                val deviation = baseline.avgThreatScore - meshAvg
                if (deviation > OUTLIER_THRESHOLD) {
                    GuardianLog.logThreat(moduleId, "baseline_outlier",
                        "Device $deviceId deviates from mesh baseline (avg: ${"%.1f".format(baseline.avgThreatScore)} vs mesh: ${"%.1f".format(meshAvg)})",
                        ThreatLevel.LOW)
                }
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        deviceBaselines.clear()
    }

    fun getMeshBaseline(): Float {
        if (deviceBaselines.isEmpty()) return 0.0f
        return deviceBaselines.values.map { it.avgThreatScore }.average().toFloat()
    }

    companion object {
        private const val OUTLIER_THRESHOLD = 1.5f
    }
}

internal data class DeviceBaselineSnapshot(
    val deviceId: String,
    val avgThreatScore: Float,
    val avgModuleCount: Float,
    val samples: Int,
    val lastUpdated: Long
)
