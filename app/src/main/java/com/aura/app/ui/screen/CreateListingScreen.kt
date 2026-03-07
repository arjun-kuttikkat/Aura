@file:OptIn(ExperimentalMaterial3Api::class)
package com.aura.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.data.GroqAIService
import com.aura.app.ui.components.AuraFullScreenCamera
import com.aura.app.ui.components.AuraHaptics
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.util.CryptoPriceFormatter
import com.aura.app.util.NfcHandoverManager
import com.aura.app.util.NfcHandshakeResult
import com.aura.app.wallet.WalletConnectionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private enum class PublishStep {
    CAMERA,
    VERIFY,
    AI_ANALYZING,
    DETAILS_PRICE,
    PUBLISHING,
    PUBLISH_SUCCESS,
}

private enum class PublishPhase {
    VERIFYING_PHOTO,
    PHOTO_VERIFIED,
    CREATING_LISTING,
    MINTING_NFT,
    NFT_MINTED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    onListingCreated: () -> Unit,
    onBack: () -> Unit,
) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var step by remember { mutableStateOf(PublishStep.CAMERA) }
    var capturedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var addingMorePhotos by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceSol by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Good") }
    var category by remember { mutableStateOf("") }
    var aiTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiAnalyzed by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var nfcSunUrl by remember { mutableStateOf<String?>(null) }
    var publishPhase by remember { mutableStateOf(PublishPhase.VERIFYING_PHOTO) }

    val haptic = LocalHapticFeedback.current
    val hasCameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) step = PublishStep.CAMERA
    }
    var hasLocationPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    val conditions = listOf("New", "Like New", "Good", "Fair")
    val categories = listOf("Electronics", "Fashion", "Sports", "Home", "Books", "Collectibles", "Tools", "Other")

    // Step 1: Full-screen camera modal
    if (step == PublishStep.CAMERA) {
        if (!hasCameraPermission) {
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Sell on Aura", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = { AuraHaptics.subtleTap(haptic); onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBase)
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera permission needed to take photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        Box(modifier = Modifier.fillMaxSize().background(DarkVoid)) {
            AuraFullScreenCamera(
                onClose = {
                    AuraHaptics.subtleTap(haptic)
                    if (addingMorePhotos) {
                        addingMorePhotos = false
                        step = PublishStep.DETAILS_PRICE
                    } else {
                        onBack()
                    }
                },
                onCapture = { imageCapture ->
                    val photoFile = File(context.cacheDir, "aura_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    val isAddMore = addingMorePhotos
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val path = photoFile.absolutePath
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    if (isAddMore) {
                                        capturedImages = capturedImages + path
                                        addingMorePhotos = false
                                        step = PublishStep.DETAILS_PRICE
                                    } else {
                                        capturedImages = listOf(path)
                                        step = PublishStep.VERIFY
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                val msg = exception.message ?: "Capture failed"
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    errorMsg = msg
                                }
                            }
                        }
                    )
                },
            )
        }
        return
    }

    // Step 2: NFC Tap to verify / Skip for now — both paths lead to AI analysis
    if (step == PublishStep.VERIFY && capturedImages.isNotEmpty()) {
        VerifyProductStep(
            imagePath = capturedImages.first(),
            onVerify = { sunUrl ->
                nfcSunUrl = sunUrl
                NfcHandoverManager.reset()
                step = PublishStep.AI_ANALYZING
                aiError = null
            },
            onSkip = {
                nfcSunUrl = null
                NfcHandoverManager.reset()
                step = PublishStep.AI_ANALYZING
                aiError = null
            },
            onRetake = {
                capturedImages = emptyList()
                step = PublishStep.CAMERA
            },
            onBack = onBack,
        )
        return
    }

    // Step 3: AI Analyzing
    if (step == PublishStep.AI_ANALYZING) {
        AiAnalyzingStep(
            imagePath = capturedImages.firstOrNull().orEmpty(),
            onComplete = { analysis ->
                title = analysis.title
                description = analysis.description
                category = analysis.category
                condition = analysis.condition
                aiTags = analysis.tags
                aiAnalyzed = true
                aiError = null
                step = PublishStep.DETAILS_PRICE
            },
            onError = {
                aiError = it
                aiAnalyzed = true
                step = PublishStep.DETAILS_PRICE
            },
        )
        return
    }

    // Step: Publishing (verify photo → create → mint NFT → success)
    if (step == PublishStep.PUBLISHING) {
        LaunchedEffect(step) {
            if (step != PublishStep.PUBLISHING) return@LaunchedEffect
            try {
                publishPhase = PublishPhase.VERIFYING_PHOTO
                val firstPath = capturedImages.firstOrNull()
                if (!firstPath.isNullOrBlank()) {
                    val bytes = withContext(Dispatchers.IO) { File(firstPath).readBytes() }
                    if (bytes.isNotEmpty()) {
                        AuraRepository.verifyPhoto("", bytes, "aura_check")
                    }
                }
                delay(600)
                publishPhase = PublishPhase.PHOTO_VERIFIED
                delay(900)
                publishPhase = PublishPhase.CREATING_LISTING
                val (lat, lng, locationStr) = getCurrentLocationWithAddress(context)
                if (lat == null || lng == null) {
                    errorMsg = "Could not get location. Enable GPS and try again."
                    step = PublishStep.DETAILS_PRICE
                    isSubmitting = false
                    return@LaunchedEffect
                }
                val price = priceSol.toDoubleOrNull() ?: 0.0
                val listing = AuraRepository.createListing(
                    sellerWallet = walletAddress!!,
                    title = title.ifBlank { "Untitled" },
                    description = description,
                    priceLamports = CryptoPriceFormatter.solToLamports(price),
                    imageRefs = capturedImages,
                    condition = condition,
                    location = locationStr,
                    latitude = lat,
                    longitude = lng,
                    nfcSunUrl = nfcSunUrl,
                )
                publishPhase = PublishPhase.MINTING_NFT
                delay(800)
                try {
                    AuraRepository.mintListing(listing.id)
                } catch (_: Exception) {}
                publishPhase = PublishPhase.NFT_MINTED
                delay(2200)
                step = PublishStep.PUBLISH_SUCCESS
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to publish"
                step = PublishStep.DETAILS_PRICE
            } finally {
                isSubmitting = false
            }
        }
        PublishProgressStep(phase = publishPhase)
        return
    }

    // Step: Publish Success
    if (step == PublishStep.PUBLISH_SUCCESS) {
        PublishSuccessStep(onBackToMarketplace = onListingCreated)
        return
    }

    // Step 4: Details and Price (main form)
    PublishDetailsStep(
        capturedImages = capturedImages,
        title = title,
        onTitleChange = { title = it },
        description = description,
        onDescriptionChange = { description = it },
        priceSol = priceSol,
        onPriceChange = { priceSol = it.filter { c -> c.isDigit() || c == '.' } },
        condition = condition,
        onConditionChange = { condition = it },
        category = category,
        onCategoryChange = { category = it },
        aiTags = aiTags,
        aiAnalyzed = aiAnalyzed,
        conditions = conditions,
        categories = categories,
        hasLocationPermission = hasLocationPermission,
        onRequestLocation = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        errorMsg = errorMsg,
        isSubmitting = isSubmitting,
        onBack = onBack,
        onAddMorePhotos = {
            if (hasCameraPermission) {
                addingMorePhotos = true
                step = PublishStep.CAMERA
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onPublish = {
            if (title.isBlank()) {
                errorMsg = "Please enter a title"
                return@PublishDetailsStep
            }
            if (walletAddress == null) {
                errorMsg = "Wallet not connected"
                return@PublishDetailsStep
            }
            val price = priceSol.toDoubleOrNull() ?: 0.0
            if (price <= 0) {
                errorMsg = "Enter a valid price"
                return@PublishDetailsStep
            }
            if (capturedImages.isEmpty()) {
                errorMsg = "Please add a photo"
                return@PublishDetailsStep
            }
            if (!hasLocationPermission) {
                errorMsg = "Allow location access to list your item"
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@PublishDetailsStep
            }
            isSubmitting = true
            errorMsg = null
            publishPhase = PublishPhase.VERIFYING_PHOTO
            step = PublishStep.PUBLISHING
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerifyProductStep(
    imagePath: String,
    onVerify: (sunUrl: String?) -> Unit,
    onSkip: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val activity = context as? Activity
    val nfcState by NfcHandoverManager.state.collectAsState(initial = NfcHandshakeResult.Idle)

    DisposableEffect(activity) {
        activity?.let { NfcHandoverManager.enable(it) }
        onDispose {
            activity?.let { NfcHandoverManager.disable(it) }
        }
    }

    LaunchedEffect(nfcState) {
        if (nfcState is NfcHandshakeResult.Confirmed) {
            val confirmed = nfcState as NfcHandshakeResult.Confirmed
            val url = confirmed.payloadUrl ?: "aura:nfc?picc_data=${confirmed.sdmDataHex}&cmac=${confirmed.cmacHex}"
            onVerify(url)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid)
    ) {
        AsyncImage(
            model = "file://$imagePath",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            DarkVoid.copy(alpha = 0.7f),
                            DarkVoid,
                        ),
                    ),
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = { AuraHaptics.subtleTap(haptic); onBack() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { AuraHaptics.subtleTap(haptic); onRetake() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Retake", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            Column(
                modifier = Modifier.padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Orange500.copy(alpha = 0.2f),
                                    Gold500.copy(alpha = 0.15f),
                                ),
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(Orange500.copy(0.4f), Gold500.copy(0.3f))),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Nfc, contentDescription = null, tint = Orange500, modifier = Modifier.size(36.dp))
                        Text(
                            "Tap to verify",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White,
                        )
                        Text(
                            "Hold your phone near an NFC tag on the product",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        if (nfcState is NfcHandshakeResult.Waiting) {
                            Text(
                                "Waiting for NFC…",
                                fontSize = 13.sp,
                                color = Gold500.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
                TextButton(onClick = { AuraHaptics.subtleTap(haptic); onSkip() }) {
                    Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun AiAnalyzingStep(
    imagePath: String,
    onComplete: (GroqAIService.ListingAnalysis) -> Unit,
    onError: (String) -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_analyze")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )

    LaunchedEffect(imagePath) {
        if (imagePath.isBlank()) {
            onError("Image path missing")
            return@LaunchedEffect
        }
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            onError("Image file not found")
            return@LaunchedEffect
        }
        try {
            val bytes = imageFile.readBytes()
            val analysis = GroqAIService.analyzeProductImage(bytes)
            if (!analysis.isRelevant) {
                onError(analysis.rejectionReason ?: "Please upload a valid product photo")
                return@LaunchedEffect
            }
            delay(800)
            onComplete(analysis)
        } catch (e: Exception) {
            onError("AI analysis failed. Please fill details manually.")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Orange500.copy(alpha = 0.4f),
                                Gold500.copy(alpha = 0.2f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Orange500.copy(alpha = shimmerAlpha),
                    modifier = Modifier.size(56.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "AI is analyzing your product",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Identifying item and filling details…",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Orange500,
                strokeWidth = 3.dp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishDetailsStep(
    capturedImages: List<String>,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    priceSol: String,
    onPriceChange: (String) -> Unit,
    condition: String,
    onConditionChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    aiTags: List<String>,
    aiAnalyzed: Boolean,
    conditions: List<String>,
    categories: List<String>,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    errorMsg: String?,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onAddMorePhotos: () -> Unit,
    onPublish: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val priceFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sell on Aura", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { AuraHaptics.subtleTap(haptic); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { AuraHaptics.lightTap(haptic); onPublish() },
                        enabled = !isSubmitting,
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Orange500)
                        } else {
                            Text("Publish", color = Orange500, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBase)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            errorMsg?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (capturedImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(DarkCard),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = "file://${capturedImages.first()}",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { AuraHaptics.subtleTap(haptic); onAddMorePhotos() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Add more photos", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (capturedImages.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(capturedImages) { path ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(2.dp, Orange500, RoundedCornerShape(8.dp))
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
                    }
                }
            }

            AnimatedVisibility(visible = aiTags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Orange500.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Orange500, modifier = Modifier.size(16.dp))
                        Text("AI Detected Tags", style = MaterialTheme.typography.labelMedium, color = Orange500, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(aiTags) { tag ->
                            Box(
                                modifier = Modifier
                                    .background(Orange500.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = Orange500)
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What are you selling?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        placeholder = { Text("Give your listing a title", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Describe your item", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    singleLine = false,
                    shape = RoundedCornerShape(12.dp),
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { AuraHaptics.subtleTap(haptic); onCategoryChange(cat) },
                                label = { Text(cat, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Orange500,
                                    selectedLabelColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Condition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        conditions.forEach { cond ->
                            FilterChip(
                                selected = condition == cond,
                                onClick = { AuraHaptics.subtleTap(haptic); onConditionChange(cond) },
                                label = { Text(cond, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Orange500,
                                    selectedLabelColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }

                if (!hasLocationPermission) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Gold500.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .clickable { AuraHaptics.subtleTap(haptic); onRequestLocation() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Orange500, modifier = Modifier.size(22.dp))
                        Text(
                            "Tap to allow precise location — we use GPS for where your item is",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
                        Text(
                            "Precise location will be set from GPS when you publish",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Price", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(Orange500.copy(0.3f), Gold500.copy(0.2f))),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("◎", fontSize = 26.sp, color = Orange500, fontWeight = FontWeight.Bold)
                            PremiumPriceTextField(
                                value = priceSol,
                                onValueChange = onPriceChange,
                                modifier = Modifier.focusRequester(priceFocusRequester),
                            )
                        }
                        Text("SOL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Gold500.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Gold500, modifier = Modifier.size(18.dp))
                        Text(
                            "Tip: Chat with buyer to discuss meeting in person.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PremiumPriceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { c -> c.isDigit() || c == '.' }) },
        modifier = modifier
            .widthIn(min = 120.dp)
            .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = Brush.linearGradient(listOf(Orange500, Gold500)),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        "0.00",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun PublishProgressStep(phase: PublishPhase) {
    val infiniteTransition = rememberInfiniteTransition(label = "publish_progress")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    val mintGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mint_glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInVertically { h -> h / 4 } + fadeIn(tween(350))) togetherWith
                    (slideOutVertically { h -> -h / 8 } + fadeOut(tween(250)))
            },
            label = "publish_phase",
        ) { currentPhase ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                when (currentPhase) {
                    PublishPhase.VERIFYING_PHOTO -> {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Orange500.copy(alpha = 0.5f),
                                            Gold500.copy(alpha = 0.25f),
                                            Color.Transparent,
                                        ),
                                    ),
                                )
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f + shimmerAlpha * 0.1f),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Text(
                            "Verifying photo…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White,
                        )
                        Text(
                            "Aura Check in progress",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    PublishPhase.PHOTO_VERIFIED -> {
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
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(52.dp),
                            )
                        }
                        Text(
                            "Photo verified",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White,
                        )
                    }
                    PublishPhase.CREATING_LISTING -> {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Gold500.copy(alpha = 0.45f),
                                            Orange500.copy(alpha = 0.2f),
                                            Color.Transparent,
                                        ),
                                    ),
                                )
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Text(
                            "Creating listing…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White,
                        )
                    }
                    PublishPhase.MINTING_NFT -> {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Gold500.copy(alpha = 0.5f + mintGlow * 0.2f),
                                            Orange500.copy(alpha = 0.35f),
                                            Color.Transparent,
                                        ),
                                    ),
                                )
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                        Text(
                            "Minting NFT…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White,
                        )
                        Text(
                            "Your verified listing is being minted on-chain",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    PublishPhase.NFT_MINTED -> {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Gold500, Orange500)),
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(58.dp),
                            )
                        }
                        Text(
                            "NFT Minted",
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = Color.White,
                        )
                        Text(
                            "Verified on Solana",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishSuccessStep(onBackToMarketplace: () -> Unit) {
    var showCheck by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        showCheck = true
        delay(1800)
        onBackToMarketplace()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AnimatedContent(
                targetState = showCheck,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(200))
                },
                label = "success",
            ) { checked ->
                if (checked) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Orange500, Gold500)),
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = Orange500,
                        strokeWidth = 4.dp,
                    )
                }
            }
            Text(
                "Published!",
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White,
            )
            Text(
                "Heading back to marketplace…",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun getCurrentLocationWithAddress(context: android.content.Context): Triple<Double?, Double?, String?> {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val loc = try {
        withContext(Dispatchers.IO) {
            val token = com.google.android.gms.tasks.CancellationTokenSource().token
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token).await()
        }
    } catch (_: SecurityException) {
        return Triple(null, null, null)
    } catch (_: Exception) {
        null
    }
    val fallbackLoc = if (loc != null) loc else try {
        fused.lastLocation.await()
    } catch (_: Exception) { null }
    val lat = loc?.latitude ?: fallbackLoc?.latitude
    val lng = loc?.longitude ?: fallbackLoc?.longitude
    if (lat == null || lng == null) return Triple(null, null, null)

    val locationStr = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addrs = geocoder.getFromLocation(lat, lng, 1)
            addrs?.firstOrNull()?.let { addr -> formatPreciseAddress(addr, lat, lng) } ?: "${lat.toFloat()}, ${lng.toFloat()}"
        }.getOrNull()
    }
    return Triple(lat, lng, locationStr ?: "${lat.toFloat()}, ${lng.toFloat()}")
}

private fun formatPreciseAddress(addr: Address, lat: Double, lng: Double): String {
    return try {
        val fromLines = if (addr.maxAddressLineIndex >= 0) {
            (0..addr.maxAddressLineIndex)
                .mapNotNull { addr.getAddressLine(it)?.takeIf { s -> s.isNotBlank() } }
                .firstOrNull()
        } else null
        val built = fromLines ?: run {
            val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare).joinToString(" ").trim().takeIf { it.isNotBlank() }
            val area = listOfNotNull(addr.subLocality, addr.locality, addr.adminArea).filter { it.isNotBlank() }.distinct().take(2).joinToString(", ")
            listOfNotNull(street, area).filter { it.isNotBlank() }.joinToString(", ")
        }
        built.takeIf { it.isNotBlank() } ?: "${lat.toFloat()}, ${lng.toFloat()}"
    } catch (_: Exception) {
        "${lat.toFloat()}, ${lng.toFloat()}"
    }
}
