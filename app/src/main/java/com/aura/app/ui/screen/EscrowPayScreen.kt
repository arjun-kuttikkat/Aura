package com.aura.app.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.model.EscrowState
import com.aura.app.model.TradeState
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CheckCircle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.ui.platform.LocalView
import com.aura.app.ui.util.HapticEngine
import com.aura.app.ui.util.breathe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowPayScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val listing = session?.let { AuraRepository.getListing(it.listingId) }
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var status by remember { mutableStateOf<EscrowState?>(null) }
    var txSig by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val isLocked = status == EscrowState.LOCKED || txSig != null
    val lockScale by animateFloatAsState(
        targetValue = if (isLocked) 1.1f else 1f,
        label = "lockScale",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay / Escrow") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBase,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = DarkBase,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .then(if (isLoading) Modifier.breathe() else Modifier),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listing?.let {
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = Orange500) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            it.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "%.2f SOL".format(it.priceLamports / 1_000_000_000.0),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Orange500,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).scale(lockScale),
                    tint = if (isLocked) Gold500 else Orange500,
                )
            }

            if (walletAddress == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wallet Not Connected", style = MaterialTheme.typography.titleMedium)
                        Text("Connect your Solana wallet to proceed.")
                        Spacer(modifier = Modifier.height(8.dp))
                        com.aura.app.ui.components.AuraPrimaryButton(
                            text = "Connect Wallet",
                            onClick = {
                                isLoading = true
                                WalletConnectionState.connect(
                                    scope = scope,
                                    onSuccess = { isLoading = false },
                                    onError = { isLoading = false; errorMsg = it.message }
                                )
                            },
                            enabled = !isLoading
                        )
                    }
                }
            } else {
                val isSuccess = status == EscrowState.LOCKED || txSig != null
                
                // ── 300ms Liquid Morphing State Completion Protocol ──
                androidx.compose.animation.AnimatedContent(
                    targetState = isSuccess,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(300)),
                    label = "morphing_button"
                ) { success ->
                    if (!success) {
                        com.aura.app.ui.components.AuraPrimaryButton(
                            text = if (isLoading) "Processing..." else "Confirmed: Pay %.2f SOL".format(listing?.priceLamports?.div(1_000_000_000.0) ?: 0.0),
                            onClick = {
                                HapticEngine.triggerThud(view) // Financial gravity
                                val amount = listing?.priceLamports ?: 0L
                                val amountSol = amount / 1_000_000_000.0
                                
                                isLoading = true
                                errorMsg = null
                                
                                scope.launch {
                                    try {
                                        // 1. Fetch serialized Escrow TX from our backend/smart-contract interface
                                        // The backend constructs the message and returns it as a ByteArray, which we encode to Base64
                                        // for safe transport across layers here before the wallet adapter takes it.
                                        val escrowTxBytes = AuraRepository.initEscrow(
                                            tradeId = session?.id ?: return@launch,
                                            amount = amount
                                        )
                                        val base64Tx = android.util.Base64.encodeToString(escrowTxBytes, android.util.Base64.NO_WRAP)
                                        
                                        // 2. Pass to Mobile Wallet Adapter for physical signing
                                        WalletConnectionState.signAndSendTransaction(
                                            scope = scope,
                                            base64EncodedTx = base64Tx,
                                            onSuccess = { sig ->
                                                HapticEngine.triggerSuccess(view)
                                                isLoading = false
                                                txSig = sig
                                                status = EscrowState.LOCKED
                                                AuraRepository.updateTradeState(TradeState.ESCROW_LOCKED)
                                            },
                                            onError = { e ->
                                                isLoading = false
                                                errorMsg = e.message ?: "Transaction failed"
                                            }
                                        )
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMsg = "Escrow Init Failed: ${e.message}"
                                    }
                                }
                            },
                            enabled = !isLoading
                        )
                    } else {
                        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "escrow_pulse")
                        val bgAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.05f, targetValue = 0.35f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = tween(1200, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ), label = "bg_alpha"
                        )
                        // Success State Morph
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, com.aura.app.ui.theme.SolanaGreen.copy(alpha = bgAlpha * 2), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = com.aura.app.ui.theme.SolanaGreen.copy(alpha = bgAlpha)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val composition by rememberLottieComposition(
                                    LottieCompositionSpec.Url("https://lottie.host/80fb48c8-b5cc-4ff2-bc0d-bf5dc34ebc21/j5QvL9VdK4.json")
                                )
                                LottieAnimation(
                                    composition = composition,
                                    iterations = 1,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Escrow Locked", style = MaterialTheme.typography.titleMedium, color = com.aura.app.ui.theme.SolanaGreen)
                                txSig?.let { Text("Sig: ${it.take(8)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                )
            }

            errorMsg?.let { Text(it, color = com.aura.app.ui.theme.RadicalRed) }

            if (status == EscrowState.LOCKED || txSig != null) {
                Spacer(modifier = Modifier.weight(1f))
                com.aura.app.ui.components.AuraPrimaryButton(
                    text = "Complete Trade & Release Goods",
                    onClick = {
                        AuraRepository.updateTradeState(TradeState.COMPLETE)
                        onComplete()
                    }
                )
            }
        }
    }
}
