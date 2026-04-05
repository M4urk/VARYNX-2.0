/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyPair
import com.varynx.varynx20.core.mesh.crypto.MeshCrypto
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Local-first trust graph. No central authority.
 *
 * Trust is established by physical proximity pairing (6-digit code exchange).
 * Trust is NOT transitive by default — A trusts B, B trusts C does NOT mean A trusts C.
 * The user must explicitly approve chain trust per device.
 */
class TrustGraph {

    private val lock = Any()
    private val trustedDevices = mutableMapOf<String, TrustEdge>()

    /** Add a trusted device after successful pairing handshake. */
    fun addTrust(edge: TrustEdge) {
        withLock(lock) { trustedDevices[edge.remoteDeviceId] = edge }
    }

    /** Revoke trust for a device. */
    fun revokeTrust(deviceId: String) {
        withLock(lock) { trustedDevices.remove(deviceId) }
    }

    /** Check if a device is trusted. */
    fun isTrusted(deviceId: String): Boolean = withLock(lock) { trustedDevices.containsKey(deviceId) }

    /** Get trust edge for a device (contains shared secret, role, etc). */
    fun getTrustEdge(deviceId: String): TrustEdge? = withLock(lock) { trustedDevices[deviceId] }

    /** All currently trusted device IDs. */
    fun trustedDeviceIds(): Set<String> = withLock(lock) { trustedDevices.keys.toSet() }

    /** Total number of trusted peers in the mesh. */
    fun peerCount(): Int = withLock(lock) { trustedDevices.size }

    /**
     * Establish trust with a remote device by computing the DH shared secret
     * and deriving an encryption key. Called after successful pairing.
     */
    fun establishTrust(remoteIdentity: DeviceIdentity, localKeyPair: DeviceKeyPair): TrustEdge {
        val rawDh = CryptoProvider.x25519SharedSecret(
            localKeyPair.exchangePrivate,
            remoteIdentity.publicKeyExchange
        )
        val encKey = MeshCrypto.deriveEncryptionKey(rawDh)
        val edge = TrustEdge(
            remoteDeviceId = remoteIdentity.deviceId,
            remoteDisplayName = remoteIdentity.displayName,
            remoteRole = remoteIdentity.role,
            remoteCapabilities = remoteIdentity.capabilities,
            remotePublicKeyExchange = remoteIdentity.publicKeyExchange,
            remotePublicKeySigning = remoteIdentity.publicKeySigning,
            sharedSecret = encKey
        )
        addTrust(edge)
        return edge
    }
}

/**
 * A bidirectional trust relationship between this device and a remote peer.
 * Created after a successful pairing handshake.
 */
data class TrustEdge(
    val remoteDeviceId: String,
    val remoteDisplayName: String,
    val remoteRole: DeviceRole,
    val remoteCapabilities: Set<DeviceCapability>,
    val remotePublicKeyExchange: ByteArray,  // X25519 public (32 bytes)
    val remotePublicKeySigning: ByteArray,   // Ed25519 public (32 bytes)
    val sharedSecret: ByteArray,             // X25519 DH result (32 bytes)
    val pairedAt: Long = currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustEdge) return false
        return remoteDeviceId == other.remoteDeviceId
    }

    override fun hashCode(): Int = remoteDeviceId.hashCode()
}
