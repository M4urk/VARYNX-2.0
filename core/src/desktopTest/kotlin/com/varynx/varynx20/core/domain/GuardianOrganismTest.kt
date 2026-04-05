/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.protection.ScamDetector
import com.varynx.varynx20.core.registry.ModuleRegistry
import kotlin.test.*

class GuardianOrganismTest {

    private lateinit var organism: GuardianOrganism

    @BeforeTest
    fun setup() {
        ModuleRegistry.initialize()
        organism = GuardianOrganism()
    }

    @Test
    fun awakenActivatesAllDomains() {
        assertFalse(organism.isAlive)
        organism.awaken()
        assertTrue(organism.isAlive)
        assertTrue(organism.core.isAlive)
        assertTrue(organism.engine.isAlive)
        assertTrue(organism.reflex.isAlive)
        assertTrue(organism.identity.isAlive)
    }

    @Test
    fun awakenRegisters17ProtectionModules() {
        organism.awaken()
        assertEquals(17, organism.core.getModules().size)
        assertEquals(17, organism.core.getActiveCount())
    }

    @Test
    fun awakenRegisters10Reflexes() {
        organism.awaken()
        assertEquals(10, organism.reflex.getReflexes().size)
    }

    @Test
    fun cycleReturnsClearStateByDefault() {
        organism.awaken()
        val state = organism.cycle()
        assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
        assertEquals(GuardianMode.SENTINEL, state.guardianMode)
        assertEquals(78, state.activeModuleCount)
    }

    @Test
    fun cycleMultipleTimesStable() {
        organism.awaken()
        repeat(5) {
            val state = organism.cycle()
            assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
        }
    }

    @Test
    fun sleepDeactivatesAllDomains() {
        organism.awaken()
        assertTrue(organism.isAlive)
        organism.sleep()
        assertFalse(organism.isAlive)
        assertFalse(organism.core.isAlive)
        assertFalse(organism.engine.isAlive)
        assertFalse(organism.reflex.isAlive)
        assertFalse(organism.identity.isAlive)
    }

    @Test
    fun getCurrentModeReflectsState() {
        organism.awaken()
        assertEquals(GuardianMode.SENTINEL, organism.getCurrentMode())
    }

    @Test
    fun getCurrentThreatLevelReflectsState() {
        organism.awaken()
        assertEquals(ThreatLevel.NONE, organism.getCurrentThreatLevel())
    }

    @Test
    fun cycleDoesNothingWhenNotAwake() {
        // Not awakened — cycle should not crash
        val state = organism.cycle()
        assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
    }
}
