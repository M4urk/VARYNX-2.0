/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class ReflexPropagationTest {

    private lateinit var aliceKeyStore: DeviceKeyStore
    private lateinit var bobKeyStore: DeviceKeyStore
    private lateinit var trustGraph: TrustGraph

    @BeforeTest
    fun setup() {
        aliceKeyStore = DeviceKeyStore.generate("Alice", DeviceRole.HUB_HOME)
        bobKeyStore = DeviceKeyStore.generate("Bob", DeviceRole.GUARDIAN)
        trustGraph = TrustGraph()
        trustGraph.establishTrust(bobKeyStore.identity, aliceKeyStore.keyPair)
    }

    // ── Local Reflex ──

    @Test
    fun localReflexQueuedForBroadcast() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        prop.onLocalReflexFired(makeReflex("r1", ThreatLevel.HIGH, ReflexAction.ALERT))

        val pending = prop.drainPendingReflexes()
        assertEquals(1, pending.size)
        assertEquals("r1", pending[0].reflexId)
        assertEquals(DeviceRole.HUB_HOME, pending[0].sourceRole)
    }

    @Test
    fun drainClearsPending() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        prop.onLocalReflexFired(makeReflex("r1", ThreatLevel.HIGH, ReflexAction.ALERT))
        prop.drainPendingReflexes()

        assertTrue(prop.drainPendingReflexes().isEmpty())
    }

    @Test
    fun suppressedReflexNotQueued() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        prop.suppressReflex("r1")
        prop.onLocalReflexFired(makeReflex("r1", ThreatLevel.HIGH, ReflexAction.ALERT))

        assertTrue(prop.drainPendingReflexes().isEmpty())
    }

    // ── Remote Reflex: Trust Gating ──

    @Test
    fun remoteReflexFromUntrustedIgnored() {
        val prop = ReflexPropagation(DeviceRole.GUARDIAN, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.HIGH, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, "unknown-device-id")
        assertTrue(result is RemoteReflexResponse.Ignored)
    }

    @Test
    fun remoteReflexFromTrustedAccepted() {
        val prop = ReflexPropagation(DeviceRole.GUARDIAN, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.HIGH, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId)
        assertTrue(result is RemoteReflexResponse.Act)
    }

    @Test
    fun duplicateReflexIgnored() {
        val prop = ReflexPropagation(DeviceRole.GUARDIAN, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.HIGH, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId)
        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId)
        assertTrue(result is RemoteReflexResponse.Ignored)
        assertEquals("Duplicate", (result as RemoteReflexResponse.Ignored).reason)
    }

    // ── Role-Aware Mapping: SENTINEL ──

    @Test
    fun sentinelEscalatesOnCritical() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.CRITICAL, ReflexAction.ALERT, DeviceRole.GUARDIAN)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.LOG_AND_ESCALATE, result.localAction)
    }

    @Test
    fun sentinelHonorsLockdown() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.MEDIUM, ReflexAction.LOCKDOWN, DeviceRole.CONTROLLER)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.LOCKDOWN, result.localAction)
    }

    @Test
    fun sentinelDefaultsToLogOnly() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.LOW, ReflexAction.ALERT, DeviceRole.GUARDIAN)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.LOG_ONLY, result.localAction)
    }

    // ── Role-Aware Mapping: CONTROLLER ──

    @Test
    fun controllerBlocksOnHigh() {
        val prop = ReflexPropagation(DeviceRole.CONTROLLER, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.HIGH, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.ALERT_AND_BLOCK, result.localAction)
    }

    @Test
    fun controllerAlertsOnMedium() {
        val prop = ReflexPropagation(DeviceRole.CONTROLLER, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.MEDIUM, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.ALERT, result.localAction)
    }

    // ── Role-Aware Mapping: GUARDIAN ──

    @Test
    fun guardianAlertsOnHigh() {
        val prop = ReflexPropagation(DeviceRole.GUARDIAN, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.HIGH, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.ALERT, result.localAction)
    }

    // ── Role-Aware Mapping: NOTIFIER ──

    @Test
    fun notifierHapticOnHigh() {
        val prop = ReflexPropagation(DeviceRole.HUB_WEAR, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.HIGH, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.HAPTIC_ALERT, result.localAction)
    }

    @Test
    fun notifierSilentOnMedium() {
        val prop = ReflexPropagation(DeviceRole.HUB_WEAR, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.MEDIUM, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.SILENT_ALERT, result.localAction)
    }

    @Test
    fun notifierIgnoresLow() {
        val prop = ReflexPropagation(DeviceRole.HUB_WEAR, trustGraph)
        val broadcast = makeBroadcast("r1", ThreatLevel.LOW, ReflexAction.ALERT, DeviceRole.HUB_HOME)

        val result = prop.onRemoteReflexReceived(broadcast, bobKeyStore.identity.deviceId) as RemoteReflexResponse.Act
        assertEquals(ReflexAction.IGNORE, result.localAction)
    }

    // ── Stats ──

    @Test
    fun statsTrackState() {
        val prop = ReflexPropagation(DeviceRole.HUB_HOME, trustGraph)
        prop.onLocalReflexFired(makeReflex("r1", ThreatLevel.HIGH, ReflexAction.ALERT))
        prop.suppressReflex("r2")

        val stats = prop.getStats()
        assertEquals(1, stats.totalPropagated)
        assertEquals(1, stats.pendingCount)
        assertEquals(1, stats.suppressedCount)
    }

    // ── Helpers ──

    private fun makeReflex(id: String, threat: ThreatLevel, action: ReflexAction) =
        LocalReflexOutcome(
            reflexId = id,
            reflexName = "test-reflex-$id",
            triggerModuleId = "module-1",
            triggerThreatLevel = threat,
            action = action
        )

    private fun makeBroadcast(id: String, threat: ThreatLevel, action: ReflexAction, role: DeviceRole) =
        ReflexBroadcast(
            reflexId = id,
            reflexName = "test-reflex-$id",
            triggerModuleId = "module-1",
            triggerThreatLevel = threat,
            action = action,
            sourceRole = role,
            timestamp = System.currentTimeMillis()
        )
}
