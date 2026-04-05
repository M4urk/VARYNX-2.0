/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.protection.QrScamScanner
import com.varynx.varynx20.core.protection.QrScanResult
import com.varynx.varynx20.ui.components.CameraQrScanner
import com.varynx.varynx20.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<QrScanResult?>(null) }
    var scanComplete by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf(true) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val scanner = remember { QrScamScanner().also { it.activate() } }
    val context = LocalContext.current

    // Check existing permission on launch
    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Request camera permission in camera mode if not granted
    LaunchedEffect(cameraMode) {
        if (cameraMode && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun analyzeContent(content: String) {
        inputText = content
        scanResult = scanner.analyzeQrContent(content.trim())
        scanComplete = true
        cameraMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("QR / SCAM SCAN", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VarynxPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        cameraMode = !cameraMode
                        if (cameraMode) { scanComplete = false; scanResult = null; inputText = "" }
                    }) {
                        Icon(
                            imageVector = if (cameraMode) Icons.Default.Edit else Icons.Default.CameraAlt,
                            contentDescription = if (cameraMode) "Paste mode" else "Camera mode",
                            tint = VarynxPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = when {
                            scanComplete && scanResult?.safe == true -> ModuleActive
                            scanComplete && scanResult?.safe == false -> SeverityHigh
                            else -> VarynxPrimary
                        },
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when {
                            scanComplete && scanResult?.safe == true -> "SAFE"
                            scanComplete && scanResult?.safe == false -> "THREAT DETECTED"
                            cameraMode -> "POINT AT QR CODE"
                            else -> "QR CODE SAFETY CHECK"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = when {
                            scanComplete && scanResult?.safe == true -> ModuleActive
                            scanComplete && scanResult?.safe == false -> SeverityHigh
                            else -> VarynxOnSurface
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (cameraMode) "Camera scans automatically \u00B7 Tap \u270E for manual paste"
                               else "URL risk analysis \u00B7 Scam heuristics \u00B7 Offline pattern detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = VarynxOnSurfaceFaint
                    )
                }
            }

            // Camera viewfinder
            if (cameraMode && hasCameraPermission) {
                item {
                    CameraQrScanner(
                        onQrCodeScanned = { content -> analyzeContent(content) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else if (cameraMode && !hasCameraPermission) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VarynxSurfaceVariant)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Camera permission required",
                            style = MaterialTheme.typography.titleSmall,
                            color = VarynxOnSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VarynxPrimary,
                                contentColor = VarynxOnPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("GRANT CAMERA ACCESS")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Manual paste input (non-camera mode)
            if (!cameraMode) {
                item {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Paste QR content or URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VarynxPrimary,
                            unfocusedBorderColor = VarynxSurfaceElevated,
                            focusedLabelColor = VarynxPrimary,
                            unfocusedLabelColor = VarynxOnSurfaceFaint,
                            cursorColor = VarynxPrimary,
                            focusedTextColor = VarynxOnSurface,
                            unfocusedTextColor = VarynxOnSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (inputText.isNotBlank()) analyzeContent(inputText)
                        }),
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Analyze button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { if (inputText.isNotBlank()) analyzeContent(inputText) },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VarynxPrimary,
                                contentColor = VarynxOnPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ANALYZE")
                        }
                        OutlinedButton(
                            onClick = {
                                cameraMode = true; scanComplete = false; scanResult = null; inputText = ""
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VarynxPrimary)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SCAN AGAIN")
                        }
                    }
                }
            }

            // Result
            if (scanComplete && scanResult != null) {
                val result = scanResult!!

                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "ANALYSIS",
                        style = MaterialTheme.typography.labelLarge,
                        color = VarynxOnSurfaceFaint,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                // Payload type + threat level summary
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VarynxSurfaceVariant)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Type: ${result.payloadType.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = VarynxOnSurface
                            )
                            Text(
                                text = result.threatLevel.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (result.threatLevel) {
                                    ThreatLevel.CRITICAL -> SeverityCritical
                                    ThreatLevel.HIGH -> SeverityHigh
                                    ThreatLevel.MEDIUM -> SeverityMedium
                                    ThreatLevel.LOW -> SeverityLow
                                    else -> ModuleActive
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (result.threatLevel) {
                                            ThreatLevel.CRITICAL -> SeverityCritical.copy(alpha = 0.12f)
                                            ThreatLevel.HIGH -> SeverityHigh.copy(alpha = 0.12f)
                                            ThreatLevel.MEDIUM -> SeverityMedium.copy(alpha = 0.12f)
                                            ThreatLevel.LOW -> SeverityLow.copy(alpha = 0.12f)
                                            else -> ModuleActive.copy(alpha = 0.12f)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Show scanned content
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = inputText,
                            style = MaterialTheme.typography.bodySmall,
                            color = VarynxOnSurfaceDim,
                            maxLines = 3
                        )
                    }
                }

                // Findings
                if (result.findings.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VarynxSurfaceVariant)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ModuleActive,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "No threats detected. Content appears safe.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VarynxOnSurface
                            )
                        }
                    }
                } else {
                    items(result.findings) { finding ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VarynxSurfaceVariant)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SeverityMedium,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = finding,
                                style = MaterialTheme.typography.bodySmall,
                                color = VarynxOnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
