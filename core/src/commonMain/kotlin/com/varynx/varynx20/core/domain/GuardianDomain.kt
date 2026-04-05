/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain
import com.varynx.varynx20.core.platform.currentTimeMillis

import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * The four logic domains that define VARYNX 2.0 as a single guardian organism.
 *
 * Core detects → Engine interprets → Reflex responds → Identity expresses.
 * This loop repeats continuously, giving VARYNX its OS-level behavior.
 */
enum class DomainType(val label: String) {
    CORE("Core"),
    ENGINE("Engine"),
    REFLEX("Reflex"),
    IDENTITY("Identity")
}

/**
 * Contract for every domain in the guardian organism.
 * Each domain handles one phase of the detect → interpret → respond → express loop.
 */
interface GuardianDomain {
    val domainType: DomainType
    val isAlive: Boolean
    fun awaken()
    fun sleep()
}

/**
 * A detection signal emitted by the Core domain.
 * Feeds directly into the Engine for interpretation and scoring.
 */
data class DetectionSignal(
    val sourceModuleId: String,
    val severity: ThreatLevel,
    val title: String,
    val detail: String,
    val timestamp: Long = currentTimeMillis()
)

/**
 * An interpreted verdict emitted by the Engine domain.
 * Tells the Reflex domain what defensive action is required.
 */
data class EngineVerdict(
    val event: ThreatEvent,
    val computedLevel: ThreatLevel,
    val requiresReflex: Boolean,
    val timestamp: Long = currentTimeMillis()
)

/**
 * A reflex outcome emitted by the Reflex domain.
 * Tells Identity what just happened so it can update guardian expression.
 */
data class ReflexOutcome(
    val reflexId: String,
    val action: String,
    val resultingLevel: ThreatLevel,
    val message: String,
    val timestamp: Long = currentTimeMillis()
)
