package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.scale
import com.aura.app.ui.util.pulseGlow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.navigation.LocalBottomNavInset
import com.aura.app.model.Listing
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.theme.DarkBase
import com.aura.app.model.MintedStatus
import com.aura.app.model.TradeSession
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.Gold500
import com.aura.app.data.TradeRiskOracle
import com.aura.app.wallet.WalletConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    tradeSession: TradeSession?,
    onStartMeetup: () -> Unit,
    onBack: () -> Unit,
) {
    val listing = AuraRepository.getListing(listingId)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    
    // Animation states
    var isImageLoaded by remember { mutableStateOf<Boolean>(false) }
    var contentVisible by remember { mutableStateOf<Boolean>(false) }
    val imageScale by animateFloatAsState(
        targetValue = if (isImageLoaded) 1.0f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    LaunchedEffect(Unit) {
        delay(50) // Slight delay to ensure layout pass before triggering cascade
        contentVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.title ?: "Listing", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBase,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
        containerColor = DarkBase,
    ) { padding ->
        if (listing == null) {
            Text("Listing not found", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val l = listing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(imageScale)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Orange500.copy(alpha = 0.15f),
                                DarkBase,
                            ),
                        ),
                    )
                    .border(0.5.dp, Orange500.copy(alpha = 0.2f), RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp)),
            ) {
                val imageUrl = l.images.firstOrNull()
                if (imageUrl != null && imageUrl.isNotBlank()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val modelData = if (imageUrl.startsWith("/")) "file://$imageUrl" else imageUrl
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(modelData)
                            .crossfade(true)
                            .build(),
                        contentDescription = l.title,
                        modifier = Modifier.fillMaxWidth().scale(imageScale),
                        onSuccess = { isImageLoaded = true },
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Orange500.copy(alpha = 0.2f), Orange500.copy(alpha = 0.1f)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = Orange500.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                        },
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Orange500.copy(alpha = 0.08f), Orange500.copy(alpha = 0.04f)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {}
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                            colors = listOf(Orange500.copy(alpha = 0.25f), Orange500.copy(alpha = 0.12f)),
                        ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = Orange500.copy(alpha = 0.5f))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .background(
                            when (l.mintedStatus) {
                                MintedStatus.VERIFIED -> Orange500
                                MintedStatus.MINTED -> Gold500
                                MintedStatus.PENDING -> MaterialTheme.colorScheme.outline
                                MintedStatus.SOLD -> Color(0xFF4CAF50)
                            },
                            RoundedCornerShape(12.dp),
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (l.mintedStatus == MintedStatus.VERIFIED || l.mintedStatus == MintedStatus.SOLD) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = when (l.mintedStatus) {
                                MintedStatus.PENDING -> "Pending"
                                MintedStatus.MINTED -> "Minted"
                                MintedStatus.VERIFIED -> "Verified"
                                MintedStatus.SOLD -> "Sold"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(
                    initialOffsetY = { 100 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                    Text(
                        text = l.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "%.2f SOL ◎".format(l.priceLamports / 1_000_000_000.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Orange500,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.pulseGlow()
                    )
                    }
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = Orange500,
                    cornerRadius = 16.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Condition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(l.condition, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Seller", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${l.sellerWallet.take(6)}...${l.sellerWallet.takeLast(4)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // ── AI Trade-Risk Oracle ──────────────────────────────
                    val sellerProfile = AuraRepository.currentProfile.collectAsState().value
                    val riskAssessment = remember(sellerProfile, l) {
                        TradeRiskOracle.evaluate(sellerProfile, l)
                    }

                    val riskColor = when (riskAssessment.level) {
                        TradeRiskOracle.RiskLevel.LOW -> Orange500
                        TradeRiskOracle.RiskLevel.MEDIUM -> Gold500
                        TradeRiskOracle.RiskLevel.HIGH -> Orange500
                        TradeRiskOracle.RiskLevel.CRITICAL -> Color.Red
                    }
                    val riskIcon = when (riskAssessment.level) {
                        TradeRiskOracle.RiskLevel.LOW -> Icons.Default.CheckCircle
                        else -> Icons.Filled.Warning
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = riskColor.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    riskIcon,
                                    contentDescription = null,
                                    tint = riskColor,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    "AI Risk: ${riskAssessment.level.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = riskColor,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                riskAssessment.recommendation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (riskAssessment.flags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                riskAssessment.flags.forEach { flag ->
                                    Text(
                                        "• $flag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = riskColor.copy(alpha = 0.8f),
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            walletAddress?.let { wallet ->
                                AuraRepository.createTradeSession(
                                    listingId = l.id,
                                    buyerWallet = wallet,
                                    sellerWallet = l.sellerWallet,
                                )
                                onStartMeetup()
                            }
                        },
                        enabled = walletAddress != null && l.mintedStatus != MintedStatus.SOLD,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange500,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text(
                            when {
                                l.mintedStatus == MintedStatus.SOLD -> "Sold"
                                walletAddress != null -> "Start Meetup / Buy"
                                else -> "Connect Wallet First"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(LocalBottomNavInset.current))
                }
            }
        }
    }
}
