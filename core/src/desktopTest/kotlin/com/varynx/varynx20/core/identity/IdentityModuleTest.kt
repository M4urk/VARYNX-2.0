/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class IdentityModuleTest {

    private fun stateWith(level: ThreatLevel, mode: GuardianMode = GuardianMode.SENTINEL) =
        GuardianState(
            overallThreatLevel = level,
            guardianMode = mode,
            activeModuleCount = 35,
            totalModuleCount = 76
        )

    // ── MultiFormGuardian ──

    @Test
    fun multiFormStartsAsShield() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        assertEquals(GuardianForm.SHIELD, mfg.getCurrentForm())
    }

    @Test
    fun multiFormShiftsOnThreat() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        val expr = mfg.evaluate(stateWith(ThreatLevel.MEDIUM))
        assertNotNull(expr)
        assertEquals(ExpressionType.FORM_CHANGE, expr.expressionType)
        assertEquals(GuardianForm.SENTINEL, mfg.getCurrentForm())
    }

    @Test
    fun multiFormHighThreatToPhantom() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        mfg.evaluate(stateWith(ThreatLevel.HIGH))
        assertEquals(GuardianForm.PHANTOM, mfg.getCurrentForm())
    }

    @Test
    fun multiFormLockdownToWarden() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        mfg.evaluate(stateWith(ThreatLevel.CRITICAL, GuardianMode.LOCKDOWN))
        assertEquals(GuardianForm.WARDEN, mfg.getCurrentForm())
    }

    @Test
    fun multiFormSafeModeToAegis() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        mfg.evaluate(stateWith(ThreatLevel.CRITICAL, GuardianMode.SAFE))
        assertEquals(GuardianForm.AEGIS, mfg.getCurrentForm())
    }

    @Test
    fun multiFormNoChangeWhenSame() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        val expr = mfg.evaluate(stateWith(ThreatLevel.NONE))
        assertNull(expr, "No expression when form doesn't change")
    }

    @Test
    fun multiFormResetReturnToShield() {
        val mfg = MultiFormGuardian()
        mfg.initialize()
        mfg.evaluate(stateWith(ThreatLevel.HIGH))
        assertEquals(GuardianForm.PHANTOM, mfg.getCurrentForm())
        mfg.reset()
        assertEquals(GuardianForm.SHIELD, mfg.getCurrentForm())
    }

    // ── All Identity Modules Exist ──

    @Test
    fun allIdentityModulesHaveUniqueIds() {
        val modules: List<IdentityModule> = listOf(
            MultiFormGuardian(),
            IdentityDrivenUiStates(),
            HybridModeTransitions(),
            LayeredReflexChains(),
            GuardianProfiles(),
            IdentityMemoryModule(),
            AdaptivePresence(),
            ThreatInterpretationStyles(),
            GuardianMoodStates(),
            IdentityEvolution()
        )
        assertEquals(10, modules.size)
        val ids = modules.map { it.moduleId }.toSet()
        assertEquals(10, ids.size, "All identity module IDs must be unique")
    }

    @Test
    fun allIdentityModulesInitializeAndReset() {
        val modules: List<IdentityModule> = listOf(
            MultiFormGuardian(),
            IdentityDrivenUiStates(),
            HybridModeTransitions(),
            LayeredReflexChains(),
            GuardianProfiles(),
            IdentityMemoryModule(),
            AdaptivePresence(),
            ThreatInterpretationStyles(),
            GuardianMoodStates(),
            IdentityEvolution()
        )
        for (mod in modules) {
            mod.initialize()
            assertEquals(ModuleState.ACTIVE, mod.state, "Failed init for ${mod.moduleId}")
            mod.reset()
        }
    }

    @Test
    fun allIdentityModulesEvaluateWithoutCrash() {
        val modules: List<IdentityModule> = listOf(
            MultiFormGuardian(),
            IdentityDrivenUiStates(),
            HybridModeTransitions(),
            LayeredReflexChains(),
            GuardianProfiles(),
            IdentityMemoryModule(),
            AdaptivePresence(),
            ThreatInterpretationStyles(),
            GuardianMoodStates(),
            IdentityEvolution()
        )
        val states = listOf(
            stateWith(ThreatLevel.NONE),
            stateWith(ThreatLevel.LOW),
            stateWith(ThreatLevel.MEDIUM),
            stateWith(ThreatLevel.HIGH),
            stateWith(ThreatLevel.CRITICAL, GuardianMode.LOCKDOWN)
        )
        for (mod in modules) {
            mod.initialize()
            for (state in states) {
                mod.evaluate(state) // Should not crash
            }
        }
    }

    // ── GuardianMood ──

    @Test
    fun guardianMoodLevels() {
        assertEquals(6, GuardianMood.entries.size)
        assertTrue(GuardianMood.DORMANT.intensity < GuardianMood.CALM.intensity)
        assertTrue(GuardianMood.CALM.intensity < GuardianMood.WATCHFUL.intensity)
        assertTrue(GuardianMood.WATCHFUL.intensity < GuardianMood.ALERT.intensity)
        assertTrue(GuardianMood.ALERT.intensity < GuardianMood.AGGRESSIVE.intensity)
        assertTrue(GuardianMood.AGGRESSIVE.intensity < GuardianMood.CRITICAL.intensity)
    }

    // ── ExpressionType ──

    @Test
    fun expressionTypesExist() {
        assertEquals(8, ExpressionType.entries.size)
        assertTrue(ExpressionType.entries.map { it.name }.contains("FORM_CHANGE"))
        assertTrue(ExpressionType.entries.map { it.name }.contains("MOOD_SHIFT"))
        assertTrue(ExpressionType.entries.map { it.name }.contains("EVOLUTION_STEP"))
    }

    // ── GuardianForm ──

    @Test
    fun guardianFormsHaveMoods() {
        assertEquals(5, GuardianForm.entries.size)
        assertEquals(GuardianMood.CALM, GuardianForm.SHIELD.mood)
        assertEquals(GuardianMood.WATCHFUL, GuardianForm.SENTINEL.mood)
        assertEquals(GuardianMood.ALERT, GuardianForm.PHANTOM.mood)
        assertEquals(GuardianMood.AGGRESSIVE, GuardianForm.WARDEN.mood)
        assertEquals(GuardianMood.CRITICAL, GuardianForm.AEGIS.mood)
    }
}
