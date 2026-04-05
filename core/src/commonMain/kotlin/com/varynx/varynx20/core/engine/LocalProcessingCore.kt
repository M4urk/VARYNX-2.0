/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.platform.withLock

class LocalProcessingCore : Engine {
    override val engineId = "engine_local_core"
    override val engineName = "Local Processing Core"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val engines = mutableListOf<Engine>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        withLock(lock) { engines.toList() }.forEach { it.initialize() }
    }

    override fun shutdown() {
        withLock(lock) { engines.toList() }.forEach { it.shutdown() }
        state = ModuleState.IDLE
    }

    override fun process() {
        withLock(lock) { engines.toList() }.forEach { engine ->
            if (engine.state == ModuleState.ACTIVE) {
                engine.process()
            }
        }
    }

    fun registerEngine(engine: Engine) {
        withLock(lock) { engines.add(engine) }
    }

    fun getRegisteredEngines(): List<Engine> = withLock(lock) { engines.toList() }

    fun getEngine(engineId: String): Engine? = withLock(lock) { engines.find { it.engineId == engineId } }
}
