/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.meshtest

import com.varynx.tools.meshtest.scenarios.DegradedNetworkScenario
import com.varynx.tools.meshtest.scenarios.MultiUserSharedMeshScenario
import com.varynx.tools.meshtest.scenarios.SingleUserMultiDeviceScenario

/**
 * VARYNX 2.0 — Mesh Validation Runner
 *
 * Runs all Phase 8 mesh/scenario validation tests.
 *
 * Usage:
 *   ./gradlew :tools:run -PmainClass=com.varynx.tools.meshtest.MeshValidationRunnerKt
 *
 * Or run individual scenarios:
 *   --scenario=single-user
 *   --scenario=multi-user
 *   --scenario=degraded
 */
fun main(args: Array<String>) {
    println("╔══════════════════════════════════════════════════╗")
    println("║    VARYNX 2.0 — Mesh & Scenario Validation      ║")
    println("╚══════════════════════════════════════════════════╝")

    val targetScenario = args.firstOrNull { it.startsWith("--scenario=") }
        ?.substringAfter("=")

    val results = mutableListOf<Pair<String, Boolean>>()

    if (targetScenario == null || targetScenario == "single-user") {
        val passed = SingleUserMultiDeviceScenario().run()
        results.add("8.1 Single-User Multi-Device" to passed)
    }

    if (targetScenario == null || targetScenario == "multi-user") {
        val passed = MultiUserSharedMeshScenario().run()
        results.add("8.2 Multi-User Shared Mesh" to passed)
    }

    if (targetScenario == null || targetScenario == "degraded") {
        val passed = DegradedNetworkScenario().run()
        results.add("8.3 Degraded Network" to passed)
    }

    println("\n╔══════════════════════════════════════════════════╗")
    println("║              OVERALL RESULTS                     ║")
    println("╠══════════════════════════════════════════════════╣")
    for ((name, passed) in results) {
        val status = if (passed) "PASS" else "FAIL"
        val mark = if (passed) "✓" else "✗"
        println("║  $mark $name: $status")
    }
    println("╚══════════════════════════════════════════════════╝")

    val allPassed = results.all { it.second }
    if (!allPassed) {
        System.exit(1)
    }
}
