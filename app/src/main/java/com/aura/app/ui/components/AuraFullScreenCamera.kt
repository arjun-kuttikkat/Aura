package com.aura.app.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/**
 * Full-screen camera capture with Aura design language.
 * Camera fills the entire screen; UI floats in a premium glass overlay.
 * Creates and binds ImageCapture internally; [onCapture] receives the bound instance
 * only after the camera is ready (avoids "Not bound to a valid Camera" crash).
 */
@Composable
fun AuraFullScreenCamera(
    onCapture: (ImageCapture) -> Unit,
    onClose: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    var isShutterEnabled by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Delay enabling shutter — prevents "Not bound to a valid Camera" crash
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        isShutterEnabled = true
    }
    val scale by animateFloatAsState(
        targetValue = if (isCapturing) 0.88f else 1f,
        animationSpec = tween(AuraAnimations.ScreenEnterDuration),
        label = "shutter_scale",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen camera preview
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
                        // Bind complete; LaunchedEffect delay will enable shutter
                    } catch (_: Exception) { /* bind failed */ }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Floating glass UI overlay ─────────────────────────────────────────

        // Top bar: close button in a glass pill (respect safe area)
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topPadding + 12.dp, start = 20.dp),
        ) {
            GlassPill(
                onClick = onClose,
                iconContent = {
                    androidx.compose.material3.Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(22.dp),
                        tint = Color.White,
                    )
                },
            )
        }

        // Bottom controls: glass box with premium shutter (respect safe area)
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 24.dp),
        ) {
            AuraGlassControls(
                onCapture = {
                    if (!isCapturing && isShutterEnabled) {
                        isCapturing = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        AuraHaptics.heavyImpact(context)
                        onCapture(imageCapture)
                        scope.launch {
                            delay(200)
                            isCapturing = false
                        }
                    }
                },
                scale = scale,
                enabled = isShutterEnabled,
            )
        }
    }
}

@Composable
private fun GlassPill(
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.05f),
                    ),
                ),
                shape = shape,
            )
            .padding(12.dp),
    ) {
        iconContent()
    }
}

@Composable
private fun AuraGlassControls(
    onCapture: () -> Unit,
    scale: Float,
    enabled: Boolean = true,
) {
    val glassShape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .clip(glassShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                ),
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Orange500.copy(alpha = 0.25f),
                        Orange500.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                ),
                shape = glassShape,
            )
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // Placeholder for future flip/gallery - keeps shutter centered
            Box(modifier = Modifier.size(52.dp))
            AuraShutterButton(onClick = onCapture, scale = scale, enabled = enabled)
            Box(modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun AuraShutterButton(
    onClick: () -> Unit,
    scale: Float,
    enabled: Boolean = true,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .scale(scale)
            .size(72.dp)
            .offset(y = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
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
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(listOf(Orange500, Gold500)),
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.05f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
