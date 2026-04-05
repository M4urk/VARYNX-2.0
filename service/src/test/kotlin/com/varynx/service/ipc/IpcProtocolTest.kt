/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.ipc

import kotlinx.serialization.json.Json
import kotlin.test.*

class IpcProtocolTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    // ── IpcRequest Serialization ──

    @Test
    fun serializeGetDashboard() {
        val req = IpcRequest.GetDashboard
        val str = json.encodeToString(IpcRequest.serializer(), req)
        assertTrue(str.contains("GetDashboard"))
        val decoded = json.decodeFromString(IpcRequest.serializer(), str)
        assertEquals(IpcRequest.GetDashboard, decoded)
    }

    @Test
    fun serializeStartPairing() {
        val req = IpcRequest.StartPairing
        val str = json.encodeToString(IpcRequest.serializer(), req)
        assertTrue(str.contains("StartPairing"))
        val decoded = json.decodeFromString(IpcRequest.serializer(), str)
        assertEquals(IpcRequest.StartPairing, decoded)
    }

    @Test
    fun serializeSubmitPairingCode() {
        val req = IpcRequest.SubmitPairingCode("123456")
        val str = json.encodeToString(IpcRequest.serializer(), req)
        assertTrue(str.contains("123456"))
        val decoded = json.decodeFromString(IpcRequest.serializer(), str) as IpcRequest.SubmitPairingCode
        assertEquals("123456", decoded.code)
    }

    @Test
    fun serializeJoinPairing() {
        val req = IpcRequest.JoinPairing("654321", "target-uuid")
        val str = json.encodeToString(IpcRequest.serializer(), req)
        val decoded = json.decodeFromString(IpcRequest.serializer(), str) as IpcRequest.JoinPairing
        assertEquals("654321", decoded.code)
        assertEquals("target-uuid", decoded.targetDeviceId)
    }

    @Test
    fun serializeGetThreats() {
        val req = IpcRequest.GetThreats(25)
        val str = json.encodeToString(IpcRequest.serializer(), req)
        val decoded = json.decodeFromString(IpcRequest.serializer(), str) as IpcRequest.GetThreats
        assertEquals(25, decoded.limit)
    }

    @Test
    fun serializeAction() {
        val req = IpcRequest.Action("force_scan", mapOf("mode" to "deep"))
        val str = json.encodeToString(IpcRequest.serializer(), req)
        val decoded = json.decodeFromString(IpcRequest.serializer(), str) as IpcRequest.Action
        assertEquals("force_scan", decoded.action)
        assertEquals("deep", decoded.params["mode"])
    }

    // ── IpcResponse Serialization ──

    @Test
    fun serializePairingStarted() {
        val resp = IpcResponse.PairingStarted("703788")
        val str = json.encodeToString(IpcResponse.serializer(), resp)
        assertTrue(str.contains("703788"))
        val decoded = json.decodeFromString(IpcResponse.serializer(), str) as IpcResponse.PairingStarted
        assertEquals("703788", decoded.code)
    }

    @Test
    fun serializeActionResult() {
        val resp = IpcResponse.ActionResult(true, "Scan triggered")
        val str = json.encodeToString(IpcResponse.serializer(), resp)
        val decoded = json.decodeFromString(IpcResponse.serializer(), str) as IpcResponse.ActionResult
        assertTrue(decoded.success)
        assertEquals("Scan triggered", decoded.message)
    }

    @Test
    fun serializeError() {
        val resp = IpcResponse.Error("Something went wrong")
        val str = json.encodeToString(IpcResponse.serializer(), resp)
        val decoded = json.decodeFromString(IpcResponse.serializer(), str) as IpcResponse.Error
        assertEquals("Something went wrong", decoded.message)
    }

    // ── IpcEvent Serialization ──

    @Test
    fun serializePairingCode() {
        val evt = IpcEvent.PairingCode("386604")
        val str = json.encodeToString(IpcEvent.serializer(), evt)
        assertTrue(str.contains("386604"))
        val decoded = json.decodeFromString(IpcEvent.serializer(), str) as IpcEvent.PairingCode
        assertEquals("386604", decoded.code)
    }

    @Test
    fun serializePairingComplete() {
        val evt = IpcEvent.PairingComplete("Bob's Phone", "uuid-123")
        val str = json.encodeToString(IpcEvent.serializer(), evt)
        val decoded = json.decodeFromString(IpcEvent.serializer(), str) as IpcEvent.PairingComplete
        assertEquals("Bob's Phone", decoded.deviceName)
        assertEquals("uuid-123", decoded.deviceId)
    }

    @Test
    fun serializePairingFailed() {
        val evt = IpcEvent.PairingFailed("Code mismatch")
        val str = json.encodeToString(IpcEvent.serializer(), evt)
        val decoded = json.decodeFromString(IpcEvent.serializer(), str) as IpcEvent.PairingFailed
        assertEquals("Code mismatch", decoded.reason)
    }

    @Test
    fun serializeLockdownStateChanged() {
        val evt = IpcEvent.LockdownStateChanged(true, "device-001")
        val str = json.encodeToString(IpcEvent.serializer(), evt)
        val decoded = json.decodeFromString(IpcEvent.serializer(), str) as IpcEvent.LockdownStateChanged
        assertTrue(decoded.active)
        assertEquals("device-001", decoded.initiator)
    }

    // ── IpcEnvelope ──

    @Test
    fun envelopeWithRequest() {
        val env = IpcEnvelope(id = "req-1", request = IpcRequest.GetDashboard)
        val str = json.encodeToString(IpcEnvelope.serializer(), env)
        val decoded = json.decodeFromString(IpcEnvelope.serializer(), str)
        assertEquals("req-1", decoded.id)
        assertNotNull(decoded.request)
        assertNull(decoded.response)
        assertNull(decoded.event)
    }

    @Test
    fun envelopeWithResponse() {
        val env = IpcEnvelope(id = "req-1", response = IpcResponse.PairingStarted("111111"))
        val str = json.encodeToString(IpcEnvelope.serializer(), env)
        val decoded = json.decodeFromString(IpcEnvelope.serializer(), str)
        assertNotNull(decoded.response)
        assertNull(decoded.request)
    }

    @Test
    fun envelopeWithEvent() {
        val env = IpcEnvelope(event = IpcEvent.PairingCode("222222"))
        val str = json.encodeToString(IpcEnvelope.serializer(), env)
        val decoded = json.decodeFromString(IpcEnvelope.serializer(), str)
        assertNotNull(decoded.event)
        assertNull(decoded.id) // Events don't need ID
    }

    @Test
    fun envelopeRoundTripFull() {
        val original = IpcEnvelope(
            id = "test-42",
            request = IpcRequest.SubmitPairingCode("999999")
        )
        val roundTripped = json.decodeFromString(
            IpcEnvelope.serializer(),
            json.encodeToString(IpcEnvelope.serializer(), original)
        )
        assertEquals("test-42", roundTripped.id)
        assertTrue(roundTripped.request is IpcRequest.SubmitPairingCode)
        assertEquals("999999", (roundTripped.request as IpcRequest.SubmitPairingCode).code)
    }

    // ── All Request Types Serialize ──

    @Test
    fun allRequestTypesSerialize() {
        val requests: List<IpcRequest> = listOf(
            IpcRequest.GetDashboard,
            IpcRequest.GetIdentity,
            IpcRequest.GetDevices,
            IpcRequest.GetMeshStatus,
            IpcRequest.GetNetwork,
            IpcRequest.GetThreats(10),
            IpcRequest.GetHealth,
            IpcRequest.GetSettings,
            IpcRequest.GetModules,
            IpcRequest.StartPairing,
            IpcRequest.JoinPairing("000000", "target"),
            IpcRequest.Action("test"),
            IpcRequest.GetTopology,
            IpcRequest.GetDeviceRoles,
            IpcRequest.SubmitPairingCode("123456")
        )
        for (req in requests) {
            val str = json.encodeToString(IpcRequest.serializer(), req)
            val decoded = json.decodeFromString(IpcRequest.serializer(), str)
            assertEquals(req::class, decoded::class, "Failed round-trip for ${req::class.simpleName}")
        }
    }

    // ── All Event Types Serialize ──

    @Test
    fun allEventTypesSerialize() {
        val events: List<IpcEvent> = listOf(
            IpcEvent.DashboardUpdated(DashboardData("Clear", 0, "Sentinel", 35, 76, 0, 0, 0L, 0L, "Local Only", 0, "NONE", null, false, null, false)),
            IpcEvent.PairingCode("111111"),
            IpcEvent.PairingComplete("Test", "uuid"),
            IpcEvent.PairingFailed("reason"),
            IpcEvent.LockdownStateChanged(false, null)
        )
        for (evt in events) {
            val str = json.encodeToString(IpcEvent.serializer(), evt)
            val decoded = json.decodeFromString(IpcEvent.serializer(), str)
            assertEquals(evt::class, decoded::class, "Failed round-trip for ${evt::class.simpleName}")
        }
    }
}
