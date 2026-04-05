/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class ReflexTest {

    private fun event(level: ThreatLevel, id: String = "evt-1") = ThreatEvent(
        id = id, sourceModuleId = "test_module", threatLevel = level,
        title = "Test Threat", description = "Test description"
    )

    // ── WarningReflex ──

    @Test
    fun warningTriggersOnLow() {
        val reflex = WarningReflex()
        assertTrue(reflex.canTrigger(event(ThreatLevel.LOW)))
        assertTrue(reflex.canTrigger(event(ThreatLevel.CRITICAL)))
    }

    @Test
    fun warningDoesNotTriggerOnNone() {
        val reflex = WarningReflex()
        assertFalse(reflex.canTrigger(event(ThreatLevel.NONE)))
    }

    @Test
    fun warningProducesResult() {
        val reflex = WarningReflex()
        val result = reflex.trigger(event(ThreatLevel.MEDIUM))
        assertTrue(result.success)
        assertEquals("WARN", result.action)
        assertEquals(1, reflex.getWarnings().size)
    }

    @Test
    fun warningResetClears() {
        val reflex = WarningReflex()
        reflex.trigger(event(ThreatLevel.LOW))
        reflex.reset()
        assertTrue(reflex.getWarnings().isEmpty())
    }

    // ── BlockReflex ──

    @Test
    fun blockTriggersOnMedium() {
        val reflex = BlockReflex()
        assertFalse(reflex.canTrigger(event(ThreatLevel.LOW)))
        assertTrue(reflex.canTrigger(event(ThreatLevel.MEDIUM)))
        assertTrue(reflex.canTrigger(event(ThreatLevel.CRITICAL)))
    }

    @Test
    fun blockProducesResult() {
        val reflex = BlockReflex()
        val result = reflex.trigger(event(ThreatLevel.HIGH))
        assertTrue(result.success)
        assertEquals("BLOCK", result.action)
        assertEquals(1, reflex.getBlockedActions().size)
    }

    // ── LockdownReflex ──

    @Test
    fun lockdownOnlyTriggersOnCritical() {
        val reflex = LockdownReflex()
        assertFalse(reflex.canTrigger(event(ThreatLevel.HIGH)))
        assertTrue(reflex.canTrigger(event(ThreatLevel.CRITICAL)))
    }

    @Test
    fun lockdownEngages() {
        val reflex = LockdownReflex()
        assertFalse(reflex.isLockdownActive)
        reflex.trigger(event(ThreatLevel.CRITICAL))
        assertTrue(reflex.isLockdownActive)
        assertEquals(ModuleState.TRIGGERED, reflex.state)
    }

    @Test
    fun lockdownResetDisengages() {
        val reflex = LockdownReflex()
        reflex.trigger(event(ThreatLevel.CRITICAL))
        reflex.reset()
        assertFalse(reflex.isLockdownActive)
        assertEquals(ModuleState.ACTIVE, reflex.state)
    }

    // ── Priority Ordering ──

    @Test
    fun reflexPriorityOrdering() {
        val reflexes = listOf(
            WarningReflex(),
            BlockReflex(),
            LockdownReflex()
        ).sortedByDescending { it.priority }

        assertEquals("reflex_lockdown", reflexes[0].reflexId)
        assertEquals("reflex_block", reflexes[1].reflexId)
        assertEquals("reflex_warning", reflexes[2].reflexId)
    }

    // ── ReflexResult ──

    @Test
    fun reflexResultContainsAllFields() {
        val result = ReflexResult("test-id", "ACT", true, "msg")
        assertEquals("test-id", result.reflexId)
        assertEquals("ACT", result.action)
        assertTrue(result.success)
        assertEquals("msg", result.message)
    }
}
