/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

/**
 * Analyzes notification content for phishing, scam, and social-engineering patterns.
 * Deterministic regex-based detection. No learning.
 */
class NotificationAnalyzer : ProtectionModule {
    override val moduleId = "protect_notification_analyzer"
    override val moduleName = "Notification Analyzer"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    private val phishingPatterns = listOf(
        Regex("verify.*your.*(account|identity|payment)", RegexOption.IGNORE_CASE),
        Regex("(security|suspicious).*(alert|warning|activity)", RegexOption.IGNORE_CASE),
        Regex("(click|tap).*(?:here|now|immediately).*(?:to|and)", RegexOption.IGNORE_CASE),
        Regex("you.*(won|winner|selected|chosen)", RegexOption.IGNORE_CASE),
        Regex("(expire|expiring|expired).*(soon|today|hours)", RegexOption.IGNORE_CASE),
        Regex("(password|credential).*(reset|change|update)", RegexOption.IGNORE_CASE),
        Regex("unusual.*(sign.?in|login|access)", RegexOption.IGNORE_CASE),
        Regex("(bank|paypal|venmo).*action.*required", RegexOption.IGNORE_CASE)
    )

    private val trustedNotificationPackages = setOf(
        "com.android.systemui",
        "com.google.android.gms",
        "com.android.providers.downloads"
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }
    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE
    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeNotification(
        packageName: String,
        title: String,
        body: String
    ): ThreatLevel {
        if (packageName in trustedNotificationPackages) return ThreatLevel.NONE

        val fullText = "$title $body"
        val matchCount = phishingPatterns.count { it.containsMatchIn(fullText) }

        val level = when {
            matchCount >= 3 -> ThreatLevel.HIGH
            matchCount >= 2 -> ThreatLevel.MEDIUM
            matchCount >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Suspicious Notification",
                description = "From $packageName: matched $matchCount phishing patterns"
            )
        }
        return level
    }
}
