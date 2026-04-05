/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.ipc

import kotlinx.serialization.Serializable

@Serializable
data class DashboardData(
    val threatLevel: String,
    val threatScore: Int,
    val guardianMode: String,
    val activeModules: Int,
    val totalModules: Int,
    val meshPeers: Int,
    val recentEventCount: Int,
    val uptime: Long,
    val cycleCount: Long,
    val syncStatus: String,
    val alertCount: Int,
    val consensusThreatLevel: String = "NONE",
    val meshLeader: String? = null,
    val lockdownActive: Boolean = false,
    val lockdownInitiator: String? = null,
    val quorumThreatConfirmed: Boolean = false
)

@Serializable
data class DeviceIdentityData(
    val deviceId: String,
    val displayName: String,
    val role: String,
    val capabilities: List<String>,
    val publicKeyExchange: String,
    val publicKeySigning: String,
    val createdAt: Long
)

@Serializable
data class PeerData(
    val deviceId: String,
    val displayName: String,
    val role: String,
    val threatLevel: String,
    val guardianMode: String,
    val activeModuleCount: Int,
    val lastSeen: Long,
    val isTrusted: Boolean
)

@Serializable
data class MeshStatusData(
    val isActive: Boolean,
    val localDeviceId: String,
    val role: String,
    val trustedPeerCount: Int,
    val discoveredPeerCount: Int,
    val lastHeartbeat: Long,
    val syncStatus: String,
    val vectorClock: Map<String, Long>,
    val consensusThreatLevel: String = "NONE",
    val meshLeader: String? = null,
    val lockdownActive: Boolean = false
)

@Serializable
data class NetworkStatusData(
    val interfaces: List<NetworkInterfaceData>,
    val openPorts: List<PortData>,
    val activeConnections: Int,
    val dnsServer: String,
    val gatewayIp: String,
    val publicIp: String,
    val exposureLevel: String
)

@Serializable
data class NetworkInterfaceData(
    val name: String,
    val ip: String,
    val mac: String,
    val type: String,
    val isUp: Boolean
)

@Serializable
data class PortData(
    val port: Int,
    val protocol: String,
    val process: String,
    val state: String
)

@Serializable
data class ThreatEventData(
    val id: String,
    val timestamp: Long,
    val sourceModuleId: String,
    val threatLevel: String,
    val title: String,
    val description: String,
    val reflexTriggered: String? = null,
    val resolved: Boolean = false
)

@Serializable
data class SystemHealthData(
    val cpuUsage: Double,
    val memoryUsedMb: Long,
    val memoryTotalMb: Long,
    val diskUsedGb: Double,
    val diskTotalGb: Double,
    val serviceUptime: Long,
    val guardianCycles: Long,
    val logEntryCount: Int,
    val engineStatus: Map<String, String>,
    val jvmHeapUsedMb: Long,
    val jvmHeapMaxMb: Long
)

@Serializable
data class SettingsData(
    val deviceName: String,
    val role: String,
    val meshEnabled: Boolean,
    val meshPort: Int,
    val heartbeatIntervalSec: Int,
    val guardianCycleIntervalSec: Int,
    val logRetentionCount: Int,
    val autoStartEnabled: Boolean
)

@Serializable
data class ModuleData(
    val id: String,
    val name: String,
    val category: String,
    val state: String,
    val threatLevel: String,
    val description: String,
    val statusText: String,
    val eventsDetected: Int,
    val isV2Active: Boolean
)

@Serializable
data class LogEntryData(
    val id: Long,
    val timestamp: Long,
    val category: String,
    val source: String,
    val action: String,
    val detail: String,
    val threatLevel: String
)

@Serializable
data class TopologyData(
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>,
    val meshLeader: String? = null,
    val lockdownActive: Boolean = false,
    val consensusThreatLevel: String = "NONE"
)

@Serializable
data class TopologyNode(
    val deviceId: String,
    val displayName: String,
    val role: String,
    val threatLevel: String,
    val guardianMode: String,
    val isLocal: Boolean,
    val isOnline: Boolean,
    val activeModuleCount: Int,
    val lastSeen: Long
)

@Serializable
data class TopologyEdge(
    val fromDeviceId: String,
    val toDeviceId: String,
    val isTrusted: Boolean,
    val latencyMs: Long = 0
)

@Serializable
data class DeviceRoleData(
    val role: String,
    val label: String,
    val weight: Int,
    val capabilities: List<String>,
    val description: String,
    val icon: String
)

@Serializable
data class SecurityScanData(
    val overallThreatLevel: String,
    val findingCount: Int,
    val findings: List<AuditFindingData>
)

@Serializable
data class AuditFindingData(
    val moduleId: String,
    val moduleName: String,
    val threatLevel: String,
    val title: String,
    val description: String
)

@Serializable
data class SkimmerScanData(
    val overallThreatLevel: String,
    val devicesScanned: Int,
    val suspiciousCount: Int,
    val findings: List<SkimmerFindingData>
)

@Serializable
data class SkimmerFindingData(
    val deviceName: String,
    val rssi: Int,
    val threatLevel: String,
    val description: String
)

@Serializable
data class QrScanResultData(
    val content: String,
    val payloadType: String,
    val threatLevel: String,
    val safe: Boolean,
    val findings: List<String>
)
