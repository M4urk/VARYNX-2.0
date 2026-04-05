/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.simulator

import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.registry.ModuleRegistry

/**
 * VARYNX Threat Simulator — developer tool for injecting synthetic
 * threat scenarios into the guardian organism and observing behavior.
 *
 * Runs the four-domain loop with simulated detection signals,
 * captures engine verdicts and reflex outcomes, and produces
 * a simulation report.
 */
class ThreatSimulator {

    private val results = mutableListOf<SimulationRun>()

    /**
     * Run a simulation scenario — creates an organism, injects
     * a specified number of cycles, and captures state transitions.
     */
    fun runScenario(scenario: SimulationScenario): SimulationRun {
        ModuleRegistry.initialize()
        val organism = GuardianOrganism()
        organism.awaken()

        val snapshots = mutableListOf<CycleSnapshot>()
        val startTime = currentTimeMillis()

        repeat(scenario.cycles) { i ->
            val state = organism.cycle()
            snapshots.add(CycleSnapshot(
                cycleNumber = i + 1,
                threatLevel = state.overallThreatLevel,
                guardianMode = state.guardianMode.label,
                activeModules = state.activeModuleCount,
                eventCount = state.recentEvents.size
            ))
        }

        organism.sleep()
        val elapsed = currentTimeMillis() - startTime

        val run = SimulationRun(
            scenario = scenario,
            snapshots = snapshots,
            peakThreatLevel = snapshots.maxOfOrNull { it.threatLevel.score } ?: 0,
            finalState = snapshots.lastOrNull(),
            elapsedMs = elapsed,
            logCount = GuardianLog.size()
        )
        results.add(run)
        return run
    }

    fun printReport(run: SimulationRun) {
        println("═".repeat(60))
        println("Simulation: ${run.scenario.name}")
        println("═".repeat(60))
        println("Cycles: ${run.scenario.cycles}  Elapsed: ${run.elapsedMs}ms")
        println("Peak Threat Score: ${run.peakThreatLevel}")
        println("Final State: ${run.finalState?.guardianMode ?: "N/A"}")
        println("Log Entries Generated: ${run.logCount}")
        println()
        println("Cycle Trace:")
        println("${"#".padEnd(6)} ${"Threat".padEnd(12)} ${"Mode".padEnd(14)} Events")
        println("─".repeat(50))
        for (snap in run.snapshots) {
            println("${snap.cycleNumber.toString().padEnd(6)} " +
                "${snap.threatLevel.label.padEnd(12)} " +
                "${snap.guardianMode.padEnd(14)} " +
                snap.eventCount)
        }
    }

    fun getAllResults(): List<SimulationRun> = results.toList()

    fun clearResults() { results.clear() }
}

data class SimulationScenario(
    val name: String,
    val cycles: Int = 10,
    val description: String = ""
)

data class SimulationRun(
    val scenario: SimulationScenario,
    val snapshots: List<CycleSnapshot>,
    val peakThreatLevel: Int,
    val finalState: CycleSnapshot?,
    val elapsedMs: Long,
    val logCount: Int
)

data class CycleSnapshot(
    val cycleNumber: Int,
    val threatLevel: ThreatLevel,
    val guardianMode: String,
    val activeModules: Int,
    val eventCount: Int
)
