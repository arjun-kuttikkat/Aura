package com.aura.app.ui.screen

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
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
import com.aura.app.util.NfcHandoverManager
import com.aura.app.util.NfcHandshakeResult
import com.aura.app.wallet.WalletConnectionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetSessionScreen(
    onHandshakeComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    val nfcState by NfcHandoverManager.state.collectAsState()

    LaunchedEffect(nfcState) {
        if (nfcState is NfcHandshakeResult.Confirmed) {
            val confirmedState = nfcState as NfcHandshakeResult.Confirmed
            session?.let { s ->
                val listing = AuraRepository.getListing(s.listingId)
                val success = AuraRepository.releaseEscrowWithNfc(
                    tradeId = s.id,
                    listingId = s.listingId,
                    sdmDataHex = confirmedState.sdmDataHex,
                    receivedCmacHex = confirmedState.cmacHex,
                    escrowPdaBase58 = "AuRAVaULtXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                    sellerWalletBase58 = s.sellerWallet,
                    amount = listing?.priceLamports ?: 0L
                )
                if (success) {
                    AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                    onHandshakeComplete()
                }
            }
        }
    }

    val qrBitmap = remember(session?.id, walletAddress) {
        session?.let { s ->
            val data = "aura:meet:${s.id}:${walletAddress ?: ""}"
            generateQrBitmap(data, 256)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = nfcState,
                transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200)) },
                label = "nfc_state"
            ) { state ->
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
                                Text("No NFC? Scan QR instead", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(glowColor = Orange500, cornerRadius = 16.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(180.dp)
                                            .padding(12.dp)
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
                                "Cryptographic proof (CMAC) sent for settlement",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
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
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text("NFC not available on this device", style = MaterialTheme.typography.titleMedium)
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.size(200.dp).background(Color.White),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Button(
                                onClick = {
                                    AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                                    onHandshakeComplete()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                            ) {
                                Text("Continue without NFC")
                            }
                        }
                        is NfcHandshakeResult.Idle -> {
                            Text("Initializing NFC…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (true) {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                        onHandshakeComplete()
                    },
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Simulate Handshake (debug only)")
                }
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
