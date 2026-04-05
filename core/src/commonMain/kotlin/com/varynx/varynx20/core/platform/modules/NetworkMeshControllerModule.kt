/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Network Mesh Controller — controls and manages network mesh topology.
 *
 * Responsible for mesh topology decisions: which devices act as relays,
 * optimal routing between peers, mesh partition detection, and
 * topology healing when peers drop.
 */
class NetworkMeshControllerModule : PlatformModule {

    override val moduleId = "plat_mesh_controller"
    override val moduleName = "Network Mesh Controller"
    override var state = ModuleState.IDLE

    private val topologyGraph = mutableMapOf<String, MutableSet<String>>()
    private var partitionDetected = false

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Mesh controller active")
    }

    override fun process() {
        // Detect mesh partitions (disconnected subgraphs)
        if (topologyGraph.size >= 3) {
            val reachable = bfs(topologyGraph.keys.firstOrNull() ?: return)
            if (reachable.size < topologyGraph.size) {
                if (!partitionDetected) {
                    partitionDetected = true
                    GuardianLog.logThreat(moduleId, "mesh_partition",
                        "Mesh partition detected: ${reachable.size}/${topologyGraph.size} nodes reachable",
                        ThreatLevel.MEDIUM)
                }
            } else {
                partitionDetected = false
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        topologyGraph.clear()
    }

    fun addLink(a: String, b: String) {
        topologyGraph.getOrPut(a) { mutableSetOf() }.add(b)
        topologyGraph.getOrPut(b) { mutableSetOf() }.add(a)
    }

    fun removeLink(a: String, b: String) {
        topologyGraph[a]?.remove(b)
        topologyGraph[b]?.remove(a)
    }

    fun removeNode(nodeId: String) {
        topologyGraph.remove(nodeId)
        topologyGraph.values.forEach { it.remove(nodeId) }
    }

    fun getTopologySize(): Int = topologyGraph.size

    private fun bfs(start: String): Set<String> {
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<String>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (neighbor in topologyGraph[node] ?: emptySet()) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return visited
    }
}
