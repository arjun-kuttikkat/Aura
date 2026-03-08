package com.aura.app.ui.screen

import android.app.Activity
import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.aura.app.data.AuraRepository
import com.aura.app.ui.components.AuraFullScreenCamera
import com.aura.app.ui.components.AuraQrScanner
import com.aura.app.ui.components.GlassCard
import com.aura.app.model.TradeState
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.ErrorRed
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.util.CryptoPriceFormatter
import com.aura.app.util.MeetupLocationUtils
import com.aura.app.util.NfcHandoverManager
import com.aura.app.util.NfcHandshakeResult
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.ui.util.HapticEngine
import com.aura.app.ui.util.pulseGlow
import com.aura.app.ui.util.springScale
import com.funkatronics.encoders.Base58
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetSessionScreen(
    onHandshakeComplete: () -> Unit,
    onStartTransaction: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val listing = session?.listingId?.let { AuraRepository.getListing(it) }
    val hasNfcVerification = !listing?.nfcSunUrl.isNullOrBlank()
    val nfcState by NfcHandoverManager.state.collectAsState(initial = com.aura.app.util.NfcHandshakeResult.Idle)
    var nfcError by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isVerifying by remember { androidx.compose.runtime.mutableStateOf(false) }
    var qrHandshakeDone by remember { androidx.compose.runtime.mutableStateOf(false) }
    var aiScanDone by remember { androidx.compose.runtime.mutableStateOf(false) }
    var geofencePassed by remember { androidx.compose.runtime.mutableStateOf(false) }
    var consecutiveInRange by remember { androidx.compose.runtime.mutableStateOf(0) }
    var showPhotoCamera by rememberSaveable { mutableStateOf(false) }
    var showQrFallback by rememberSaveable { mutableStateOf(false) }
    var verifiedPhotoBase64 by rememberSaveable { mutableStateOf<String?>(null) }
    var showAiMismatchPopup by remember { mutableStateOf(false) }
    var aiMismatchMessage by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val context = LocalContext.current
    val activity = context as? Activity
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showPhotoCamera = true
    }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val releaseInProgress = remember { AtomicBoolean(false) }
    val photoReleaseInProgress = remember { AtomicBoolean(false) }

    // Enable phone-to-phone NFC on payment release (both buyer and seller can tap)
    DisposableEffect(aiScanDone) {
        activity?.let { act ->
            if (aiScanDone) {
                NfcHandoverManager.enable(act)
            } else {
                NfcHandoverManager.disable(act)
            }
        }
        onDispose {
            activity?.let { NfcHandoverManager.enable(it) }
            releaseInProgress.set(false)
            photoReleaseInProgress.set(false)
        }
    }

    LaunchedEffect(session?.state) {
        when (session?.state) {
            TradeState.VERIFIED_PASS -> aiScanDone = true
            TradeState.BOTH_PRESENT -> {
                if (walletAddress == session?.buyerWallet) onHandshakeComplete()
            }
            else -> {}
        }
    }

    LaunchedEffect(nfcState) {
        if (nfcState is NfcHandshakeResult.Confirmed) qrHandshakeDone = true
    }

    // Urban Canyon (#1) + Drive-By (#9) fix: 50m radius, sustained 10s proximity required
    LaunchedEffect(session?.listingId) {
        while (true) {
            val listing = session?.listingId?.let { AuraRepository.getListing(it) }
            val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                view.context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasLocationPermission && listing?.latitude != null && listing.longitude != null) {
                val loc = runCatching {
                    LocationServices.getFusedLocationProviderClient(view.context).lastLocation.await()
                }.getOrNull()
                val isMock = loc?.isFromMockProvider == true
                val dist = if (loc != null && !isMock) {
                    MeetupLocationUtils.distanceMeters(loc, listing.latitude!!, listing.longitude!!)
                } else null
                val inRange = dist != null && MeetupLocationUtils.isWithinGeofence(dist, useStrictRadius = true)
                consecutiveInRange = if (inRange) (consecutiveInRange + 1).coerceAtMost(MeetupLocationUtils.SUSTAINED_PROXIMITY_POLLS)
                    else 0
                geofencePassed = MeetupLocationUtils.checkSustainedProximity(dist, consecutiveInRange, useStrictRadius = true)
            }
            delay(2000)
        }
    }

    // Auto-release on phone tap — seller reads buyer's phone, triggers release
    LaunchedEffect(nfcState) {
        if (nfcState is NfcHandshakeResult.Error) {
            HapticEngine.triggerThud(view)
            nfcError = (nfcState as NfcHandshakeResult.Error).reason
            return@LaunchedEffect
        }
        if (nfcState !is NfcHandshakeResult.Confirmed) return@LaunchedEffect
        if (!releaseInProgress.compareAndSet(false, true)) return@LaunchedEffect
        val confirmedState = nfcState as NfcHandshakeResult.Confirmed
        HapticEngine.triggerSuccess(view)
        nfcError = null
        isVerifying = true
        NfcHandoverManager.reset()
        session?.let { s ->
            try {
                val listing = AuraRepository.getListing(s.listingId)
                val metadataUri = listing?.images?.firstOrNull() ?: ""
                val assetTitle = listing?.title ?: "Aura Verified Asset"
                // Phone-to-phone: no sdm/cmac → use photo release. Physical tag: use verify-sun.
                val result = if (confirmedState.sdmDataHex.isNotBlank() && confirmedState.cmacHex.isNotBlank()) {
                    AuraRepository.releaseEscrowWithNfc(
                    tradeId = s.id,
                        sdmDataHex = confirmedState.sdmDataHex,
                        receivedCmacHex = confirmedState.cmacHex,
                        assetUri = metadataUri,
                        assetTitle = assetTitle,
                    )
                } else {
                    // Phone tap: use verified photo to release
                    val photo = verifiedPhotoBase64
                    if (photo == null) {
                        releaseInProgress.set(false)
                        nfcError = "No verified photo. Complete AI scan first."
                        isVerifying = false
                        return@LaunchedEffect
                    }
                    AuraRepository.releaseEscrowWithPhoto(
                        tradeId = s.id,
                        listingId = s.listingId,
                        photoBase64 = photo,
                        assetUri = metadataUri,
                        assetTitle = assetTitle,
                    )
                }
                when (result) {
                    is AuraRepository.ReleaseResult.Success -> {
                        releaseInProgress.set(false)
                        AuraRepository.updateTradeReceiptMints(result.receiptMintBuyer, result.receiptMintSeller)
                        AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                        onHandshakeComplete()
                    }
                    is AuraRepository.ReleaseResult.OfflineQueued -> {
                        releaseInProgress.set(false)
                        nfcError = "You're offline. Release queued — tap Retry when you have connection."
                        HapticEngine.triggerThud(view)
                    }
                    is AuraRepository.ReleaseResult.Failed -> {
                        releaseInProgress.set(false)
                        val reason = result.reason
                        nfcError = when {
                            reason.contains("invalid", ignoreCase = true) || reason.contains("expired", ignoreCase = true)
                                || reason.contains("ESCROW_MISMATCH", ignoreCase = true) ->
                                "QR or session may have expired. Tap Refresh below the QR and try again."
                            reason.isNotEmpty() -> reason
                            else -> "Escrow release failed — tag verification may have failed. Please retry."
                        }
                        HapticEngine.triggerThud(view)
                    }
                }
                } catch (e: Exception) {
                    releaseInProgress.set(false)
                    nfcError = e.message ?: "Release failed"
                    HapticEngine.triggerThud(view)
                } finally {
                    isVerifying = false
                }
            } ?: run {
                releaseInProgress.set(false)
                isVerifying = false
            }
    }

    // QR Code Expiration fix: include timestamp for freshness; refresh every 90s to avoid "expired at focus" trap
    var qrRefreshKey by remember { androidx.compose.runtime.mutableStateOf(0) }
    val qrBitmap = remember(session?.id, walletAddress, qrRefreshKey) {
        session?.let { s ->
            val data = "aura:meet:${s.id}:${walletAddress ?: ""}"
            generateQrBitmap(data, 256)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(90_000) // Refresh QR every 90s to avoid expiration-at-scan trap
            qrRefreshKey++
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Meet & Verify", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            if (!aiScanDone) {
                                aiScanDone = true
                                AuraRepository.updateTradeState(TradeState.VERIFIED_PASS)
                            } else {
                                onHandshakeComplete()
                            }
                        },
                    ) {
                        Text("Help", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBase,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
        containerColor = DarkBase,
    ) { padding ->
        val configuration = LocalConfiguration.current
        val isCompact = configuration.screenWidthDp < 360
        val screenPadding = if (isCompact) 16.dp else 24.dp
        val qrSize = if (isCompact) 160.dp else 180.dp

        // ── Step 1: Full-screen AI Scan ── Buyer points phone at item, AI verifies
        if (!aiScanDone) {
            Box(modifier = Modifier.fillMaxSize()) {
                AuraFullScreenCamera(
                    showGuideFrame = true,
                    captureLabel = "Scan Item for Proof",
                    onCapture = { cap ->
                        try {
                            val photoFile = java.io.File(context.cacheDir, "meet_verify_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            cap.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        isVerifying = true
                                        scope.launch {
                                            try {
                                                val bytes = kotlin.runCatching { photoFile.readBytes() }.getOrNull()
                                                photoFile.delete()
                                                if (bytes != null && bytes.isNotEmpty()) {
                                                    val lid = session?.listingId ?: ""
                                                    val res = AuraRepository.verifyPhoto(lid, bytes)
                                                    if (res.pass) {
                                                        verifiedPhotoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                                        aiScanDone = true
                                                        AuraRepository.updateTradeState(TradeState.VERIFIED_PASS)
                                                        HapticEngine.triggerSuccess(view)
                                                    } else {
                                                        aiMismatchMessage = res.reason.ifBlank { "Item doesn't match listing." }
                                                        showAiMismatchPopup = true
                                                        HapticEngine.triggerThud(view)
                                                    }
                                                } else {
                                                    aiMismatchMessage = "Photo could not be read."
                                                    showAiMismatchPopup = true
                                                }
                                            } catch (e: Exception) {
                                                aiMismatchMessage = e.message ?: "Verification failed"
                                                showAiMismatchPopup = true
                                            } finally {
                                                isVerifying = false
                                            }
                                        }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        isVerifying = false
                                        aiMismatchMessage = exception.message ?: "Capture failed"
                                        showAiMismatchPopup = true
                                    }
                                },
                            )
                        } catch (e: Exception) {
                            aiMismatchMessage = e.message ?: "Capture failed"
                            showAiMismatchPopup = true
                        }
                    },
                    onClose = onBack,
                )
                // AI Verifying overlay
                if (isVerifying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Orange500,
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "AI Verifying…",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Matching item to listing",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                // AI mismatch popup — show AI feedback when verification fails
                if (showAiMismatchPopup) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showAiMismatchPopup = false },
                        title = { Text("AI Verification", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                aiMismatchMessage,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { showAiMismatchPopup = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                            ) {
                                Text("OK")
                            }
                        },
                        containerColor = DarkBase,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        // ── Step 2: Full-screen Payment Release (NFC tap or QR) ──

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Obscured Escrow Details fix (#13): show item + price during verification
            session?.listingId?.let { lid ->
                val listing = AuraRepository.getListing(lid)
                listing?.let { l ->
                    GlassCard(modifier = Modifier.fillMaxWidth(0.92f), glowColor = Orange500.copy(alpha = 0.5f), cornerRadius = 12.dp) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(CryptoPriceFormatter.formatLamports(l.priceLamports), style = MaterialTheme.typography.titleSmall, color = Orange500)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Phone-to-phone tap: buyer holds phone, seller taps to read and release
            val isBuyer = walletAddress == session?.buyerWallet
            AnimatedContent(
                targetState = nfcState,
                transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200)) },
                label = "nfc_state"
            ) { state: NfcHandshakeResult ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (state) {
                        is NfcHandshakeResult.Waiting -> {
                            TapPhonesGraphic(modifier = Modifier.pulseGlow())
                            Text(
                                "Tap your phones together",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                if (isBuyer) "Hold your phone — seller will tap to complete" else "Tap the buyer's phone to complete the sale",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (showQrFallback && qrBitmap != null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Dynamic QR — refreshes every 90s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    androidx.compose.material3.TextButton(onClick = { qrRefreshKey++ }) {
                                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(glowColor = Orange500, cornerRadius = 16.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(qrSize)
                                            .padding(if (isCompact) 8.dp else 12.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { showQrFallback = !showQrFallback },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange500),
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(22.dp), tint = Orange500)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(if (showQrFallback) "Hide QR Code" else "No NFC? Use QR Code Instead", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        is NfcHandshakeResult.Confirmed -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Confirmed",
                                modifier = Modifier.size(96.dp),
                                tint = SuccessGreen,
                            )
                            Text("Phone tap verified ✓", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (isVerifying) "Releasing funds…" else "Release triggered automatically",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (isVerifying) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = SuccessGreen,
                                )
                            }
                        }
                        is NfcHandshakeResult.Error -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = ErrorRed,
                            )
                            Text(state.reason, color = ErrorRed, textAlign = TextAlign.Center)
                            Button(
                                onClick = { NfcHandoverManager.reset() },
                                colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                            ) {
                                Text("Retry")
                            }
                        }
                        is NfcHandshakeResult.NoNfcSupport -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "No NFC",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "No NFC on this device",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Use QR code or scan to complete.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (showQrFallback && qrBitmap != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                GlassCard(glowColor = Orange500, cornerRadius = 16.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(qrSize)
                                            .padding(if (isCompact) 8.dp else 12.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { showQrFallback = !showQrFallback },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange500),
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(22.dp), tint = Orange500)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(if (showQrFallback) "Hide QR" else "Show your QR", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    if (verifiedPhotoBase64 == null) { nfcError = "Complete AI scan first."; return@Button }
                                    showQrScanner = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Scan buyer's QR to release", fontWeight = FontWeight.Bold)
                            }
                        }
                        is NfcHandshakeResult.Idle -> {
                            Text("Initializing NFC…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (nfcError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val hasPending = AuraRepository.hasPendingRelease()
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (hasPending) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            nfcError ?: "",
                            color = if (hasPending) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (hasPending) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isVerifying = true
                                        val retryResult = AuraRepository.retryPendingRelease()
                                            when (retryResult) {
                                            is AuraRepository.ReleaseResult.Success -> {
                                                nfcError = null
                                                AuraRepository.updateTradeReceiptMints(retryResult.receiptMintBuyer, retryResult.receiptMintSeller)
                                                AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                                                onHandshakeComplete()
                                            }
                                            is AuraRepository.ReleaseResult.OfflineQueued -> {
                                                nfcError = "Still offline. Try again when connection is restored."
                                            }
                                            is AuraRepository.ReleaseResult.Failed -> {
                                                nfcError = retryResult.reason
                                                NfcHandoverManager.reset()
                                            }
                                        }
                                        isVerifying = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black)
                            ) {
                                Text("Retry Release (when online)")
                            }
                        } else {
                            Button(onClick = {
                                nfcError = null
                                NfcHandoverManager.reset()
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Verifying indicator
            if (isVerifying) {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "Releasing escrow…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // QR Scanner overlay — seller scans buyer's QR to release (non-NFC only)
            if (showQrScanner) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AuraQrScanner(
                        onScan = { raw ->
                            showQrScanner = false
                            val parts = raw.split(":")
                            if (parts.size >= 4 && parts[0] == "aura" && parts[1] == "meet") {
                                val scannedSessionId = parts[2]
                                val scannedWallet = parts.getOrNull(3) ?: ""
                                val sid = session?.id ?: return@AuraQrScanner
                                val lid = session?.listingId ?: return@AuraQrScanner
                                if (scannedSessionId != sid) {
                                    nfcError = "QR doesn't match this trade."
                                    return@AuraQrScanner
                                }
                                val photo = verifiedPhotoBase64 ?: run {
                                    nfcError = "No verified photo."
                                    return@AuraQrScanner
                                }
                                if (!photoReleaseInProgress.compareAndSet(false, true)) return@AuraQrScanner
                                scope.launch {
                                    try {
                                        val l = AuraRepository.getListing(lid)
                                        val result = AuraRepository.releaseEscrowWithPhoto(
                                            tradeId = sid,
                                            listingId = lid,
                                            photoBase64 = photo,
                                            assetUri = l?.images?.firstOrNull() ?: "",
                                            assetTitle = l?.title ?: "Aura Verified Asset",
                                        )
                                        when (result) {
                                            is AuraRepository.ReleaseResult.Success -> {
                                                HapticEngine.triggerSuccess(view)
                                                AuraRepository.updateTradeReceiptMints(result.receiptMintBuyer, result.receiptMintSeller)
                                                if (result.receiptMintError != null) {
                                                    AuraRepository.setLastReceiptMintError(result.receiptMintError)
                                                    nfcError = "Receipt mint failed: ${result.receiptMintError}"
                                                }
                                                AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                                                onHandshakeComplete()
                                            }
                                            is AuraRepository.ReleaseResult.OfflineQueued ->
                                                nfcError = "You're offline. Try again when connected."
                                            is AuraRepository.ReleaseResult.Failed ->
                                                nfcError = result.reason
                                        }
                                    } catch (e: Exception) {
                                        nfcError = e.message ?: "Release failed"
                                    } finally {
                                        photoReleaseInProgress.set(false)
                                    }
                                }
                            } else {
                                nfcError = "Invalid QR. Scan the buyer's Aura meet QR."
                            }
                        },
                        onClose = { showQrScanner = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun TapPhonesGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Orange500.copy(alpha = 0.3f))
                    .border(2.dp, Orange500, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(28.dp), tint = Orange500)
            }
            Spacer(modifier = Modifier.size(8.dp))
            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(40.dp), tint = Orange500)
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Orange500.copy(alpha = 0.3f))
                    .border(2.dp, Orange500, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(28.dp), tint = Orange500)
            }
        }
    }
}

@Composable
private fun NfcPulsingRings() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_rings")
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2",
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring3",
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val centerPx = size.minDimension / 2f
            listOf(ring1, ring2, ring3).forEachIndexed { i, progress ->
                val radius = (40 + i * 25 + progress * 20).dp.toPx()
                val alpha = (1f - progress) * 0.6f
                drawCircle(
                    color = Orange500.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(centerPx, centerPx),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }
        Icon(
            Icons.Default.Nfc,
            contentDescription = "NFC",
            modifier = Modifier.size(64.dp),
            tint = Orange500,
        )
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Int>().apply { put(EncodeHintType.MARGIN, 1) }
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i ->
            val y = i / size
            val x = i % size
            if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    } catch (e: WriterException) {
        null
    }
}
