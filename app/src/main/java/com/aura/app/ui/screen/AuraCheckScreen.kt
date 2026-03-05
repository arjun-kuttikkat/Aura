package com.aura.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.app.data.AuraRepository
import com.aura.app.model.AuraCheckResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCheckScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<AuraCheckResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Aura Check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.aura.app.ui.theme.DarkBase,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        containerColor = com.aura.app.ui.theme.DarkBase,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (result == null) {
                Text(
                    text = "Capture a real-time photo of your face or beautiful scenery to earn Aura Credits.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        CameraPreviewBox(imageCapture = imageCapture, lifecycleOwner = lifecycleOwner, context = context)
                        
                        if (isAnalyzing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = com.aura.app.ui.theme.Orange500)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Reading your Aura...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Camera Permission")
                        }
                    }
                }

                Button(
                    onClick = {
                        isAnalyzing = true
                        scope.launch {
                            try {
                                // Real camera capture via ImageCapture API
                                val photoBytes = captureImageBytes(imageCapture, context)
                                if (photoBytes.isEmpty()) {
                                    result = AuraCheckResult(0, "Camera capture failed. Please try again.", false, 0)
                                    return@launch
                                }
                                // Real GPS from FusedLocationProvider
                                var lat: Double? = null
                                var lng: Double? = null
                                try {
                                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                                    val loc = fusedClient.lastLocation.await()
                                    lat = loc?.latitude
                                    lng = loc?.longitude
                                } catch (_: SecurityException) { /* permission not granted */ }
                                
                                result = AuraRepository.performAuraCheck(photoBytes, lat, lng)
                            } catch (e: Exception) {
                                result = AuraCheckResult(0, "Capture failed: ${e.message}", false, 0)
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isAnalyzing && hasCameraPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.aura.app.ui.theme.Orange500
                    )
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Capture Aura", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                }
            } else {
                // Results Screen - result is non-null here (else of result == null)
                val checkResult = result
                if (checkResult != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 100 })
                ) {
                    com.aura.app.ui.components.GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = com.aura.app.ui.theme.Orange500,
                        cornerRadius = 24.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Score",
                                modifier = Modifier.size(64.dp),
                                tint = com.aura.app.ui.theme.Gold500
                            )
                            Text(
                                text = "Aura Rating: ${checkResult.rating}/100",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = com.aura.app.ui.theme.Orange500
                            )
                            Text(
                                text = """ "${checkResult.feedback}" """,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(com.aura.app.ui.theme.Orange500, com.aura.app.ui.theme.Gold500)
                                        )
                                    )
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = "+${checkResult.creditsEarned} Aura Credits Earned",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (checkResult.streakMaintained) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "🔥 Streak Maintained! 🔥",
                                    color = com.aura.app.ui.theme.Orange500,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
                } // end if checkResult != null

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = com.aura.app.ui.theme.Orange500, contentColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Return to Map", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewBox(
    imageCapture: ImageCapture,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: android.content.Context,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Capture a real JPEG image from the camera and return as ByteArray.
 */
private suspend fun captureImageBytes(
    imageCapture: ImageCapture,
    context: android.content.Context,
): ByteArray = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    val executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
            try {
                val planes = imageProxy.planes
                if (planes.isNullOrEmpty()) {
                    cont.resume(ByteArray(0)) {}
                    return
                }
                val buffer = planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                cont.resume(bytes) {}
            } catch (e: Exception) {
                cont.resume(ByteArray(0)) {}
            } finally {
                imageProxy.close()
            }
        }
        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
            cont.resume(ByteArray(0)) {}
        }
    })
}
