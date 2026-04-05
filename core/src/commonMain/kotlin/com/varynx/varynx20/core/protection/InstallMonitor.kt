/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

class InstallMonitor : ProtectionModule {
    override val moduleId = "protect_install_monitor"
    override val moduleName = "Install Monitor"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    private val highRiskInstallers = setOf(
        "com.android.browser",     // Sideloaded via browser
        "com.android.shell",       // ADB install
    )

    /** System-level installers that are normal and expected. */
    private val trustedInstallers = setOf(
        "com.android.vending",            // Google Play Store
        "com.amazon.venezia",             // Amazon Appstore
        "com.sec.android.app.samsungapps",// Samsung Galaxy Store
        "com.huawei.appmarket",           // Huawei AppGallery
    )

    private val highRiskCategories = setOf(
        "vpn", "root", "xposed", "magisk", "lucky patcher",
        "game guardian", "freedom", "creehack"
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeInstall(
        packageName: String,
        installerPackage: String?,
        appLabel: String
    ): ThreatLevel {
        var score = 0

        if (installerPackage in highRiskInstallers) score += 2
        // Only flag unknown source if not a system/pre-installed app;
        // null installer is normal for pre-loaded and system apps.
        if (installerPackage == null && !packageName.startsWith("com.android.") &&
            !packageName.startsWith("com.google.") && !packageName.startsWith("com.samsung.") &&
            !packageName.startsWith("com.qualcomm.") && !packageName.startsWith("com.sec.")) {
            score += 1 // Informational — unknown source but not necessarily dangerous
        }
        if (highRiskCategories.any { appLabel.contains(it, ignoreCase = true) }) score += 3

        val level = when {
            score >= 5 -> ThreatLevel.HIGH
            score >= 3 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        // Only update event when we detect something genuinely suspicious
        if (level >= ThreatLevel.MEDIUM) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Suspicious App Installed",
                description = "$appLabel ($packageName) from ${installerPackage ?: "unknown source"}"
            )
        }
        return level
    }
}
