package com.aura.app.ui.screen

import android.Manifest
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import kotlin.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aura.app.util.FaceAnalyzer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FaceVerificationScreen(
    onVerificationSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    var feedbackText by remember { mutableStateOf("Align your face within the circle") }
    var isFaceDetected by remember { mutableStateOf(false) }
    var verificationProgress by remember { mutableStateOf(0f) }
    var verificationComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Identity Verification",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    ) { padding ->
        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            this.scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor(), FaceAnalyzer(
                                        onFaceDetected = { face ->
                                            // Simple logic: If face is detected, we consider it "aligned" for this demo
                                            // In a real app, we would check bounding box coordinates vs the UI circle overlay
                                            isFaceDetected = true
                                            feedbackText = "Hold still..."
                                        },
                                        onNoFaceDetected = {
                                            isFaceDetected = false
                                            feedbackText = "Align your face within the circle"
                                        }
                                    ))
                                }

                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("FaceVerification", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.width * 0.4f
                    val center = Offset(size.width / 2, size.height / 2 - 100)
                    
                    // Draw semi-transparent background logic essentially implies drawing a path 
                    // or just a circle stroke for simplicity here
                    drawCircle(
                        color = if (isFaceDetected) Color.Green else Color.White,
                        radius = radius,
                        center = center,
                        style = Stroke(width = 8.dp.toPx())
                    )
                }

                // Verification Logic Simulation
                LaunchedEffect(isFaceDetected) {
                    if (isFaceDetected && !verificationComplete) {
                        while (verificationProgress < 1f && isFaceDetected) {
                            delay(50)
                            verificationProgress += 0.02f
                        }
                        if (verificationProgress >= 1f) {
                            verificationComplete = true
                            feedbackText = "Verification Complete!"
                            com.aura.app.data.AuraRepository.performMirrorRitual()
                            delay(1000)
                            onVerificationSuccess()
                        }
                    } else {
                        if (!verificationComplete) {
                            verificationProgress = 0f
                        }
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (verificationComplete) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color.Green,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (!verificationComplete && isFaceDetected) {
                        CircularProgressIndicator(
                            progress = { verificationProgress },
                            color = Color.Green,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is required for face verification.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
