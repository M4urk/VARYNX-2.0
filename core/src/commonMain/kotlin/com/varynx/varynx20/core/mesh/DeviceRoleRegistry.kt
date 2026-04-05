/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

/**
 * Registry of device roles, their capabilities, and metadata.
 * Centralises role definitions so they can be extended without code changes
 * (e.g., by loading supplemental entries from a config file at startup).
 */
object DeviceRoleRegistry {

    data class RoleDefinition(
        val role: DeviceRole,
        val weight: Int,
        val capabilities: List<DeviceCapability>,
        val description: String,
        val icon: String
    )

    private val definitions = mutableListOf(
        RoleDefinition(
            DeviceRole.CONTROLLER, 5,
            listOf(DeviceCapability.DETECT, DeviceCapability.RESPOND, DeviceCapability.ALERT, DeviceCapability.CONTROL),
            "Desktop command center — trust authority & mesh controller", "\uD83D\uDCBB"
        ),
        RoleDefinition(
            DeviceRole.GUARDIAN, 3,
            listOf(DeviceCapability.DETECT, DeviceCapability.RESPOND, DeviceCapability.ALERT, DeviceCapability.RELAY),
            "Full mobile guardian — scans, reflex, identity, mesh", "\uD83D\uDCF1"
        ),
        RoleDefinition(
            DeviceRole.GUARDIAN_MICRO, 1,
            listOf(DeviceCapability.ALERT, DeviceCapability.DETECT),
            "Micro guardian — constrained/wearable device, auto-mode only", "\u231A"
        ),
        RoleDefinition(
            DeviceRole.HUB_HOME, 4,
            listOf(DeviceCapability.DETECT, DeviceCapability.RESPOND, DeviceCapability.ALERT, DeviceCapability.CONTROL, DeviceCapability.RELAY),
            "Always-on home hub — LAN anchor, device discovery, local intelligence", "\uD83C\uDFE0"
        ),
        RoleDefinition(
            DeviceRole.HUB_WEAR, 2,
            listOf(DeviceCapability.ALERT, DeviceCapability.RELAY, DeviceCapability.DETECT),
            "Wear hub node — aggregates watch state, mesh satellite", "\u2328"
        ),
        RoleDefinition(
            DeviceRole.NODE_SATELLITE, 1,
            listOf(DeviceCapability.DETECT, DeviceCapability.RELAY),
            "Offline-first edge node — mesh repeater, autonomous guardian", "\uD83D\uDCE1"
        ),
        RoleDefinition(
            DeviceRole.NODE_POCKET, 1,
            listOf(DeviceCapability.DETECT, DeviceCapability.ALERT, DeviceCapability.RELAY),
            "On-person micro guardian — BLE/NFC scanning, proximity", "\uD83D\uDCDF"
        ),
        RoleDefinition(
            DeviceRole.NODE_LINUX, 3,
            listOf(DeviceCapability.DETECT, DeviceCapability.RESPOND, DeviceCapability.ALERT, DeviceCapability.RELAY),
            "Headless Linux guardian — server-grade monitoring, mesh node", "\uD83D\uDDA5"
        )
    )

    /** All currently registered role definitions, ordered by weight descending. */
    fun all(): List<RoleDefinition> = definitions.sortedByDescending { it.weight }

    /** Look up a role definition by enum value. */
    fun forRole(role: DeviceRole): RoleDefinition? = definitions.firstOrNull { it.role == role }

    /** Register an additional role definition (e.g., loaded from config). */
    fun register(def: RoleDefinition) {
        definitions.removeAll { it.role == def.role }
        definitions.add(def)
    }
}
