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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.app.data.AuraRepository
import com.aura.app.model.TradeState
import com.aura.app.model.VerificationResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyItemScreen(
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var result by mutableStateOf<VerificationResult?>(null)
    var isVerifying by mutableStateOf(false)
    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Take a photo of the item to verify",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (result == null) {
                if (hasCameraPermission) {
                    CameraPreviewBox(imageCapture = imageCapture, lifecycleOwner = lifecycleOwner, context = context)
                } else {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant Camera Permission")
                    }
                }
                Button(
                    onClick = {
                        isVerifying = true
                        scope.launch {
                            val listingId = session?.listingId ?: ""
                            val dummyBytes = ByteArray(100) { it.toByte() }
                            result = AuraRepository.verifyPhoto(listingId, dummyBytes)
                            if (result?.pass == true) {
                                AuraRepository.updateTradeState(TradeState.VERIFIED_PASS)
                            } else {
                                AuraRepository.updateTradeState(TradeState.VERIFIED_FAIL)
                            }
                            isVerifying = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isVerifying,
                ) {
                    Text(if (isVerifying) "Verifying…" else "Verify Photo")
                }
            } else {
                Text(
                    text = if (result!!.pass) "✓ Verified" else "✗ Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (result!!.pass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text("Score: ${(result!!.score * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                if (result!!.pass) {
                    Button(onClick = onVerified, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue to Pay")
                    }
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
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
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
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
