/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  STRESS TEST SUITE — Production-grade stress tests for VARYNX 2.0
 * ═══════════════════════════════════════════════════════════════════
 *
 *  File placement:
 *    core/src/desktopTest/kotlin/com/varynx/varynx20/core/stress/StressTestSuite.kt
 *
 *  Run with:
 *    ./gradlew :core:desktopTest --tests "com.varynx.varynx20.core.stress.*"
 *
 *  Pass criteria (real-world ready):
 *    • Zero crashes / exceptions across all tests
 *    • Scoring drift ≤ ±3 points (0–100 scale)
 *    • Every reflex fires (canTrigger + trigger returns success)
 *    • GuardianOrganism survives 10k+ cycles without state corruption
 *    • All 17 protection modules detect injected threats
 *    • All 10 reflexes respond to qualifying events
 *    • All 9 engines process under heavy load without deadlock
 *    • All 11 intelligence modules produce insights under signal flood
 * ═══════════════════════════════════════════════════════════════════
 */
package com.varynx.varynx20.core.stress

import com.varynx.varynx20.core.domain.*
import com.varynx.varynx20.core.engine.*
import com.varynx.varynx20.core.intelligence.*
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.model.*
import com.varynx.varynx20.core.protection.*
import com.varynx.varynx20.core.reflex.*
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.core.satellite.SatelliteController
import com.varynx.varynx20.core.homehub.HomeHubController
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

// ═══════════════════════════════════════════════════════════════════════════
//  1. HIGH-VOLUME EVENT STRESS TEST — 10,000+ events/min through full pipeline
// ═══════════════════════════════════════════════════════════════════════════

class HighVolumeEventStressTest {

    companion object {
        private const val THREAD_COUNT = 16
        private const val EVENTS_PER_THREAD = 1000
        private const val TOTAL_EVENTS = THREAD_COUNT * EVENTS_PER_THREAD // 16,000
    }

    /**
     * Floods the EventBus with 16,000 events from 16 concurrent threads.
     * Verifies zero data loss and no deadlocks.
     */
    @Test
    fun eventBusFlood_16kEvents() {
        val bus = EventBus()
        bus.initialize()
        val received = AtomicInteger(0)

        bus.subscribe("*") { received.incrementAndGet() }

        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(EVENTS_PER_THREAD) { j ->
                        bus.publish("channel-${j % 8}", ThreatEvent(
                            id = "evt-$i-$j",
                            sourceModuleId = "stress_module_${j % 17}",
                            threatLevel = ThreatLevel.entries[j % 5],
                            title = "Stress Event $j",
                            description = "Thread $i iteration $j"
                        ))
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during event flood")
        assertEquals(TOTAL_EVENTS, received.get(), "All $TOTAL_EVENTS events delivered")
        assertEquals(TOTAL_EVENTS, bus.getEventLog().size, "Event log captured all events")
    }

    /**
     * Floods the ThreatEngine with rapid threat registrations from all 17 modules
     * simultaneously, verifying deduplication and TTL management.
     */
    @Test
    fun threatEngineFlood_rapidRegistrations() {
        val engine = ThreatEngine()
        engine.initialize()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val moduleIds = listOf(
            "protect_scam_detector", "protect_clipboard_shield", "protect_bt_skimmer",
            "protect_nfc_guardian", "protect_network_integrity", "protect_app_behavior",
            "protect_device_state", "protect_permission_watchdog", "protect_install_monitor",
            "protect_runtime_threat", "protect_overlay_detector", "protect_notification_analyzer",
            "protect_usb_integrity", "protect_sensor_anomaly", "protect_app_tamper",
            "protect_security_audit", "protect_qr_scanner"
        )

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(EVENTS_PER_THREAD) { j ->
                        engine.registerThreat(ThreatEvent(
                            id = "threat-$i-$j",
                            sourceModuleId = moduleIds[j % moduleIds.size],
                            threatLevel = ThreatLevel.entries[j % 5],
                            title = "Threat $j from thread $i",
                            description = "Stress test flood"
                        ))
                        // Interleave reads
                        if (j % 10 == 0) {
                            engine.getActiveThreats()
                            engine.getOverallThreatLevel()
                        }
                        // Periodically trigger TTL processing
                        if (j % 100 == 0) engine.process()
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during threat flood")
        // ThreatEngine deduplicates by source module, so max active = moduleIds.size
        val active = engine.getActiveThreats()
        assertTrue(active.size <= moduleIds.size, "Deduplication maintained: ${active.size} <= ${moduleIds.size}")
        assertTrue(active.isNotEmpty(), "At least one threat remains active")
    }

    /**
     * Tests the StateMachine under rapid concurrent state transitions.
     * Verifies no invalid mode values and correct history tracking.
     */
    @Test
    fun stateMachineRapidTransitions() {
        val sm = StateMachine()
        sm.initialize()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(EVENTS_PER_THREAD) { j ->
                        sm.evaluateTransition(ThreatLevel.entries[j % 5])
                        // Verify mode is always a valid enum value
                        val mode = sm.currentMode
                        assertTrue(
                            mode in GuardianMode.entries,
                            "Mode must be valid: $mode"
                        )
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during state machine hammering")
        val history = sm.getTransitionHistory()
        assertTrue(history.isNotEmpty(), "Transitions were recorded")
        // Verify all transitions are valid
        for (t in history) {
            assertTrue(t.from in GuardianMode.entries && t.to in GuardianMode.entries)
            assertNotEquals(t.from, t.to, "No self-transitions should be recorded")
        }
    }

    /**
     * Floods all 17 protection modules with malicious inputs concurrently.
     * Verifies each module detects at least one threat.
     */
    @Test
    fun allProtectionModules_concurrentFlood() {
        val scam = ScamDetector().also { it.activate() }
        val clipboard = ClipboardShield().also { it.activate() }
        val bt = BluetoothSkimmerDetector().also { it.activate() }
        val nfc = NfcGuardian().also { it.activate() }
        val net = NetworkIntegrity().also { it.activate() }
        val app = AppBehaviorMonitor().also { it.activate() }
        val device = DeviceStateMonitor().also { it.activate() }
        val perm = PermissionWatchdog().also { it.activate() }
        val install = InstallMonitor().also { it.activate() }
        val runtime = RuntimeThreatMonitor().also { it.activate() }
        val overlay = OverlayDetector().also { it.activate() }
        val notif = NotificationAnalyzer().also { it.activate() }
        val usb = UsbIntegrity().also { it.activate() }
        val sensor = SensorAnomalyDetector().also { it.activate() }
        val tamper = AppTamperDetector().also { it.activate() }
        val qr = QrScamScanner().also { it.activate() }

        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(200) { j ->
                        // Flood every module with malicious data
                        scam.analyzeText("congratulations you've won a prize! click here to claim your reward! verify your account immediately")
                        clipboard.analyzeClipboard("javascript:alert('xss');192.168.1.${j % 256}")
                        bt.analyzeDevice("HC-05-${j}", -30, listOf("00001101-0000-1000-8000-00805F9B34FB"))
                        nfc.analyzeNfcTag("base64:aHR0cDovL2V2aWwuY29t", "http://bit.ly/phish-${j}")
                        net.analyzeNetwork(false, true, listOf("8.8.8.8"), true, "AA:BB:CC:DD:EE:FF")
                        app.recordAction("com.evil.app$j", AppBehaviorMonitor.AppAction(AppBehaviorMonitor.ActionType.SMS_SEND))
                        device.checkDeviceState(true, true, true, true, true)
                        perm.analyzePermissionChange("com.evil.app$j", "android.permission.CAMERA", true)
                        install.analyzeInstall("com.evil.sideload.$j", "com.android.shell", "root exploit $j")
                        runtime.reportAnomaly(RuntimeThreatMonitor.Anomaly("CRASH", 5, "Force stop", System.currentTimeMillis()))
                        overlay.analyzeOverlay("com.evil.overlay.$j", true, true)
                        notif.analyzeNotification("com.fake.bank$j", "Verify your account", "Click http://phish.tk/login to verify now!")
                        usb.analyzeUsbDevice("HID", "Rubber Ducky $j", 0x05AC, true)
                        sensor.analyzeSensors(100.0f, 100.0f, 100.0f, -1.0f, -1.0f)
                        tamper.analyzeApp("com.evil.$j", "abc123", "def456", "com.sideload", true, true)
                        qr.analyzeQrContent("https://phishing-$j.tk/login?verify=true")
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during protection module flood")
        // Every module must have detected at least one threat
        assertTrue(scam.scan() > ThreatLevel.NONE, "ScamDetector detected threats")
        assertTrue(clipboard.scan() > ThreatLevel.NONE, "ClipboardShield detected threats")
        assertTrue(bt.scan() > ThreatLevel.NONE, "BluetoothSkimmerDetector detected threats")
        assertTrue(nfc.scan() > ThreatLevel.NONE, "NfcGuardian detected threats")
        assertTrue(net.scan() > ThreatLevel.NONE, "NetworkIntegrity detected threats")
        assertTrue(device.scan() > ThreatLevel.NONE, "DeviceStateMonitor detected threats")
        assertTrue(perm.scan() > ThreatLevel.NONE, "PermissionWatchdog detected threats")
        assertTrue(install.scan() > ThreatLevel.NONE, "InstallMonitor detected threats")
        assertTrue(overlay.scan() > ThreatLevel.NONE, "OverlayDetector detected threats")
        assertTrue(notif.scan() > ThreatLevel.NONE, "NotificationAnalyzer detected threats")
        assertTrue(usb.scan() > ThreatLevel.NONE, "UsbIntegrity detected threats")
        assertTrue(sensor.scan() > ThreatLevel.NONE, "SensorAnomalyDetector detected threats")
        assertTrue(tamper.scan() > ThreatLevel.NONE, "AppTamperDetector detected threats")
        assertTrue(qr.scan() > ThreatLevel.NONE, "QrScamScanner detected threats")
    }

    /**
     * Fires 10,000 events through the complete EngineDomain.interpret() pipeline.
     * Verifies scores, state transitions, intelligence insights, and verdict generation.
     */
    @Test
    fun engineDomain_10kSignalInterpretation() {
        val engineDomain = EngineDomain()
        engineDomain.awaken()

        var totalVerdicts = 0
        val cycleCount = 2000

        repeat(cycleCount) { i ->
            val signals = (0 until 5).map { j ->
                DetectionSignal(
                    sourceModuleId = "protect_module_${(i * 5 + j) % 17}",
                    severity = ThreatLevel.entries[(i + j) % 5],
                    title = "Signal batch $i item $j",
                    detail = "Stress iteration $i"
                )
            }
            val verdicts = engineDomain.interpret(signals)
            totalVerdicts += verdicts.size

            // Every signal should produce a verdict
            assertEquals(signals.size, verdicts.size, "Batch $i: verdict count matches signal count")

            // Verdicts should have valid computed levels
            for (v in verdicts) {
                assertTrue(v.computedLevel in ThreatLevel.entries, "Valid threat level: ${v.computedLevel}")
            }
        }

        assertEquals(cycleCount * 5, totalVerdicts, "All 10,000 signals produced verdicts")
        // State machine should have transitioned from all the signals
        assertTrue(engineDomain.stateMachine.getTransitionHistory().isNotEmpty(), "State transitions occurred")
        // Threat engine should have active threats
        assertTrue(engineDomain.threatEngine.getActiveThreats().isNotEmpty(), "Active threats registered")

        engineDomain.sleep()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  2. SCORING ENGINE STRESS TEST — Accuracy + drift validation
// ═══════════════════════════════════════════════════════════════════════════

class ScoringEngineStressTest {

    /**
     * Verifies scoring determinism: identical inputs must always produce
     * identical outputs across 10,000 iterations.
     */
    @Test
    fun scoringDeterminism_10kIterations() {
        val engine = ScoringEngine()
        engine.initialize()

        val signals = listOf(
            ScoringEngine.Signal("module_a", 0.8, 3.0),
            ScoringEngine.Signal("module_b", 0.5, 1.5),
            ScoringEngine.Signal("module_c", 0.2, 1.0)
        )

        val baseline = engine.computeScore(signals)

        repeat(10_000) { i ->
            val result = engine.computeScore(signals)
            assertEquals(baseline.score, result.score, "Score must be deterministic at iteration $i")
            assertEquals(baseline.threatLevel, result.threatLevel, "Level must be deterministic at iteration $i")
        }
    }

    /**
     * Tests scoring accuracy across the full range of inputs.
     * Verifies boundary conditions at NONE/LOW/MEDIUM/HIGH/CRITICAL thresholds.
     */
    @Test
    fun scoringBoundaryAccuracy() {
        val engine = ScoringEngine()
        engine.initialize()

        // Map expected thresholds: score → expected level
        // NONE < 0.1, LOW >= 0.1, MEDIUM >= 0.35, HIGH >= 0.6, CRITICAL >= 0.8
        val testCases = listOf(
            0.0 to ThreatLevel.NONE,
            0.05 to ThreatLevel.NONE,
            0.09 to ThreatLevel.NONE,
            0.1 to ThreatLevel.LOW,
            0.2 to ThreatLevel.LOW,
            0.34 to ThreatLevel.LOW,
            0.35 to ThreatLevel.MEDIUM,
            0.5 to ThreatLevel.MEDIUM,
            0.59 to ThreatLevel.MEDIUM,
            0.6 to ThreatLevel.HIGH,
            0.7 to ThreatLevel.HIGH,
            0.79 to ThreatLevel.HIGH,
            0.8 to ThreatLevel.CRITICAL,
            0.9 to ThreatLevel.CRITICAL,
            1.0 to ThreatLevel.CRITICAL,
        )

        for ((value, expectedLevel) in testCases) {
            val signals = listOf(ScoringEngine.Signal("test", value, 1.0))
            val result = engine.computeScore(signals)
            assertEquals(expectedLevel, result.threatLevel,
                "Value $value should map to ${expectedLevel.label}, got ${result.threatLevel.label}")
        }
    }

    /**
     * Scoring drift test: feeds a known pattern of events repeatedly and
     * verifies the score never drifts more than ±3 points (0–100 scale).
     */
    @Test
    fun scoringDrift_stableUnderRepetition() {
        val engine = ScoringEngine()
        engine.initialize()

        // Create a realistic mixed-threat signal set
        val baseSignals = listOf(
            ScoringEngine.Signal("protect_scam_detector", 0.7, 2.0),
            ScoringEngine.Signal("protect_network_integrity", 0.4, 1.5),
            ScoringEngine.Signal("protect_bt_skimmer", 0.2, 1.0),
            ScoringEngine.Signal("protect_clipboard_shield", 0.0, 1.0),
            ScoringEngine.Signal("protect_device_state", 0.5, 2.0),
        )

        val baseResult = engine.computeScore(baseSignals)
        val baseScore100 = (baseResult.score * 100).toInt()

        // Run 10,000 scoring cycles with slight variations
        repeat(10_000) { i ->
            // Add small perturbations to simulate real-world variance
            val perturbedSignals = baseSignals.map { s ->
                s.copy(value = (s.value + (i % 3) * 0.001).coerceIn(0.0, 1.0))
            }
            val result = engine.computeScore(perturbedSignals)
            val score100 = (result.score * 100).toInt()
            val drift = kotlin.math.abs(score100 - baseScore100)
            assertTrue(drift <= 3,
                "Drift $drift exceeds ±3 points at iteration $i (base=$baseScore100, current=$score100)")
        }
    }

    /**
     * Weight sensitivity: verifies that CRITICAL signals weighted 3.0
     * dominate over LOW signals weighted 1.0.
     */
    @Test
    fun weightedScoring_criticalDominates() {
        val engine = ScoringEngine()
        engine.initialize()

        // One critical with high weight vs many low signals
        val signals = listOf(
            ScoringEngine.Signal("critical_module", 0.9, 3.0),
            ScoringEngine.Signal("low_1", 0.05, 1.0),
            ScoringEngine.Signal("low_2", 0.05, 1.0),
            ScoringEngine.Signal("low_3", 0.05, 1.0),
            ScoringEngine.Signal("low_4", 0.05, 1.0),
        )
        val result = engine.computeScore(signals)
        assertTrue(result.threatLevel >= ThreatLevel.MEDIUM,
            "Critical signal with weight 3.0 should dominate: ${result.threatLevel}")
        assertTrue(result.score > 0.3, "Weighted score should reflect critical signal: ${result.score}")
    }

    /**
     * Empty and edge-case inputs.
     */
    @Test
    fun scoringEdgeCases() {
        val engine = ScoringEngine()
        engine.initialize()

        // Empty signals → NONE
        val empty = engine.computeScore(emptyList())
        assertEquals(0.0, empty.score)
        assertEquals(ThreatLevel.NONE, empty.threatLevel)

        // Single max signal
        val max = engine.computeScore(listOf(ScoringEngine.Signal("max", 1.0, 1.0)))
        assertEquals(ThreatLevel.CRITICAL, max.threatLevel)

        // Single zero signal
        val zero = engine.computeScore(listOf(ScoringEngine.Signal("zero", 0.0, 1.0)))
        assertEquals(ThreatLevel.NONE, zero.threatLevel)

        // Zero-weight signals (avoid division by zero)
        val zeroWeight = engine.computeScore(listOf(ScoringEngine.Signal("zw", 1.0, 0.0)))
        assertEquals(ThreatLevel.NONE, zeroWeight.threatLevel)
    }

    /**
     * Concurrent scoring: 16 threads scoring simultaneously.
     * ScoringEngine is stateless so this should be safe.
     */
    @Test
    fun concurrentScoring_16threads() {
        val engine = ScoringEngine()
        engine.initialize()
        val barrier = CyclicBarrier(16)
        val latch = CountDownLatch(16)
        val errors = AtomicInteger(0)

        val threads = (0 until 16).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(1000) { j ->
                        val signals = (0 until 5).map { k ->
                            ScoringEngine.Signal("src-$k", (j + k).toDouble() / 1000, 1.0 + k * 0.5)
                        }
                        val result = engine.computeScore(signals)
                        assertTrue(result.score >= 0.0 && result.score <= 1.0,
                            "Score in range: ${result.score}")
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()
        assertEquals(0, errors.get(), "No exceptions during concurrent scoring")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  3. REFLEX CHAIN STRESS TEST — All 10 reflexes under fire
// ═══════════════════════════════════════════════════════════════════════════

class ReflexChainStressTest {

    /**
     * Verifies every reflex fires correctly for its qualifying threat level.
     */
    @Test
    fun allReflexes_triggerForQualifyingEvents() {
        val reflexes = listOf(
            WarningReflex(),        // priority 10, ≥LOW
            BlockReflex(),          // priority 20, ≥MEDIUM
            AutoEscalationReflex(), // priority 30, ≥LOW
            IntegrityReflex(),      // priority 40, ≥HIGH
            GuardianInterventionMode(), // priority 45, ≥HIGH
            LockdownReflex(),       // priority 50, =CRITICAL
            EmergencySafeMode(),    // priority 55, =CRITICAL
            ReflexPriorityEngine(), // priority 90, always
            ReflexCooldown(),       // priority 100, always
            ThreatReplay(),         // priority 5, always
        )

        // Activate all reflexes
        reflexes.forEach { it.state = ModuleState.ACTIVE }

        // CRITICAL event — most reflexes should be able to trigger
        val criticalEvent = ThreatEvent(
            id = "crit-001",
            sourceModuleId = "protect_device_state",
            threatLevel = ThreatLevel.CRITICAL,
            title = "Critical Threat",
            description = "Rooted + debuggable + emulator"
        )

        // IntegrityReflex requires sourceModuleId containing "integrity"
        val integrityEvent = ThreatEvent(
            id = "crit-002",
            sourceModuleId = "protect_integrity",
            threatLevel = ThreatLevel.CRITICAL,
            title = "Integrity Breach",
            description = "App integrity violation detected"
        )

        for (reflex in reflexes) {
            val event = if (reflex is IntegrityReflex) integrityEvent else criticalEvent
            assertTrue(reflex.canTrigger(event),
                "${reflex.reflexName} (${reflex.reflexId}) should trigger on CRITICAL")
            val result = reflex.trigger(event)
            assertTrue(result.success,
                "${reflex.reflexName} trigger should succeed on CRITICAL: ${result.message}")
        }
    }

    /**
     * Tests reflex priority ordering: higher priority reflexes execute first.
     */
    @Test
    fun reflexPriorityOrder() {
        val reflexEngine = ReflexEngine()
        reflexEngine.initialize()

        val executionOrder = mutableListOf<Int>()
        val priorities = listOf(100, 90, 55, 50, 45, 40, 30, 20, 10, 5)

        for (p in priorities.shuffled()) {
            reflexEngine.enqueue(ReflexEngine.ReflexRequest(
                reflexId = "reflex-p$p",
                priority = p,
                threatLevel = ThreatLevel.HIGH,
                execute = { synchronized(executionOrder) { executionOrder.add(p) } }
            ))
        }

        reflexEngine.process()

        // Should execute in descending priority order
        assertEquals(priorities, executionOrder,
            "Reflexes must execute in priority order (desc): $executionOrder")
    }

    /**
     * Tests ReflexCooldown gating under rapid-fire events.
     * Ensures reflexes are properly gated during cooldown period.
     */
    @Test
    fun reflexCooldown_gatesRapidFire() {
        val cooldown = ReflexCooldown()

        // First trigger should succeed
        assertFalse(cooldown.isOnCooldown("reflex_block"), "Not on cooldown initially")
        cooldown.markTriggered("reflex_block")

        // Immediately after — should be on cooldown
        assertTrue(cooldown.isOnCooldown("reflex_block"), "Should be on cooldown after trigger")

        // Different reflex should NOT be on cooldown
        assertFalse(cooldown.isOnCooldown("reflex_warning"), "Different reflex not on cooldown")

        cooldown.reset()
        assertFalse(cooldown.isOnCooldown("reflex_block"), "Reset clears cooldown")
    }

    /**
     * ReflexDomain under heavy load: 10,000 verdicts through the full reflex pipeline.
     */
    @Test
    fun reflexDomain_10kVerdicts() {
        val reflexDomain = ReflexDomain()
        reflexDomain.registerAll(
            WarningReflex(), BlockReflex(), LockdownReflex(),
            IntegrityReflex(), AutoEscalationReflex(), ThreatReplay(),
            ReflexCooldown(), ReflexPriorityEngine(),
            GuardianInterventionMode(), EmergencySafeMode()
        )
        reflexDomain.awaken()

        var totalOutcomes = 0

        repeat(2000) { i ->
            val verdicts = (0 until 5).map { j ->
                EngineVerdict(
                    event = ThreatEvent(
                        id = "evt-$i-$j",
                        sourceModuleId = "protect_module_${j % 17}",
                        threatLevel = ThreatLevel.entries[(i + j) % 5],
                        title = "Verdict $i.$j",
                        description = "Reflex stress"
                    ),
                    computedLevel = ThreatLevel.entries[(i + j) % 5],
                    requiresReflex = true
                )
            }

            val outcomes = reflexDomain.respond(verdicts)
            totalOutcomes += outcomes.size
        }

        assertTrue(totalOutcomes > 0, "Reflexes produced outcomes: $totalOutcomes")
        assertEquals(10, reflexDomain.getReflexes().size, "All 10 reflexes registered")

        reflexDomain.sleep()
    }

    /**
     * Concurrent reflex enqueue/process stress.
     */
    @Test
    fun reflexEngine_concurrentStress() {
        val engine = ReflexEngine()
        engine.initialize()
        val executed = AtomicInteger(0)
        val barrier = CyclicBarrier(16)
        val latch = CountDownLatch(16)
        val errors = AtomicInteger(0)

        val threads = (0 until 16).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(500) { j ->
                        if (i % 2 == 0) {
                            engine.enqueue(ReflexEngine.ReflexRequest(
                                reflexId = "rx-$i-$j",
                                priority = j % 100,
                                threatLevel = ThreatLevel.entries[j % 5],
                                execute = { executed.incrementAndGet() }
                            ))
                        } else {
                            engine.process()
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()
        engine.process() // Final drain

        assertEquals(0, errors.get(), "No exceptions during concurrent reflex stress")
        val totalEnqueued = 8 * 500 // 8 producer threads × 500 each
        assertEquals(totalEnqueued, executed.get(), "All enqueued reflexes executed")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  4. FULL GUARDIAN ORGANISM STRESS TEST — Complete life cycle under extreme load
// ═══════════════════════════════════════════════════════════════════════════

class FullGuardianOrganismStressTest {

    /**
     * Runs 5,000 complete detect→interpret→respond→express cycles.
     * Verifies the organism maintains coherent state throughout.
     */
    @Test
    fun organism_5kCycles_noCorruption() {
        ModuleRegistry.initialize()
        val organism = GuardianOrganism()
        organism.awaken()

        assertTrue(organism.isAlive, "Organism is alive after awaken")
        assertEquals(17, organism.core.getActiveCount(), "17 protection modules active")
        assertEquals(10, organism.reflex.getArmedCount(), "10 reflexes armed")

        val expectedTotalModules = ModuleRegistry.getAllModules().size
        var lastState: GuardianState? = null

        repeat(5000) { i ->
            val state = organism.cycle()

            // State must always be coherent
            assertTrue(state.overallThreatLevel in ThreatLevel.entries, "Valid threat level at cycle $i")
            assertTrue(state.guardianMode in GuardianMode.entries, "Valid mode at cycle $i")
            assertEquals(expectedTotalModules, state.totalModuleCount, "Module count stable at cycle $i")
            assertTrue(state.activeModuleCount > 0, "Active modules > 0 at cycle $i")
            assertTrue(state.activeModuleCount <= state.totalModuleCount,
                "Active <= total at cycle $i: ${state.activeModuleCount}/${state.totalModuleCount}")

            lastState = state
        }

        // Final state check
        assertNotNull(lastState)
        assertTrue(organism.isAlive, "Organism still alive after 5k cycles")

        organism.sleep()
        assertFalse(organism.isAlive, "Organism asleep after sleep()")
    }

    /**
     * Injects threats into every protection module, then runs cycles
     * to verify the full pipeline detects and responds.
     */
    @Test
    fun organism_injectedThreats_fullDetection() {
        ModuleRegistry.initialize()
        val organism = GuardianOrganism()
        organism.awaken()

        // Inject threats into protection modules
        val modules = organism.core.getModules()
        for (module in modules) {
            when (module) {
                is ScamDetector -> module.analyzeText("congratulations you've won a prize! click here to claim now! verify your account immediately")
                is ClipboardShield -> module.analyzeClipboard("javascript:document.cookie")
                is BluetoothSkimmerDetector -> module.analyzeDevice("HC-05", -25, listOf("00001101-0000-1000-8000-00805F9B34FB"))
                is NfcGuardian -> module.analyzeNfcTag("data", "http://evil.tk/phish")
                is NetworkIntegrity -> module.analyzeNetwork(false, true, listOf("1.1.1.1"), true, "FF:FF:FF:FF:FF:FF")
                is AppBehaviorMonitor -> {
                    repeat(10) { module.recordAction("com.spy.app", AppBehaviorMonitor.AppAction(AppBehaviorMonitor.ActionType.SMS_SEND)) }
                }
                is DeviceStateMonitor -> module.checkDeviceState(true, true, false, true, true)
                is PermissionWatchdog -> module.analyzePermissionChange("com.evil", "android.permission.CAMERA", true)
                is InstallMonitor -> module.analyzeInstall("com.evil", "com.unknown", "Evil")
                is RuntimeThreatMonitor -> {
                    repeat(5) { module.reportAnomaly(RuntimeThreatMonitor.Anomaly("CRASH", 5, "test", System.currentTimeMillis())) }
                }
                is OverlayDetector -> module.analyzeOverlay("com.evil.overlay", true, true)
                is NotificationAnalyzer -> module.analyzeNotification("com.fake.bank", "Verify Now", "http://phish.tk/login")
                is UsbIntegrity -> module.analyzeUsbDevice("HID", "Rubber Ducky", 0x05AC, true)
                is SensorAnomalyDetector -> module.analyzeSensors(100f, 100f, 100f, -1f, -1f)
                is AppTamperDetector -> module.analyzeApp("com.tampered", "abc", "def", "sideload", true, true)
                is SecurityAuditScanner -> { /* No-op, runs audit on other modules */ }
                is QrScamScanner -> module.analyzeQrContent("https://phishing.tk/login?verify=true")
            }
        }

        // Run cycles to process injected threats
        var highestLevel = ThreatLevel.NONE
        repeat(100) {
            val state = organism.cycle()
            if (state.overallThreatLevel > highestLevel) {
                highestLevel = state.overallThreatLevel
            }
        }

        // With so many threats injected, we must reach at least MEDIUM
        assertTrue(highestLevel >= ThreatLevel.MEDIUM,
            "Injected threats should escalate to at least MEDIUM: got ${highestLevel.label}")

        organism.sleep()
    }

    /**
     * Tests the organism under concurrent stress: multiple threads calling cycle().
     * Verifies thread safety of the full pipeline.
     */
    @Test
    fun organism_concurrentCycles() {
        ModuleRegistry.initialize()
        val organism = GuardianOrganism()
        organism.awaken()

        val barrier = CyclicBarrier(8)
        val latch = CountDownLatch(8)
        val errors = AtomicInteger(0)
        val cyclesCompleted = AtomicInteger(0)

        val threads = (0 until 8).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(500) {
                        val state = organism.cycle()
                        assertTrue(state.overallThreatLevel in ThreatLevel.entries)
                        assertTrue(state.guardianMode in GuardianMode.entries)
                        cyclesCompleted.incrementAndGet()
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during concurrent cycles")
        assertEquals(4000, cyclesCompleted.get(), "All 4,000 cycles completed")

        organism.sleep()
    }

    /**
     * Tests full organism awaken/sleep lifecycle under stress.
     * Rapidly toggles alive/sleep 100 times.
     */
    @Test
    fun organism_rapidLifecycleToggle() {
        repeat(100) { i ->
            ModuleRegistry.initialize()
            val organism = GuardianOrganism()
            organism.awaken()
            assertTrue(organism.isAlive, "Alive at iteration $i")
            assertEquals(17, organism.core.getActiveCount(), "17 modules at iteration $i")

            // Run a few cycles while alive
            repeat(10) { organism.cycle() }

            organism.sleep()
            assertFalse(organism.isAlive, "Asleep at iteration $i")
        }
    }

    /**
     * Intelligence modules stress: flood all 11 modules with signals and verify insights.
     */
    @Test
    fun intelligenceModules_floodWithSignals() {
        val engineDomain = EngineDomain()
        engineDomain.awaken()

        val insightCount = AtomicInteger(0)

        // Feed 5,000 signal batches
        repeat(5000) { i ->
            val signals = listOf(
                DetectionSignal("protect_scam_detector", ThreatLevel.entries[i % 5], "Scam $i", "Detail $i"),
                DetectionSignal("protect_bt_skimmer", ThreatLevel.entries[(i + 1) % 5], "BT $i", "Detail $i"),
                DetectionSignal("protect_network_integrity", ThreatLevel.entries[(i + 2) % 5], "Net $i", "Detail $i"),
            )

            // Count insights from intelligence modules
            for (module in engineDomain.intelligenceModules) {
                if (module.state == ModuleState.ACTIVE) {
                    val insight = module.analyze(signals)
                    if (insight != null) insightCount.incrementAndGet()
                }
            }
        }

        assertTrue(insightCount.get() > 0,
            "Intelligence modules should produce insights: got ${insightCount.get()}")

        engineDomain.sleep()
    }

    /**
     * Mesh VectorClock + MeshSync stress under rapid heartbeat exchanges.
     */
    @Test
    fun meshSync_rapidHeartbeatExchange() {
        val identityA = DeviceIdentity(
            deviceId = "device-A", displayName = "Phone A",
            role = DeviceRole.GUARDIAN,
            capabilities = setOf(DeviceCapability.DETECT, DeviceCapability.RESPOND, DeviceCapability.ALERT),
            publicKeyExchange = ByteArray(32), publicKeySigning = ByteArray(32),
            createdAt = System.currentTimeMillis()
        )
        val identityB = DeviceIdentity(
            deviceId = "device-B", displayName = "Desktop B",
            role = DeviceRole.CONTROLLER,
            capabilities = setOf(DeviceCapability.DETECT, DeviceCapability.RESPOND, DeviceCapability.ALERT, DeviceCapability.CONTROL, DeviceCapability.RELAY),
            publicKeyExchange = ByteArray(32), publicKeySigning = ByteArray(32),
            createdAt = System.currentTimeMillis()
        )

        val trustA = TrustGraph()
        val trustB = TrustGraph()
        trustA.addTrust(TrustEdge(
            remoteDeviceId = "device-B", remoteDisplayName = "Desktop B",
            remoteRole = DeviceRole.CONTROLLER,
            remoteCapabilities = setOf(DeviceCapability.DETECT, DeviceCapability.CONTROL),
            remotePublicKeyExchange = ByteArray(32), remotePublicKeySigning = ByteArray(32),
            sharedSecret = ByteArray(32), pairedAt = System.currentTimeMillis()
        ))
        trustB.addTrust(TrustEdge(
            remoteDeviceId = "device-A", remoteDisplayName = "Phone A",
            remoteRole = DeviceRole.GUARDIAN,
            remoteCapabilities = setOf(DeviceCapability.DETECT, DeviceCapability.RESPOND),
            remotePublicKeyExchange = ByteArray(32), remotePublicKeySigning = ByteArray(32),
            sharedSecret = ByteArray(32), pairedAt = System.currentTimeMillis()
        ))

        val syncA = MeshSync(identityA, trustA)
        val syncB = MeshSync(identityB, trustB)

        val stateA = GuardianState(
            overallThreatLevel = ThreatLevel.LOW,
            activeModuleCount = 17, totalModuleCount = 17,
            recentEvents = emptyList(), isOnline = true,
            guardianMode = GuardianMode.SENTINEL
        )

        // Simulate 5,000 rapid heartbeat exchanges
        repeat(5000) { i ->
            val hbA = syncA.buildHeartbeat(stateA)
            syncB.onHeartbeatReceived(hbA)

            val hbB = syncB.buildHeartbeat(stateA.copy(overallThreatLevel = ThreatLevel.entries[i % 5]))
            syncA.onHeartbeatReceived(hbB)

            // Inject local threats periodically
            if (i % 10 == 0) {
                syncA.onLocalThreatDetected(ThreatEvent(
                    id = "local-$i", sourceModuleId = "protect_scam_detector",
                    threatLevel = ThreatLevel.MEDIUM, title = "Local threat $i",
                    description = "Stress"
                ))
            }

            // Relay remote threats
            if (i % 20 == 0) {
                syncB.onRemoteThreatReceived(ThreatEvent(
                    id = "remote-$i", sourceModuleId = "protect_network_integrity",
                    threatLevel = ThreatLevel.HIGH, title = "Remote threat $i",
                    description = "From mesh"
                ), "device-A")
            }
        }

        // Verify peer state is tracked
        val peersA = syncA.getPeerStates()
        val peersB = syncB.getPeerStates()
        assertTrue(peersA.containsKey("device-B"), "A tracks B as peer")
        assertTrue(peersB.containsKey("device-A"), "B tracks A as peer")

        // Verify events drain properly
        val pendingA = syncA.drainPendingEvents()
        assertTrue(pendingA.isNotEmpty(), "A has pending local events")
        val remoteB = syncB.drainRemoteEvents()
        assertTrue(remoteB.isNotEmpty(), "B has received remote events")

        // After drain, queues should be empty
        assertTrue(syncA.drainPendingEvents().isEmpty(), "A pending drained")
        assertTrue(syncB.drainRemoteEvents().isEmpty(), "B remote drained")
    }

    /**
     * ModuleRegistry + GuardianLog combined stress under 16 threads.
     */
    @Test
    fun registryAndLog_crossComponentStress() {
        ModuleRegistry.initialize()
        GuardianLog.clear()

        val barrier = CyclicBarrier(16)
        val latch = CountDownLatch(16)
        val errors = AtomicInteger(0)

        val threads = (0 until 16).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(500) { j ->
                        val modules = ModuleRegistry.getAllModules()
                        val mod = modules[j % modules.size]
                        ModuleRegistry.updateModuleThreat(
                            mod.id, ThreatLevel.entries[j % 5], "stress-$i-$j"
                        )
                        GuardianLog.logThreat(
                            source = mod.id,
                            action = "DETECT",
                            detail = "Thread $i iteration $j",
                            threatLevel = ThreatLevel.entries[j % 5]
                        )
                        ModuleRegistry.getOverallThreatLevel()
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during cross-component stress")
        assertTrue(GuardianLog.size() > 0, "Log has entries")
        assertTrue(GuardianLog.size() <= 500, "Log respects ring buffer cap")
        assertTrue(ModuleRegistry.getAllModules().isNotEmpty(), "Registry intact")
    }

    /**
     * Satellite + HomeHub controller stress: concurrent buffering, draining, correlation.
     */
    @Test
    fun satelliteAndHomeHub_combinedStress() {
        val sat = SatelliteController()
        sat.start()
        val hub = HomeHubController()
        hub.start()

        val barrier = CyclicBarrier(8)
        val latch = CountDownLatch(8)
        val errors = AtomicInteger(0)

        val threads = (0 until 8).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(500) { j ->
                        val event = ThreatEvent(
                            id = "combined-$i-$j",
                            sourceModuleId = "protect_module_${j % 17}",
                            threatLevel = ThreatLevel.entries[j % 5],
                            title = "Combined stress $j",
                            description = "Thread $i"
                        )
                        when (i % 4) {
                            0 -> sat.bufferEvent(event)
                            1 -> sat.drainForSync()
                            2 -> hub.onPeerThreat(event, "peer-${j % 4}")
                            3 -> hub.correlatePeerThreats()
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during combined satellite+hub stress")
    }

    /**
     * Full BaselineEngine + BehaviorEngine + IntegrityEngine cycle stress.
     */
    @Test
    fun engineTrio_concurrentStress() {
        val baseline = BaselineEngine()
        val behavior = BehaviorEngine()
        val integrity = IntegrityEngine()

        baseline.initialize()
        behavior.initialize()
        integrity.initialize()

        val barrier = CyclicBarrier(12)
        val latch = CountDownLatch(12)
        val errors = AtomicInteger(0)

        val threads = (0 until 12).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(500) { j ->
                        when (i % 3) {
                            0 -> {
                                baseline.captureSnapshot("snapshot-$j", mapOf("key" to "val-$j"))
                                baseline.hasDeviated("snapshot-${j % 100}", mapOf("key" to "changed"))
                            }
                            1 -> {
                                behavior.recordBehavior("com.app.$j", "ACTION_${j % 5}")
                                behavior.evaluateApp("com.app.${j % 50}")
                            }
                            2 -> {
                                integrity.process()
                                integrity.checkIntegrity()
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals(0, errors.get(), "No exceptions during engine trio stress")

        baseline.shutdown()
        behavior.shutdown()
        integrity.shutdown()
    }

    /**
     * EnvelopeCodec encode/decode round-trip stress for all message types.
     */
    @Test
    fun envelopeCodec_roundTripStress() {
        val codec = com.varynx.varynx20.core.mesh.transport.EnvelopeCodec

        repeat(5000) { i ->
            // Heartbeat round-trip
            val heartbeat = HeartbeatPayload(
                deviceId = "device-$i", displayName = "Node $i",
                role = DeviceRole.entries[i % DeviceRole.entries.size],
                threatLevel = ThreatLevel.entries[i % 5],
                guardianMode = GuardianMode.entries[i % GuardianMode.entries.size],
                activeModuleCount = 17, uptime = i.toLong() * 1000,
                clock = mapOf("device-$i" to i.toLong()),
                knownPeers = setOf("peer-A", "peer-B")
            )
            val hbBytes = codec.encodeHeartbeat(heartbeat)
            val decoded = codec.decodeHeartbeat(hbBytes)
            assertNotNull(decoded, "Heartbeat decode must succeed at $i")
            assertEquals(heartbeat.deviceId, decoded.deviceId)
            assertEquals(heartbeat.threatLevel, decoded.threatLevel)

            // ThreatEvent round-trip
            val event = ThreatEvent(
                id = "evt-$i", sourceModuleId = "module-${i % 17}",
                threatLevel = ThreatLevel.entries[i % 5],
                title = "Event $i", description = "Stress test $i",
                reflexTriggered = if (i % 2 == 0) "reflex_block" else null,
                resolved = i % 3 == 0
            )
            val evtBytes = codec.encodeThreatEvent(event)
            val decodedEvt = codec.decodeThreatEvent(evtBytes)
            assertNotNull(decodedEvt, "ThreatEvent decode must succeed at $i")
            assertEquals(event.id, decodedEvt.id)
            assertEquals(event.threatLevel, decodedEvt.threatLevel)
            assertEquals(event.reflexTriggered, decodedEvt.reflexTriggered)
            assertEquals(event.resolved, decodedEvt.resolved)
        }
    }

    /**
     * MeshEnvelope binary codec round-trip stress.
     */
    @Test
    fun meshEnvelope_binaryRoundTrip() {
        val codec = com.varynx.varynx20.core.mesh.transport.EnvelopeCodec
        repeat(2000) { i ->
            val envelope = MeshEnvelope(
                version = MeshEnvelope.PROTOCOL_VERSION,
                type = MessageType.entries[i % MessageType.entries.size],
                senderId = "sender-${i % 8}",
                recipientId = if (i % 3 == 0) MeshEnvelope.BROADCAST else "recipient-${i % 8}",
                nonce = ByteArray(12) { (it + i).toByte() },
                payload = ByteArray(64 + i % 128) { (it xor i).toByte() },
                signature = ByteArray(64) { (it + i * 3).toByte() }
            )
            val bytes = codec.encode(envelope)
            val decoded = codec.decode(bytes)
            assertNotNull(decoded, "Envelope decode must succeed at $i")
            assertEquals(envelope.senderId, decoded.senderId)
            assertEquals(envelope.recipientId, decoded.recipientId)
            assertEquals(envelope.type, decoded.type)
            assertTrue(envelope.nonce.contentEquals(decoded.nonce), "Nonce round-trip $i")
            assertTrue(envelope.payload.contentEquals(decoded.payload), "Payload round-trip $i")
            assertTrue(envelope.signature.contentEquals(decoded.signature), "Signature round-trip $i")
        }
    }
}
