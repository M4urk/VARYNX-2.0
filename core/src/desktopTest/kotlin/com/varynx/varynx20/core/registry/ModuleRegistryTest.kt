/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.registry

import com.varynx.varynx20.core.model.ModuleCategory
import com.varynx.varynx20.core.model.ModuleState
import kotlin.test.*

class ModuleRegistryTest {

    @BeforeTest
    fun setup() {
        ModuleRegistry.initialize()
    }

    @Test
    fun registryContains78Modules() {
        assertEquals(78, ModuleRegistry.getAllModules().size)
    }

    @Test
    fun allModulesAreV2Active() {
        val allActive = ModuleRegistry.getAllModules().all { it.isV2Active }
        assertTrue(allActive, "All 76 modules should be V2 active")
    }

    @Test
    fun allModuleIdsUnique() {
        val ids = ModuleRegistry.getAllModules().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Module IDs must be unique")
    }

    @Test
    fun protectionCategory17Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.PROTECTION)
        assertEquals(17, mods.size)
    }

    @Test
    fun reflexCategory10Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.REFLEX)
        assertEquals(10, mods.size)
    }

    @Test
    fun engineCategory10Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.ENGINE)
        assertEquals(10, mods.size)
    }

    @Test
    fun intelligenceCategory11Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.INTELLIGENCE)
        assertEquals(11, mods.size)
    }

    @Test
    fun identityCategory10Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.IDENTITY)
        assertEquals(10, mods.size)
    }

    @Test
    fun meshCategory10Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.MESH)
        assertEquals(10, mods.size)
    }

    @Test
    fun platformCategory10Modules() {
        val mods = ModuleRegistry.getModulesByCategory(ModuleCategory.PLATFORM)
        assertEquals(10, mods.size)
    }

    @Test
    fun getActiveModulesReturnsNonEmpty() {
        val active = ModuleRegistry.getActiveModules()
        assertTrue(active.isNotEmpty())
    }

    @Test
    fun updateModuleState() {
        val firstId = ModuleRegistry.getAllModules().first().id
        ModuleRegistry.updateModuleState(firstId, ModuleState.TRIGGERED)
        val updated = ModuleRegistry.getAllModules().first { it.id == firstId }
        assertEquals(ModuleState.TRIGGERED, updated.state)
        // Reset
        ModuleRegistry.updateModuleState(firstId, ModuleState.ACTIVE)
    }

    @Test
    fun overallThreatLevelStartsClear() {
        val level = ModuleRegistry.getOverallThreatLevel()
        assertNotNull(level)
    }

    @Test
    fun getV2ActiveModulesReturnsAll78() {
        val v2 = ModuleRegistry.getV2ActiveModules()
        assertEquals(78, v2.size)
    }
}
