/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.mesh.AndroidMeshBridge
import kotlinx.coroutines.*

class GuardianService : Service() {

    private lateinit var organism: GuardianOrganism
    private lateinit var meshBridge: AndroidMeshBridge
    private lateinit var detectionProvider: AndroidDetectionProvider
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ModuleRegistry.initialize()
        organism = GuardianOrganism()
        organism.awaken()

        detectionProvider = AndroidDetectionProvider(this)

        // Start LAN mesh for live Android ↔ Desktop discovery
        meshBridge = AndroidMeshBridge(this)
        GuardianServiceBridge.attach(meshBridge)
        try {
            meshBridge.start()
        } catch (e: Exception) {
            android.util.Log.e("GuardianService", "Mesh start failed (will retry): ${e.message}")
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ThreatLevel.NONE))

        // Start the guardian cycle — this is the organism's heartbeat
        serviceScope.launch {
            while (isActive) {
                try {
                    processCycle()
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        android.util.Log.e("GuardianService", "Cycle error: ${e.message}")
                    }
                }
                delay(CYCLE_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        meshBridge.stop()
        GuardianServiceBridge.detach()
        organism.sleep()
        super.onDestroy()
    }

    /**
     * Runs one complete guardian cycle:
     * Core detects → Engine interprets → Reflex responds → Identity expresses.
     * Also ticks the mesh engine for peer heartbeats and sync.
     */
    fun processCycle() {
        // Feed real Android sensor data into protection modules before the cycle
        detectionProvider.feedModules(organism.core.getModules())
        val state = organism.cycle()
        GuardianServiceBridge.updateState(state)
        meshBridge.tick(state)
        updateNotification(state.overallThreatLevel)
    }

    private fun updateNotification(level: ThreatLevel) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(level))
    }

    private fun buildNotification(level: ThreatLevel): Notification {
        val mode = if (::organism.isInitialized) organism.getCurrentMode().label else "Initializing"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VARYNX Guardian Active")
            .setContentText("Status: ${level.label} | Mode: $mode")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Varynx Guardian",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Varynx 2.0 guardian protection status"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "varynx_guardian"
        private const val NOTIFICATION_ID = 2020
        private const val CYCLE_INTERVAL_MS = 30_000L
    }
}
