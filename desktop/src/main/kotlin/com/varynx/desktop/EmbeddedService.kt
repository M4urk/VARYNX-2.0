/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.desktop

import com.varynx.service.ServiceLoop.guardianCycle
import com.varynx.service.ServiceLoop.meshCycle
import com.varynx.service.VarynxServiceState
import com.varynx.service.ipc.*
import kotlinx.coroutines.*
import java.net.Socket

object EmbeddedService {

    private const val PORT = 42400
    @Volatile private var started = false
    @Volatile var authToken: String? = null
        private set
    private val lock = Any()

    fun start() {
        synchronized(lock) {
            if (started) return
            if (isPortInUse()) { started = true; return }
            started = true
        }

        val thread = Thread({
            try {
                val state = VarynxServiceState()
                val handler = IpcHandler(state)
                val ipc = IpcServer(handler, PORT)
                authToken = ipc.authToken

                val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                state.onPairingCodeCallback = { code -> serviceScope.launch { ipc.pushEvent(IpcEvent.PairingCode(code)) } }
                state.onPairingCompleteCallback = { id -> serviceScope.launch { ipc.pushEvent(IpcEvent.PairingComplete(id.displayName, id.deviceId)) } }
                state.onPairingFailedCallback = { reason -> serviceScope.launch { ipc.pushEvent(IpcEvent.PairingFailed(reason)) } }

                ipc.start()
                state.startMesh()

                Runtime.getRuntime().addShutdownHook(Thread { ipc.stop(); state.shutdown() })

                runBlocking {
                    launch { guardianCycle(state, ipc) { System.err.println("[VARYNX] $it") } }
                    launch { meshCycle(state, ipc) { System.err.println("[VARYNX] $it") } }
                }
            } catch (e: Exception) {
                System.err.println("[VARYNX] Service failed: ${e.message}")
                e.printStackTrace()
            }
        }, "varynx-service")

        thread.isDaemon = true
        thread.start()

        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            if (isPortInUse()) return
            Thread.sleep(200)
        }
        System.err.println("[VARYNX] Service did not start within 8s")
    }

    private fun isPortInUse(): Boolean =
        try { Socket("127.0.0.1", PORT).use { true } } catch (_: Exception) { false }
}
