/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.platform.currentTimeMillis
import kotlin.uuid.Uuid

/**
 * Unique identity of a device in the Varynx mesh.
 * Generated once on first launch and persisted in platform keystore.
 *
 * Each device holds an X25519 key pair (for key exchange) and an Ed25519 key pair
 * (for signing). The public keys are shared during pairing; private keys never leave
 * the device.
 */
data class DeviceIdentity(
    val deviceId: String = Uuid.random().toString(),
    val displayName: String,
    val role: DeviceRole,
    val capabilities: Set<DeviceCapability>,
    val publicKeyExchange: ByteArray,  // X25519 public key (32 bytes)
    val publicKeySigning: ByteArray,   // Ed25519 public key (32 bytes)
    val createdAt: Long = currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()

    companion object {
        /** Default capabilities for each role. */
        fun defaultCapabilities(role: DeviceRole): Set<DeviceCapability> = when (role) {
            DeviceRole.CONTROLLER -> setOf(
                DeviceCapability.DETECT, DeviceCapability.RESPOND,
                DeviceCapability.ALERT, DeviceCapability.CONTROL
            )
            DeviceRole.GUARDIAN -> setOf(
                DeviceCapability.DETECT, DeviceCapability.RESPOND,
                DeviceCapability.ALERT, DeviceCapability.RELAY
            )
            DeviceRole.GUARDIAN_MICRO -> setOf(DeviceCapability.ALERT, DeviceCapability.DETECT)
            DeviceRole.HUB_HOME -> setOf(
                DeviceCapability.DETECT, DeviceCapability.RESPOND,
                DeviceCapability.ALERT, DeviceCapability.CONTROL, DeviceCapability.RELAY
            )
            DeviceRole.HUB_WEAR -> setOf(
                DeviceCapability.ALERT, DeviceCapability.RELAY, DeviceCapability.DETECT
            )
            DeviceRole.NODE_SATELLITE -> setOf(DeviceCapability.DETECT, DeviceCapability.RELAY)
            DeviceRole.NODE_POCKET -> setOf(
                DeviceCapability.DETECT, DeviceCapability.ALERT, DeviceCapability.RELAY
            )
            DeviceRole.NODE_LINUX -> setOf(
                DeviceCapability.DETECT, DeviceCapability.RESPOND,
                DeviceCapability.ALERT, DeviceCapability.RELAY
            )
        }
    }
}
