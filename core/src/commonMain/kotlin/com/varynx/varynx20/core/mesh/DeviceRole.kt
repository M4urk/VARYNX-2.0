/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

/**
 * Device role within the Varynx mesh network.
 * Each device has exactly one role, chosen at setup.
 * Role determines which modules activate and which capabilities are available.
 *
 * 8 device classes across 4 tiers:
 *   Controller tier:  CONTROLLER (trust authority, mesh controller)
 *   Full guardian:     GUARDIAN (full scans, reflex, identity)
 *   Hub tier:          HUB_HOME (LAN anchor), HUB_WEAR (watch aggregator)
 *   Node tier:         NODE_LINUX (headless server), NODE_POCKET (on-person),
 *                      NODE_SATELLITE (edge/offline), GUARDIAN_MICRO (constrained)
 */
enum class DeviceRole(val label: String) {
    CONTROLLER("Controller"),         // Desktop command center — trust authority
    GUARDIAN("Guardian"),              // Full mobile/tablet guardian (Android)
    GUARDIAN_MICRO("Micro Guardian"),  // Constrained device (Wear OS watch app)
    HUB_HOME("Home Hub"),             // Always-on LAN anchor (Pi, NUC, NAS)
    HUB_WEAR("Wear Hub"),             // Wear device aggregator (JVM daemon)
    NODE_SATELLITE("Satellite"),      // Offline-first edge/repeater node
    NODE_POCKET("Pocket Node"),       // On-person micro guardian (Pi Zero)
    NODE_LINUX("Linux Node")          // Headless server guardian
}

/**
 * Capability flags for mesh devices.
 * A device's role determines its default capabilities.
 */
enum class DeviceCapability {
    DETECT,   // Can run protection modules
    RESPOND,  // Can execute reflex actions
    ALERT,    // Can display/emit alerts
    CONTROL,  // Can push policy changes to other devices
    RELAY     // Can forward mesh events between devices
}
