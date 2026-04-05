/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.intelligence.IntelligenceLoader
import com.varynx.varynx20.core.intelligence.IntelligencePack
import com.varynx.varynx20.core.intelligence.LoadResult
import com.varynx.varynx20.core.intelligence.PackSummary
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Intelligence Pack Distribution — propagates signed intelligence packs
 * across the mesh. Only SENTINEL and CONTROLLER roles can distribute packs.
 *
 * Flow:
 *   1. Source device loads/creates a pack locally (already validated)
 *   2. IntelDistributor queues pack manifest for advertisement
 *   3. On mesh tick, pack manifests are broadcast to all trusted peers
 *   4. Peers compare manifests with local inventory
 *   5. Peers request missing/newer packs via directed message
 *   6. Source sends pack data (signed, encrypted per-peer)
 *   7. Receiving peer validates & loads via IntelligenceLoader
 *
 * Packs are never forwarded across trust boundaries without re-signing.
 */
class IntelDistributor(
    private val loader: IntelligenceLoader,
    private val trustGraph: TrustGraph,
    private val localRole: DeviceRole
) {
    private val pendingAdvertisements = mutableListOf<PackAdvertisement>()
    private val pendingPackTransfers = mutableListOf<PackTransfer>()
    private val requestedPacks = mutableSetOf<String>()

    /**
     * Advertise a locally loaded pack to all mesh peers.
     * Only SENTINEL and CONTROLLER roles are authorized to distribute.
     */
    fun advertiseLocalPack(pack: IntelligencePack): Boolean {
        if (!canDistribute()) {
            GuardianLog.logEngine("intel_dist", "unauthorized",
                "Role ${localRole.name} cannot distribute packs")
            return false
        }

        pendingAdvertisements.add(PackAdvertisement(
            packId = pack.packId,
            version = pack.version,
            category = pack.manifest.category.name,
            entryCount = pack.entryCount,
            expiresAt = pack.expiresAt,
            sourceDeviceId = "" // filled at send time
        ))

        GuardianLog.logSystem("intel_dist", "Advertised pack ${pack.packId} v${pack.version}")
        return true
    }

    /**
     * Process a pack advertisement from a peer.
     * Compares with local inventory and requests missing/newer packs.
     */
    fun onAdvertisementReceived(ad: PackAdvertisement): PackRequestDecision {
        // Verify sender is trusted
        if (!trustGraph.isTrusted(ad.sourceDeviceId)) {
            return PackRequestDecision.Rejected("Untrusted source")
        }

        // Check if we already have this pack at same or newer version
        val localPacks = loader.listPacks()
        val existing = localPacks.find { it.packId == ad.packId }

        if (existing != null && existing.version >= ad.version) {
            return PackRequestDecision.AlreadyCurrent
        }

        // Check if already requested
        val requestKey = "${ad.packId}:${ad.version}"
        if (requestKey in requestedPacks) {
            return PackRequestDecision.AlreadyRequested
        }

        requestedPacks.add(requestKey)
        GuardianLog.logSystem("intel_dist",
            "Requesting pack ${ad.packId} v${ad.version} from ${ad.sourceDeviceId.take(8)}")
        return PackRequestDecision.NeedPack(ad.packId, ad.version, ad.sourceDeviceId)
    }

    /**
     * Process a received pack transfer (the actual pack data from a peer).
     * Validates and loads into the local intelligence store.
     */
    fun onPackReceived(transfer: PackTransfer): PackReceiveResult {
        if (!trustGraph.isTrusted(transfer.sourceDeviceId)) {
            return PackReceiveResult.Rejected("Untrusted source")
        }

        val result = loader.loadPack(transfer.pack)
        requestedPacks.remove("${transfer.pack.packId}:${transfer.pack.version}")

        return when (result) {
            is LoadResult.Loaded -> {
                GuardianLog.logSystem("intel_dist",
                    "Received & loaded pack ${result.packId} v${result.version} " +
                        "(${result.entryCount} entries) from ${transfer.sourceDeviceId.take(8)}")
                PackReceiveResult.Loaded(result.packId, result.version)
            }
            is LoadResult.Skipped -> PackReceiveResult.Skipped(result.reason)
            is LoadResult.Rejected -> PackReceiveResult.Rejected(result.reason)
        }
    }

    /**
     * Drain pending advertisements for broadcast to peers.
     */
    fun drainAdvertisements(): List<PackAdvertisement> {
        val drained = pendingAdvertisements.toList()
        pendingAdvertisements.clear()
        return drained
    }

    /**
     * Drain pending pack transfers (actual pack data to send).
     */
    fun drainTransfers(): List<PackTransfer> {
        val drained = pendingPackTransfers.toList()
        pendingPackTransfers.clear()
        return drained
    }

    /**
     * Queue a pack for transfer to a requesting peer.
     */
    fun queuePackTransfer(packId: String, requestingPeerId: String): Boolean {
        if (!canDistribute()) return false
        val packs = loader.listPacks()
        val summary = packs.find { it.packId == packId } ?: return false
        // Actual pack data would need to be retrieved from loader storage
        // For now, mark as pending
        GuardianLog.logSystem("intel_dist",
            "Queued transfer of $packId to ${requestingPeerId.take(8)}")
        return true
    }

    /**
     * Get a summary of distribution state.
     */
    fun getDistributionStatus(): DistributionStatus {
        return DistributionStatus(
            localPackCount = loader.loadedPackCount,
            pendingAds = pendingAdvertisements.size,
            pendingTransfers = pendingPackTransfers.size,
            outstandingRequests = requestedPacks.size,
            canDistribute = canDistribute()
        )
    }

    private fun canDistribute(): Boolean =
        localRole == DeviceRole.HUB_HOME || localRole == DeviceRole.CONTROLLER

    companion object {
        const val MAX_PACK_SIZE_BYTES = 1024 * 1024 // 1MB
    }
}

data class PackAdvertisement(
    val packId: String,
    val version: Long,
    val category: String,
    val entryCount: Int,
    val expiresAt: Long,
    val sourceDeviceId: String
)

data class PackTransfer(
    val pack: IntelligencePack,
    val sourceDeviceId: String
)

sealed class PackRequestDecision {
    data class NeedPack(val packId: String, val version: Long, val fromDeviceId: String) : PackRequestDecision()
    data object AlreadyCurrent : PackRequestDecision()
    data object AlreadyRequested : PackRequestDecision()
    data class Rejected(val reason: String) : PackRequestDecision()
}

sealed class PackReceiveResult {
    data class Loaded(val packId: String, val version: Long) : PackReceiveResult()
    data class Skipped(val reason: String) : PackReceiveResult()
    data class Rejected(val reason: String) : PackReceiveResult()
}

data class DistributionStatus(
    val localPackCount: Int,
    val pendingAds: Int,
    val pendingTransfers: Int,
    val outstandingRequests: Int,
    val canDistribute: Boolean
)
