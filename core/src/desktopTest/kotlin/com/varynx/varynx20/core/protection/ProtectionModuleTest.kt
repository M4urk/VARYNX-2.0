/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class ProtectionModuleTest {

    // ── ScamDetector ──

    @Test
    fun scamDetectorActivateDeactivate() {
        val sd = ScamDetector()
        assertEquals(ModuleState.IDLE, sd.state)
        sd.activate()
        assertEquals(ModuleState.ACTIVE, sd.state)
        sd.deactivate()
        assertEquals(ModuleState.IDLE, sd.state)
    }

    @Test
    fun scamDetectorSinglePattern() {
        val sd = ScamDetector()
        sd.activate()
        val level = sd.analyzeText("congratulations you've won a new iPhone!")
        assertEquals(ThreatLevel.MEDIUM, level)
        assertNotNull(sd.getLastEvent())
        assertEquals("protect_scam_detector", sd.getLastEvent()!!.sourceModuleId)
    }

    @Test
    fun scamDetectorMultiplePatterns() {
        val sd = ScamDetector()
        sd.activate()
        val level = sd.analyzeText("congratulations you've won! click here to claim your prize. verify your account immediately")
        assertEquals(ThreatLevel.CRITICAL, level) // 3+ matches
    }

    @Test
    fun scamDetectorTwoPatterns() {
        val sd = ScamDetector()
        sd.activate()
        val level = sd.analyzeText("urgent action required - send gift card now")
        assertEquals(ThreatLevel.HIGH, level) // 2 matches
    }

    @Test
    fun scamDetectorCleanText() {
        val sd = ScamDetector()
        sd.activate()
        val level = sd.analyzeText("Hello, your package will arrive tomorrow")
        assertEquals(ThreatLevel.NONE, level)
        assertNull(sd.getLastEvent())
    }

    @Test
    fun scamDetectorCaseInsensitive() {
        val sd = ScamDetector()
        sd.activate()
        val level = sd.analyzeText("CONGRATULATIONS YOU'VE WON")
        assertEquals(ThreatLevel.MEDIUM, level)
    }

    @Test
    fun scamDetectorScanReturnsLastLevel() {
        val sd = ScamDetector()
        sd.activate()
        assertEquals(ThreatLevel.NONE, sd.scan()) // no analysis yet
        sd.analyzeText("congratulations you've won")
        assertEquals(ThreatLevel.MEDIUM, sd.scan())
    }

    // ── ClipboardShield ──

    @Test
    fun clipboardShieldInterface() {
        val cs = ClipboardShield()
        assertEquals("protect_clipboard_shield", cs.moduleId)
        cs.activate()
        assertEquals(ModuleState.ACTIVE, cs.state)
        cs.deactivate()
        assertEquals(ModuleState.IDLE, cs.state)
    }

    // ── NetworkIntegrity ──

    @Test
    fun networkIntegrityInterface() {
        val ni = NetworkIntegrity()
        assertEquals("protect_network_integrity", ni.moduleId)
        ni.activate()
        assertEquals(ModuleState.ACTIVE, ni.state)
    }

    // ── All 15 modules exist and have correct IDs ──

    @Test
    fun allProtectionModulesHaveUniqueIds() {
        val modules: List<ProtectionModule> = listOf(
            ScamDetector(),
            ClipboardShield(),
            BluetoothSkimmerDetector(),
            NfcGuardian(),
            NetworkIntegrity(),
            AppBehaviorMonitor(),
            DeviceStateMonitor(),
            PermissionWatchdog(),
            InstallMonitor(),
            RuntimeThreatMonitor(),
            OverlayDetector(),
            NotificationAnalyzer(),
            UsbIntegrity(),
            SensorAnomalyDetector(),
            AppTamperDetector()
        )
        assertEquals(15, modules.size)
        val ids = modules.map { it.moduleId }.toSet()
        assertEquals(15, ids.size, "All module IDs must be unique")
    }

    @Test
    fun allModulesActivateAndDeactivate() {
        val modules: List<ProtectionModule> = listOf(
            ScamDetector(), ClipboardShield(), BluetoothSkimmerDetector(),
            NfcGuardian(), NetworkIntegrity(), AppBehaviorMonitor(),
            DeviceStateMonitor(), PermissionWatchdog(), InstallMonitor(),
            RuntimeThreatMonitor(), OverlayDetector(), NotificationAnalyzer(),
            UsbIntegrity(), SensorAnomalyDetector(), AppTamperDetector()
        )
        for (mod in modules) {
            mod.activate()
            assertEquals(ModuleState.ACTIVE, mod.state, "Failed for ${mod.moduleId}")
            mod.deactivate()
            assertEquals(ModuleState.IDLE, mod.state, "Failed deactivate for ${mod.moduleId}")
        }
    }

    @Test
    fun allModulesScanWithoutCrash() {
        val modules: List<ProtectionModule> = listOf(
            ScamDetector(), ClipboardShield(), BluetoothSkimmerDetector(),
            NfcGuardian(), NetworkIntegrity(), AppBehaviorMonitor(),
            DeviceStateMonitor(), PermissionWatchdog(), InstallMonitor(),
            RuntimeThreatMonitor(), OverlayDetector(), NotificationAnalyzer(),
            UsbIntegrity(), SensorAnomalyDetector(), AppTamperDetector()
        )
        for (mod in modules) {
            mod.activate()
            val level = mod.scan()
            assertTrue(level.score >= 0, "Scan should return valid level for ${mod.moduleId}")
        }
    }
}
