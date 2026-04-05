/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Policy engine for cross-device configuration.
 * Only CONTROLLER role devices can push policy updates.
 * All other devices verify the signature before applying.
 */
class PolicyEngine {

    private val rules = mutableListOf<PolicyRule>()

    fun addRule(rule: PolicyRule) {
        // Replace existing rule for same target
        rules.removeAll { it.targetModuleId == rule.targetModuleId && it.ruleType == rule.ruleType }
        rules.add(rule)
    }

    fun removeRule(targetModuleId: String, ruleType: PolicyRuleType) {
        rules.removeAll { it.targetModuleId == targetModuleId && it.ruleType == ruleType }
    }

    fun getRulesForModule(moduleId: String): List<PolicyRule> =
        rules.filter { it.targetModuleId == moduleId }

    fun getAllRules(): List<PolicyRule> = rules.toList()

    /** Check if a module should be enabled based on policy. */
    fun isModuleEnabled(moduleId: String): Boolean {
        val disableRule = rules.find {
            it.targetModuleId == moduleId && it.ruleType == PolicyRuleType.DISABLE_MODULE
        }
        return disableRule == null
    }

    /** Get the threshold override for a module, if any. */
    fun getThresholdOverride(moduleId: String): ThreatLevel? {
        val rule = rules.find {
            it.targetModuleId == moduleId && it.ruleType == PolicyRuleType.THRESHOLD_OVERRIDE
        }
        return rule?.thresholdValue
    }

    fun clear() = rules.clear()
}

data class PolicyRule(
    val ruleId: String,
    val targetModuleId: String,
    val ruleType: PolicyRuleType,
    val thresholdValue: ThreatLevel? = null,
    val issuedBy: String,          // Device ID of the CONTROLLER that issued this
    val signature: ByteArray       // Ed25519 signature for verification
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PolicyRule) return false
        return ruleId == other.ruleId
    }

    override fun hashCode(): Int = ruleId.hashCode()
}

enum class PolicyRuleType {
    DISABLE_MODULE,
    ENABLE_MODULE,
    THRESHOLD_OVERRIDE,
    FORCE_MODE,
    SENSITIVITY_ADJUST
}
