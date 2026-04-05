/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.crypto

import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.DeviceRole

/**
 * Complete key material for this device. Private keys never leave the device.
 * Call [zeroPrivateKeys] on shutdown to scrub sensitive material from heap.
 */
data class DeviceKeyPair(
    val exchangePublic: ByteArray,   // X25519 public (32 bytes)
    val exchangePrivate: ByteArray,  // X25519 private (32 bytes)
    val signingPublic: ByteArray,    // Ed25519 public (32 bytes)
    val signingPrivate: ByteArray    // Ed25519 private (32 bytes)
) {
    /** Zero out private key material. Call on shutdown/teardown. */
    fun zeroPrivateKeys() {
        exchangePrivate.fill(0)
        signingPrivate.fill(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceKeyPair) return false
        return exchangePublic.contentEquals(other.exchangePublic) &&
            signingPublic.contentEquals(other.signingPublic)
    }

    override fun hashCode(): Int =
        exchangePublic.contentHashCode() * 31 + signingPublic.contentHashCode()
}

/**
 * Bundles a device's public identity with its private key material.
 * Generated once on first launch and persisted in platform keystore.
 */
data class DeviceKeyStore(
    val identity: DeviceIdentity,
    val keyPair: DeviceKeyPair
) {
    companion object {
        fun generate(displayName: String, role: DeviceRole): DeviceKeyStore {
            val exchange = CryptoProvider.generateX25519KeyPair()
            val signing = CryptoProvider.generateEd25519KeyPair()
            return DeviceKeyStore(
                identity = DeviceIdentity(
                    displayName = displayName,
                    role = role,
                    capabilities = DeviceIdentity.defaultCapabilities(role),
                    publicKeyExchange = exchange.publicKey,
                    publicKeySigning = signing.publicKey
                ),
                keyPair = DeviceKeyPair(
                    exchangePublic = exchange.publicKey,
                    exchangePrivate = exchange.privateKey,
                    signingPublic = signing.publicKey,
                    signingPrivate = signing.privateKey
                )
            )
        }
    }
}
