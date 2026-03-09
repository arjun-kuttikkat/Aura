package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.LottieConstants
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.aura.app.data.AuraRepository
import com.aura.app.data.LocalReceipt
import com.aura.app.data.LocalReceiptStore
import com.aura.app.ui.components.AuraHaptics
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.util.CryptoPriceFormatter
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TradeCompleteScreen(
    onDone: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val receiptMintError by AuraRepository.lastReceiptMintError.collectAsState(initial = null)
    val myReceiptMint = when {
        walletAddress == session?.buyerWallet -> session?.receiptMintBuyer
        walletAddress == session?.sellerWallet -> session?.receiptMintSeller
        else -> session?.receiptMintBuyer ?: session?.receiptMintSeller
    }
    var showCheck by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showBadge by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    var showReceiptDetail by remember { mutableStateOf(false) }

    // Save local receipt when Supabase mint may fail (so user always has proof)
    LaunchedEffect(session, walletAddress) {
        val s = session ?: return@LaunchedEffect
        if (s.state != com.aura.app.model.TradeState.BOTH_PRESENT) return@LaunchedEffect
        val addr = walletAddress ?: return@LaunchedEffect
        val role = if (addr == s.buyerWallet) "buyer" else if (addr == s.sellerWallet) "seller" else return@LaunchedEffect
        val listing = AuraRepository.getListing(s.listingId)
        LocalReceiptStore.save(
            context,
            s.id,
            LocalReceipt(
                tradeId = s.id,
                listingTitle = listing?.title ?: "Trade",
                amountLamports = listing?.priceLamports ?: 0L,
                timestamp = s.lastUpdated,
                role = role,
                counterpartyWallet = if (role == "buyer") s.sellerWallet else s.buyerWallet,
            ),
        )
    }

    // Poll for receipt NFT when mint is still null (edge function may complete after release)
    val sessionId = session?.id
    LaunchedEffect(sessionId, myReceiptMint) {
        if (sessionId != null && myReceiptMint == null) {
            kotlinx.coroutines.delay(2000)
            AuraRepository.refreshTradeSessionReceiptMints(sessionId)
            kotlinx.coroutines.delay(3000)
            AuraRepository.refreshTradeSessionReceiptMints(sessionId)
        }
    }

    LaunchedEffect(session?.id ?: "", walletAddress) {
        AuraHaptics.successPattern(context)
        // Ensure profile is loaded before awarding trade bonus
        walletAddress?.let { AuraRepository.loadProfile(it) }
        if (!session?.id.isNullOrBlank()) AuraRepository.tryAwardTradeBonus(session!!.id)
        showCheck = true
        delay(400)
        showText = true
        delay(300)
        showBadge = true
        delay(500)
        showButton = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "celebrate")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBase),
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .alpha(0.2f)
                .blur(100.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(SuccessGreen, Color.Transparent))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Url("https://lottie.host/80fb48c8-b5cc-4ff2-bc0d-bf5dc34ebc21/j5QvL9VdK4.json")
            )
            LottieAnimation(
                composition = composition,
                iterations = 1,
                modifier = Modifier.size(120.dp).scale(pulse)
            )
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(300)),
            ) {
                Text(
                    "Trade Complete!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(300)),
            ) {
                Text(
                    "Both parties verified. Escrow released.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = showBadge,
                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 60 }, animationSpec = spring(dampingRatio = 0.6f)),
            ) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myReceiptMint != null) {
                                val uri = Uri.parse("https://solscan.io/token/$myReceiptMint")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } else {
                                showReceiptDetail = true
                            }
                        },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Token, contentDescription = null, tint = Gold500, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (myReceiptMint != null) "Your Receipt NFT" else "Trade Receipt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        if (myReceiptMint != null) {
                            Text(
                                "${myReceiptMint.take(8)}...${myReceiptMint.takeLast(8)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Tap to view on Solscan",
                                style = MaterialTheme.typography.labelSmall,
                                color = Orange500,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            // Local receipt fallback when Supabase mint fails
                            val listing = session?.listingId?.let { AuraRepository.getListing(it) }
                            Text(
                                listing?.title ?: "Trade",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                CryptoPriceFormatter.formatLamports(listing?.priceLamports ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Trade ID: ${session?.id?.take(8) ?: "—"}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (receiptMintError != null) {
                                Text(
                                    "On-chain receipt unavailable: $receiptMintError",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                "Tap to view receipt",
                                style = MaterialTheme.typography.labelSmall,
                                color = Orange500,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = showBadge,
                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 100 }, animationSpec = spring(dampingRatio = 0.6f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Gold500.copy(alpha = 0.2f), Orange500.copy(alpha = 0.15f)),
                            ),
                        )
                        .border(1.dp, Gold500.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Token, contentDescription = null, tint = Gold500, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "+10 \$AURA Earned",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Gold500,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Tap-to-Earn reward for completing a verified trade",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 20 }, animationSpec = spring(dampingRatio = 0.7f)),
            ) {
                Button(
                    onClick = {
                        AuraHaptics.lightTap(haptic)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange500,
                        contentColor = Color.Black,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Text("Return to Marketplace", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (showReceiptDetail) {
            val listing = session?.listingId?.let { AuraRepository.getListing(it) }
            val dateStr = session?.lastUpdated?.let {
                SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(it))
            } ?: "—"
            val role = when {
                walletAddress == session?.buyerWallet -> "Buyer"
                walletAddress == session?.sellerWallet -> "Seller"
                else -> "Participant"
            }
            val counterparty = when {
                walletAddress == session?.buyerWallet -> session?.sellerWallet
                walletAddress == session?.sellerWallet -> session?.buyerWallet
                else -> null
            }
            AlertDialog(
                onDismissRequest = { showReceiptDetail = false },
                title = { Text("Trade Receipt", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listing?.title ?: "Trade", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(CryptoPriceFormatter.formatLamports(listing?.priceLamports ?: 0L), style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Trade ID: ${session?.id ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Date: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Your role: $role", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        counterparty?.let { Text("Counterparty: ${it.take(8)}…${it.takeLast(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                confirmButton = {
                    Button(onClick = { showReceiptDetail = false }, colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black)) {
                        Text("Close")
                    }
                },
            )
        }
    }
}
