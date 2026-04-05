/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.homehub

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.PeerState
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Home Hub Controller — the brains of the home mesh network.
 *
 * The Home Hub is an always-on device (NUC, Pi, NAS) that acts as the
 * mesh CONTROLLER. It maintains a live inventory of all devices on the
 * home network, correlates threats across the entire mesh, manages
 * device onboarding, and distributes policy updates.
 *
 * Unique capabilities:
 *   - IoT device inventory with health tracking
 *   - Network-wide threat correlation (cross-device pattern detection)
 *   - Device onboarding queue (approve/deny new devices)
 *   - ARP table monitoring for rogue device detection
 *   - Automatic threat escalation when multiple devices report
 */
class HomeHubController {

    private val lock = Any()
    @Volatile private var _state = HomeHubState()
    val state: HomeHubState get() = _state

    private val deviceInventory = mutableMapOf<String, IoTDevice>()
    private val onboardingQueue = ArrayDeque<OnboardingRequest>(MAX_ONBOARDING_QUEUE)
    private val networkThreats = ArrayDeque<NetworkThreatCorrelation>(MAX_THREAT_HISTORY)
    private val peerThreatBuffers = mutableMapOf<String, MutableList<ThreatEvent>>()
    @Volatile private var _isRunning = false
    @Volatile private var cycleCount = 0L
    @Volatile private var startTime = 0L

    val isRunning: Boolean get() = _isRunning

    /**
     * Start the home hub controller.
     */
    fun start() {
        _isRunning = true
        startTime = currentTimeMillis()
        _state = _state.copy(startedAt = startTime)
        GuardianLog.logSystem("homehub-controller", "Home Hub controller started")
    }

    /**
     * Stop the controller.
     */
    fun stop() {
        _isRunning = false
        GuardianLog.logSystem("homehub-controller",
            "Home Hub stopped after $cycleCount cycles, ${deviceInventory.size} devices tracked")
    }

    // ── IoT Device Inventory ──

    /**
     * Register or update a device in the inventory.
     * Called when a device is discovered via ARP, mDNS, or mesh pairing.
     */
    fun registerDevice(device: IoTDevice) = withLock(lock) {
        val existing = deviceInventory[device.macAddress]
        deviceInventory[device.macAddress] = if (existing != null) {
            existing.copy(
                lastSeen = currentTimeMillis(),
                ipAddress = device.ipAddress,
                status = DeviceStatus.ONLINE
            )
        } else {
            device.copy(firstSeen = currentTimeMillis(), lastSeen = currentTimeMillis())
        }
        _state = _state.copy(trackedDeviceCount = deviceInventory.size)
    }

    /**
     * Mark stale devices as offline based on timeout.
     */
    fun expireStaleDevices(timeoutMs: Long = DEVICE_TIMEOUT_MS) = withLock(lock) {
        val now = currentTimeMillis()
        var offlineCount = 0
        for ((mac, device) in deviceInventory) {
            if (device.status == DeviceStatus.ONLINE && now - device.lastSeen > timeoutMs) {
                deviceInventory[mac] = device.copy(status = DeviceStatus.OFFLINE)
                offlineCount++
            }
        }
        if (offlineCount > 0) {
            GuardianLog.logSystem("homehub-inventory",
                "$offlineCount device(s) went offline")
        }
    }

    /**
     * Get all devices matching a status filter.
     */
    fun getDevices(status: DeviceStatus? = null): List<IoTDevice> = withLock(lock) {
        if (status != null) {
            deviceInventory.values.filter { it.status == status }
        } else {
            deviceInventory.values.toList()
        }
    }

    /**
     * Check for rogue devices — devices on the network that were never onboarded.
     */
    fun detectRogueDevices(): List<IoTDevice> = withLock(lock) {
        deviceInventory.values.filter {
            it.status == DeviceStatus.ONLINE && !it.isTrusted && !it.isOnboarding
        }
    }

    // ── Device Onboarding ──

    /**
     * Add a device to the onboarding queue.
     * New devices discovered on the network go here for user approval.
     */
    fun requestOnboarding(request: OnboardingRequest) = withLock(lock) {
        if (onboardingQueue.size >= MAX_ONBOARDING_QUEUE) onboardingQueue.removeFirst()
        onboardingQueue.addLast(request)
        deviceInventory[request.macAddress]?.let {
            deviceInventory[request.macAddress] = it.copy(isOnboarding = true)
        }
        _state = _state.copy(pendingOnboardingCount = onboardingQueue.size)
        GuardianLog.logSystem("homehub-onboard",
            "Onboarding request: ${request.displayName} (${request.macAddress})")
    }

    /**
     * Approve a device from the onboarding queue.
     */
    fun approveOnboarding(macAddress: String): Boolean = withLock(lock) {
        val request = onboardingQueue.firstOrNull { it.macAddress == macAddress } ?: return@withLock false
        onboardingQueue.removeAll { it.macAddress == macAddress }
        deviceInventory[macAddress]?.let {
            deviceInventory[macAddress] = it.copy(isTrusted = true, isOnboarding = false)
        }
        _state = _state.copy(pendingOnboardingCount = onboardingQueue.size)
        GuardianLog.logSystem("homehub-onboard",
            "Approved: ${request.displayName} (${request.macAddress})")
        true
    }

    /**
     * Deny and remove a device from the onboarding queue.
     */
    fun denyOnboarding(macAddress: String): Boolean = withLock(lock) {
        val removed = onboardingQueue.removeAll { it.macAddress == macAddress }
        if (removed) {
            deviceInventory[macAddress]?.let {
                deviceInventory[macAddress] = it.copy(isOnboarding = false, status = DeviceStatus.BLOCKED)
            }
            _state = _state.copy(pendingOnboardingCount = onboardingQueue.size)
        }
        removed
    }

    fun getPendingOnboarding(): List<OnboardingRequest> = withLock(lock) { onboardingQueue.toList() }

    // ── Network-Wide Threat Correlation ──

    /**
     * Receive a threat from a mesh peer. Buffers threats per peer
     * for cross-device correlation.
     */
    fun onPeerThreat(event: ThreatEvent, fromDeviceId: String) = withLock(lock) {
        val buffer = peerThreatBuffers.getOrPut(fromDeviceId) { mutableListOf() }
        buffer.add(event)
        if (buffer.size > MAX_THREATS_PER_PEER) buffer.removeAt(0)
    }

    /**
     * Run network-wide threat correlation.
     * Detects when multiple mesh devices are reporting similar threats
     * within a time window — indicates a coordinated or network-wide attack.
     */
    fun correlatePeerThreats(): List<NetworkThreatCorrelation> = withLock(lock) {
        val now = currentTimeMillis()
        val windowStart = now - CORRELATION_WINDOW_MS
        val results = mutableListOf<NetworkThreatCorrelation>()

        // Group recent threats by source module across all peers
        val recentByModule = mutableMapOf<String, MutableList<Pair<String, ThreatEvent>>>()
        for ((peerId, threats) in peerThreatBuffers) {
            for (event in threats) {
                if (event.timestamp >= windowStart) {
                    recentByModule.getOrPut(event.sourceModuleId) { mutableListOf() }
                        .add(peerId to event)
                }
            }
        }

        // Correlations: same module triggered on 2+ different devices
        for ((moduleId, peerEvents) in recentByModule) {
            val uniquePeers = peerEvents.map { it.first }.toSet()
            if (uniquePeers.size >= MIN_PEERS_FOR_CORRELATION) {
                val maxLevel = peerEvents.maxOf { it.second.threatLevel }
                val escalated = escalateLevel(maxLevel)
                val correlation = NetworkThreatCorrelation(
                    sourceModuleId = moduleId,
                    affectedPeers = uniquePeers,
                    peerCount = uniquePeers.size,
                    maxThreatLevel = maxLevel,
                    escalatedLevel = escalated,
                    timestamp = now,
                    detail = "Module $moduleId triggered on ${uniquePeers.size} devices within " +
                        "${CORRELATION_WINDOW_MS / 1000}s — possible network-wide attack"
                )
                results.add(correlation)
                networkThreats.addLast(correlation)
                if (networkThreats.size > MAX_THREAT_HISTORY) networkThreats.removeFirst()
            }
        }

        if (results.isNotEmpty()) {
            _state = _state.copy(
                activeCorrelations = results.size,
                lastCorrelationTime = now
            )
        }
        results
    }

    /**
     * Run one controller cycle. Updates inventory, correlates threats.
     */
    fun cycle(trustedPeers: Map<String, PeerState>): HomeHubState {
        if (!_isRunning) return _state
        cycleCount++
        val now = currentTimeMillis()

        // Update mesh peer tracking
        _state = _state.copy(
            meshPeerCount = trustedPeers.size,
            cycleCount = cycleCount,
            uptimeMs = now - startTime
        )

        // Expire stale devices
        expireStaleDevices()

        // Run threat correlation
        val correlations = correlatePeerThreats()

        // Determine overall network threat level
        val networkLevel = if (correlations.isNotEmpty()) {
            correlations.maxOf { it.escalatedLevel }
        } else {
            ThreatLevel.NONE
        }

        _state = _state.copy(
            networkThreatLevel = networkLevel,
            onlineDeviceCount = deviceInventory.values.count { it.status == DeviceStatus.ONLINE },
            trackedDeviceCount = deviceInventory.size
        )

        return _state
    }

    /**
     * Build a guardian state snapshot for mesh heartbeat.
     */
    fun buildState(): GuardianState {
        return GuardianState(
            overallThreatLevel = _state.networkThreatLevel,
            activeModuleCount = deviceInventory.values.count { it.status == DeviceStatus.ONLINE },
            totalModuleCount = deviceInventory.size,
            recentEvents = emptyList(),
            guardianMode = GuardianMode.SENTINEL
        )
    }

    // ── Internal ──

    private fun escalateLevel(level: ThreatLevel): ThreatLevel {
        return when (level) {
            ThreatLevel.NONE -> ThreatLevel.NONE
            ThreatLevel.LOW -> ThreatLevel.MEDIUM
            ThreatLevel.MEDIUM -> ThreatLevel.HIGH
            ThreatLevel.HIGH -> ThreatLevel.CRITICAL
            ThreatLevel.CRITICAL -> ThreatLevel.CRITICAL
        }
    }

    companion object {
        private const val DEVICE_TIMEOUT_MS = 300_000L        // 5 minutes
        private const val CORRELATION_WINDOW_MS = 60_000L     // 60 seconds
        private const val MIN_PEERS_FOR_CORRELATION = 2
        private const val MAX_ONBOARDING_QUEUE = 50
        private const val MAX_THREAT_HISTORY = 200
        private const val MAX_THREATS_PER_PEER = 50
    }
}

// ── Data Classes ──

data class HomeHubState(
    val startedAt: Long = 0L,
    val meshPeerCount: Int = 0,
    val trackedDeviceCount: Int = 0,
    val onlineDeviceCount: Int = 0,
    val pendingOnboardingCount: Int = 0,
    val networkThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val activeCorrelations: Int = 0,
    val lastCorrelationTime: Long = 0L,
    val cycleCount: Long = 0L,
    val uptimeMs: Long = 0L
)

data class IoTDevice(
    val macAddress: String,
    val ipAddress: String,
    val displayName: String,
    val deviceType: IoTDeviceType = IoTDeviceType.UNKNOWN,
    val manufacturer: String = "",
    val status: DeviceStatus = DeviceStatus.ONLINE,
    val isTrusted: Boolean = false,
    val isOnboarding: Boolean = false,
    val meshDeviceId: String? = null,
    val firstSeen: Long = 0L,
    val lastSeen: Long = 0L
)

enum class DeviceStatus { ONLINE, OFFLINE, BLOCKED }

enum class IoTDeviceType {
    UNKNOWN,
    COMPUTER,
    PHONE,
    TABLET,
    SMART_SPEAKER,
    SMART_TV,
    CAMERA,
    THERMOSTAT,
    LIGHT,
    APPLIANCE,
    ROUTER,
    ACCESS_POINT,
    NAS,
    PRINTER,
    GAME_CONSOLE,
    WEARABLE,
    OTHER_IOT
}

data class OnboardingRequest(
    val macAddress: String,
    val ipAddress: String,
    val displayName: String,
    val deviceType: IoTDeviceType = IoTDeviceType.UNKNOWN,
    val requestedAt: Long = currentTimeMillis()
)

data class NetworkThreatCorrelation(
    val sourceModuleId: String,
    val affectedPeers: Set<String>,
    val peerCount: Int,
    val maxThreatLevel: ThreatLevel,
    val escalatedLevel: ThreatLevel,
    val timestamp: Long,
    val detail: String
)
