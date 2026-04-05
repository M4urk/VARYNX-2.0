/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Requests (UI → Service) ──

@Serializable
sealed class IpcRequest {
    @Serializable @SerialName("GetDashboard")
    data object GetDashboard : IpcRequest()

    @Serializable @SerialName("GetIdentity")
    data object GetIdentity : IpcRequest()

    @Serializable @SerialName("GetDevices")
    data object GetDevices : IpcRequest()

    @Serializable @SerialName("GetMeshStatus")
    data object GetMeshStatus : IpcRequest()

    @Serializable @SerialName("GetNetwork")
    data object GetNetwork : IpcRequest()

    @Serializable @SerialName("GetThreats")
    data class GetThreats(val limit: Int = 50) : IpcRequest()

    @Serializable @SerialName("GetHealth")
    data object GetHealth : IpcRequest()

    @Serializable @SerialName("GetSettings")
    data object GetSettings : IpcRequest()

    @Serializable @SerialName("GetModules")
    data object GetModules : IpcRequest()

    @Serializable @SerialName("StartPairing")
    data object StartPairing : IpcRequest()

    @Serializable @SerialName("JoinPairing")
    data class JoinPairing(val code: String, val targetDeviceId: String) : IpcRequest()

    @Serializable @SerialName("Action")
    data class Action(val action: String, val params: Map<String, String> = emptyMap()) : IpcRequest()

    @Serializable @SerialName("GetTopology")
    data object GetTopology : IpcRequest()

    @Serializable @SerialName("GetDeviceRoles")
    data object GetDeviceRoles : IpcRequest()

    @Serializable @SerialName("SubmitPairingCode")
    data class SubmitPairingCode(val code: String) : IpcRequest()

    @Serializable @SerialName("RunSecurityScan")
    data object RunSecurityScan : IpcRequest()

    @Serializable @SerialName("RunSkimmerScan")
    data object RunSkimmerScan : IpcRequest()

    @Serializable @SerialName("RunQrScan")
    data class RunQrScan(val content: String) : IpcRequest()
}

// ── Responses (Service → UI) ──

@Serializable
sealed class IpcResponse {
    @Serializable @SerialName("Dashboard")
    data class Dashboard(val data: DashboardData) : IpcResponse()

    @Serializable @SerialName("Identity")
    data class Identity(val data: DeviceIdentityData) : IpcResponse()

    @Serializable @SerialName("Devices")
    data class Devices(val trusted: List<PeerData>, val discovered: List<PeerData>) : IpcResponse()

    @Serializable @SerialName("MeshStatus")
    data class MeshStatus(val data: MeshStatusData) : IpcResponse()

    @Serializable @SerialName("Network")
    data class Network(val data: NetworkStatusData) : IpcResponse()

    @Serializable @SerialName("Threats")
    data class Threats(val events: List<ThreatEventData>) : IpcResponse()

    @Serializable @SerialName("Health")
    data class Health(val data: SystemHealthData) : IpcResponse()

    @Serializable @SerialName("Settings")
    data class Settings(val data: SettingsData) : IpcResponse()

    @Serializable @SerialName("Modules")
    data class Modules(val modules: List<ModuleData>) : IpcResponse()

    @Serializable @SerialName("PairingStarted")
    data class PairingStarted(val code: String) : IpcResponse()

    @Serializable @SerialName("ActionResult")
    data class ActionResult(val success: Boolean, val message: String = "") : IpcResponse()

    @Serializable @SerialName("Error")
    data class Error(val message: String) : IpcResponse()

    @Serializable @SerialName("Topology")
    data class Topology(val data: TopologyData) : IpcResponse()

    @Serializable @SerialName("DeviceRoles")
    data class DeviceRoles(val roles: List<DeviceRoleData>) : IpcResponse()

    @Serializable @SerialName("SecurityScanResult")
    data class SecurityScanResult(val data: SecurityScanData) : IpcResponse()

    @Serializable @SerialName("SkimmerScanResult")
    data class SkimmerScanResult(val data: SkimmerScanData) : IpcResponse()

    @Serializable @SerialName("QrScanResult")
    data class QrScanResult(val data: QrScanResultData) : IpcResponse()
}

// ── Events (Service → UI, pushed over WebSocket) ──

@Serializable
sealed class IpcEvent {
    @Serializable @SerialName("DashboardUpdated")
    data class DashboardUpdated(val data: DashboardData) : IpcEvent()

    @Serializable @SerialName("MeshUpdated")
    data class MeshUpdated(val data: MeshStatusData) : IpcEvent()

    @Serializable @SerialName("ThreatCreated")
    data class ThreatCreated(val event: ThreatEventData) : IpcEvent()

    @Serializable @SerialName("HealthUpdated")
    data class HealthUpdated(val data: SystemHealthData) : IpcEvent()

    @Serializable @SerialName("DevicesUpdated")
    data class DevicesUpdated(val trusted: List<PeerData>, val discovered: List<PeerData>) : IpcEvent()

    @Serializable @SerialName("PairingCode")
    data class PairingCode(val code: String) : IpcEvent()

    @Serializable @SerialName("PairingComplete")
    data class PairingComplete(val deviceName: String, val deviceId: String) : IpcEvent()

    @Serializable @SerialName("PairingFailed")
    data class PairingFailed(val reason: String) : IpcEvent()

    @Serializable @SerialName("ModuleChanged")
    data class ModuleChanged(val module: ModuleData) : IpcEvent()

    @Serializable @SerialName("Log")
    data class Log(val entry: LogEntryData) : IpcEvent()

    @Serializable @SerialName("TopologyUpdated")
    data class TopologyUpdated(val data: TopologyData) : IpcEvent()

    @Serializable @SerialName("LockdownStateChanged")
    data class LockdownStateChanged(val active: Boolean, val initiator: String?) : IpcEvent()
}

// ── Envelope ──

@Serializable
data class IpcEnvelope(
    val id: String? = null,
    val request: IpcRequest? = null,
    val response: IpcResponse? = null,
    val event: IpcEvent? = null
)
