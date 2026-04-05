/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.transport

import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MeshTransport
import com.varynx.varynx20.core.mesh.MeshTransportListener
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LAN mesh transport: UDP multicast (discovery/heartbeats) + TCP (reliable sync/pairing).
 *
 * UDP multicast on 239.42.42.1:42420 — heartbeats and announce (scoped to LAN).
 * TCP on port 42421 — directed messages, pairing handshake, state sync.
 *
 * Note: On Android, acquire WifiManager.MulticastLock for reliable UDP reception.
 */
class LanMeshTransport(
    private val udpPort: Int = 42420,
    private val tcpPort: Int = 42421
) : MeshTransport {

    private var listener: MeshTransportListener? = null
    private var multicastSocket: MulticastSocket? = null
    private var tcpServer: ServerSocket? = null
    private var udpRecvThread: Thread? = null
    private var tcpAcceptThread: Thread? = null
    @Volatile private var running = false

    private val peerAddresses = ConcurrentHashMap<String, InetAddress>()
    private val peerTcpPorts = ConcurrentHashMap<String, Int>()
    private val activeConnections = AtomicInteger(0)
    private val connectionTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    override val isActive: Boolean get() = running

    companion object {
        private val MULTICAST_GROUP = InetAddress.getByName("239.42.42.1")
        private const val MAX_TCP_CONNECTIONS = 50
        private const val MAX_TCP_MESSAGE_SIZE = 256 * 1024  // 256 KB
        private const val TCP_READ_TIMEOUT_MS = 10_000       // 10s idle timeout
        private const val MAX_CONNECTIONS_PER_IP_PER_MINUTE = 20
    }

    override fun start(listener: MeshTransportListener) {
        this.listener = listener
        running = true

        // UDP multicast socket — scoped to LAN via multicast group
        val udp = MulticastSocket(udpPort).apply {
            reuseAddress = true
            joinGroup(InetSocketAddress(MULTICAST_GROUP, udpPort), NetworkInterface.getByIndex(0))
        }
        this.multicastSocket = udp

        udpRecvThread = Thread({
            val buf = ByteArray(65507)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    udp.receive(packet)
                    val data = buf.copyOfRange(0, packet.length)
                    val envelope = EnvelopeCodec.decode(data) ?: continue
                    trackPeer(envelope.senderId, packet.address, envelope.senderTcpPort)
                    listener.onEnvelopeReceived(envelope)
                } catch (_: SocketException) {
                    // socket closed during shutdown
                } catch (e: Exception) {
                    if (running) listener.onTransportError("UDP recv: ${e.message}")
                }
            }
        }, "varynx-udp-recv").apply { isDaemon = true; start() }

        // TCP accept — with connection cap and per-IP rate limiting
        val tcp = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), tcpPort))
        }
        this.tcpServer = tcp

        tcpAcceptThread = Thread({
            while (running) {
                try {
                    val client = tcp.accept()
                    val clientIp = client.inetAddress.hostAddress ?: "unknown"

                    // Connection cap
                    if (activeConnections.get() >= MAX_TCP_CONNECTIONS) {
                        client.close()
                        listener.onTransportError("TCP cap reached, rejected $clientIp")
                        continue
                    }

                    // Per-IP rate limit
                    if (!checkIpRateLimit(clientIp)) {
                        client.close()
                        listener.onTransportError("TCP rate limit exceeded for $clientIp")
                        continue
                    }

                    client.soTimeout = TCP_READ_TIMEOUT_MS
                    activeConnections.incrementAndGet()
                    Thread({
                        try {
                            handleTcpClient(client)
                        } finally {
                            activeConnections.decrementAndGet()
                        }
                    }, "varynx-tcp-$clientIp").apply { isDaemon = true; start() }
                } catch (_: SocketException) {
                    // server closed during shutdown
                } catch (e: Exception) {
                    if (running) listener.onTransportError("TCP accept: ${e.message}")
                }
            }
        }, "varynx-tcp-accept").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
        try { multicastSocket?.leaveGroup(InetSocketAddress(MULTICAST_GROUP, udpPort), NetworkInterface.getByIndex(0)) } catch (_: Exception) {}
        multicastSocket?.close()
        tcpServer?.close()
    }

    override fun send(envelope: MeshEnvelope) {
        val data = EnvelopeCodec.encode(envelope)
        if (envelope.recipientId == MeshEnvelope.BROADCAST) {
            sendUdpMulticast(data)
        } else {
            val addr = peerAddresses[envelope.recipientId]
            if (addr != null) {
                val port = peerTcpPorts[envelope.recipientId] ?: tcpPort
                sendTcp(addr, port, data)
            } else {
                sendUdpMulticast(data) // fallback for unknown peers
            }
        }
    }

    private fun sendUdpMulticast(data: ByteArray) {
        var sock: MulticastSocket? = null
        try {
            sock = MulticastSocket()
            val packet = DatagramPacket(data, data.size, MULTICAST_GROUP, udpPort)
            sock.send(packet)
        } catch (e: Exception) {
            listener?.onTransportError("UDP send: ${e.message}")
        } finally {
            sock?.close()
        }
    }

    private fun sendTcp(addr: InetAddress, port: Int, data: ByteArray) {
        var sock: Socket? = null
        try {
            sock = Socket()
            sock.connect(InetSocketAddress(addr, port), 5000)
            val out = sock.getOutputStream()
            // Length-prefixed framing: 4-byte big-endian length + payload
            val header = byteArrayOf(
                (data.size shr 24).toByte(),
                (data.size shr 16).toByte(),
                (data.size shr 8).toByte(),
                data.size.toByte()
            )
            out.write(header)
            out.write(data)
            out.flush()
        } catch (e: Exception) {
            listener?.onTransportError("TCP send to ${addr.hostAddress}: ${e.message}")
        } finally {
            try { sock?.close() } catch (_: Exception) {}
        }
    }

    private fun handleTcpClient(client: Socket) {
        try {
            val input = client.getInputStream()
            // Read 4-byte length header
            val header = ByteArray(4)
            var read = 0
            while (read < 4) {
                val n = input.read(header, read, 4 - read)
                if (n < 0) return
                read += n
            }
            val len = ((header[0].toInt() and 0xFF) shl 24) or
                      ((header[1].toInt() and 0xFF) shl 16) or
                      ((header[2].toInt() and 0xFF) shl 8) or
                      (header[3].toInt() and 0xFF)
            if (len > MAX_TCP_MESSAGE_SIZE) {
                listener?.onTransportError("TCP oversized message rejected: $len bytes")
                return // reject oversized messages
            }

            val data = ByteArray(len)
            read = 0
            while (read < len) {
                val n = input.read(data, read, len - read)
                if (n < 0) return
                read += n
            }

            val envelope = EnvelopeCodec.decode(data) ?: return
            trackPeer(envelope.senderId, client.inetAddress, envelope.senderTcpPort)
            listener?.onEnvelopeReceived(envelope)
        } catch (e: Exception) {
            if (running) listener?.onTransportError("TCP handle: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun trackPeer(deviceId: String, address: InetAddress, remoteTcpPort: Int = tcpPort) {
        val prev = peerAddresses.put(deviceId, address)
        peerTcpPorts[deviceId] = remoteTcpPort
        if (prev == null) {
            listener?.onPeerDiscovered(deviceId, address.hostAddress ?: "")
        }
    }

    private fun checkIpRateLimit(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = connectionTimestamps.getOrPut(ip) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > 60_000 }
            if (timestamps.size >= MAX_CONNECTIONS_PER_IP_PER_MINUTE) return false
            timestamps.add(now)
        }
        return true
    }
}
