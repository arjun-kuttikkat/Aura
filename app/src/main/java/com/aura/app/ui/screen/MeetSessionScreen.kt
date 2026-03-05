package com.aura.app.ui.screen

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.model.TradeState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetSessionScreen(
    onHandshakeComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    val nfcState by NfcHandoverManager.state.collectAsState()
    var nfcError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    // Auto-advance when NFC confirms the chip read and backend verification succeeds
    val view = LocalView.current
    LaunchedEffect(nfcState) {
        if (nfcState is NfcHandshakeResult.Confirmed) {
            val confirmedState = nfcState as NfcHandshakeResult.Confirmed
            HapticEngine.triggerSuccess(view)
            nfcError = null
            isVerifying = true
            
            session?.let { s ->
                val listing = AuraRepository.getListing(s.listingId)
                val metadataUri = listing?.images?.firstOrNull() ?: ""
                val assetTitle = listing?.title ?: "Aura Verified Asset"
                
                val success = AuraRepository.releaseEscrowWithNfc(
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
                if (success) {
                    AuraRepository.updateTradeState(TradeState.BOTH_PRESENT)
                    onHandshakeComplete()
                } else {
                    nfcError = "Escrow release failed — tag verification may have failed. Please retry."
                    HapticEngine.triggerThud(view)
                }
            } ?: run { isVerifying = false }
        } else if (nfcState is NfcHandshakeResult.Error) {
            HapticEngine.triggerThud(view)
            nfcError = (nfcState as NfcHandshakeResult.Error).reason
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
                title = { Text("Meet & Verify") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nfc_state"
            ) { state ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (state) {

                        is NfcHandshakeResult.Waiting -> {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 2.5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = FastOutLinearInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scale"
                            )
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.8f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = FastOutLinearInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "alpha"
                            )

                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .scale(pulseScale)
                                        .alpha(pulseAlpha)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )
                                Icon(
                                    imageVector = Icons.Default.Nfc,
                                    contentDescription = "NFC",
                                    modifier = Modifier.size(96.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "Tap the physical item's chip",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Hold your phone near the NTAG 424 DNA chip " +
                                        "attached to the physical asset to verify authenticity.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // QR fallback
                            if (qrBitmap != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No NFC? Scan QR instead",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .background(Color.White),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }

                        is NfcHandshakeResult.Confirmed -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Confirmed",
                                modifier = Modifier.size(96.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Text(
                                "Chip Verified ✓",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                "Cryptographic proof (CMAC) sent to Backend for settlement",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is NfcHandshakeResult.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(state.reason, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { NfcHandoverManager.reset() }) {
                                Text("Retry")
                            }
                        }

                        is NfcHandshakeResult.NoNfcSupport -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
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
                            // Should not linger here — NfcHandoverManager.enable() sets Waiting
                            Text("Initializing NFC…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // NFC error / verification failure feedback
            if (nfcError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            nfcError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            nfcError = null
                            NfcHandoverManager.reset()
                        }) {
                            Text("Retry NFC Tap")
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

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Int>().apply { put(EncodeHintType.MARGIN, 1) }
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i ->
            val y = i / size; val x = i % size
            if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    } catch (e: WriterException) {
        null
    }
}
