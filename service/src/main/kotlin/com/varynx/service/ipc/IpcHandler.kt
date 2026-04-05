/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.ipc

import com.varynx.service.VarynxServiceState
import com.varynx.varynx20.core.protection.BluetoothSkimmerDetector
import com.varynx.varynx20.core.protection.QrScamScanner
import com.varynx.varynx20.core.protection.SecurityAuditScanner

class IpcHandler(private val state: VarynxServiceState) {

    fun handle(request: IpcRequest): IpcResponse = when (request) {
        is IpcRequest.GetDashboard -> dashboard()
        is IpcRequest.GetIdentity -> identity()
        is IpcRequest.GetDevices -> devices()
        is IpcRequest.GetMeshStatus -> meshStatus()
        is IpcRequest.GetNetwork -> IpcResponse.Network(state.collectNetworkStatus())
        is IpcRequest.GetThreats -> threats(request.limit)
        is IpcRequest.GetHealth -> IpcResponse.Health(state.collectSystemHealth())
        is IpcRequest.GetSettings -> settings()
        is IpcRequest.GetModules -> modules()
        is IpcRequest.StartPairing -> IpcResponse.PairingStarted(state.startPairing())
        is IpcRequest.JoinPairing -> { state.joinPairing(request.code, request.targetDeviceId); IpcResponse.ActionResult(true, "Pairing initiated") }
        is IpcRequest.Action -> action(request.action, request.params)
        is IpcRequest.GetTopology -> topology()
        is IpcRequest.GetDeviceRoles -> deviceRoles()
        is IpcRequest.SubmitPairingCode -> { state.submitPairingCode(request.code); IpcResponse.ActionResult(true, "Pairing code submitted") }
        is IpcRequest.RunSecurityScan -> securityScan()
        is IpcRequest.RunSkimmerScan -> skimmerScan()
        is IpcRequest.RunQrScan -> qrScan(request.content)
    }

    private fun dashboard(): IpcResponse {
        val gs = state.guardianState
        return IpcResponse.Dashboard(DashboardData(
            threatLevel = gs.overallThreatLevel.label,
            threatScore = gs.overallThreatLevel.score,
            guardianMode = gs.guardianMode.label,
            activeModules = gs.activeModuleCount,
            totalModules = gs.totalModuleCount,
            meshPeers = state.trustedPeers.size,
            recentEventCount = gs.recentEvents.size,
            uptime = state.uptime,
            cycleCount = state.cycleCount,
            syncStatus = if (state.trustedPeers.isNotEmpty()) "Synced" else "Local Only",
            alertCount = gs.recentEvents.count { !it.resolved },
            consensusThreatLevel = state.consensusThreatLevel,
            meshLeader = state.meshLeader,
            lockdownActive = state.lockdownActive,
            lockdownInitiator = null,
            quorumThreatConfirmed = false
        ))
    }

    private fun identity(): IpcResponse {
        val id = state.identity
        return IpcResponse.Identity(DeviceIdentityData(
            deviceId = id.deviceId,
            displayName = id.displayName,
            role = id.role.label,
            capabilities = id.capabilities.map { it.name },
            publicKeyExchange = id.publicKeyExchange.joinToString("") { "%02x".format(it) },
            publicKeySigning = id.publicKeySigning.joinToString("") { "%02x".format(it) },
            createdAt = id.createdAt
        ))
    }

    private fun devices(): IpcResponse {
        val trusted = state.trustedPeers.values.map { it.toPeer(true) }
        val discovered = state.discoveredPeers.values.map { it.toPeer(false) }
        return IpcResponse.Devices(trusted, discovered)
    }

    private fun meshStatus(): IpcResponse {
        val id = state.identity
        return IpcResponse.MeshStatus(MeshStatusData(
            isActive = state.meshActive,
            localDeviceId = id.deviceId,
            role = id.role.label,
            trustedPeerCount = state.trustedPeers.size,
            discoveredPeerCount = state.discoveredPeers.size,
            lastHeartbeat = state.lastHeartbeatTime,
            syncStatus = if (state.trustedPeers.isNotEmpty()) "Synced" else "Local Only",
            vectorClock = state.vectorClock,
            consensusThreatLevel = state.consensusThreatLevel,
            meshLeader = state.meshLeader,
            lockdownActive = state.lockdownActive
        ))
    }

    private fun threats(limit: Int): IpcResponse {
        val events = state.recentThreatEvents.take(limit).map { it.toData() }
        return IpcResponse.Threats(events)
    }

    private fun settings(): IpcResponse {
        val id = state.identity
        return IpcResponse.Settings(SettingsData(
            deviceName = id.displayName,
            role = id.role.label,
            meshEnabled = state.meshActive,
            meshPort = 42420,
            heartbeatIntervalSec = 30,
            guardianCycleIntervalSec = 5,
            logRetentionCount = 500,
            autoStartEnabled = true
        ))
    }

    private fun modules(): IpcResponse {
        val list = state.modules.map {
            ModuleData(
                id = it.id, name = it.name, category = it.category.label,
                state = it.state.name, threatLevel = it.threatLevel.label,
                description = it.description, statusText = it.statusText,
                eventsDetected = it.eventsDetected, isV2Active = it.isV2Active
            )
        }
        return IpcResponse.Modules(list)
    }

    private fun securityScan(): IpcResponse {
        val scanner = state.organism.core.getModules()
            .filterIsInstance<SecurityAuditScanner>()
            .firstOrNull() ?: return IpcResponse.Error("SecurityAuditScanner not registered")

        val findings = scanner.runAudit(state.organism.core.getModules())
        val overallLevel = findings.maxByOrNull { it.threatLevel.score }?.threatLevel
            ?: com.varynx.varynx20.core.model.ThreatLevel.NONE

        return IpcResponse.SecurityScanResult(SecurityScanData(
            overallThreatLevel = overallLevel.label,
            findingCount = findings.size,
            findings = findings.map { f ->
                AuditFindingData(
                    moduleId = f.moduleId,
                    moduleName = f.moduleName,
                    threatLevel = f.threatLevel.label,
                    title = f.title,
                    description = f.description
                )
            }
        ))
    }

    private fun skimmerScan(): IpcResponse {
        val detector = state.organism.core.getModules()
            .filterIsInstance<BluetoothSkimmerDetector>()
            .firstOrNull() ?: return IpcResponse.Error("BluetoothSkimmerDetector not registered")

        val level = detector.scan()
        val event = detector.getLastEvent()
        val findings = if (level > com.varynx.varynx20.core.model.ThreatLevel.NONE && event != null) {
            listOf(SkimmerFindingData(
                deviceName = event.description,
                rssi = -50,
                threatLevel = level.label,
                description = event.title
            ))
        } else emptyList()

        return IpcResponse.SkimmerScanResult(SkimmerScanData(
            overallThreatLevel = level.label,
            devicesScanned = 0,
            suspiciousCount = findings.size,
            findings = findings
        ))
    }

    private fun qrScan(content: String): IpcResponse {
        val scanner = state.organism.core.getModules()
            .filterIsInstance<QrScamScanner>()
            .firstOrNull() ?: return IpcResponse.Error("QrScamScanner not registered")

        val result = scanner.analyzeQrContent(content)
        return IpcResponse.QrScanResult(QrScanResultData(
            content = result.content,
            payloadType = result.payloadType.label,
            threatLevel = result.threatLevel.label,
            safe = result.safe,
            findings = result.findings
        ))
    }

    private fun action(action: String, params: Map<String, String>): IpcResponse = when (action) {
        "force_scan" -> { state.forceScan(); IpcResponse.ActionResult(true, "Scan triggered") }
        "clear_logs" -> { state.clearLogs(); IpcResponse.ActionResult(true, "Logs cleared") }
        "lockdown" -> { state.initiateLockdown(params["reason"] ?: "Manual"); IpcResponse.ActionResult(true, "Lockdown initiated") }
        "cancel_lockdown" -> { state.cancelLockdown(); IpcResponse.ActionResult(true, "Lockdown cancelled") }
        "revoke_trust" -> {
            val deviceId = params["deviceId"] ?: return IpcResponse.Error("Missing deviceId parameter")
            state.revokeTrust(deviceId)
            IpcResponse.ActionResult(true, "Trust revoked for $deviceId")
        }
        else -> IpcResponse.Error("Unknown action: $action")
    }

    private fun topology(): IpcResponse = IpcResponse.Topology(state.collectTopology())

    private fun deviceRoles(): IpcResponse {
        val roles = com.varynx.varynx20.core.mesh.DeviceRoleRegistry.all().map { def ->
            DeviceRoleData(
                role = def.role.name,
                label = def.role.label,
                weight = def.weight,
                capabilities = def.capabilities.map { it.name },
                description = def.description,
                icon = def.icon
            )
        }
        return IpcResponse.DeviceRoles(roles)
    }
}

private fun com.varynx.varynx20.core.mesh.PeerState.toPeer(trusted: Boolean) = PeerData(
    deviceId = deviceId, displayName = displayName, role = role.label,
    threatLevel = threatLevel.label, guardianMode = guardianMode.label,
    activeModuleCount = activeModuleCount, lastSeen = lastSeen, isTrusted = trusted
)

private fun com.varynx.varynx20.core.mesh.HeartbeatPayload.toPeer(trusted: Boolean) = PeerData(
    deviceId = deviceId, displayName = displayName, role = role.label,
    threatLevel = threatLevel.label, guardianMode = guardianMode.label,
    activeModuleCount = activeModuleCount, lastSeen = 0L, isTrusted = trusted
)

private fun com.varynx.varynx20.core.model.ThreatEvent.toData() = ThreatEventData(
    id = id, timestamp = timestamp, sourceModuleId = sourceModuleId,
    threatLevel = threatLevel.label, title = title, description = description,
    reflexTriggered = reflexTriggered, resolved = resolved
)
