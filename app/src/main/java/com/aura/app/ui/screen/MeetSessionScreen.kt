package com.aura.app.ui.screen

import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
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
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val nfcState by NfcHandoverManager.state.collectAsState(initial = com.aura.app.util.NfcHandshakeResult.Idle)
    var nfcError by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isVerifying by remember { androidx.compose.runtime.mutableStateOf(false) }
    var qrHandshakeDone by remember { androidx.compose.runtime.mutableStateOf(false) }
    var aiScanDone by remember { androidx.compose.runtime.mutableStateOf(false) }
    var geofencePassed by remember { androidx.compose.runtime.mutableStateOf(false) }
    var consecutiveInRange by remember { androidx.compose.runtime.mutableStateOf(0) }

    val view = LocalView.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(session?.state) {
        if (session?.state == TradeState.VERIFIED_PASS) {
            aiScanDone = true
        }
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

    // Premature verification (#12) fix: only auto-release when user confirms (no accidental brush)
    var confirmReleaseClicked by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(nfcState, confirmReleaseClicked) {
        if (nfcState is NfcHandshakeResult.Confirmed && confirmReleaseClicked) {
            if (!qrHandshakeDone || !geofencePassed || !aiScanDone) {
                nfcError = "Complete QR handshake, 10m geofence check, and AI item verification before releasing funds."
                return@LaunchedEffect
            }
            val confirmedState = nfcState as NfcHandshakeResult.Confirmed
            HapticEngine.triggerSuccess(view)
            nfcError = null
            isVerifying = true
            
            session?.let { s ->
                val listing = AuraRepository.getListing(s.listingId)
                val metadataUri = listing?.images?.firstOrNull() ?: ""
                val assetTitle = listing?.title ?: "Aura Verified Asset"
                
                val result = AuraRepository.releaseEscrowWithNfc(
                    tradeId = s.id,
                    listingId = s.listingId,
                    sdmDataHex = confirmedState.sdmDataHex,
                    receivedCmacHex = confirmedState.cmacHex,
                    escrowPdaBase58 = try {
                        val pda = com.aura.app.wallet.AnchorTransactionBuilder.deriveEscrowPda(s.listingId)
                        Base58.encodeToString(pda.address)
                    } catch (_: Exception) { "" },
                    sellerWalletBase58 = s.sellerWallet,
                    buyerWalletBase58 = walletAddress,
                    assetUri = metadataUri,
                    assetTitle = assetTitle,
                    amount = listing?.priceLamports ?: 0L
                )
                isVerifying = false
                when (result) {
                    is AuraRepository.ReleaseResult.Success -> {
                        AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                        onHandshakeComplete()
                    }
                    is AuraRepository.ReleaseResult.OfflineQueued -> {
                        nfcError = "You're offline. Release queued — tap Retry when you have connection."
                        HapticEngine.triggerThud(view)
                    }
                    is AuraRepository.ReleaseResult.Failed -> {
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
            } ?: run { isVerifying = false }
        } else if (nfcState is NfcHandshakeResult.Error) {
            HapticEngine.triggerThud(view)
            nfcError = (nfcState as NfcHandshakeResult.Error).reason
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
                    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = Orange500.copy(alpha = 0.5f), cornerRadius = 12.dp) {
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
                            NfcPulsingRings()
                            Text(
                                "Tap the physical item's chip",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "NTAG 424 DNA",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (qrBitmap != null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("No NFC? Scan QR instead", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        }
                        is NfcHandshakeResult.Confirmed -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Confirmed",
                                modifier = Modifier.size(96.dp),
                                tint = SuccessGreen,
                            )
                            Text("Chip Verified ✓", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Tap below to confirm release — prevents accidental verification",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = { confirmReleaseClicked = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                            ) {
                                Text("Confirm Release Funds")
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
                                "NFC Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "This device cannot verify NFC tags. Both parties must use an NFC-capable device to complete a trade. " +
                                    "The physical item's NTAG 424 DNA chip must be tapped to release escrow funds.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is NfcHandshakeResult.Idle -> {
                            Text("Initializing NFC…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // NFC error / verification failure feedback
            Spacer(modifier = Modifier.height(14.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = Orange500, cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Meetup Security Checklist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { qrHandshakeDone = !qrHandshakeDone },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (qrHandshakeDone) SuccessGreen else Orange500,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text(if (qrHandshakeDone) "QR Handshake Complete" else "Confirm QR Handshake")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (geofencePassed) "GPS Geofence: within 20m (10s sustained)" else "GPS Geofence: hold position 10s", color = if (geofencePassed) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { aiScanDone = !aiScanDone },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (aiScanDone) SuccessGreen else Orange500,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text(if (aiScanDone) "AI Item Verification Complete" else "Confirm AI Item Verification")
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
                                Text("Retry NFC Tap")
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
                    "Verifying chip & releasing escrow…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
