/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Location Context Engine — adjusts guardian behavior based on
 * physical location context (home, work, travel, public).
 *
 * Determines context from network characteristics (known WiFi SSID,
 * LAN peer count, IP address patterns) — NO GPS, NO cloud geolocation.
 * Fully offline and privacy-preserving.
 */
class LocationContextModule : PlatformModule {

    override val moduleId = "plat_location_context"
    override val moduleName = "Location Context Engine"
    override var state = ModuleState.IDLE

    private var currentContext = LocationContext.UNKNOWN
    private val knownNetworks = mutableMapOf<String, LocationContext>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Location context engine active (context: ${currentContext.label})")
    }

    override fun process() {
        // Context is updated via setNetworkIdentifier when network changes
    }

    override fun shutdown() {
        state = ModuleState.IDLE
    }

    fun setNetworkIdentifier(networkId: String) {
        val context = knownNetworks[networkId] ?: LocationContext.UNKNOWN
        if (context != currentContext) {
            val prev = currentContext
            currentContext = context
            GuardianLog.logEngine(moduleId, "context_change",
                "Location context: ${prev.label} → ${context.label}")
        }
    }

    fun registerNetwork(networkId: String, context: LocationContext) {
        knownNetworks[networkId] = context
    }

    fun getCurrentContext(): LocationContext = currentContext

    fun getSensitivityMultiplier(): Float = currentContext.sensitivityMultiplier
}

enum class LocationContext(
    val label: String,
    val sensitivityMultiplier: Float,
    val alertLevel: Float
) {
    HOME("Home", 0.8f, 0.3f),
    WORK("Work", 1.0f, 0.5f),
    TRAVEL("Travel", 1.5f, 0.8f),
    PUBLIC("Public", 1.3f, 0.7f),
    UNKNOWN("Unknown", 1.2f, 0.6f)
}
