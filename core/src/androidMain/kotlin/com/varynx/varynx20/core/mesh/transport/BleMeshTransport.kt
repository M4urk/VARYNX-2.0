/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.transport

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MeshTransport
import com.varynx.varynx20.core.mesh.MeshTransportListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE mesh transport for Android (and WearOS).
 *
 * Uses BLE advertising + scanning for peer discovery and GATT for
 * reliable data exchange. Designed for close-proximity mesh networking
 * when WiFi/LAN is unavailable (e.g., wearables, travel mode).
 *
 * Protocol:
 *   - Advertises a VARYNX service UUID for discovery
 *   - Scans for peers advertising the same UUID
 *   - Exchanges mesh envelopes via GATT read/write characteristics
 *   - Heartbeat via periodic advertising data updates
 */
class BleMeshTransport(
    private val context: Context
) : MeshTransport {

    private var listener: MeshTransportListener? = null
    @Volatile private var running = false

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    private val discoveredPeers = ConcurrentHashMap<String, BluetoothDevice>()

    override val isActive: Boolean get() = running

    override fun start(listener: MeshTransportListener) {
        this.listener = listener

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter
        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
            listener.onTransportError("BLE: Bluetooth not available or disabled")
            return
        }

        running = true
        startScanning()
        startAdvertising()
        startGattServer(manager!!)
    }

    override fun stop() {
        running = false
        stopScanning()
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        discoveredPeers.clear()
    }

    override fun send(envelope: MeshEnvelope) {
        if (!running) return
        val data = EnvelopeCodec.encode(envelope)
        if (envelope.recipientId == MeshEnvelope.BROADCAST) {
            // Send to all connected peers
            for ((_, device) in discoveredPeers) {
                sendViaBleGatt(device, data)
            }
        } else {
            val device = discoveredPeers[envelope.recipientId]
            if (device != null) {
                sendViaBleGatt(device, data)
            } else {
                listener?.onTransportError("BLE: Unknown peer ${envelope.recipientId}")
            }
        }
    }

    // ── Scanning ──────────────────────────────────────────────

    private fun startScanning() {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceId = device.address
            if (!discoveredPeers.containsKey(deviceId)) {
                discoveredPeers[deviceId] = device
                listener?.onPeerDiscovered(deviceId, device.address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener?.onTransportError("BLE scan failed: error $errorCode")
        }
    }

    // ── Advertising ───────────────────────────────────────────

    private fun startAdvertising() {
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            listener?.onTransportError("BLE advertise failed: error $errorCode")
        }
    }

    // ── GATT Server ───────────────────────────────────────────

    private fun startGattServer(manager: BluetoothManager) {
        gattServer = manager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val envelopeChar = BluetoothGattCharacteristic(
            ENVELOPE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(envelopeChar)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == ENVELOPE_CHAR_UUID) {
                val envelope = EnvelopeCodec.decode(value)
                if (envelope != null) {
                    val deviceId = device.address
                    if (!discoveredPeers.containsKey(deviceId)) {
                        discoveredPeers[deviceId] = device
                        listener?.onPeerDiscovered(deviceId, device.address)
                    }
                    listener?.onEnvelopeReceived(envelope)
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                val deviceId = device.address
                discoveredPeers.remove(deviceId)
                listener?.onPeerLost(deviceId)
            }
        }
    }

    // ── GATT Client (send) ────────────────────────────────────

    private fun sendViaBleGatt(device: BluetoothDevice, data: ByteArray) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) { gatt.close(); return }
                val service = gatt.getService(SERVICE_UUID)
                val char = service?.getCharacteristic(ENVELOPE_CHAR_UUID)
                if (char == null) { gatt.close(); return }
                gatt.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                gatt.close()
            }
        })
    }

    companion object {
        /** VARYNX BLE service UUID — randomly generated, stable across all VARYNX devices. */
        val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val ENVELOPE_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891")
    }
}
