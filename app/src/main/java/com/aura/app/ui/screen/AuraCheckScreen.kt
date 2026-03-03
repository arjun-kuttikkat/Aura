package com.aura.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.app.data.AuraRepository
import com.aura.app.model.AuraCheckResult
import com.aura.app.utils.SpatialSweeper
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class AuraCheckStep {
    SELECT_ITEM_TYPE,
    SCANNING,
    RESULTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCheckScreen(
    onBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(AuraCheckStep.SELECT_ITEM_TYPE) }
    var result by remember { mutableStateOf<AuraCheckResult?>(null) }
    var selectedItemType by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aura Check", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkSurface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = currentStep, label = "AuraCheckFlow") { step ->
                when (step) {
                    AuraCheckStep.SELECT_ITEM_TYPE -> {
                        ItemSelectionView(
                            onItemSelected = { type ->
                                selectedItemType = type
                                currentStep = AuraCheckStep.SCANNING
                            }
                        )
                    }
                    AuraCheckStep.SCANNING -> {
                        if (hasCameraPermission) {
                            CameraScanningView(
                                itemType = selectedItemType,
                                onScanComplete = { scanResult ->
                                    result = scanResult
                                    currentStep = AuraCheckStep.RESULTS
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Grant Camera Permission")
                                }
                            }
                        }
                    }
                    AuraCheckStep.RESULTS -> {
                        result?.let {
                            AstonishingResultView(result = it, onFinish = onBack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemSelectionView(onItemSelected: (String) -> Unit) {
    val items = listOf(
        "Sneaker" to Icons.Default.ShoppingBag,
        "Watch" to Icons.Default.Watch,
        "Trading Card" to Icons.Default.Diamond,
        "Electronics" to Icons.Default.LaptopMac,
        "Handbag" to Icons.Default.ShoppingBag,
        "Other" to Icons.Default.Star
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "What are you scanning?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select the asset type to tune the AI model.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { (name, icon) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .clickable { onItemSelected(name) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = name, tint = Orange500, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraScanningView(
    itemType: String,
    onScanComplete: (AuraCheckResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    // State refs for the ML Kit Analyzer callback
    val isScanningState = androidx.compose.runtime.rememberUpdatedState(isScanning)
    val itemTypeState = androidx.compose.runtime.rememberUpdatedState(itemType)
    val onScanCompleteState = androidx.compose.runtime.rememberUpdatedState(onScanComplete)
    val scopeState = androidx.compose.runtime.rememberUpdatedState(scope)

    // Laser Animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                    val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        if (isScanningState.value) {
                            val bitmap = imageProxy.toBitmap()
                            SpatialSweeper.analyzeFrame(bitmap, itemTypeState.value) { res, conf ->
                                if (res == SpatialSweeper.SweepResult.VALIDATED && isScanningState.value) {
                                    isScanning = false // Stop further processing
                                    scanProgress = 1f
                                    
                                    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                    var lat = 0.0
                                    var lng = 0.0
                                    try {
                                        @SuppressLint("MissingPermission")
                                        val loc: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                        lat = loc?.latitude ?: 0.0
                                        lng = loc?.longitude ?: 0.0
                                    } catch (e: Exception) {}

                                    scopeState.value.launch {
                                        val dummyBytes = ByteArray(10) { it.toByte() }
                                        val initialResult = AuraRepository.performAuraCheck(dummyBytes, lat, lng)
                                        // Override mock rating with the actual ML kit confidence
                                        onScanCompleteState.value(initialResult.copy(rating = (conf * 100).toInt()))
                                    }
                                } else if (res == SpatialSweeper.SweepResult.DETECTING) {
                                    scanProgress = conf
                                }
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header instructions
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isScanning) "AI Analyzing $itemType..." else "Align $itemType in frame",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isScanning) {
                // Capture Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { isScanning = true }
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White))
                }
            } else {
                CircularProgressIndicator(
                    progress = { scanProgress },
                    color = Orange500,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Animated Laser Effect during scan
        if (isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .offset(y = screenHeight * laserY * 0.7f + 50.dp) // Bound the laser within the view
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Orange500, Gold500, Orange500, Color.Transparent)
                            )
                        )
                        .shadow(16.dp, spotColor = Orange500, ambientColor = Gold500)
                )
                // Glow around laser
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .offset(y = screenHeight * laserY * 0.7f + 20.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Orange500.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun AstonishingResultView(result: AuraCheckResult, onFinish: () -> Unit) {
    val party = Party(
        speed = 0f,
        maxSpeed = 30f,
        damping = 0.95f,
        spread = 360,
        colors = listOf(0xFFFFA500.toInt(), 0xFFFFD700.toInt(), 0xFFFFFFFF.toInt()),
        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(150),
        position = Position.Relative(0.5, 0.5),
        size = listOf(Size.SMALL, Size.LARGE, Size.LARGE)
    )

    val party2 = Party(
        speed = 10f,
        maxSpeed = 30f,
        damping = 0.9f,
        spread = 360,
        colors = listOf(0xFFFFA500.toInt(), 0xFFFFD700.toInt(), 0xFFFFFFFF.toInt()),
        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(150),
        position = Position.Relative(0.5, 0.3)
    )

    // Animated counting score
    val scoreToDisplay = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(300)
        scoreToDisplay.animateTo(
            targetValue = result.rating.toFloat(),
            animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        KonfettiView(
            modifier = Modifier.fillMaxSize(),
            parties = listOf(party, party2)
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = Gold500, modifier = Modifier.size(80.dp).padding(bottom = 16.dp))
            
            Text(
                text = "Aura Rating",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "${scoreToDisplay.value.roundToInt()} / 100",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                fontWeight = FontWeight.ExtraBold,
                color = Orange500
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                border = border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = """ "${result.feedback}" """,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "+${result.creditsEarned} Aura Earned!",
                        color = Gold500,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange500)
            ) {
                Text("Awesome!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Helper generic border function
fun border(width: androidx.compose.ui.unit.Dp, color: Color, shape: androidx.compose.ui.graphics.Shape): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}
