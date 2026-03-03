package com.aura.app.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aura.app.ui.theme.AuraAnimations
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Embedded camera that fills the given modifier (e.g. photo area on add product page).
 * Uses in-memory capture (same pattern as AuraCheckScreen) for stability.
 * Delay + bind check prevent "Not bound to a valid Camera" crashes.
 */
@Composable
fun AuraEmbeddedCamera(
    modifier: Modifier = Modifier,
    onPhotoCaptured: (String) -> Unit,
    onCaptureError: (String) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val onPhotoCapturedState = rememberUpdatedState(onPhotoCaptured)
    val onCaptureErrorState = rememberUpdatedState(onCaptureError)
    var isCapturing by remember { mutableStateOf(false) }
    var isShutterEnabled by remember { mutableStateOf(false) }
    var isBound by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(isBound) {
        if (isBound) {
            kotlinx.coroutines.delay(1200)
            isShutterEnabled = true
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isCapturing) 0.92f else 1f,
        animationSpec = tween(AuraAnimations.ScreenEnterDuration),
        label = "shutter_scale",
    )

    Box(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                        isBound = true
                    } catch (_: Exception) { /* bind failed */ }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Shutter button overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        ) {
            androidx.compose.material3.IconButton(
                onClick = {
                    if (!isCapturing && isShutterEnabled) {
                        isCapturing = true
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            AuraHaptics.heavyImpact(context)
                        } catch (_: Exception) { }
                        val executor = ContextCompat.getMainExecutor(context)
                        val onSuccess = onPhotoCapturedState
                        val onError = onCaptureErrorState
                        try {
                            imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
                                    scope.launch {
                                        try {
                                            val planes = imageProxy.planes
                                            if (!planes.isNullOrEmpty()) {
                                                val buffer = planes[0].buffer
                                                val bytes = ByteArray(buffer.remaining())
                                                buffer.get(bytes)
                                                withContext(Dispatchers.IO) {
                                                    val file = File(context.cacheDir, "aura_${System.currentTimeMillis()}.jpg")
                                                    file.writeBytes(bytes)
                                                    val path = file.absolutePath
                                                    onSuccess.value(path)
                                                }
                                            } else {
                                                onError.value("Invalid image")
                                            }
                                        } catch (e: Exception) {
                                            onError.value(e.message ?: "Save failed")
                                        } finally {
                                            try { imageProxy.close() } catch (_: Exception) { }
                                            isCapturing = false
                                        }
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    onError.value(exception.message ?: "Capture failed")
                                    isCapturing = false
                                }
                            })
                        } catch (e: Exception) {
                            onCaptureErrorState.value(e.message ?: "Capture failed")
                            isCapturing = false
                        }
                    }
                },
                enabled = isShutterEnabled,
                modifier = Modifier
                    .scale(scale)
                    .size(64.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(Orange500, Gold500)),
                            shape = CircleShape,
                        )
                        .padding(6.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Gold500.copy(alpha = 0.4f),
                                    Orange500.copy(alpha = 0.5f),
                                    DarkCard,
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent,
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .padding(10.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(Orange500, Gold500)),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
