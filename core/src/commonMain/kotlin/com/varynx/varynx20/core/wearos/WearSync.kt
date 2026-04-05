/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.wearos

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.sync.BleAdapter
import com.varynx.varynx20.core.mesh.sync.BlePeer
import com.varynx.varynx20.core.mesh.sync.BleScanCallback
import com.varynx.varynx20.core.mesh.sync.BleGattCallback
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Wear Sync — BLE-only sync layer for Wear OS devices.
 *
 * The watch syncs exclusively via BLE to its paired phone.
 * It does NOT participate in LAN mesh or direct peer-to-peer sync.
 *
 * Sync direction:
 *   Phone → Watch: threat alerts, guardian state, mode changes
 *   Watch → Phone: sensor anomalies, dismiss confirmations, heartbeat
 *
 * Battery optimization:
 *   - Sync interval: 60s idle, 10s during active threat
 *   - No scanning — phone initiates connections
 *   - Minimal payload via delta compression
 */
class WearSync(
    private val guardianLite: GuardianLite
) {
    private var bleAdapter: BleAdapter? = null
    private var _isConnected = false
    private var lastReceiveTime = 0L
    private var lastSendTime = 0L
    private var messagesSent = 0L
    private var messagesReceived = 0L
    private var syncErrors = 0L

    val isConnected: Boolean get() = _isConnected

    /**
     * Start the wear sync layer. Listens for incoming BLE connections from phone.
     */
    fun start(adapter: BleAdapter) {
        this.bleAdapter = adapter

        if (!adapter.isAvailable) {
            GuardianLog.logSystem("wear-sync", "BLE not available on this device")
            return
        }

        adapter.startGattServer(object : BleGattCallback {
            override fun onDataReceived(deviceId: String, data: ByteArray) {
                lastReceiveTime = currentTimeMillis()
                messagesReceived++
                handleIncoming(deviceId, data)
            }

            override fun onConnectionStateChanged(deviceId: String, connected: Boolean) {
                _isConnected = connected
                if (connected) {
                    GuardianLog.logSystem("wear-sync", "Phone connected: $deviceId")
                } else {
                    GuardianLog.logSystem("wear-sync", "Phone disconnected: $deviceId")
                }
            }
        })

        GuardianLog.logSystem("wear-sync", "Wear sync started — waiting for phone connection")
    }

    /**
     * Stop wear sync.
     */
    fun stop() {
        bleAdapter?.stopGattServer()
        _isConnected = false
        GuardianLog.logSystem("wear-sync", "Wear sync stopped")
    }

    /**
     * Send a sensor anomaly report to the paired phone.
     */
    fun sendSensorAnomaly(anomaly: SensorAnomaly) {
        if (!_isConnected) return
        val payload = encodeSensorAnomaly(anomaly)
        sendToPhone(WearMessageType.SENSOR_ANOMALY, payload)
    }

    /**
     * Send a heartbeat to the phone (watch is alive).
     */
    fun sendHeartbeat() {
        if (!_isConnected) return
        val payload = encodeHeartbeat()
        sendToPhone(WearMessageType.HEARTBEAT, payload)
    }

    /**
     * Send an alert dismissal to the phone.
     */
    fun sendDismissal(alertId: String) {
        if (!_isConnected) return
        sendToPhone(WearMessageType.DISMISS_ALERT, alertId.encodeToByteArray())
    }

    /**
     * Get sync statistics.
     */
    fun getStats(): WearSyncStats {
        return WearSyncStats(
            isConnected = _isConnected,
            lastReceiveTime = lastReceiveTime,
            lastSendTime = lastSendTime,
            messagesSent = messagesSent,
            messagesReceived = messagesReceived,
            syncErrors = syncErrors
        )
    }

    // ── Internal ──

    private fun handleIncoming(deviceId: String, data: ByteArray) {
        if (data.size < 2) return

        val messageType = data[0]
        val payload = data.copyOfRange(1, data.size)

        when (messageType) {
            MSG_THREAT_ALERT -> {
                val alert = decodeThreatAlert(deviceId, payload)
                if (alert != null) {
                    guardianLite.onThreatReceived(alert)
                }
            }
            MSG_STATE_SYNC -> {
                val state = decodeGuardianState(payload)
                if (state != null) {
                    guardianLite.onStateSync(state)
                }
            }
            MSG_MODE_CHANGE -> {
                // Mode change handled via state sync
                GuardianLog.logSystem("wear-sync", "Mode change received from phone")
            }
            else -> {
                syncErrors++
                GuardianLog.logSystem("wear-sync", "Unknown message type: $messageType")
            }
        }
    }

    private fun sendToPhone(type: WearMessageType, payload: ByteArray) {
        val pairedId = guardianLite.state.pairedDeviceId ?: return
        val message = byteArrayOf(type.code) + payload
        try {
            bleAdapter?.sendData(pairedId, message)
            lastSendTime = currentTimeMillis()
            messagesSent++
        } catch (e: Exception) {
            syncErrors++
            GuardianLog.logSystem("wear-sync", "Send failed: ${e.message}")
        }
    }

    private fun decodeThreatAlert(deviceId: String, payload: ByteArray): ThreatAlert? {
        // Simple wire format: [level:1][titleLen:2][title:N][desc:rest]
        if (payload.size < 3) return null
        val level = ThreatLevel.entries.getOrNull(payload[0].toInt() and 0xFF) ?: ThreatLevel.NONE
        val titleLen = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        if (payload.size < 3 + titleLen) return null
        val title = payload.copyOfRange(3, 3 + titleLen).decodeToString()
        val desc = if (payload.size > 3 + titleLen) payload.copyOfRange(3 + titleLen, payload.size).decodeToString() else ""
        return ThreatAlert(
            alertId = "${deviceId}_${currentTimeMillis()}",
            sourceDeviceId = deviceId,
            level = level,
            title = title,
            description = desc,
            requiresHaptic = level >= ThreatLevel.HIGH
        )
    }

    private fun decodeGuardianState(payload: ByteArray): GuardianState? {
        // Minimal decode: [threatLevel:1][mode:1][activeModules:2][totalModules:2]
        if (payload.size < 6) return null
        val threat = ThreatLevel.entries.getOrNull(payload[0].toInt() and 0xFF) ?: ThreatLevel.NONE
        val mode = com.varynx.varynx20.core.model.GuardianMode.entries.getOrNull(payload[1].toInt() and 0xFF)
            ?: com.varynx.varynx20.core.model.GuardianMode.SENTINEL
        val active = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        val total = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        return GuardianState(
            overallThreatLevel = threat,
            guardianMode = mode,
            activeModuleCount = active,
            totalModuleCount = total
        )
    }

    private fun encodeSensorAnomaly(anomaly: SensorAnomaly): ByteArray {
        val typeB = anomaly.sensorType.ordinal.toByte()
        val sevB = anomaly.severity.ordinal.toByte()
        val desc = anomaly.description.encodeToByteArray()
        return byteArrayOf(typeB, sevB) + desc
    }

    private fun encodeHeartbeat(): ByteArray {
        val state = guardianLite.state
        return byteArrayOf(
            state.currentThreatLevel.ordinal.toByte(),
            state.alertCount.toByte(),
            state.sensorAnomalyCount.toByte()
        )
    }

    companion object {
        private const val MSG_THREAT_ALERT: Byte = 0x01
        private const val MSG_STATE_SYNC: Byte = 0x02
        private const val MSG_MODE_CHANGE: Byte = 0x03
    }
}

enum class WearMessageType(val code: Byte) {
    HEARTBEAT(0x10),
    SENSOR_ANOMALY(0x11),
    DISMISS_ALERT(0x12)
}

data class WearSyncStats(
    val isConnected: Boolean,
    val lastReceiveTime: Long,
    val lastSendTime: Long,
    val messagesSent: Long,
    val messagesReceived: Long,
    val syncErrors: Long
)
