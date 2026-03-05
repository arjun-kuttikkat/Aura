package com.aura.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aura.app.data.AuraRepository
import com.aura.app.model.TradeState
import com.aura.app.model.VerificationResult
import com.aura.app.ui.components.AuraFullScreenCamera
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.ErrorRed
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyItemScreen(
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<VerificationResult?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var showFullScreenCamera by rememberSaveable { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showFullScreenCamera = true
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBase)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Verify Item", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBase,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            containerColor = DarkBase,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AnimatedContent(
                    targetState = result,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "verify_result",
                ) { res ->
                    when {
                        res == null && !showFullScreenCamera -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                Text(
                                    text = "Take a photo of the item to verify authenticity",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    glowColor = Orange500,
                                    cornerRadius = 20.dp,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = Orange500.copy(alpha = 0.6f),
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Point camera at the item",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "Ensure good lighting",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        if (hasCameraPermission) showFullScreenCamera = true
                                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                                ) {
                                    Text("Open Camera", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        res != null -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    glowColor = if (res.pass) SuccessGreen else ErrorRed,
                                    cornerRadius = 20.dp,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(80.dp),
                                            tint = if (res.pass) SuccessGreen else ErrorRed,
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (res.pass) "Verified ✓" else "Verification Failed",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.pass) SuccessGreen else ErrorRed,
                                        )
                                        Text(
                                            "Score: ${(res.score * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (res.pass) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = onVerified,
                                        modifier = Modifier.fillMaxWidth().height(58.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color.Black),
                                    ) {
                                        Text("Continue to Pay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFullScreenCamera && result == null) {
            AuraFullScreenCamera(
                onCapture = { imageCapture ->
                    try {
                        val photoFile = java.io.File(context.cacheDir, "verify_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    try {
                                        showFullScreenCamera = false
                                        isVerifying = true
                                        scope.launch {
                                            try {
                                                val bytes = photoFile.readBytes()
                                                photoFile.delete()
                                                val listingId = session?.listingId ?: ""
                                                result = AuraRepository.verifyPhoto(listingId, bytes)
                                                if (result?.pass == true) {
                                                    AuraRepository.updateTradeState(TradeState.VERIFIED_PASS)
                                                } else {
                                                    AuraRepository.updateTradeState(TradeState.VERIFIED_FAIL)
                                                }
                                            } catch (e: Exception) {
                                                result = VerificationResult(pass = false, score = 0f, reason = e.message ?: "Verification failed")
                                            } finally {
                                                isVerifying = false
                                            }
                                        }
                                    } catch (_: Exception) { isVerifying = false }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    showFullScreenCamera = false
                                    isVerifying = false
                                    result = VerificationResult(pass = false, score = 0f, reason = exception.message ?: "Capture failed")
                                }
                            },
                        )
                    } catch (e: Exception) {
                        showFullScreenCamera = false
                        result = VerificationResult(pass = false, score = 0f, reason = e.message ?: "Capture failed")
                    }
                },
                onClose = { showFullScreenCamera = false },
            )
        }

        if (isVerifying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Orange500, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Verifying...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
