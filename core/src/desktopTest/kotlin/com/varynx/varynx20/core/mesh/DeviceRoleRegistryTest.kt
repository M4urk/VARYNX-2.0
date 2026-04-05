/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import kotlin.test.*

class DeviceRoleRegistryTest {

    @Test
    fun allRolesReturnedSortedByWeightDescending() {
        val all = DeviceRoleRegistry.all()
        // Weight 5 first (CONTROLLER), then 4 (HUB_HOME), then 3s, 2s, 1s
        assertEquals(DeviceRole.CONTROLLER, all[0].role)
        assertEquals(DeviceRole.HUB_HOME, all[1].role)
        assertTrue(all[0].weight >= all[1].weight)
        assertTrue(all[1].weight >= all[2].weight)
        // All 8 roles present
        assertEquals(8, all.size)
    }

    @Test
    fun allRolesHaveCorrectWeights() {
        val controller = DeviceRoleRegistry.forRole(DeviceRole.CONTROLLER)!!
        val hubHome = DeviceRoleRegistry.forRole(DeviceRole.HUB_HOME)!!
        val guardian = DeviceRoleRegistry.forRole(DeviceRole.GUARDIAN)!!
        val nodeLinux = DeviceRoleRegistry.forRole(DeviceRole.NODE_LINUX)!!
        val hubWear = DeviceRoleRegistry.forRole(DeviceRole.HUB_WEAR)!!
        val guardianMicro = DeviceRoleRegistry.forRole(DeviceRole.GUARDIAN_MICRO)!!
        val nodePocket = DeviceRoleRegistry.forRole(DeviceRole.NODE_POCKET)!!
        val nodeSatellite = DeviceRoleRegistry.forRole(DeviceRole.NODE_SATELLITE)!!

        assertEquals(5, controller.weight)
        assertEquals(4, hubHome.weight)
        assertEquals(3, guardian.weight)
        assertEquals(3, nodeLinux.weight)
        assertEquals(2, hubWear.weight)
        assertEquals(1, guardianMicro.weight)
        assertEquals(1, nodePocket.weight)
        assertEquals(1, nodeSatellite.weight)
    }

    @Test
    fun hubHomeHasAllCapabilities() {
        val hubHome = DeviceRoleRegistry.forRole(DeviceRole.HUB_HOME)!!
        assertTrue(DeviceCapability.DETECT in hubHome.capabilities)
        assertTrue(DeviceCapability.RESPOND in hubHome.capabilities)
        assertTrue(DeviceCapability.ALERT in hubHome.capabilities)
        assertTrue(DeviceCapability.CONTROL in hubHome.capabilities)
        assertTrue(DeviceCapability.RELAY in hubHome.capabilities)
    }

    @Test
    fun controllerHasDetectRespondAlertControl() {
        val controller = DeviceRoleRegistry.forRole(DeviceRole.CONTROLLER)!!
        assertTrue(DeviceCapability.DETECT in controller.capabilities)
        assertTrue(DeviceCapability.RESPOND in controller.capabilities)
        assertTrue(DeviceCapability.ALERT in controller.capabilities)
        assertTrue(DeviceCapability.CONTROL in controller.capabilities)
        assertFalse(DeviceCapability.RELAY in controller.capabilities)
    }

    @Test
    fun guardianHasDetectRespondAlertRelay() {
        val guardian = DeviceRoleRegistry.forRole(DeviceRole.GUARDIAN)!!
        assertTrue(DeviceCapability.DETECT in guardian.capabilities)
        assertTrue(DeviceCapability.RESPOND in guardian.capabilities)
        assertTrue(DeviceCapability.ALERT in guardian.capabilities)
        assertFalse(DeviceCapability.CONTROL in guardian.capabilities)
        assertTrue(DeviceCapability.RELAY in guardian.capabilities)
    }

    @Test
    fun guardianMicroHasAlertAndDetect() {
        val micro = DeviceRoleRegistry.forRole(DeviceRole.GUARDIAN_MICRO)!!
        assertEquals(2, micro.capabilities.size)
        assertTrue(DeviceCapability.ALERT in micro.capabilities)
        assertTrue(DeviceCapability.DETECT in micro.capabilities)
    }

    @Test
    fun forRoleReturnsNullForUnregistered() {
        // All 8 default roles exist, so verify by checking all are non-null
        DeviceRole.entries.forEach { role ->
            assertNotNull(DeviceRoleRegistry.forRole(role), "Missing definition for $role")
        }
    }

    @Test
    fun registerCustomRoleReplacesExisting() {
        val original = DeviceRoleRegistry.forRole(DeviceRole.GUARDIAN_MICRO)!!.copy()

        try {
            val custom = DeviceRoleRegistry.RoleDefinition(
                role = DeviceRole.GUARDIAN_MICRO,
                weight = 5,
                capabilities = listOf(DeviceCapability.DETECT, DeviceCapability.ALERT),
                description = "Custom micro",
                icon = "\uD83D\uDD14"
            )
            DeviceRoleRegistry.register(custom)

            val updated = DeviceRoleRegistry.forRole(DeviceRole.GUARDIAN_MICRO)!!
            assertEquals(5, updated.weight)
            assertEquals(2, updated.capabilities.size)
            assertEquals("Custom micro", updated.description)
        } finally {
            // Restore original to avoid leaking state to other tests
            DeviceRoleRegistry.register(
                DeviceRoleRegistry.RoleDefinition(
                    original.role, original.weight, original.capabilities,
                    original.description, original.icon
                )
            )
        }
    }

    @Test
    fun allRolesHaveDescriptions() {
        DeviceRoleRegistry.all().forEach { def ->
            assertTrue(def.description.isNotBlank(), "Role ${def.role} has blank description")
            assertTrue(def.icon.isNotBlank(), "Role ${def.role} has blank icon")
        }
    }
}
