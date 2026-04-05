/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class PolicyEngineTest {

    private lateinit var engine: PolicyEngine

    @BeforeTest
    fun setup() {
        engine = PolicyEngine()
    }

    // ── Add / Remove Rules ──

    @Test
    fun addRuleStoresIt() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.ENABLE_MODULE))
        val rules = engine.getRulesForModule("module-a")
        assertEquals(1, rules.size)
        assertEquals("r1", rules[0].ruleId)
    }

    @Test
    fun addRuleReplacesExistingForSameTarget() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.DISABLE_MODULE))
        engine.addRule(makeRule("r2", "module-a", PolicyRuleType.DISABLE_MODULE))

        val rules = engine.getRulesForModule("module-a")
        assertEquals(1, rules.size)
        assertEquals("r2", rules[0].ruleId)
    }

    @Test
    fun differentRuleTypesCoexist() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.ENABLE_MODULE))
        engine.addRule(makeRule("r2", "module-a", PolicyRuleType.THRESHOLD_OVERRIDE, ThreatLevel.HIGH))

        val rules = engine.getRulesForModule("module-a")
        assertEquals(2, rules.size)
    }

    @Test
    fun removeRuleByTargetAndType() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.DISABLE_MODULE))
        engine.addRule(makeRule("r2", "module-a", PolicyRuleType.THRESHOLD_OVERRIDE))
        engine.removeRule("module-a", PolicyRuleType.DISABLE_MODULE)

        val rules = engine.getRulesForModule("module-a")
        assertEquals(1, rules.size)
        assertEquals(PolicyRuleType.THRESHOLD_OVERRIDE, rules[0].ruleType)
    }

    @Test
    fun removeNonExistentRuleIsNoOp() {
        engine.removeRule("module-x", PolicyRuleType.DISABLE_MODULE)
        assertTrue(engine.getAllRules().isEmpty())
    }

    // ── isModuleEnabled ──

    @Test
    fun moduleEnabledByDefault() {
        assertTrue(engine.isModuleEnabled("module-a"))
    }

    @Test
    fun moduleDisabledByPolicy() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.DISABLE_MODULE))
        assertFalse(engine.isModuleEnabled("module-a"))
    }

    @Test
    fun disableOneModuleDoesNotAffectOthers() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.DISABLE_MODULE))
        assertTrue(engine.isModuleEnabled("module-b"))
    }

    // ── Threshold Override ──

    @Test
    fun noThresholdOverrideByDefault() {
        assertNull(engine.getThresholdOverride("module-a"))
    }

    @Test
    fun thresholdOverrideReturnsValue() {
        engine.addRule(makeRule("r1", "module-a", PolicyRuleType.THRESHOLD_OVERRIDE, ThreatLevel.CRITICAL))
        assertEquals(ThreatLevel.CRITICAL, engine.getThresholdOverride("module-a"))
    }

    // ── getAllRules ──

    @Test
    fun getAllRulesReturnsCopy() {
        engine.addRule(makeRule("r1", "mod-a", PolicyRuleType.ENABLE_MODULE))
        engine.addRule(makeRule("r2", "mod-b", PolicyRuleType.DISABLE_MODULE))

        val all = engine.getAllRules()
        assertEquals(2, all.size)
    }

    // ── Clear ──

    @Test
    fun clearRemovesAllRules() {
        engine.addRule(makeRule("r1", "mod-a", PolicyRuleType.ENABLE_MODULE))
        engine.addRule(makeRule("r2", "mod-b", PolicyRuleType.DISABLE_MODULE))
        engine.clear()

        assertTrue(engine.getAllRules().isEmpty())
        assertTrue(engine.isModuleEnabled("mod-a"))
    }

    // ── Helpers ──

    private fun makeRule(
        ruleId: String,
        targetModuleId: String,
        ruleType: PolicyRuleType,
        threshold: ThreatLevel? = null
    ) = PolicyRule(
        ruleId = ruleId,
        targetModuleId = targetModuleId,
        ruleType = ruleType,
        thresholdValue = threshold,
        issuedBy = "controller-1",
        signature = ByteArray(64) { 0 }
    )
}
