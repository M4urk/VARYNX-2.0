/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.service

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.varynx.varynx20.core.protection.*
import java.io.File
import java.net.NetworkInterface

/**
 * Collects real Android device data and feeds it into protection modules'
 * analyze/check methods each guardian cycle.
 *
 * This bridges actual Android platform APIs to the core scoring engines
 * that were previously data-starved (pure pattern matchers with no input).
 */
class AndroidDetectionProvider(private val context: Context) {

    /**
     * Run all detection checks and feed results into the given modules.
     * Call once per guardian cycle, before organism.cycle().
     */
    fun feedModules(modules: List<ProtectionModule>) {
        for (module in modules) {
            when (module) {
                is DeviceStateMonitor -> feedDeviceState(module)
                is NetworkIntegrity -> feedNetworkIntegrity(module)
                is ClipboardShield -> feedClipboard(module)
                is OverlayDetector -> feedOverlay(module)
                is InstallMonitor -> feedInstallMonitor(module)
            }
        }
    }

    private fun feedDeviceState(module: DeviceStateMonitor) {
        module.checkDeviceState(
            isRooted = checkRooted(),
            isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            isEmulator = checkEmulator(),
            isDeveloperMode = Settings.Global.getInt(
                context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) != 0,
            isUsbDebugging = Settings.Global.getInt(
                context.contentResolver, Settings.Global.ADB_ENABLED, 0
            ) != 0
        )
    }

    private fun feedNetworkIntegrity(module: NetworkIntegrity) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val linkProps = if (network != null) cm.getLinkProperties(network) else null

        module.analyzeNetwork(
            isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            isOpenWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).not(),
            dnsServers = linkProps?.dnsServers?.map { it.hostAddress ?: "" } ?: emptyList(),
            hasProxy = linkProps?.httpProxy != null,
            gatewayMac = null // Not easily available on modern Android without ARP table parsing
        )
    }

    private fun feedClipboard(module: ClipboardShield) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    if (text.isNotEmpty()) {
                        module.analyzeClipboard(text)
                    }
                }
            }
        } catch (_: SecurityException) {
            // Android 10+ restricts background clipboard access — expected
        }
    }

    private fun feedOverlay(module: OverlayDetector) {
        // Only flag apps that actually declare SYSTEM_ALERT_WINDOW permission.
        // Settings.canDrawOverlays() checks *our* app — not other apps.
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(android.content.pm.PackageManager.GET_PERMISSIONS)
            for (pkg in packages) {
                if (pkg.packageName == context.packageName) continue
                val perms = pkg.requestedPermissions ?: continue
                val hasOverlay = perms.any { it == android.Manifest.permission.SYSTEM_ALERT_WINDOW }
                if (hasOverlay) {
                    module.analyzeOverlay(
                        packageName = pkg.packageName,
                        hasSystemAlertWindow = true,
                        isDrawingOverlay = true
                    )
                }
            }
        } catch (_: Exception) {
            // PackageManager query may fail on restricted profiles
        }
    }

    private fun feedInstallMonitor(module: InstallMonitor) {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val installer = try {
                    pm.getInstallSourceInfo(pkg.packageName).installingPackageName
                } catch (_: Exception) { null }
                module.analyzeInstall(
                    packageName = pkg.packageName,
                    installerPackage = installer,
                    appLabel = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                )
            }
        } catch (_: Exception) {
            // getInstallSourceInfo may throw on older installs
        }
    }

    // ── Device state helpers ──

    private fun checkRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/system/xbin/su", "/system/bin/su",
            "/sbin/su", "/data/local/xbin/su", "/data/local/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true)
                || Build.MODEL.contains("Emulator", ignoreCase = true)
                || Build.MODEL.contains("Android SDK", ignoreCase = true)
                || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT
    }
}
