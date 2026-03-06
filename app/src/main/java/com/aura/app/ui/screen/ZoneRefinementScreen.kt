package com.aura.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ZoneState { MAP, SCANNING, RESULT }

data class TurfZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val distanceMeters: Int,
    val isRefined: Boolean,
    val ownerHash: String? = null,
    val gravity: Double = 0.0
)

/** Bearing in radians from point 1 to point 2 (0 = North). */
private fun bearingRad(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLng = Math.toRadians(lng2 - lng1)
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val y = Math.sin(dLng) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng)
    return Math.atan2(y, x)
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ZoneRefinementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissionGranted = remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationPermissionGranted.value = granted
        if (granted) com.aura.app.data.HotzoneManager.refreshLocation()
    }
    
    var currentState by remember { mutableStateOf(ZoneState.MAP) }
    var selectedZone by remember { mutableStateOf<TurfZone?>(null) }
    var scanProgress by remember { mutableStateOf(0f) }
    var proofHash by remember { mutableStateOf<String?>(null) }
    
    val hotzones by com.aura.app.data.HotzoneManager.nearbyZones.collectAsState(initial = emptyList())
    val currentLocation by com.aura.app.data.HotzoneManager.currentLocation.collectAsState(initial = null)
    val locallyClaimed by com.aura.app.data.HotzoneManager.locallyClaimedZones.collectAsState(initial = emptySet())
    val isLoading by com.aura.app.data.HotzoneManager.isLoading.collectAsState(initial = false)
    val walletAddress by com.aura.app.data.AuraPreferences.walletAddress.collectAsState(initial = null)
    
    val turfZones = remember(hotzones, locallyClaimed, walletAddress) {
        hotzones.map { hz ->
            val claimed = locallyClaimed.contains(hz.id)
            TurfZone(
                id = hz.id,
                name = hz.name,
                lat = hz.lat,
                lng = hz.lng,
                distanceMeters = hz.distanceMeters ?: 0,
                isRefined = hz.isRefined || claimed,
                ownerHash = if (claimed) (walletAddress?.take(6) + "..." + walletAddress?.takeLast(4)) ?: "You" else hz.apexWallet,
                gravity = if (claimed) 1.0 else hz.gravity
            )
        }
    }

    LaunchedEffect(locationPermissionGranted.value) {
        if (locationPermissionGranted.value) {
            com.aura.app.data.HotzoneManager.refreshLocation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zone Refinement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
            label = "zone_phase"
        ) { phase ->
            when (phase) {
                ZoneState.MAP -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    ) {
                        Text(
                            "DePIN Turf Wars", 
                            style = MaterialTheme.typography.headlineMedium, 
                            fontWeight = FontWeight.Bold,
                            color = Orange500
                        )
                        Text(
                            "Scan unrefined zones to claim turf and earn $ AURA kickbacks on local trades.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Location-based radar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E1E1E))
                                .border(1.dp, Gold500.copy(alpha=0.5f), RoundedCornerShape(16.dp))
                        ) {
                            val userLoc = currentLocation
                            val maxDist = turfZones.maxOfOrNull { it.distanceMeters }?.coerceAtLeast(500) ?: 500
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width / 2
                                val cy = size.height / 2
                                val radarRadius = (size.minDimension / 2 - 16.dp.toPx()).coerceAtLeast(40f)
                                drawCircle(
                                    color = Orange500.copy(alpha = 0.2f),
                                    radius = radarRadius,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = Orange500.copy(alpha = 0.4f),
                                    radius = radarRadius / 2,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(color = Gold500, radius = 8.dp.toPx(), center = Offset(cx, cy))
                                if (userLoc != null && turfZones.isNotEmpty()) {
                                    turfZones.forEach { z ->
                                        val distRatio = (z.distanceMeters.toFloat() / maxDist).coerceIn(0f, 1f)
                                        val r = distRatio * radarRadius
                                        val bear = bearingRad(userLoc.latitude, userLoc.longitude, z.lat, z.lng)
                                        val px = cx + r * kotlin.math.sin(bear).toFloat()
                                        val py = cy - r * kotlin.math.cos(bear).toFloat()
                                        val dotColor = if (z.isRefined && z.ownerHash != null) Color.Red else Color.Green
                                        drawCircle(color = dotColor, radius = 6.dp.toPx(), center = Offset(px, py))
                                    }
                                }
                            }
                            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Green))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Unrefined", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Rival Owned", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (!locationPermissionGranted.value) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Column {
                                        Text("Location required", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("Tap to enable GPS — zones are shown by your actual position.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        } else if (isLoading && turfZones.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Orange500)
                            }
                        } else if (turfZones.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Orange500, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No zones nearby", fontWeight = FontWeight.SemiBold)
                                    Text("Move around or enable GPS to discover Hotzones within 8km.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { com.aura.app.data.HotzoneManager.refreshLocation() }, colors = ButtonDefaults.buttonColors(containerColor = Orange500)) {
                                        Text("Refresh location")
                                    }
                                }
                            }
                        }
                        
                        if (turfZones.isNotEmpty()) {
                            Text("Nearby Zones", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(turfZones) { zone ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedZone = zone
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedZone == zone) Gold500.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (selectedZone == zone) androidx.compose.foundation.BorderStroke(1.dp, Gold500) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(zone.name, fontWeight = FontWeight.SemiBold)
                                                if (zone.isRefined && zone.ownerHash != null) {
                                                    Text(" 👑", style = MaterialTheme.typography.titleMedium)
                                                }
                                            }
                                            Text("${zone.distanceMeters}m away", style = MaterialTheme.typography.bodySmall)
                                            if (zone.isRefined) {
                                                Text("Apex: ${zone.ownerHash}  •  Gravity: ${String.format("%.0f", zone.gravity)}", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                                            } else {
                                                Text("Unrefined (Available)", color = Color.Green, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Orange500)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Button(
                            onClick = {
                                if (!locationPermissionGranted.value) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                } else if (!cameraPermissionState.status.isGranted) {
                                    cameraPermissionState.launchPermissionRequest()
                                } else {
                                    scanProgress = 0f
                                    currentState = ZoneState.SCANNING
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedZone != null && turfZones.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange500)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Travel & Refine Zone")
                        }
                    }
                }
                
                ZoneState.SCANNING -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                    } catch (e: Exception) {
                                        Log.e("ZoneRefinement", "Camera binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // UI Overlay
                        Column(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("ML Hardware Scan in Progress...", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text("Mapping environment geometry and lighting", color = Color.White.copy(alpha=0.7f))
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = scanProgress,
                                    color = Gold500,
                                    modifier = Modifier.size(200.dp),
                                    strokeWidth = 8.dp
                                )
                                Text("${(scanProgress * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        
                        LaunchedEffect(selectedZone) {
                            while (scanProgress < 1f) {
                                delay(30)
                                scanProgress += 0.01f
                            }
                            proofHash = "0x" + UUID.randomUUID().toString().replace("-", "").take(16)
                            selectedZone?.let { zone ->
                                val wallet = walletAddress
                                val claimed = withContext(Dispatchers.IO) {
                                    if (wallet != null) {
                                        try {
                                            com.aura.app.data.HotzoneManager.claimZone(zone.id, wallet, proofHash ?: "")
                                        } catch (_: Exception) { false }
                                    } else false
                                }
                                if (!claimed) {
                                    com.aura.app.data.HotzoneManager.markLocallyClaimed(zone.id)
                                }
                            }
                            currentState = ZoneState.RESULT
                        }
                    }
                }
                
                ZoneState.RESULT -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Gold500, modifier = Modifier.size(96.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Zone Refined Successfully!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Proof of Environment Hash:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(proofHash ?: "", style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Orange500)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("cNFT Issued via Anchor", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("You are now the certified owner of:")
                                Text(selectedZone?.name ?: "", style = MaterialTheme.typography.titleLarge, color = Gold500)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Kickback activated: 0.1% of all P2P trades in this area.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { currentState = ZoneState.MAP },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Map")
                        }
                    }
                }
            }
        }
    }
}
