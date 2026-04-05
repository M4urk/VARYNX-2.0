/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.mesh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.HeartbeatPayload
import com.varynx.varynx20.core.mesh.PeerState
import com.varynx.varynx20.mesh.AndroidMeshBridge
import com.varynx.varynx20.mesh.TrustEdgeInfo
import com.varynx.varynx20.service.GuardianServiceBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MeshUiState(
    val meshActive: Boolean = false,
    val localDeviceId: String = "",
    val localDisplayName: String = "",
    val trustedPeers: Map<String, PeerState> = emptyMap(),
    val discoveredPeers: Map<String, HeartbeatPayload> = emptyMap(),
    val savedTrustedEdges: List<TrustEdgeInfo> = emptyList(),
    val pairingCode: String? = null,
    val pairingInProgress: Boolean = false,
    val pairingError: String? = null,
    val pairingSuccess: DeviceIdentity? = null
)

class MeshViewModel : ViewModel() {

    private val _state = MutableStateFlow(MeshUiState())
    val state: StateFlow<MeshUiState> = _state.asStateFlow()

    private val bridge: AndroidMeshBridge?
        get() = GuardianServiceBridge.current

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshState()
                delay(2_000L)
            }
        }
    }

    private fun refreshState() {
        val b = bridge
        if (b == null) {
            _state.value = _state.value.copy(meshActive = false)
            return
        }
        _state.value = _state.value.copy(
            meshActive = b.meshActive,
            localDeviceId = b.identity.deviceId,
            localDisplayName = b.identity.displayName,
            trustedPeers = b.trustedPeers,
            discoveredPeers = b.discoveredPeers,
            savedTrustedEdges = b.getTrustedEdges()
        )
    }

    fun startPairing() {
        val b = bridge ?: return
        _state.value = _state.value.copy(
            pairingInProgress = true,
            pairingCode = null,
            pairingError = null,
            pairingSuccess = null
        )
        // Set callbacks BEFORE starting — the listener fires synchronously
        b.onPairingCode = { c ->
            _state.value = _state.value.copy(pairingCode = c)
        }
        b.onPairingComplete = { identity ->
            _state.value = _state.value.copy(
                pairingInProgress = false,
                pairingSuccess = identity,
                pairingCode = null
            )
        }
        b.onPairingFailed = { reason ->
            _state.value = _state.value.copy(
                pairingInProgress = false,
                pairingError = reason,
                pairingCode = null
            )
        }
        val code = b.startPairing()
        _state.value = _state.value.copy(pairingCode = code)
    }

    fun joinPairing(code: String, targetDeviceId: String) {
        val b = bridge ?: return
        _state.value = _state.value.copy(
            pairingInProgress = true,
            pairingError = null,
            pairingSuccess = null
        )
        // Set callbacks BEFORE joining — response can arrive very fast on LAN
        b.onPairingComplete = { identity ->
            _state.value = _state.value.copy(
                pairingInProgress = false,
                pairingSuccess = identity,
                pairingCode = null
            )
        }
        b.onPairingFailed = { reason ->
            _state.value = _state.value.copy(
                pairingInProgress = false,
                pairingError = reason,
                pairingCode = null
            )
        }
        // Use broadcast if no specific target — user doesn't know UUIDs
        val effectiveTarget = targetDeviceId.ifEmpty { com.varynx.varynx20.core.mesh.MeshEnvelope.BROADCAST }
        b.joinPairing(code, effectiveTarget)
    }

    fun dismissPairingResult() {
        _state.value = _state.value.copy(
            pairingCode = null,
            pairingError = null,
            pairingSuccess = null,
            pairingInProgress = false
        )
    }

    fun revokeTrust(deviceId: String) {
        bridge?.revokeTrust(deviceId)
        refreshState()
    }
}
