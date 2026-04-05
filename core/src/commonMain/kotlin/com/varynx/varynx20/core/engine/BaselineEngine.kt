/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine
import com.varynx.varynx20.core.platform.currentTimeMillis

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.platform.withLock

class BaselineEngine : Engine {
    override val engineId = "engine_baseline"
    override val engineName = "Baseline Engine"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val baselines = mutableMapOf<String, BaselineSnapshot>()

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { state = ModuleState.IDLE; withLock(lock) { baselines.clear() } }

    override fun process() {
        // Update rolling baselines
    }

    fun captureSnapshot(key: String, data: Map<String, Any>) {
        withLock(lock) {
            baselines[key] = BaselineSnapshot(
                key = key,
                capturedAt = currentTimeMillis(),
                data = data
            )
        }
    }

    fun getBaseline(key: String): BaselineSnapshot? = withLock(lock) { baselines[key] }

    fun hasDeviated(key: String, currentData: Map<String, Any>): Boolean {
        val baseline = withLock(lock) { baselines[key] } ?: return false
        return baseline.data != currentData
    }

    data class BaselineSnapshot(
        val key: String,
        val capturedAt: Long,
        val data: Map<String, Any>
    )
}
