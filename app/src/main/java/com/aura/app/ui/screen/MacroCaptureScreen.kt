@file:OptIn(ExperimentalMaterial3Api::class)
package com.aura.app.ui.screen

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.aura.app.ui.components.AuraFullScreenCamera
import com.aura.app.ui.components.AuraHaptics
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.util.breathe
import com.aura.app.utils.TextureHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val MACRO_ANGLES = listOf(
    "Front view — full item",
    "Side angle — profile",
    "Detail — texture or serial",
    "Final angle — verification",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroCaptureScreen(
    onComplete: (macroPaths: List<String>, textureHash: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var macroPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    if (!hasCameraPermission) {
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkVoid),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission needed", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    if (macroPhotos.size >= 4) {
        // All 4 captured — show completion, compute texture hash on Confirm
        MacroCompleteStep(
            macroPaths = macroPhotos,
            onConfirm = { hash ->
                AuraHaptics.lightTap(haptic)
                onComplete(macroPhotos, hash)
            },
            onRetake = {
                AuraHaptics.subtleTap(haptic)
                macroPhotos = emptyList()
            },
            onBack = onBack,
        )
        return
    }

    val currentAngle = MACRO_ANGLES.getOrNull(macroPhotos.size) ?: "Capture"

    Box(modifier = Modifier.fillMaxSize().background(DarkVoid)) {
        AuraFullScreenCamera(
            onClose = {
                AuraHaptics.subtleTap(haptic)
                if (macroPhotos.isNotEmpty()) {
                    macroPhotos = macroPhotos.dropLast(1)
                } else {
                    onBack()
                }
            },
            onCapture = { imageCapture ->
                val photoFile = File(context.cacheDir, "aura_macro_${macroPhotos.size}_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                macroPhotos = macroPhotos + photoFile.absolutePath
                                errorMsg = null
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                errorMsg = exception.message ?: "Capture failed"
                            }
                        }
                    }
                )
            },
        )

        // Overlay: step indicator + guidance
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(top = 56.dp, start = 20.dp, end = 20.dp),
        ) {
            Text(
                "Macro capture",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${macroPhotos.size + 1}/4 • $currentAngle",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(MACRO_ANGLES) { i, _ ->
                    val captured = i < macroPhotos.size
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (captured) Orange500.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = 0.2f),
                            )
                            .border(
                                2.dp,
                                if (captured) Gold500 else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (captured) {
                            AsyncImage(
                                model = "file://${macroPhotos.getOrNull(i)}",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text("${i + 1}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        errorMsg?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            ) {
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MacroCompleteStep(
    macroPaths: List<String>,
    onConfirm: (textureHash: String) -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val infiniteTransition = rememberInfiniteTransition(label = "macro_complete")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { AuraHaptics.subtleTap(haptic); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = true,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                label = "complete",
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Orange500, Gold500)),
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "4 photos captured",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White,
                    )
                    Text(
                        "Texture fingerprint ready",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(macroPaths) { _, path ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, Orange500.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            ) {
                                AsyncImage(
                                    model = "file://$path",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    val hash = computeTextureHash(macroPaths.firstOrNull().orEmpty())
                                    withContext(Dispatchers.Main) { onConfirm(hash) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .breathe(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Orange500),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Continue", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        androidx.compose.material3.TextButton(onClick = onRetake) {
                            Text("Retake all", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun computeTextureHash(imagePath: String): String = withContext(Dispatchers.IO) {
    if (imagePath.isBlank()) return@withContext "fp_none"
    runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = false }
        val bitmap = BitmapFactory.decodeFile(imagePath, opts)
            ?: return@runCatching "fp_err"
        try {
            TextureHasher.extractHardwareFingerprint(bitmap)
        } finally {
            bitmap.recycle()
        }
    }.getOrElse { "fp_${System.currentTimeMillis().toString(16)}" }
}
