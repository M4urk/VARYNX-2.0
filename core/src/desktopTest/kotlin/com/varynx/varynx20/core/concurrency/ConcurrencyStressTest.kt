/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.concurrency

import com.varynx.varynx20.core.engine.ReflexEngine
import com.varynx.varynx20.core.engine.SignalRouter
import com.varynx.varynx20.core.homehub.DeviceStatus
import com.varynx.varynx20.core.homehub.HomeHubController
import com.varynx.varynx20.core.homehub.IoTDevice
import com.varynx.varynx20.core.homehub.OnboardingRequest
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.logging.LogCategory
import com.varynx.varynx20.core.logging.LogEntry
import com.varynx.varynx20.core.mesh.VectorClock
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.core.satellite.SatelliteController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Concurrency stress tests — validates thread-safety of all hardened components.
 * Each test hammers a shared object from many threads simultaneously.
 */
class ConcurrencyStressTest {

    companion object {
        private const val THREAD_COUNT = 16
        private const val OPS_PER_THREAD = 500
    }

    // ═══════════════════════════════════════════════
    // VectorClock — concurrent tick, merge, snapshot
    // ═══════════════════════════════════════════════

    @Test
    fun vectorClockConcurrentTick() {
        val clock = VectorClock()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) {
                        clock.tick("device-$i")
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

        assertEquals(0, errors.get(), "No exceptions during concurrent tick")
        // Each thread ticked OPS_PER_THREAD times for its unique device
        val snapshot = clock.toMap()
        assertEquals(THREAD_COUNT, snapshot.size, "All device entries present")
        snapshot.values.forEach { count ->
            assertEquals(OPS_PER_THREAD.toLong(), count, "Each device ticked exactly $OPS_PER_THREAD times")
        }
    }

    @Test
    fun vectorClockConcurrentMerge() {
        val primary = VectorClock()
        primary.tick("primary")
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        val other = VectorClock()
                        other.tick("merge-$i")
                        // Merge hammers the primary clock from all threads
                        primary.merge(other)
                        // Interleave reads
                        primary.toMap()
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

        assertEquals(0, errors.get(), "No exceptions during concurrent merge")
        val snapshot = primary.toMap()
        assertTrue(snapshot.containsKey("primary"), "Primary key preserved")
        // Each merge-$i device was merged OPS_PER_THREAD times, each tick=1, so max merge result ≥ 1
        for (i in 0 until THREAD_COUNT) {
            assertTrue((snapshot["merge-$i"] ?: 0L) >= 1, "merge-$i key present with value >= 1")
        }
    }

    @Test
    fun vectorClockConcurrentTickAndRead() {
        val clock = VectorClock()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) {
                        if (i % 2 == 0) {
                            clock.tick("writer-${i / 2}")
                        } else {
                            // Concurrent reads — isBefore, toMap, get
                            clock.toMap()
                            clock.get("writer-0")
                            val other = VectorClock()
                            other.tick("writer-0")
                            clock.isBefore(other)
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

        assertEquals(0, errors.get(), "No exceptions during concurrent tick + read")
    }

    // ═══════════════════════════════════════════════
    // ModuleRegistry — concurrent read/write
    // ═══════════════════════════════════════════════

    @Test
    fun moduleRegistryConcurrentReadWrite() {
        ModuleRegistry.initialize()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)
        val modules = ModuleRegistry.getAllModules()

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        val mod = modules[j % modules.size]
                        when (i % 4) {
                            0 -> ModuleRegistry.updateModuleState(mod.id, ModuleState.ACTIVE)
                            1 -> ModuleRegistry.updateModuleThreat(mod.id, ThreatLevel.entries[j % 5], "stress-$j")
                            2 -> ModuleRegistry.getAllModules()
                            3 -> ModuleRegistry.getOverallThreatLevel()
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

        assertEquals(0, errors.get(), "No exceptions during concurrent registry access")
        // Registry should still be internally consistent
        val all = ModuleRegistry.getAllModules()
        assertEquals(78, all.size, "Module count unchanged after stress")
        assertEquals(all.map { it.id }.toSet().size, all.size, "No duplicate IDs after stress")
    }

    @Test
    fun moduleRegistryConcurrentReInitialize() {
        ModuleRegistry.initialize()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) {
                        when (i % 3) {
                            0 -> ModuleRegistry.initialize() // Re-init while others read
                            1 -> ModuleRegistry.getAllModules()
                            2 -> ModuleRegistry.getOverallThreatLevel()
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

        assertEquals(0, errors.get(), "No exceptions during re-init stress")
        assertEquals(78, ModuleRegistry.getAllModules().size)
    }

    // ═══════════════════════════════════════════════
    // GuardianLog — concurrent write + read ring buffer
    // ═══════════════════════════════════════════════

    @Test
    fun guardianLogConcurrentWriteAndRead() {
        GuardianLog.clear()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        when (i % 3) {
                            0 -> GuardianLog.log(LogEntry(
                                category = LogCategory.entries[j % LogCategory.entries.size],
                                source = "stress-$i",
                                action = "op-$j",
                                detail = "thread $i iteration $j"
                            ))
                            1 -> GuardianLog.getAll()
                            2 -> GuardianLog.getByCategory(LogCategory.entries[j % LogCategory.entries.size])
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

        assertEquals(0, errors.get(), "No exceptions during concurrent log access")
        // Ring buffer should be capped at 500
        assertTrue(GuardianLog.size() <= 500, "Ring buffer respects cap")
    }

    @Test
    fun guardianLogRingBufferOverflowStress() {
        GuardianLog.clear()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)
        val totalWrites = AtomicInteger(0)

        // All threads write — force overflow
        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        GuardianLog.log(LogEntry(
                            category = LogCategory.SYSTEM,
                            source = "overflow-$i",
                            action = "write",
                            detail = "$i-$j"
                        ))
                        totalWrites.incrementAndGet()
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

        assertEquals(0, errors.get())
        assertEquals(THREAD_COUNT * OPS_PER_THREAD, totalWrites.get())
        // After 8000 writes to a 500-cap buffer, size must be exactly 500
        assertEquals(500, GuardianLog.size(), "Ring buffer capped at 500 after massive overflow")
    }

    // ═══════════════════════════════════════════════
    // SignalRouter — concurrent route add + read
    // ═══════════════════════════════════════════════

    @Test
    fun signalRouterConcurrentAddAndRead() {
        val router = SignalRouter()
        router.initialize()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        if (i % 2 == 0) {
                            router.addRoute("source-$j", "target-$i-$j")
                        } else {
                            router.getTargets("source-${j % 100}")
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

        assertEquals(0, errors.get(), "No exceptions during concurrent routing")
        // Verify we can still read routes
        val targets = router.getTargets("source-0")
        assertTrue(targets.isNotEmpty(), "Routes were written successfully")
    }

    // ═══════════════════════════════════════════════
    // ReflexEngine — concurrent enqueue + process
    // ═══════════════════════════════════════════════

    @Test
    fun reflexEngineConcurrentEnqueueAndProcess() {
        val engine = ReflexEngine()
        engine.initialize()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)
        val executed = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        if (i % 2 == 0) {
                            // Enqueue work from half the threads
                            engine.enqueue(ReflexEngine.ReflexRequest(
                                reflexId = "reflex-$i-$j",
                                priority = j % 10,
                                threatLevel = ThreatLevel.entries[j % 5],
                                execute = { executed.incrementAndGet() }
                            ))
                        } else {
                            // Process from the other half
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

        // Final drain
        engine.process()

        assertEquals(0, errors.get(), "No exceptions during concurrent enqueue/process")
        // Every enqueued item should have been executed exactly once
        val totalEnqueued = (THREAD_COUNT / 2) * OPS_PER_THREAD
        assertEquals(totalEnqueued, executed.get(), "All enqueued requests executed exactly once")
    }

    // ═══════════════════════════════════════════════
    // HomeHubController — concurrent device ops
    // ═══════════════════════════════════════════════

    @Test
    fun homeHubConcurrentDeviceOps() {
        val hub = HomeHubController()
        hub.start()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        when (i % 4) {
                            0 -> hub.registerDevice(IoTDevice(
                                macAddress = "AA:BB:CC:DD:EE:%02X".format(j % 256),
                                ipAddress = "192.168.1.${j % 256}",
                                displayName = "Device-$j"
                            ))
                            1 -> hub.getDevices()
                            2 -> hub.requestOnboarding(OnboardingRequest(
                                macAddress = "AA:BB:CC:DD:EE:%02X".format(j % 256),
                                ipAddress = "192.168.1.${j % 256}",
                                displayName = "Dev-$j"
                            ))
                            3 -> hub.detectRogueDevices()
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

        assertEquals(0, errors.get(), "No exceptions during concurrent hub ops")
        // State should be consistent
        val devices = hub.getDevices()
        val uniqueMacs = devices.map { it.macAddress }.toSet()
        assertEquals(devices.size, uniqueMacs.size, "No duplicate MACs after stress")
    }

    @Test
    fun homeHubConcurrentOnboardingApproval() {
        val hub = HomeHubController()
        hub.start()

        // Pre-populate onboarding queue
        repeat(100) { i ->
            hub.registerDevice(IoTDevice(
                macAddress = "FF:FF:FF:FF:FF:%02X".format(i),
                ipAddress = "10.0.0.${i}",
                displayName = "Pending-$i"
            ))
            hub.requestOnboarding(OnboardingRequest(
                macAddress = "FF:FF:FF:FF:FF:%02X".format(i),
                ipAddress = "10.0.0.$i",
                displayName = "Pending-$i"
            ))
        }

        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(100) { j ->
                        when (i % 3) {
                            0 -> hub.approveOnboarding("FF:FF:FF:FF:FF:%02X".format(j))
                            1 -> hub.denyOnboarding("FF:FF:FF:FF:FF:%02X".format(j))
                            2 -> hub.getPendingOnboarding()
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

        assertEquals(0, errors.get(), "No exceptions during concurrent onboarding")
    }

    @Test
    fun homeHubConcurrentThreatCorrelation() {
        val hub = HomeHubController()
        hub.start()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        when (i % 3) {
                            0 -> hub.onPeerThreat(ThreatEvent(
                                id = "evt-$i-$j",
                                sourceModuleId = "scam_detector",
                                threatLevel = ThreatLevel.entries[j % 5],
                                title = "Test",
                                description = "Stress"
                            ), "peer-${j % 8}")
                            1 -> hub.correlatePeerThreats()
                            2 -> hub.getDevices()
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

        assertEquals(0, errors.get(), "No exceptions during concurrent threat correlation")
    }

    // ═══════════════════════════════════════════════
    // SatelliteController — concurrent buffer ops
    // ═══════════════════════════════════════════════

    @Test
    fun satelliteConcurrentBufferAndDrain() {
        val sat = SatelliteController()
        sat.start()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)
        val totalDrained = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        when (i % 3) {
                            0 -> sat.bufferEvent(ThreatEvent(
                                id = "sat-$i-$j",
                                sourceModuleId = "test",
                                threatLevel = ThreatLevel.entries[j % 5],
                                title = "Stress",
                                description = "Thread $i op $j"
                            ))
                            1 -> totalDrained.addAndGet(sat.drainForSync().size)
                            2 -> sat.peekBuffer()
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

        // Final drain
        totalDrained.addAndGet(sat.drainForSync().size)

        assertEquals(0, errors.get(), "No exceptions during concurrent satellite ops")
        // After final drain, buffer should be empty
        assertEquals(0, sat.state.bufferedEventCount, "Buffer empty after final drain")
    }

    @Test
    fun satelliteConcurrentAutonomousResponse() {
        val sat = SatelliteController()
        sat.start()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val guardianState = GuardianState(
            overallThreatLevel = ThreatLevel.MEDIUM,
            activeModuleCount = 35,
            totalModuleCount = 76,
            recentEvents = emptyList(),
            isOnline = true,
            guardianMode = GuardianMode.ALERT
        )

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        when (i % 3) {
                            0 -> sat.evaluateAutonomousResponse(
                                ThreatEvent(
                                    id = "auto-$i-$j",
                                    sourceModuleId = "test",
                                    threatLevel = ThreatLevel.entries[j % 5],
                                    title = "Auto",
                                    description = "Stress"
                                ),
                                guardianState
                            )
                            1 -> sat.getResponseHistory()
                            2 -> sat.getAdaptiveCycleMs()
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

        assertEquals(0, errors.get(), "No exceptions during concurrent autonomous responses")
    }

    @Test
    fun satelliteConnectivityToggleStress() {
        val sat = SatelliteController()
        sat.start()
        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        when (i % 4) {
                            0 -> sat.onMeshContact()
                            1 -> sat.updateConnectivityStatus()
                            2 -> sat.bufferEvent(ThreatEvent(
                                id = "conn-$i-$j",
                                sourceModuleId = "test",
                                threatLevel = ThreatLevel.LOW,
                                title = "Connectivity",
                                description = "Toggle"
                            ))
                            3 -> sat.state // Volatile read
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

        assertEquals(0, errors.get(), "No exceptions during connectivity toggle stress")
    }

    // ═══════════════════════════════════════════════
    // Cross-component integration stress
    // ═══════════════════════════════════════════════

    @Test
    fun fullPipelineStress() {
        // Simulates the real guardian cycle: registry + log + router + reflex engine
        ModuleRegistry.initialize()
        GuardianLog.clear()
        val router = SignalRouter()
        router.initialize()
        val reflexEngine = ReflexEngine()
        reflexEngine.initialize()
        val executed = AtomicInteger(0)

        // Wire some routes
        repeat(10) { i ->
            router.addRoute("module-$i", "engine_reflex")
        }

        val barrier = CyclicBarrier(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)
        val errors = AtomicInteger(0)

        val threads = (0 until THREAD_COUNT).map { i ->
            Thread {
                try {
                    barrier.await()
                    repeat(OPS_PER_THREAD) { j ->
                        // Simulate a guardian cycle touching all components
                        val modules = ModuleRegistry.getAllModules()
                        val mod = modules[j % modules.size]
                        ModuleRegistry.updateModuleThreat(mod.id, ThreatLevel.entries[j % 5], "cycle-$j")

                        GuardianLog.log(LogEntry(
                            category = LogCategory.MODULE,
                            source = mod.id,
                            action = "scan",
                            detail = "Threat level: ${ThreatLevel.entries[j % 5]}"
                        ))

                        router.getTargets("module-${j % 10}")

                        reflexEngine.enqueue(ReflexEngine.ReflexRequest(
                            reflexId = "reflex-$i-$j",
                            priority = j % 10,
                            threatLevel = ThreatLevel.entries[j % 5],
                            execute = { executed.incrementAndGet() }
                        ))

                        // Periodically drain the reflex queue
                        if (j % 50 == 0) reflexEngine.process()
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

        // Final drain
        reflexEngine.process()

        assertEquals(0, errors.get(), "No exceptions during full pipeline stress")
        assertTrue(executed.get() > 0, "Reflex requests were executed")
        assertTrue(GuardianLog.size() > 0, "Log entries recorded")
        assertEquals(78, ModuleRegistry.getAllModules().size, "Registry intact")
    }
}
