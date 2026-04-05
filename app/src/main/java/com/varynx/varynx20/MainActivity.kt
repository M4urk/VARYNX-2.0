/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.service.GuardianService
import com.varynx.varynx20.ui.components.MeshBackground
import com.varynx.varynx20.ui.navigation.VarynxNavGraph
import com.varynx.varynx20.ui.theme.VarynxSecondary
import com.varynx.varynx20.ui.theme.Varynx20Theme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Start service after permission prompt completes (granted or not)
        startGuardianService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the guardian
        ModuleRegistry.initialize()

        // Request battery optimization exemption for reliable guardian operation
        requestBatteryExemption()

        // Request runtime permissions then start service
        requestMeshPermissions()

        setContent {
            Varynx20Theme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VarynxSecondary)
                ) {
                    MeshBackground()
                    val navController = rememberNavController()
                    VarynxNavGraph(navController = navController)
                }
            }
        }
    }

    private fun requestMeshPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            startGuardianService()
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startGuardianService() {
        startForegroundService(Intent(this, GuardianService::class.java))
    }
}