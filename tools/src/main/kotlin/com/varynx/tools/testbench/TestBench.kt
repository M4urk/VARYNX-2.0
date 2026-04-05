/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.testbench

import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.registry.ModuleRegistry

/**
 * VARYNX Test Bench — developer tool for validating core subsystems.
 *
 * Runs a battery of self-checks against:
 *   - Module registry (all modules register and initialize)
 *   - Guardian organism lifecycle (awaken → cycle → sleep)
 *   - Crypto provider (Ed25519 sign/verify, X25519 exchange, AES-GCM)
 *   - Key generation (DeviceKeyStore)
 *   - Log subsystem (write/read/clear)
 *
 * Each check returns PASS/FAIL. Designed for CI or manual verification.
 */
class TestBench {

    private val checks = mutableListOf<CheckResult>()

    fun runAll(): List<CheckResult> {
        checks.clear()
        checkModuleRegistry()
        checkOrganismLifecycle()
        checkCryptoEd25519()
        checkCryptoX25519()
        checkCryptoAesGcm()
        checkKeyGeneration()
        checkLogSubsystem()
        return checks.toList()
    }

    fun printReport() {
        println("═".repeat(60))
        println("VARYNX Test Bench Report")
        println("═".repeat(60))
        val passed = checks.count { it.passed }
        val failed = checks.count { !it.passed }
        for (check in checks) {
            val icon = if (check.passed) "PASS" else "FAIL"
            println("[$icon] ${check.name}")
            if (!check.passed && check.error != null) {
                println("       Error: ${check.error}")
            }
        }
        println("─".repeat(60))
        println("$passed passed, $failed failed, ${checks.size} total")
    }

    // ── Individual Checks ──

    private fun checkModuleRegistry() {
        try {
            ModuleRegistry.initialize()
            val modules = ModuleRegistry.getAllModules()
            check(modules.isNotEmpty()) { "No modules registered" }
            checks.add(CheckResult("Module Registry", true, "${modules.size} modules"))
        } catch (e: Exception) {
            checks.add(CheckResult("Module Registry", false, error = e.message))
        }
    }

    private fun checkOrganismLifecycle() {
        try {
            ModuleRegistry.initialize()
            val organism = GuardianOrganism()
            organism.awaken()
            check(organism.isAlive) { "Organism not alive after awaken" }
            val state = organism.cycle()
            check(state.totalModuleCount > 0) { "No modules in cycle" }
            organism.sleep()
            check(!organism.isAlive) { "Organism still alive after sleep" }
            checks.add(CheckResult("Organism Lifecycle", true, "awaken→cycle→sleep OK"))
        } catch (e: Exception) {
            checks.add(CheckResult("Organism Lifecycle", false, error = e.message))
        }
    }

    private fun checkCryptoEd25519() {
        try {
            val keyPair = CryptoProvider.generateEd25519KeyPair()
            val message = "VARYNX test message".encodeToByteArray()
            val signature = CryptoProvider.ed25519Sign(keyPair.privateKey, message)
            val valid = CryptoProvider.ed25519Verify(keyPair.publicKey, message, signature)
            check(valid) { "Signature verification failed" }
            // Tamper check
            val tampered = message.copyOf().also { it[0] = (it[0] + 1).toByte() }
            val invalid = CryptoProvider.ed25519Verify(keyPair.publicKey, tampered, signature)
            check(!invalid) { "Tampered message should not verify" }
            checks.add(CheckResult("Crypto: Ed25519", true, "sign+verify+tamper OK"))
        } catch (e: Exception) {
            checks.add(CheckResult("Crypto: Ed25519", false, error = e.message))
        }
    }

    private fun checkCryptoX25519() {
        try {
            val alice = CryptoProvider.generateX25519KeyPair()
            val bob = CryptoProvider.generateX25519KeyPair()
            val sharedA = CryptoProvider.x25519SharedSecret(alice.privateKey, bob.publicKey)
            val sharedB = CryptoProvider.x25519SharedSecret(bob.privateKey, alice.publicKey)
            check(sharedA.contentEquals(sharedB)) { "Shared secrets don't match" }
            checks.add(CheckResult("Crypto: X25519", true, "key exchange OK"))
        } catch (e: Exception) {
            checks.add(CheckResult("Crypto: X25519", false, error = e.message))
        }
    }

    private fun checkCryptoAesGcm() {
        try {
            val key = CryptoProvider.hkdfSha256(
                ikm = "test-key".encodeToByteArray(),
                salt = "test-salt".encodeToByteArray(),
                info = "test-info".encodeToByteArray(),
                length = 32
            )
            val nonce = CryptoProvider.randomBytes(12)
            val aad = "varynx-test".encodeToByteArray()
            val plaintext = "VARYNX AES-GCM test payload".encodeToByteArray()
            val sealed = CryptoProvider.aesGcmEncrypt(key, nonce, plaintext, aad)
            val decrypted = CryptoProvider.aesGcmDecrypt(key, nonce, sealed, aad)
            check(plaintext.contentEquals(decrypted)) { "Decrypted doesn't match plaintext" }
            checks.add(CheckResult("Crypto: AES-256-GCM", true, "encrypt+decrypt OK"))
        } catch (e: Exception) {
            checks.add(CheckResult("Crypto: AES-256-GCM", false, error = e.message))
        }
    }

    private fun checkKeyGeneration() {
        try {
            val store = DeviceKeyStore.generate("TestBench Device", DeviceRole.CONTROLLER)
            check(store.identity.deviceId.isNotEmpty()) { "Empty device ID" }
            check(store.identity.displayName == "TestBench Device") { "Wrong display name" }
            check(store.identity.role == DeviceRole.CONTROLLER) { "Wrong role" }
            checks.add(CheckResult("Key Generation", true, "deviceId=${store.identity.deviceId.take(8)}..."))
        } catch (e: Exception) {
            checks.add(CheckResult("Key Generation", false, error = e.message))
        }
    }

    private fun checkLogSubsystem() {
        try {
            val sizeBefore = GuardianLog.size()
            GuardianLog.logSystem("TESTBENCH", "Test log entry")
            val sizeAfter = GuardianLog.size()
            check(sizeAfter > sizeBefore) { "Log entry not recorded" }
            val recent = GuardianLog.getRecent(1)
            check(recent.isNotEmpty()) { "No recent entries" }
            check(recent.last().source == "TESTBENCH") { "Wrong source in log" }
            checks.add(CheckResult("Log Subsystem", true, "write+read OK"))
        } catch (e: Exception) {
            checks.add(CheckResult("Log Subsystem", false, error = e.message))
        }
    }
}

data class CheckResult(
    val name: String,
    val passed: Boolean,
    val detail: String = "",
    val error: String? = null
)
