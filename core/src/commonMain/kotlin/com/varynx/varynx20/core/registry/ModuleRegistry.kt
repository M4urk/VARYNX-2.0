/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.registry

import com.varynx.varynx20.core.model.*
import com.varynx.varynx20.core.platform.withLock

/**
 * Central registry of all 76 Varynx modules across 7 categories.
 * Single source of truth for module state, discovery, and orchestration.
 */
object ModuleRegistry {

    private val lock = Any()
    private val modules = mutableListOf<VarynxModule>()

    fun initialize() = withLock(lock) {
        modules.clear()
        modules.addAll(coreProtectionModules())
        modules.addAll(reflexModules())
        modules.addAll(engineModules())
        modules.addAll(intelligenceModules())
        modules.addAll(identityModules())
        modules.addAll(meshModules())
        modules.addAll(platformModules())
    }

    fun getAllModules(): List<VarynxModule> = withLock(lock) { modules.toList() }

    fun getModulesByCategory(category: ModuleCategory): List<VarynxModule> =
        withLock(lock) { modules.filter { it.category == category } }

    fun getActiveModules(): List<VarynxModule> =
        withLock(lock) { modules.filter { it.state == ModuleState.ACTIVE || it.state == ModuleState.TRIGGERED } }

    fun getV2ActiveModules(): List<VarynxModule> =
        withLock(lock) { modules.filter { it.isV2Active } }

    fun getModule(id: String): VarynxModule? = withLock(lock) { modules.find { it.id == id } }

    fun updateModuleState(id: String, state: ModuleState) = withLock(lock) {
        val index = modules.indexOfFirst { it.id == id }
        if (index >= 0) {
            modules[index] = modules[index].copy(state = state)
        }
    }

    fun updateModuleThreat(id: String, level: ThreatLevel, statusText: String) = withLock(lock) {
        val index = modules.indexOfFirst { it.id == id }
        if (index >= 0) {
            modules[index] = modules[index].copy(
                threatLevel = level,
                statusText = statusText,
                eventsDetected = modules[index].eventsDetected + 1
            )
        }
    }

    fun getOverallThreatLevel(): ThreatLevel =
        withLock(lock) { modules.maxByOrNull { it.threatLevel.score }?.threatLevel ?: ThreatLevel.NONE }

    // ──────────────────────────────────────
    // Category 1: Core Protection (V2 Active)
    // ──────────────────────────────────────
    private fun coreProtectionModules() = listOf(
        VarynxModule("protect_scam_detector", "Scam Detector", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Pattern-based scam detection across SMS, notifications, and clipboard", isV2Active = true),
        VarynxModule("protect_clipboard_shield", "Clipboard Shield", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Detects malicious content in paste operations", isV2Active = true),
        VarynxModule("protect_bt_skimmer", "Bluetooth Skimmer Detector", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Scans for rogue Bluetooth devices and skimmers", isV2Active = true),
        VarynxModule("protect_nfc_guardian", "NFC Guardian", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Detects unsafe NFC tags and payloads", isV2Active = true),
        VarynxModule("protect_network_integrity", "Network Integrity", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "MITM detection, unsafe WiFi, DNS hijack, proxy detection", isV2Active = true),
        VarynxModule("protect_app_behavior", "App Behavior Monitor", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Tracks and scores suspicious app action sequences", isV2Active = true),
        VarynxModule("protect_device_state", "Device State Monitor", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Root detection, emulator detection, tamper checks", isV2Active = true),
        VarynxModule("protect_permission_watchdog", "Permission Watchdog", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Monitors dangerous permission grants in real-time", isV2Active = true),
        VarynxModule("protect_install_monitor", "Install Monitor", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Classifies high-risk app installations by source and type", isV2Active = true),
        VarynxModule("protect_runtime_threat", "Runtime Threat Monitor", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Real-time anomaly detection and clustering", isV2Active = true),
        VarynxModule("protect_overlay_detector", "Overlay Detector", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Detects apps drawing overlays for tapjacking prevention", isV2Active = true),
        VarynxModule("protect_notification_analyzer", "Notification Analyzer", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Scans notification content for phishing and social engineering", isV2Active = true),
        VarynxModule("protect_usb_integrity", "USB/OTG Integrity", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Monitors USB connections for rogue devices and BadUSB attacks", isV2Active = true),
        VarynxModule("protect_sensor_anomaly", "Sensor Anomaly Detector", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Detects spoofed sensors, relay attacks, and hardware tampering", isV2Active = true),
        VarynxModule("protect_app_tamper", "App Tamper Detector", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Detects post-install app modification, re-signing, and dex patching", isV2Active = true),
        VarynxModule("protect_security_audit", "Security Audit Scanner", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "Comprehensive device security scan aggregating all protection signals", isV2Active = true),
        VarynxModule("protect_qr_scanner", "QR / Scam Scanner", ModuleCategory.PROTECTION, ModuleState.ACTIVE,
            description = "QR code safety analysis with URL risk scoring and scam heuristics", isV2Active = true)
    )

    // ──────────────────────────────────────
    // Category 2: Reflex Modules (V2 Active)
    // ──────────────────────────────────────
    private fun reflexModules() = listOf(
        VarynxModule("reflex_warning", "Warning Reflex", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Instant alerts for detected threats", isV2Active = true),
        VarynxModule("reflex_block", "Block Reflex", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Auto-block unsafe actions", isV2Active = true),
        VarynxModule("reflex_lockdown", "Lockdown Reflex", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Emergency containment for critical threats", isV2Active = true),
        VarynxModule("reflex_integrity", "Integrity Reflex", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Restores safe baseline after integrity breach", isV2Active = true),
        VarynxModule("reflex_auto_escalation", "Auto-Escalation Reflex", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Dynamically raises severity based on patterns", isV2Active = true),
        VarynxModule("reflex_threat_replay", "Threat Replay", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Reconstructs threat event sequences", isV2Active = true),
        VarynxModule("reflex_cooldown", "Reflex Cooldown", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Prevents reflex over-triggering", isV2Active = true),
        VarynxModule("reflex_priority_engine", "Reflex Priority Engine", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Orders reflex execution by priority", isV2Active = true),
        VarynxModule("reflex_intervention", "Guardian Intervention Mode", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Activates high-risk containment state", isV2Active = true),
        VarynxModule("reflex_safe_mode", "Emergency Safe Mode", ModuleCategory.REFLEX, ModuleState.ACTIVE,
            description = "Full system isolation for critical emergencies", isV2Active = true)
    )

    // ──────────────────────────────────────
    // Category 3: Engine Modules (V2 Active)
    // ──────────────────────────────────────
    private fun engineModules() = listOf(
        VarynxModule("engine_behavior", "Behavior Engine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Behavioral pattern analysis and scoring", isV2Active = true),
        VarynxModule("engine_reflex", "Reflex Engine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Reflex orchestration and execution queue", isV2Active = true),
        VarynxModule("engine_integrity", "Integrity Engine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "System integrity verification and baseline comparison", isV2Active = true),
        VarynxModule("engine_threat", "Threat Engine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Active threat tracking and lifecycle management", isV2Active = true),
        VarynxModule("engine_baseline", "Baseline Engine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Captures and manages baseline system snapshots", isV2Active = true),
        VarynxModule("engine_scoring", "Deterministic Scoring Engine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Weighted multi-signal threat scoring", isV2Active = true),
        VarynxModule("engine_event_bus", "Event Bus", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Pub/sub event routing across all modules", isV2Active = true),
        VarynxModule("engine_state_machine", "State Machine", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Guardian mode transitions based on threat level", isV2Active = true),
        VarynxModule("engine_signal_router", "Signal Router", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Routes detection signals to appropriate engines", isV2Active = true),
        VarynxModule("engine_local_core", "Local Processing Core", ModuleCategory.ENGINE, ModuleState.ACTIVE,
            description = "Offline-first local computation coordinator", isV2Active = true)
    )

    // ──────────────────────────────────────
    // Category 4: Intelligence (V2 Active)
    // ──────────────────────────────────────
    private fun intelligenceModules() = listOf(
        VarynxModule("intel_adaptive_scoring", "Adaptive Scoring", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Dynamic score adjustment based on learned patterns", isV2Active = true),
        VarynxModule("intel_baseline_learning", "Baseline Learning", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Learns normal device behavior over time", isV2Active = true),
        VarynxModule("intel_threat_memory", "Threat Memory", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Persistent memory of past threats for pattern matching", isV2Active = true),
        VarynxModule("intel_pattern_correlation", "Pattern Correlation", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Cross-signal pattern correlation engine", isV2Active = true),
        VarynxModule("intel_context_thresholds", "Context-Aware Thresholds", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Adjusts detection thresholds based on context", isV2Active = true),
        VarynxModule("intel_multi_signal_fusion", "Multi-Signal Fusion", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Fuses signals from multiple modules for composite scoring", isV2Active = true),
        VarynxModule("intel_behavior_drift", "Behavior Drift Detection", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Detects gradual behavioral changes over time", isV2Active = true),
        VarynxModule("intel_sequence_prediction", "Sequence Prediction", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Predicts likely next threat based on sequence analysis", isV2Active = true),
        VarynxModule("intel_threat_clustering", "Threat Clustering", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Groups related threat events into clusters", isV2Active = true),
        VarynxModule("intel_adaptive_reflex", "Adaptive Reflex Tuning", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "Auto-tunes reflex parameters based on outcomes", isV2Active = true),
        VarynxModule("intel_anomaly_detection", "Anomaly Detection", ModuleCategory.INTELLIGENCE, ModuleState.ACTIVE,
            description = "ML-lite statistical anomaly detection for signal patterns", isV2Active = true)
    )

    // ──────────────────────────────────────
    // Category 5: Identity (V2 Active)
    // ──────────────────────────────────────
    private fun identityModules() = listOf(
        VarynxModule("id_multi_form", "Multi-Form Guardian System", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Guardian form switching based on threat context", isV2Active = true),
        VarynxModule("id_ui_states", "Identity-Driven UI States", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "UI adapts to guardian identity and mood", isV2Active = true),
        VarynxModule("id_hybrid_transitions", "Hybrid Mode Transitions", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Smooth transitions between guardian modes", isV2Active = true),
        VarynxModule("id_layered_reflex", "Layered Reflex Chains", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Multi-stage reflex chains for complex threats", isV2Active = true),
        VarynxModule("id_profiles", "Guardian Profiles", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Configurable guardian personality profiles", isV2Active = true),
        VarynxModule("id_memory", "Identity Memory", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Guardian remembers past interactions and decisions", isV2Active = true),
        VarynxModule("id_adaptive_presence", "Adaptive Presence", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Guardian visibility adapts to user context", isV2Active = true),
        VarynxModule("id_threat_styles", "Threat Interpretation Styles", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Different threat analysis approaches per identity", isV2Active = true),
        VarynxModule("id_mood_states", "Guardian Mood States", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Mood-based modifier on alert presentation", isV2Active = true),
        VarynxModule("id_evolution", "Identity Evolution Engine", ModuleCategory.IDENTITY, ModuleState.ACTIVE,
            description = "Guardian identity evolves with usage patterns", isV2Active = true)
    )

    // ──────────────────────────────────────
    // Category 6: Mesh (V2 Active)
    // ──────────────────────────────────────
    private fun meshModules() = listOf(
        VarynxModule("mesh_device_identity", "Device Identity", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Unique device fingerprint for mesh network", isV2Active = true),
        VarynxModule("mesh_handshake", "Guardian Handshake", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Secure handshake between guardian instances", isV2Active = true),
        VarynxModule("mesh_heartbeat", "Mesh Heartbeat", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Periodic health signal between mesh nodes", isV2Active = true),
        VarynxModule("mesh_event_sync", "Event Sync Model", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Synchronizes threat events across devices", isV2Active = true),
        VarynxModule("mesh_cross_device", "Cross-Device Awareness", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Awareness of threats detected on other devices", isV2Active = true),
        VarynxModule("mesh_shared_reflex", "Shared Reflex Chains", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Coordinated reflex responses across mesh", isV2Active = true),
        VarynxModule("mesh_distributed_memory", "Distributed Threat Memory", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Shared threat intelligence across mesh nodes", isV2Active = true),
        VarynxModule("mesh_multi_baseline", "Multi-Device Baseline", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Baseline computed across all mesh devices", isV2Active = true),
        VarynxModule("mesh_integrity", "Mesh Integrity", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Verifies integrity of the mesh network itself", isV2Active = true),
        VarynxModule("mesh_swarm", "Guardian Swarm Mode", ModuleCategory.MESH, ModuleState.ACTIVE,
            description = "Coordinated multi-device threat response", isV2Active = true)
    )

    // ──────────────────────────────────────
    // Category 7: Platform (V2 Active)
    // ──────────────────────────────────────
    private fun platformModules() = listOf(
        VarynxModule("plat_desktop", "Desktop Guardian", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Guardian extension for desktop environments", isV2Active = true),
        VarynxModule("plat_router", "Router Guardian", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Network-level guardian for routers", isV2Active = true),
        VarynxModule("plat_wearable", "Wearable Guardian", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Lightweight guardian for smartwatches", isV2Active = true),
        VarynxModule("plat_iot", "IoT Integrity Monitor", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Monitors IoT device integrity", isV2Active = true),
        VarynxModule("plat_mesh_controller", "Network Mesh Controller", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Controls and manages network mesh topology", isV2Active = true),
        VarynxModule("plat_threat_relay", "Local Threat Relay", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Relays threat data between local devices", isV2Active = true),
        VarynxModule("plat_offline_sync", "Offline Sync Engine", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Syncs data when connectivity is restored", isV2Active = true),
        VarynxModule("plat_cross_reflex", "Cross-Platform Reflex Engine", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Reflex coordination across platforms", isV2Active = true),
        VarynxModule("plat_location_context", "Location Context Engine", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Adjusts guardian behavior based on physical location context", isV2Active = true),
        VarynxModule("plat_power_mgmt", "Power Management", ModuleCategory.PLATFORM, ModuleState.ACTIVE,
            description = "Manages guardian power consumption across battery-powered devices", isV2Active = true)
    )
}
