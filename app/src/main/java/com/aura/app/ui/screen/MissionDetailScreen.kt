package com.aura.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.app.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    viewModel: DirectivesViewModel,
    onBack: () -> Unit
) {
    val phase by viewModel.phase.collectAsState()
    val pendingMission by viewModel.pendingMission.collectAsState()
    val context = LocalContext.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.advanceMissionStep((pendingMission?.currentStep ?: 0))
    }

    if (phase == MissionPhase.IDLE || pendingMission == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Fullscreen Camera state
    if (phase == MissionPhase.CAPTURING) {
        var verificationError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(pendingMission?.verificationResult) {
            val res = pendingMission?.verificationResult
            if (res != null && !res.first) verificationError = res.second
        }
        
        // Intercept back presses in the camera so it doesn't pop the whole screen
        BackHandler { viewModel.cancelCamera() }
        
        CameraProofOverlay(
            imageCapture = imageCapture,
            lifecycleOwner = LocalLifecycleOwner.current,
            context = context,
            verificationError = verificationError,
            onDismissError = { verificationError = null },
            onPhotoTaken = { path ->
                verificationError = null
                viewModel.submitPhoto(path)
            },
            onCancel = { viewModel.cancelCamera() }
        )
        return
    }

    if (phase == MissionPhase.VERIFYING) {
        // Prevent back out while verifying
        BackHandler { /* Do nothing */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pendingMission?.mission?.title ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (phase == MissionPhase.ACTIVE) {
                        TextButton(onClick = {
                            viewModel.cancelMission()
                            onBack()
                        }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // Celebration / Complete State
                if (phase == MissionPhase.COMPLETE) {
                    MissionCompleteCard(
                        mission = pendingMission!!,
                        onDone = {
                            viewModel.claimRewardsAndComplete(context)
                            onBack()
                        }
                    )
                } else {
                    // Active Mission Steps
                    ActiveMissionContent(
                        activeMission = pendingMission!!,
                        onStepComplete = { step ->
                            val mission = pendingMission?.mission ?: return@ActiveMissionContent
                            val isLastStep = step == mission.steps.size - 1
                            if (isLastStep) {
                                if (hasCameraPermission) {
                                    viewModel.advanceMissionStep(step)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            } else {
                                viewModel.advanceMissionStep(step)
                            }
                        }
                    )
                }
            }

            // Verifying Overlay
            if (phase == MissionPhase.VERIFYING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(60.dp), color = UltraViolet, strokeWidth = 5.dp)
                        Text("Aura AI is verifying your mission...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Hold tight! ✨", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveMissionContent(
    activeMission: ActiveMission,
    onStepComplete: (Int) -> Unit
) {
    val mission = activeMission.mission
    val currentStep = activeMission.currentStep
    val progress by animateFloatAsState(
        targetValue = (currentStep.toFloat() + 1f) / mission.steps.size.toFloat(),
        animationSpec = tween(600), label = "progress"
    )
    val isLastStep = currentStep == mission.steps.size - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Orange500.copy(alpha = 0.1f), Gold500.copy(alpha = 0.06f))))
            .border(1.5.dp, Orange500.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mission.emoji, fontSize = 32.sp)
            }
            Text("${currentStep + 1}/${mission.steps.size}", style = MaterialTheme.typography.labelLarge, color = Orange500, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(mission.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Orange500,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // All steps
        mission.steps.forEachIndexed { i, step ->
            val isDone = i < currentStep
            val isCurrent = i == currentStep
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) Orange500.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(if (isCurrent) 10.dp else 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            when {
                                isDone -> SolanaGreen.copy(alpha = 0.2f)
                                isCurrent -> Orange500.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = SolanaGreen, modifier = Modifier.size(16.dp))
                    } else {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isCurrent -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(18.dp))
            Text("+${mission.auraReward} Aura Points on completion", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Gold500)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onStepComplete(currentStep) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLastStep) UltraViolet else Orange500
            )
        ) {
            when {
                isLastStep -> {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take Proof Photo 📸", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                else -> {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Done — Next Step ➜", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MissionCompleteCard(
    mission: ActiveMission,
    onDone: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(500)) + slideInVertically(initialOffsetY = { 80 })) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(SolanaGreen.copy(alpha = 0.15f), UltraViolet.copy(alpha = 0.08f))))
                .border(2.dp, SolanaGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Mission Complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = SolanaGreen)
            Spacer(modifier = Modifier.height(4.dp))
            Text(mission.mission.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(24.dp))

            mission.verificationResult?.let { (_, feedback, score) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("🤖 AI says:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("\"$feedback\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Photo Quality Score: $score/100", style = MaterialTheme.typography.labelMedium, color = Orange500, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(28.dp))
                Text("+${mission.mission.auraReward} Aura Earned", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold500)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolanaGreen)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Claim Aura Points", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CameraProofOverlay(
    imageCapture: ImageCapture,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: android.content.Context,
    verificationError: String? = null,
    onDismissError: () -> Unit = {},
    onPhotoTaken: (String?) -> Unit,
    onCancel: () -> Unit
) {
    if (verificationError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Verification Failed", fontWeight = FontWeight.Bold) },
            text = { Text(verificationError, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("Try Again", color = UltraViolet, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = DarkSurface
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("📸 Mission Proof", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Capture a photo to prove you completed the mission", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(30.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(Color.White, CircleShape)
                        .clickable {
                            val photoFile = File(context.cacheDir, "mission_proof_${System.currentTimeMillis()}.jpg")
                            val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            imageCapture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        onPhotoTaken(photoFile.absolutePath)
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        onPhotoTaken(null)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.Black, modifier = Modifier.size(30.dp))
                }

                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}
