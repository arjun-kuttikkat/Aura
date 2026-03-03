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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.data.GroqAIService
import com.aura.app.ui.theme.*
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.launch
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

    // ── State ────────────────────────────────────────────────────────────────
    var showCamera by remember { mutableStateOf(false) }
    var capturedImagePath by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceSol by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Good") }
    var category by remember { mutableStateOf("") }
    var aiTags by remember { mutableStateOf<List<String>>(emptyList()) }

    // AI states
    var isAnalyzing by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiAnalyzed by remember { mutableStateOf(false) }

    // Submission states
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }

    val conditions = listOf("New", "Like New", "Good", "Fair")
    val categories = listOf("Electronics", "Fashion", "Sports", "Home", "Books", "Collectibles", "Tools", "Other")

    // ── Layout ───────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sell on Aura",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (walletAddress == null) { errorMsg = "Wallet not connected"; return@TextButton }
                            val price = priceSol.toDoubleOrNull() ?: 0.0
                            if (price <= 0) { errorMsg = "Enter a valid price"; return@TextButton }
                            if (capturedImagePath == null) { errorMsg = "Please add a photo"; return@TextButton }
                            if (!aiAnalyzed) { errorMsg = "Tap AI Analyze to verify your product"; return@TextButton }
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
                                    )
                                    // Minting is best-effort: if the Edge Function isn't deployed yet
                                    // the listing is still created. Mint status will be PENDING.
                                    try { AuraRepository.mintListing(listing.id) } catch (_: Exception) {}
                                    onListingCreated()
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Failed to publish"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "Publish",
                                color = Orange500,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Photo Section ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (showCamera) {
                    // Camera preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
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
                                    imageCapture
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Capture button overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showCamera = false },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White, CircleShape)
                                    .clickable {
                                        val photoFile = File(context.cacheDir, "aura_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                        imageCapture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                    capturedImagePath = photoFile.absolutePath
                                                    showCamera = false
                                                    aiAnalyzed = false
                                                    aiTags = emptyList()
                                                }
                                                override fun onError(exception: ImageCaptureException) {
                                                    errorMsg = exception.message ?: "Capture failed"
                                                    showCamera = false
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .border(3.dp, Color.Gray, CircleShape)
                                )
                            }
                        }
                    }
                } else if (capturedImagePath != null) {
                    AsyncImage(
                        model = "file://$capturedImagePath",
                        contentDescription = "Product photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Re-take button
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        IconButton(
                            onClick = {
                                capturedImagePath = null
                                aiAnalyzed = false
                                aiTags = emptyList()
                                title = ""
                                description = ""
                                if (hasCameraPermission) showCamera = true
                                else permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    // Add more hint
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable {
                                    if (hasCameraPermission) showCamera = true
                                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Add more detail photos", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Empty state - tap to add photo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.clickable {
                            if (hasCameraPermission) showCamera = true
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    Brush.linearGradient(listOf(Orange500.copy(0.2f), Gold500.copy(0.15f))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Orange500,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text("Add product photo", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Tap to take a photo of your item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // ── AI Analyze Button ─────────────────────────────────────────────
            if (capturedImagePath != null && !aiAnalyzed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6C3BE8).copy(alpha = 0.1f),
                                    Color(0xFF3B82F6).copy(alpha = 0.05f)
                                )
                            )
                        )
                        .clickable(enabled = !isAnalyzing) {
                            if (!isAnalyzing) {
                                isAnalyzing = true
                                aiError = null
                                scope.launch {
                                    try {
                                        val imageFile = File(capturedImagePath!!)
                                        val imageBytes = imageFile.readBytes()
                                        val analysis = GroqAIService.analyzeProductImage(imageBytes)
                                        if (!analysis.isRelevant) {
                                            aiError = "❌ Item not suitable: ${analysis.rejectionReason ?: "Please upload a valid product photo"}"
                                            capturedImagePath = null
                                        } else {
                                            title = analysis.title
                                            description = analysis.description
                                            category = analysis.category
                                            condition = analysis.condition
                                            aiTags = analysis.tags
                                            aiAnalyzed = true
                                        }
                                    } catch (e: Exception) {
                                        aiError = "AI analysis failed. Please fill details manually."
                                        aiAnalyzed = true // Let them proceed manually
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF6C3BE8))
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF6C3BE8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                if (isAnalyzing) "AI is analyzing your product..." else "✨ AI Analyze & Auto-fill",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6C3BE8)
                            )
                            Text(
                                "Let Aura AI identify your item and fill in details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF6C3BE8))
                    }
                }
            }

            // AI Error Message
            aiError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                ) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }

            // AI Tags Row (after analysis)
            AnimatedVisibility(visible = aiTags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF6C3BE8).copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6C3BE8), modifier = Modifier.size(16.dp))
                        Text("AI Detected Tags", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6C3BE8), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(aiTags) { tag ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF6C3BE8).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6C3BE8))
                            }
                        }
                    }
                }
            }

            // ── Details Section ───────────────────────────────────────────────
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Title field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What are you selling?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Give your listing a title", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Describe your item — mention special features, flaws, etc.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    singleLine = false,
                    shape = RoundedCornerShape(12.dp),
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Category chips
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Category / Brand / Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Category row
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { cat ->
                                val isSelected = category == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { category = cat },
                                    label = { Text(cat, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Orange500,
                                        selectedLabelColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Condition tags
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Condition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            conditions.forEach { cond ->
                                val isSelected = condition == cond
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { condition = cond },
                                    label = { Text(cond, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Orange500,
                                        selectedLabelColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Price field
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Price", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("◎", fontSize = 20.sp, color = Orange500, fontWeight = FontWeight.Bold)
                            BasicPriceTextField(
                                value = priceSol,
                                onValueChange = { priceSol = it.filter { c -> c.isDigit() || c == '.' } }
                            )
                        }
                        Text("SOL / item", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Tip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Gold500.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Gold500, modifier = Modifier.size(16.dp))
                        Text(
                            "Tip: Prices include shipping by default. Chat with buyer to discuss meeting in person.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error message
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BasicPriceTextField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        ),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text("0.00", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), fontWeight = FontWeight.Bold)
            }
            inner()
        }
    )
}
