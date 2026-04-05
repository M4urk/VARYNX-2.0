/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service

import com.varynx.service.ServiceLoop.guardianCycle
import com.varynx.service.ServiceLoop.meshCycle
import com.varynx.service.ipc.*
import kotlinx.coroutines.*

fun main(args: Array<String>) {
    println("""
        ╔════════════════════════════════════════════════╗
        ║  VARYNX 2.0 — Guardian Service                ║
        ║  Offline-first · Mesh-ready · OS-grade        ║
        ╚════════════════════════════════════════════════╝
    """.trimIndent())

    val port = args.firstOrNull()?.toIntOrNull() ?: 42400
    val state = VarynxServiceState()
    val handler = IpcHandler(state)
    val ipc = IpcServer(handler, port)

    val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    state.onPairingCodeCallback = { code ->
        serviceScope.launch { ipc.pushEvent(IpcEvent.PairingCode(code)) }
    }
    state.onPairingCompleteCallback = { identity ->
        serviceScope.launch { ipc.pushEvent(IpcEvent.PairingComplete(identity.displayName, identity.deviceId)) }
    }
    state.onPairingFailedCallback = { reason ->
        serviceScope.launch { ipc.pushEvent(IpcEvent.PairingFailed(reason)) }
    }

    ipc.start()
    println("[VARYNX] IPC on 127.0.0.1:$port")

    state.startMesh()

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[VARYNX] Shutting down...")
        ipc.stop()
        state.shutdown()
    })

    runBlocking {
        launch { guardianCycle(state, ipc) { println("[VARYNX] $it") } }
        launch { meshCycle(state, ipc) { println("[VARYNX] $it") } }
    }
}
