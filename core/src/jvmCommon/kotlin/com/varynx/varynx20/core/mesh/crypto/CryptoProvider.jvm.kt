/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object CryptoProvider {
    private val secureRandom = SecureRandom()

    // ASN.1 DER headers for raw ↔ encoded key conversion (RFC 8410)
    private val X25519_PUB_HEADER = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
    )
    private val X25519_PRIV_HEADER = byteArrayOf(
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20
    )
    private val ED25519_PUB_HEADER = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    )
    private val ED25519_PRIV_HEADER = byteArrayOf(
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    )

    actual fun generateX25519KeyPair(): KeyPairBytes {
        val kp = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        return KeyPairBytes(
            publicKey = kp.public.encoded.copyOfRange(12, 44),
            privateKey = kp.private.encoded.copyOfRange(16, 48)
        )
    }

    actual fun generateEd25519KeyPair(): KeyPairBytes {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return KeyPairBytes(
            publicKey = kp.public.encoded.copyOfRange(12, 44),
            privateKey = kp.private.encoded.copyOfRange(16, 48)
        )
    }

    actual fun x25519SharedSecret(ourPrivate: ByteArray, theirPublic: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("X25519")
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(X25519_PRIV_HEADER + ourPrivate))
        val pub = kf.generatePublic(X509EncodedKeySpec(X25519_PUB_HEADER + theirPublic))
        return KeyAgreement.getInstance("X25519").run {
            init(priv)
            doPhase(pub, true)
            generateSecret()
        }
    }

    actual fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("Ed25519")
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(ED25519_PRIV_HEADER + privateKey))
        return Signature.getInstance("Ed25519").run {
            initSign(priv)
            update(message)
            sign()
        }
    }

    actual fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return try {
            val kf = KeyFactory.getInstance("Ed25519")
            val pub = kf.generatePublic(X509EncodedKeySpec(ED25519_PUB_HEADER + publicKey))
            Signature.getInstance("Ed25519").run {
                initVerify(pub)
                update(message)
                verify(signature)
            }
        } catch (_: Exception) {
            false
        }
    }

    actual fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    actual fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    actual fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "HKDF output length must be 1..8160 bytes" }
        // HKDF-Extract (RFC 5869 §2.2)
        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        // HKDF-Expand (RFC 5869 §2.3) — multi-round for outputs > 32 bytes
        val rounds = (length + 31) / 32
        val okm = ByteArray(rounds * 32)
        var prev = ByteArray(0)
        for (i in 1..rounds) {
            prev = hmacSha256(prk, prev + info + byteArrayOf(i.toByte()))
            prev.copyInto(okm, (i - 1) * 32)
        }
        return okm.copyOf(length)
    }

    actual fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { secureRandom.nextBytes(it) }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
}
