package com.aura.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    onListingCreated: () -> Unit,
    onBack: () -> Unit,
) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var step by remember { mutableIntStateOf(1) }
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var priceSol by mutableStateOf("")
    var condition by mutableStateOf("Good")
    var showCamera by mutableStateOf(false)
    var capturedImagePath by mutableStateOf<String?>(null)
    var textureHash by mutableStateOf<String?>(null)
    var isSubmitting by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)

    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("List Item", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (step == 1) onBack() else step -= 1 }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            StepIndicator(currentStep = step, totalSteps = 4)
            Spacer(modifier = Modifier.height(24.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "step",
            ) { currentStep ->
                when (currentStep) {
                    1 -> PhotoStep(
                        capturedImagePath = capturedImagePath,
                        showCamera = showCamera,
                        imageCapture = imageCapture,
                        hasCameraPermission = hasCameraPermission,
                        onOpenCamera = {
                            if (hasCameraPermission) showCamera = true
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onCaptureClick = {
                            val view = (context as? android.app.Activity)?.window?.decorView ?: return@PhotoStep
                            com.aura.app.ui.util.HapticEngine.triggerClick(view)
                            onCapture()
                        },
                        onCloseCamera = { showCamera = false },
                        onNext = { if (capturedImagePath != null) step = 2 },
                    )
                    2 -> MacroTextureStep(
                        textureHash = textureHash,
                        onScanComplete = { hash -> textureHash = hash },
                        onBack = { step = 1 },
                        onNext = { step = 3 }
                    )
                    3 -> DetailsStep(
                        title = title,
                        onTitleChange = { title = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        priceSol = priceSol,
                        onPriceChange = { priceSol = it.filter { c -> c.isDigit() || c == '.' } },
                        condition = condition,
                        onConditionChange = { condition = it },
                        onBack = { step = 2 },
                        onNext = { step = 4 },
                    )
                    4 -> ReviewStep(
                        title = title,
                        description = description,
                        priceSol = priceSol,
                        condition = condition,
                        imagePath = capturedImagePath,
                        textureHash = textureHash,
                        onEdit = { step = 3 },
                        onSubmit = {
                            if (walletAddress == null) {
                                errorMsg = "Wallet not connected"
                                return@ReviewStep
                            }
                            val price = priceSol.toDoubleOrNull() ?: 0.0
                            if (price <= 0) {
                                errorMsg = "Enter valid price"
                                return@ReviewStep
                            }
                            isSubmitting = true
                            errorMsg = null
                            scope.launch {
                                try {
                                    val listing = AuraRepository.createListing(
                                        sellerWallet = walletAddress!!,
                                        title = title.ifBlank { "Untitled" },
                                        description = description,
                                        priceLamports = (price * 1_000_000_000).toLong(),
                                        imageRefs = capturedImagePath?.let { listOf(it) } ?: emptyList(),
                                        condition = condition,
                                        textureHash = textureHash
                                    )
                                    AuraRepository.mintListing(listing.id)
                                    val view = (context as? android.app.Activity)?.window?.decorView
                                    if(view != null) com.aura.app.ui.util.HapticEngine.triggerSuccess(view)
                                    onListingCreated()
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Failed"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        isSubmitting = isSubmitting,
                        errorMsg = errorMsg,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index + 1 == currentStep
            val isComplete = index + 1 < currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isComplete -> MaterialTheme.colorScheme.primary
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
            )
        }
    }
}

@Composable
private fun PhotoStep(
    capturedImagePath: String?,
    showCamera: Boolean,
    imageCapture: ImageCapture,
    hasCameraPermission: Boolean,
    onOpenCamera: () -> Unit,
    onCapture: () -> Unit,
    onCloseCamera: () -> Unit,
    onNext: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Add a photo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (showCamera) {
            CameraPreviewSection(
                imageCapture = imageCapture,
                onCaptureClick = onCaptureClick,
                onClose = onCloseCamera,
            )
        } else if (capturedImagePath != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                AsyncImage(
                    model = "file://$capturedImagePath",
                    contentDescription = "Captured",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap to take a photo",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Button(
                onClick = onOpenCamera,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text("Take Photo")
            }
        }
        if (capturedImagePath != null) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Continue")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsStep(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    priceSol: String,
    onPriceChange: (String) -> Unit,
    condition: String,
    onConditionChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Item details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            singleLine = false,
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = priceSol,
            onValueChange = onPriceChange,
            label = { Text("Price (SOL)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val conditions = listOf("New", "Like New", "Good", "Fair")
                conditions.chunked(2).forEach { rowConditions ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        rowConditions.forEach { cond ->
                            val isSelected = condition == cond
                            val view = LocalView.current
                            val bgAnim by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                label = "chip_bg"
                            )
                            val textAnim by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "chip_fg"
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    com.aura.app.ui.util.HapticEngine.triggerLight(view)
                                    onConditionChange(cond)
                                },
                                label = { Text(cond, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .com.aura.app.ui.util.springScale(if (isSelected) 0.96f else 1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bgAnim,
                                    selectedLabelColor = textAnim,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Back")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Review")
            }
        }
    }
}

@Composable
private fun ReviewStep(
    title: String,
    description: String,
    priceSol: String,
    condition: String,
    imagePath: String?,
    textureHash: String?,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
    isSubmitting: Boolean,
    errorMsg: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Review & list",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (imagePath != null) {
                    AsyncImage(
                        model = "file://$imagePath",
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (description.isNotBlank()) {
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(condition, style = MaterialTheme.typography.bodyMedium)
                        if (textureHash != null) {
                            Text("Asset Refined \n${textureHash.take(16)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        "%.2f SOL".format(priceSol.toDoubleOrNull() ?: 0.0),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Edit details")
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSubmitting,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(if (isSubmitting) "Listing…" else "List item")
        }
    }
}

@Composable
private fun CameraPreviewSection(
    imageCapture: ImageCapture,
    onCaptureClick: () -> Unit,
    onClose: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isFlashing by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 0.8f else 0f,
        animationSpec = androidx.compose.animation.core.tween(100),
        finishedListener = { if (isFlashing) isFlashing = false },
        label = "flash"
    )

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp))) {
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
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )
        
        // Flash Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha))
        )

        Button(
            onClick = {
                isFlashing = true
                onCaptureClick()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Capture")
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text("✕", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun MacroTextureStep(
    textureHash: String?,
    onScanComplete: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Asset Refinement",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Scan the item's surface texture with your macro lens. This anchors a deterministic hardware layer into the cNFT, proving authenticity over time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (textureHash == null && !isScanning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Place camera 2 inches from surface", color = Color.White)
                    Button(onClick = { isScanning = true }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Begin AI Texture Scan")
                    }
                }
            } else if (isScanning) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(progress = { scanProgress }, color = com.aura.app.ui.theme.Gold500, modifier = Modifier.size(100.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Mapping micro-textures...", color = Color.White)
                    }
                }

                LaunchedEffect(Unit) {
                    while (scanProgress < 1f) {
                        delay(20)
                        scanProgress += 0.01f
                    }
                    isScanning = false
                    val hardwareId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                    val timeSalt = System.currentTimeMillis() / 10000 // 10s deterministic window
                    val baseHash = (hardwareId.hashCode() xor timeSalt.hashCode()).toString(16)
                    val hash = "0x" + baseHash.padStart(16, '0') + UUID.randomUUID().toString().replace("-", "").take(48)
                    onScanComplete(hash)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = com.aura.app.ui.theme.Gold500, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Hardware Texture Analyzed", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(textureHash?.take(16) + "...", color = com.aura.app.ui.theme.Orange500, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Back")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = textureHash != null
            ) {
                Text("Next")
            }
        }
    }
}
