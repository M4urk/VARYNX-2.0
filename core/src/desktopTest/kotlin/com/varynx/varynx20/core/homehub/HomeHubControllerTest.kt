/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.homehub

import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class HomeHubControllerTest {

    private lateinit var controller: HomeHubController

    @BeforeTest
    fun setup() {
        controller = HomeHubController()
        controller.start()
    }

    // ── IoT Device Inventory ──

    @Test
    fun startsEmpty() {
        assertTrue(controller.isRunning)
        assertEquals(0, controller.state.trackedDeviceCount)
        assertEquals(0, controller.state.meshPeerCount)
    }

    @Test
    fun registersDevice() {
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:01",
            ipAddress = "192.168.1.100",
            displayName = "Smart TV"
        ))
        val devices = controller.getDevices()
        assertEquals(1, devices.size)
        assertEquals("Smart TV", devices[0].displayName)
        assertEquals(DeviceStatus.ONLINE, devices[0].status)
    }

    @Test
    fun updatesExistingDevice() {
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:01",
            ipAddress = "192.168.1.100",
            displayName = "Smart TV"
        ))
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:01",
            ipAddress = "192.168.1.101",
            displayName = "Smart TV v2"
        ))
        // Should update, not duplicate
        assertEquals(1, controller.getDevices().size)
        assertEquals("192.168.1.101", controller.getDevices()[0].ipAddress)
    }

    @Test
    fun filtersDevicesByStatus() {
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:01", ipAddress = "192.168.1.100",
            displayName = "TV", status = DeviceStatus.ONLINE
        ))
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:02", ipAddress = "192.168.1.101",
            displayName = "Printer", status = DeviceStatus.ONLINE
        ))
        assertEquals(2, controller.getDevices(DeviceStatus.ONLINE).size)
        assertEquals(0, controller.getDevices(DeviceStatus.OFFLINE).size)
    }

    @Test
    fun detectsRogueDevices() {
        // Trusted device
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:01", ipAddress = "192.168.1.100",
            displayName = "Trusted TV", isTrusted = true
        ))
        // Untrusted device (rogue)
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:02", ipAddress = "192.168.1.200",
            displayName = "Unknown Device"
        ))
        val rogues = controller.detectRogueDevices()
        assertEquals(1, rogues.size)
        assertEquals("Unknown Device", rogues[0].displayName)
    }

    // ── Device Onboarding ──

    @Test
    fun addsOnboardingRequest() {
        controller.requestOnboarding(OnboardingRequest(
            macAddress = "AA:BB:CC:DD:EE:03",
            ipAddress = "192.168.1.200",
            displayName = "New Camera"
        ))
        assertEquals(1, controller.state.pendingOnboardingCount)
        assertEquals(1, controller.getPendingOnboarding().size)
    }

    @Test
    fun approvesOnboarding() {
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:03", ipAddress = "192.168.1.200",
            displayName = "New Camera"
        ))
        controller.requestOnboarding(OnboardingRequest(
            macAddress = "AA:BB:CC:DD:EE:03",
            ipAddress = "192.168.1.200",
            displayName = "New Camera"
        ))

        assertTrue(controller.approveOnboarding("AA:BB:CC:DD:EE:03"))
        assertEquals(0, controller.state.pendingOnboardingCount)
        val device = controller.getDevices().first { it.macAddress == "AA:BB:CC:DD:EE:03" }
        assertTrue(device.isTrusted)
        assertFalse(device.isOnboarding)
    }

    @Test
    fun deniesOnboarding() {
        controller.registerDevice(IoTDevice(
            macAddress = "AA:BB:CC:DD:EE:03", ipAddress = "192.168.1.200",
            displayName = "Suspicious Device"
        ))
        controller.requestOnboarding(OnboardingRequest(
            macAddress = "AA:BB:CC:DD:EE:03",
            ipAddress = "192.168.1.200",
            displayName = "Suspicious Device"
        ))

        assertTrue(controller.denyOnboarding("AA:BB:CC:DD:EE:03"))
        assertEquals(0, controller.state.pendingOnboardingCount)
        val device = controller.getDevices().first { it.macAddress == "AA:BB:CC:DD:EE:03" }
        assertEquals(DeviceStatus.BLOCKED, device.status)
    }

    @Test
    fun denyNonexistentReturnsFalse() {
        assertFalse(controller.denyOnboarding("NONEXISTENT"))
    }

    // ── Network-Wide Threat Correlation ──

    @Test
    fun correlatesThreatFromMultiplePeers() {
        // Two different peers report same module threat
        controller.onPeerThreat(
            ThreatEvent("t1", sourceModuleId = "network_integrity",
                threatLevel = ThreatLevel.HIGH, title = "ARP Spoof", description = "ARP"),
            "peer-1"
        )
        controller.onPeerThreat(
            ThreatEvent("t2", sourceModuleId = "network_integrity",
                threatLevel = ThreatLevel.HIGH, title = "ARP Spoof", description = "ARP"),
            "peer-2"
        )

        val correlations = controller.correlatePeerThreats()
        assertEquals(1, correlations.size)
        assertEquals("network_integrity", correlations[0].sourceModuleId)
        assertEquals(2, correlations[0].peerCount)
        // Should escalate HIGH → CRITICAL
        assertEquals(ThreatLevel.CRITICAL, correlations[0].escalatedLevel)
    }

    @Test
    fun noCorrelationForSinglePeer() {
        controller.onPeerThreat(
            ThreatEvent("t1", sourceModuleId = "scam_detector",
                threatLevel = ThreatLevel.MEDIUM, title = "Scam", description = "Pattern"),
            "peer-1"
        )

        val correlations = controller.correlatePeerThreats()
        assertTrue(correlations.isEmpty())
    }

    @Test
    fun controllerCycleUpdatesState() {
        val hubState = controller.cycle(emptyMap())
        assertTrue(hubState.cycleCount > 0)
        assertTrue(hubState.uptimeMs >= 0)
    }

    @Test
    fun buildsGuardianState() {
        val state = controller.buildState()
        assertNotNull(state)
        assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
    }

    @Test
    fun stopSetsRunningFalse() {
        controller.stop()
        assertFalse(controller.isRunning)
    }
}
