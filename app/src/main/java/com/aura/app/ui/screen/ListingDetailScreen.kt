package com.aura.app.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.aura.app.data.AuraRepository
import com.aura.app.data.TradeRiskOracle
import com.aura.app.model.Listing
import com.aura.app.model.MintedStatus
import com.aura.app.model.ProfileDto
import com.aura.app.model.TradeSession
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SlateElevated
import com.aura.app.ui.theme.SlateLight
import com.aura.app.util.CryptoPriceFormatter
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.model.RankSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    tradeSession: TradeSession?,
    onStartMeetup: () -> Unit,
    onBack: () -> Unit,
    onChatClicked: () -> Unit = {},
) {
    val listings by AuraRepository.listings.collectAsState(initial = emptyList())
    val listing = listings.find { it.id == listingId }
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)

    // Refresh when entering — fixes "Listing not found" for newly created listings
    LaunchedEffect(listingId) {
        if (listing == null) AuraRepository.refreshListingsAwait()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = DarkVoid,
    ) { padding ->
        if (listing == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Listing not found", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack) { Text("Go Back") }
            }
            return@Scaffold
        }

        val configuration = LocalConfiguration.current
        val isCompact = configuration.screenWidthDp < 360
        val contentPadding = if (isCompact) 16.dp else 24.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateElevated)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
        ) {
            // ── Hero Image ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(0.dp)),
            ) {
                val imageUrl = listing.images.firstOrNull()?.takeIf { it.isNotBlank() }
                val context = androidx.compose.ui.platform.LocalContext.current
                if (imageUrl != null) {
                    val modelData = if (imageUrl.startsWith("/")) "file://$imageUrl" else imageUrl
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(modelData)
                            .crossfade(true)
                            .build(),
                        contentDescription = listing.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Orange500.copy(alpha = 0.15f), Gold500.copy(alpha = 0.08f)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.BrokenImage, null, modifier = Modifier.size(48.dp), tint = Orange500.copy(alpha = 0.5f))
                            }
                        },
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SlateElevated),
                            )
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Orange500.copy(alpha = 0.2f), Gold500.copy(alpha = 0.1f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.BrokenImage, null, modifier = Modifier.size(48.dp), tint = Orange500.copy(alpha = 0.5f))
                    }
                }
                // Status badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (isCompact) 12.dp else 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (listing.mintedStatus) {
                                MintedStatus.VERIFIED -> Orange500
                                MintedStatus.MINTED -> Gold500
                                MintedStatus.PENDING -> SlateLight.copy(alpha = 0.9f)
                                MintedStatus.SOLD -> SlateLight.copy(alpha = 0.9f)
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (listing.mintedStatus == MintedStatus.VERIFIED) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        }
                        Text(
                            when (listing.mintedStatus) {
                                MintedStatus.PENDING -> "Pending"
                                MintedStatus.MINTED -> "Minted"
                                MintedStatus.VERIFIED -> "Verified"
                                MintedStatus.SOLD -> "Sold"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ── Premium content card (overlaps image slightly) ──────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(0.dp, if (isCompact) (-16).dp else (-24).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SlateElevated)
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 16.dp else 20.dp),
            ) {
                // Title + Price
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listing.title,
                        style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            CryptoPriceFormatter.formatLamports(listing.priceLamports),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Orange500,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassSurface)
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                listing.condition,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    val locationText = listing.location ?: listing.emirate
                    if (!locationText.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = SlateLight,
                            )
                            Text(
                                buildString {
                                    append(locationText)
                                    listing.distanceMeters?.let { m ->
                                        when {
                                            m < 1000 -> append(" · $m m away")
                                            else -> append(" · ${m / 1000} km away")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val scope = rememberCoroutineScope()
                var isStartingTrade by remember { mutableStateOf(false) }
                var tradeError by remember { mutableStateOf<String?>(null) }
                var showBuyConfirm by remember { mutableStateOf(false) }
                val isSeller = walletAddress == listing.sellerWallet

                // ── Action buttons (high up, prominent) ────────────────────
                if (!isSeller) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showBuyConfirm = true },
                            enabled = walletAddress != null && listing.mintedStatus != MintedStatus.SOLD && !isStartingTrade,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = SlateLight.copy(alpha = 0.5f),
                            ),
                            contentPadding = PaddingValues(),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(listOf(Orange500, Gold500)),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isStartingTrade) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = DarkVoid)
                                } else {
                                    Text(
                                        when {
                                            listing.mintedStatus == MintedStatus.SOLD -> "Sold"
                                            walletAddress != null -> "Buy Now"
                                            else -> "Connect Wallet"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DarkVoid,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onChatClicked,
                            enabled = walletAddress != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange500),
                            border = BorderStroke(1.dp, Orange500.copy(alpha = 0.6f)),
                        ) {
                            Text("Message Seller", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (showBuyConfirm) {
                        AlertDialog(
                            onDismissRequest = { showBuyConfirm = false },
                            title = { Text("Start trade", fontWeight = FontWeight.Bold) },
                            text = { Text("Meet the seller and verify the item before payment.", style = MaterialTheme.typography.bodyMedium) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showBuyConfirm = false
                                    walletAddress?.let { wallet ->
                                        isStartingTrade = true
                                        scope.launch {
                                            try {
                                                AuraRepository.createTradeSession(
                                                    listingId = listing.id,
                                                    buyerWallet = wallet,
                                                    sellerWallet = listing.sellerWallet,
                                                )
                                                // Defer navigation so the dialog can fully dismiss first (prevents crash)
                                                delay(150)
                                                onStartMeetup()
                                            } catch (e: Throwable) {
                                                tradeError = e.message ?: "Failed to start trade"
                                            } finally {
                                                isStartingTrade = false
                                            }
                                        }
                                    }
                                }) { Text("Start Meetup", color = Orange500, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBuyConfirm = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            },
                        )
                    }
                    tradeError?.let {
                        Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassSurface)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Your listing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // ── Description (minimal) ───────────────────────────────────
                if (listing.description.isNotBlank()) {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Details",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            listing.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f,
                        )
                    }
                }

                // ── AI Risk Assessment (why risk is high) ───────────────────
                val riskAssessment = remember(listing) {
                    TradeRiskOracle.evaluate(
                        ProfileDto(walletAddress = listing.sellerWallet, auraScore = listing.sellerAuraScore),
                        listing,
                    )
                }
                val riskColor = when (riskAssessment.level) {
                    TradeRiskOracle.RiskLevel.LOW -> Orange500
                    TradeRiskOracle.RiskLevel.MEDIUM -> Gold500
                    TradeRiskOracle.RiskLevel.HIGH -> Orange500
                    TradeRiskOracle.RiskLevel.CRITICAL -> Color.Red
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(riskColor.copy(alpha = 0.08f))
                        .border(0.5.dp, riskColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Shield, null, modifier = Modifier.size(20.dp), tint = riskColor)
                            Text(
                                "AI Risk: ${riskAssessment.level.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = riskColor,
                            )
                        }
                        Text(
                            riskAssessment.recommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
                        )
                        if (riskAssessment.flags.isNotEmpty()) {
                            riskAssessment.flags.forEach { flag ->
                                Text(
                                    "• $flag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskColor.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }
                }

                // ── Seller footer ────────────────────────────────────────────
                val sellerRankInfo = remember(listing.sellerAuraScore) {
                    RankSystem.getRankInfo(listing.sellerAuraScore)
                }
                val rankColor = when (sellerRankInfo.rankName) {
                    "Ember" -> Color(0xFFE57373)
                    "Spark" -> Color(0xFF64B5F6)
                    "Flame" -> Orange500
                    "Nova" -> Gold500
                    "Radiant" -> Color(0xFFBA68C8)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassSurface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            sellerRankInfo.emoji,
                            fontSize = 20.sp
                        )
                        Column {
                            Text(
                                "Seller ${listing.sellerWallet.take(4)}…${listing.sellerWallet.takeLast(4)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    sellerRankInfo.rankName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = rankColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (sellerRankInfo.rankName != "Radiant") {
                                    Text(sellerRankInfo.tierString, style = MaterialTheme.typography.labelSmall, color = rankColor.copy(alpha=0.8f))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(riskColor.copy(alpha = 0.1f))
                            .border(0.5.dp, riskColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = Gold500)
                        Text(
                            "${sellerRankInfo.absoluteStars} Stars",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold500,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
