/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.stress

import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.engine.EventBus
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.model.*
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.tools.meshtest.MeshTestHarness
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * VARYNX 2.0 — Stress Test Runner
 *
 * Exercises core subsystems under concurrent load:
 *   1. Organism rapid cycling — thread contention on detect→interpret→respond→express
 *   2. EventBus flood — concurrent publish/subscribe across channels
 *   3. ModuleRegistry concurrent access — parallel reads during state updates
 *   4. Mesh heartbeat storm — 20-node network with simultaneous heartbeats
 *   5. Threat propagation flood — hundreds of events through mesh sync
 *   6. Combined load — all subsystems at once
 *
 * Usage:
 *   ./gradlew :tools:run -PmainClass=com.varynx.tools.stress.StressTestRunnerKt
 */
fun main() {
    println("╔══════════════════════════════════════════════════╗")
    println("║      VARYNX 2.0 — Stress Test Suite             ║")
    println("╚══════════════════════════════════════════════════╝")

    val runner = StressTestRunner()
    val results = runner.runAll()

    println("\n╔══════════════════════════════════════════════════╗")
    println("║              STRESS TEST RESULTS                 ║")
    println("╠══════════════════════════════════════════════════╣")
    for (r in results) {
        val mark = if (r.passed) "✓" else "✗"
        val status = if (r.passed) "PASS" else "FAIL"
        println("║  $mark [$status] ${r.name}")
        println("║         ${r.detail}")
        if (!r.passed && r.error != null) {
            println("║         ERROR: ${r.error}")
        }
    }
    val passed = results.count { it.passed }
    val failed = results.count { !it.passed }
    println("╠══════════════════════════════════════════════════╣")
    println("║  Total: ${results.size}  |  Passed: $passed  |  Failed: $failed")
    println("╚══════════════════════════════════════════════════╝")

    if (failed > 0) System.exit(1)
}

class StressTestRunner {

    fun runAll(): List<StressResult> {
        val results = mutableListOf<StressResult>()
        results.add(stressOrganismCycling())
        results.add(stressEventBusFlood())
        results.add(stressModuleRegistryConcurrency())
        results.add(stressMeshHeartbeatStorm())
        results.add(stressThreatPropagationFlood())
        results.add(stressCombinedLoad())
        return results
    }

    // ── 1. Organism Rapid Cycling ──────────────────────────────────

    private fun stressOrganismCycling(): StressResult {
        val name = "Organism Rapid Cycling"
        print("  Running $name...")
        return try {
            ModuleRegistry.initialize()
            val organism = GuardianOrganism()
            organism.awaken()

            val threads = 8
            val cyclesPerThread = 500
            val pool = Executors.newFixedThreadPool(threads)
            val errors = AtomicInteger(0)
            val completedCycles = AtomicInteger(0)
            val latch = CountDownLatch(threads)
            val start = System.nanoTime()

            repeat(threads) {
                pool.submit {
                    try {
                        repeat(cyclesPerThread) {
                            try {
                                organism.cycle()
                                completedCycles.incrementAndGet()
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(60, TimeUnit.SECONDS)
            pool.shutdownNow()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            organism.sleep()

            val total = completedCycles.get()
            val rate = if (elapsed > 0) (total * 1000L / elapsed) else total.toLong()
            val detail = "${total} cycles in ${elapsed}ms (${rate}/s), ${errors.get()} errors, $threads threads"
            println(" $detail")

            StressResult(
                name = name,
                passed = errors.get() == 0 && total >= threads * cyclesPerThread * 0.9,
                detail = detail
            )
        } catch (e: Exception) {
            println(" EXCEPTION: ${e.message}")
            StressResult(name, false, "Exception during test", e.message)
        }
    }

    // ── 2. EventBus Concurrent Flood ───────────────────────────────

    private fun stressEventBusFlood(): StressResult {
        val name = "EventBus Concurrent Flood"
        print("  Running $name...")
        return try {
            ModuleRegistry.initialize()
            val organism = GuardianOrganism()
            organism.awaken()
            val eventBus = organism.engine.eventBus

            val publishers = 10
            val eventsPerPublisher = 1000
            val received = AtomicInteger(0)
            val channels = listOf("threats", "alerts", "mesh", "reflex", "system")
            val pool = Executors.newFixedThreadPool(publishers + 5)
            val subscriberReady = CountDownLatch(channels.size)
            val latch = CountDownLatch(publishers)

            // Subscribe on each channel
            for (ch in channels) {
                pool.submit {
                    eventBus.subscribe(ch) { received.incrementAndGet() }
                    subscriberReady.countDown()
                }
            }
            subscriberReady.await(5, TimeUnit.SECONDS)

            val start = System.nanoTime()

            // Publish flood
            repeat(publishers) { pubIdx ->
                pool.submit {
                    try {
                        repeat(eventsPerPublisher) { i ->
                            val ch = channels[i % channels.size]
                            val event = ThreatEvent(
                                id = "stress-$pubIdx-$i",
                                sourceModuleId = "stress_publisher_$pubIdx",
                                threatLevel = ThreatLevel.entries[i % ThreatLevel.entries.size],
                                title = "Stress Event $pubIdx-$i",
                                description = "Stress test event"
                            )
                            eventBus.publish(ch, event)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(30, TimeUnit.SECONDS)
            pool.shutdownNow()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            organism.sleep()

            val totalPublished = publishers * eventsPerPublisher
            val detail = "${totalPublished} published, ${received.get()} received in ${elapsed}ms"
            println(" $detail")

            // Each event delivers to its channel subscriber + wildcard (*) is not subscribed here,
            // so received should be >= totalPublished
            StressResult(
                name = name,
                passed = received.get() >= totalPublished,
                detail = detail
            )
        } catch (e: Exception) {
            println(" EXCEPTION: ${e.message}")
            StressResult(name, false, "Exception during test", e.message)
        }
    }

    // ── 3. ModuleRegistry Concurrent Access ────────────────────────

    private fun stressModuleRegistryConcurrency(): StressResult {
        val name = "ModuleRegistry Concurrent Access"
        print("  Running $name...")
        return try {
            ModuleRegistry.initialize()

            val threads = 12
            val opsPerThread = 2000
            val pool = Executors.newFixedThreadPool(threads)
            val errors = AtomicInteger(0)
            val reads = AtomicInteger(0)
            val writes = AtomicInteger(0)
            val latch = CountDownLatch(threads)
            val moduleIds = ModuleRegistry.getAllModules().map { it.id }
            val start = System.nanoTime()

            repeat(threads) { t ->
                pool.submit {
                    try {
                        repeat(opsPerThread) { i ->
                            try {
                                when (i % 5) {
                                    0 -> { ModuleRegistry.getAllModules(); reads.incrementAndGet() }
                                    1 -> { ModuleRegistry.getActiveModules(); reads.incrementAndGet() }
                                    2 -> { ModuleRegistry.getOverallThreatLevel(); reads.incrementAndGet() }
                                    3 -> {
                                        val id = moduleIds[i % moduleIds.size]
                                        val state = if (i % 2 == 0) ModuleState.ACTIVE else ModuleState.IDLE
                                        ModuleRegistry.updateModuleState(id, state)
                                        writes.incrementAndGet()
                                    }
                                    4 -> {
                                        val id = moduleIds[i % moduleIds.size]
                                        val level = ThreatLevel.entries[i % ThreatLevel.entries.size]
                                        ModuleRegistry.updateModuleThreat(id, level, "stress-$t-$i")
                                        writes.incrementAndGet()
                                    }
                                }
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(30, TimeUnit.SECONDS)
            pool.shutdownNow()
            val elapsed = (System.nanoTime() - start) / 1_000_000

            val totalOps = reads.get() + writes.get()
            val rate = if (elapsed > 0) (totalOps * 1000L / elapsed) else totalOps.toLong()
            val detail = "${totalOps} ops (${reads.get()} reads, ${writes.get()} writes) in ${elapsed}ms (${rate}/s), ${errors.get()} errors"
            println(" $detail")

            // Reset to ACTIVE
            for (id in moduleIds) ModuleRegistry.updateModuleState(id, ModuleState.ACTIVE)

            StressResult(
                name = name,
                passed = errors.get() == 0,
                detail = detail
            )
        } catch (e: Exception) {
            println(" EXCEPTION: ${e.message}")
            StressResult(name, false, "Exception during test", e.message)
        }
    }

    // ── 4. Mesh Heartbeat Storm ────────────────────────────────────

    private fun stressMeshHeartbeatStorm(): StressResult {
        val name = "Mesh Heartbeat Storm (20 nodes)"
        print("  Running $name...")
        return try {
            val harness = MeshTestHarness()
            val roles = DeviceRole.entries
            val nodes = (0 until 20).map { i ->
                harness.addNode("Node-$i", roles[i % roles.size])
            }

            // Full mesh pairing — every node trusts every other node
            for (i in nodes.indices) {
                for (j in (i + 1) until nodes.size) {
                    harness.pairNodes(nodes[i], nodes[j])
                }
            }

            // Set varied threat states
            for ((i, node) in nodes.withIndex()) {
                val level = ThreatLevel.entries[i % ThreatLevel.entries.size]
                harness.setNodeThreat(node, level)
            }

            val rounds = 200
            val start = System.nanoTime()
            var errors = 0

            repeat(rounds) {
                try {
                    harness.broadcastHeartbeats()
                } catch (e: Exception) {
                    errors++
                }
            }

            val elapsed = (System.nanoTime() - start) / 1_000_000
            val totalHeartbeats = rounds.toLong() * nodes.size
            val rate = if (elapsed > 0) (totalHeartbeats * 1000 / elapsed) else totalHeartbeats
            val detail = "$totalHeartbeats heartbeats in $rounds rounds, ${elapsed}ms (${rate}/s), $errors errors"
            println(" $detail")

            // Verify all peers see each other
            var peerCheckPassed = true
            for (node in nodes) {
                val eval = harness.evaluateMesh(node)
                if (eval.activePeers < nodes.size - 1) {
                    peerCheckPassed = false
                }
            }

            StressResult(
                name = name,
                passed = errors == 0 && peerCheckPassed,
                detail = detail + if (!peerCheckPassed) " [PEER VISIBILITY FAILED]" else ""
            )
        } catch (e: Exception) {
            println(" EXCEPTION: ${e.message}")
            StressResult(name, false, "Exception during test", e.message)
        }
    }

    // ── 5. Threat Propagation Flood ────────────────────────────────

    private fun stressThreatPropagationFlood(): StressResult {
        val name = "Threat Propagation Flood"
        print("  Running $name...")
        return try {
            val harness = MeshTestHarness()
            val roles = DeviceRole.entries
            val nodes = (0 until 10).map { i ->
                harness.addNode("FloodNode-$i", roles[i % roles.size])
            }

            // Full mesh pairing
            for (i in nodes.indices) {
                for (j in (i + 1) until nodes.size) {
                    harness.pairNodes(nodes[i], nodes[j])
                }
            }

            // Each node generates 50 threat events
            val eventsPerNode = 50
            for ((idx, node) in nodes.withIndex()) {
                repeat(eventsPerNode) { i ->
                    val event = ThreatEvent(
                        id = "flood-$idx-$i",
                        sourceModuleId = "stress_module_$idx",
                        threatLevel = ThreatLevel.entries[(idx + i) % ThreatLevel.entries.size],
                        title = "Flood Event $idx-$i",
                        description = "Flood stress test event"
                    )
                    node.meshSync.onLocalThreatDetected(event)
                }
                harness.setNodeThreat(node, ThreatLevel.HIGH, GuardianMode.ALERT)
            }

            val start = System.nanoTime()
            var propagationErrors = 0

            // Propagate + heartbeat rounds
            val rounds = 50
            repeat(rounds) {
                try {
                    harness.propagateThreats()
                    harness.broadcastHeartbeats()
                } catch (e: Exception) {
                    propagationErrors++
                }
            }

            val elapsed = (System.nanoTime() - start) / 1_000_000
            val totalEvents = nodes.size.toLong() * eventsPerNode
            val detail = "$totalEvents events across ${nodes.size} nodes, $rounds rounds in ${elapsed}ms, $propagationErrors errors"
            println(" $detail")

            StressResult(
                name = name,
                passed = propagationErrors == 0,
                detail = detail
            )
        } catch (e: Exception) {
            println(" EXCEPTION: ${e.message}")
            StressResult(name, false, "Exception during test", e.message)
        }
    }

    // ── 6. Combined Load ───────────────────────────────────────────

    private fun stressCombinedLoad(): StressResult {
        val name = "Combined Load (all subsystems)"
        print("  Running $name...")
        return try {
            ModuleRegistry.initialize()
            val organism = GuardianOrganism()
            organism.awaken()

            val pool = Executors.newFixedThreadPool(16)
            val errors = AtomicInteger(0)
            val ops = AtomicInteger(0)
            val latch = CountDownLatch(16)
            val start = System.nanoTime()
            val duration = 10_000L // 10 seconds
            val moduleIds = ModuleRegistry.getAllModules().map { it.id }

            // 4 threads: organism cycling
            repeat(4) {
                pool.submit {
                    try {
                        val deadline = System.currentTimeMillis() + duration
                        while (System.currentTimeMillis() < deadline) {
                            try {
                                organism.cycle()
                                ops.incrementAndGet()
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // 4 threads: registry hammering
            repeat(4) { t ->
                pool.submit {
                    try {
                        val deadline = System.currentTimeMillis() + duration
                        var i = 0
                        while (System.currentTimeMillis() < deadline) {
                            try {
                                ModuleRegistry.getAllModules()
                                ModuleRegistry.getActiveModules()
                                ModuleRegistry.getOverallThreatLevel()
                                if (moduleIds.isNotEmpty()) {
                                    val id = moduleIds[i % moduleIds.size]
                                    val level = ThreatLevel.entries[i % ThreatLevel.entries.size]
                                    ModuleRegistry.updateModuleThreat(id, level, "combined-$t")
                                }
                                ops.addAndGet(4)
                                i++
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // 4 threads: mesh simulation
            repeat(4) {
                pool.submit {
                    try {
                        val harness = MeshTestHarness()
                        val n1 = harness.addNode("Combined-A-$it", DeviceRole.GUARDIAN)
                        val n2 = harness.addNode("Combined-B-$it", DeviceRole.HUB_HOME)
                        harness.pairNodes(n1, n2)
                        val deadline = System.currentTimeMillis() + duration
                        var i = 0
                        while (System.currentTimeMillis() < deadline) {
                            try {
                                harness.setNodeThreat(n1, ThreatLevel.entries[i % ThreatLevel.entries.size])
                                harness.broadcastHeartbeats()
                                harness.propagateThreats()
                                ops.addAndGet(3)
                                i++
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // 4 threads: log + event bus
            repeat(4) { t ->
                pool.submit {
                    try {
                        val deadline = System.currentTimeMillis() + duration
                        var i = 0
                        while (System.currentTimeMillis() < deadline) {
                            try {
                                GuardianLog.logEngine("STRESS_$t", "Combined stress event $i", "")
                                GuardianLog.getRecent(10)
                                ops.addAndGet(2)
                                i++
                            } catch (e: Exception) {
                                errors.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(duration + 15_000, TimeUnit.MILLISECONDS)
            pool.shutdownNow()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            organism.sleep()

            val totalOps = ops.get()
            val rate = if (elapsed > 0) (totalOps.toLong() * 1000 / elapsed) else totalOps.toLong()
            val detail = "${totalOps} ops in ${elapsed}ms (${rate}/s), ${errors.get()} errors, 16 threads"
            println(" $detail")

            StressResult(
                name = name,
                passed = errors.get() == 0,
                detail = detail
            )
        } catch (e: Exception) {
            println(" EXCEPTION: ${e.message}")
            StressResult(name, false, "Exception during test", e.message)
        }
    }
}

data class StressResult(
    val name: String,
    val passed: Boolean,
    val detail: String,
    val error: String? = null
)
