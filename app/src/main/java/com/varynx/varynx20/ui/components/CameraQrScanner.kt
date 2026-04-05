/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@Composable
fun CameraQrScanner(
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cameraProvider = future.get() }, ContextCompat.getMainExecutor(context))
        onDispose { cameraProvider?.unbindAll() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (cameraProvider != null) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val scanner = BarcodeScanning.getClient()
                    val scannedCodes = mutableSetOf<String>()

                    @androidx.camera.core.ExperimentalGetImage
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER)
                                )
                                .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                                val mediaImage = proxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        proxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val value = barcode.rawValue ?: continue
                                                if (value.isNotBlank() && scannedCodes.add(value)) {
                                                    onQrCodeScanned(value)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                } else {
                                    proxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider?.unbindAll()
                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (_: Exception) { }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder overlay
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
            ) {
                // Corner markers
                val cornerColor = Color(0xFF00E5FF)
                val cornerSize = 28.dp
                val cornerWidth = 3.dp

                // Top-left
                Box(Modifier.align(Alignment.TopStart).width(cornerSize).height(cornerWidth).background(cornerColor))
                Box(Modifier.align(Alignment.TopStart).width(cornerWidth).height(cornerSize).background(cornerColor))
                // Top-right
                Box(Modifier.align(Alignment.TopEnd).width(cornerSize).height(cornerWidth).background(cornerColor))
                Box(Modifier.align(Alignment.TopEnd).width(cornerWidth).height(cornerSize).background(cornerColor))
                // Bottom-left
                Box(Modifier.align(Alignment.BottomStart).width(cornerSize).height(cornerWidth).background(cornerColor))
                Box(Modifier.align(Alignment.BottomStart).width(cornerWidth).height(cornerSize).background(cornerColor))
                // Bottom-right
                Box(Modifier.align(Alignment.BottomEnd).width(cornerSize).height(cornerWidth).background(cornerColor))
                Box(Modifier.align(Alignment.BottomEnd).width(cornerWidth).height(cornerSize).background(cornerColor))
            }
        }
    }
}
