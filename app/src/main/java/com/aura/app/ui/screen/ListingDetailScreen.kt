package com.aura.app.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.model.Listing
import com.aura.app.model.MintedStatus
import com.aura.app.model.TradeSession
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SolanaGreen
import com.aura.app.ui.theme.Gold500
import com.aura.app.data.TradeRiskOracle
import com.aura.app.model.ProfileDto
import com.aura.app.wallet.WalletConnectionState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
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
    val listing = AuraRepository.getListing(listingId)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.title ?: "Listing", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (listing == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Listing not found", style = MaterialTheme.typography.titleMedium)
                Text("It may have been removed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack) { Text("Go Back") }
            }
            return@Scaffold
        }
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            ) {
                val imageUrl = listing.images.firstOrNull()
                if (imageUrl != null && imageUrl.isNotBlank()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val modelData = if (imageUrl.startsWith("/")) "file://$imageUrl" else imageUrl
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(modelData)
                            .crossfade(true)
                            .build(),
                        contentDescription = listing.title,
                        modifier = Modifier.fillMaxWidth(),
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
                            )
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Orange500.copy(alpha = 0.3f), Orange500.copy(alpha = 0.2f)),
                                ),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            when (listing.mintedStatus) {
                                MintedStatus.VERIFIED -> Orange500
                                MintedStatus.MINTED -> MaterialTheme.colorScheme.primary
                                MintedStatus.PENDING -> MaterialTheme.colorScheme.outline
                                MintedStatus.SOLD -> Color(0xFF4CAF50)
                            },
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (listing.mintedStatus == MintedStatus.VERIFIED || listing.mintedStatus == MintedStatus.SOLD) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = when (listing.mintedStatus) {
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
                        text = listing.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "%.2f SOL".format(listing.priceLamports / 1_000_000_000.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = SolanaGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Marketplace detail chips (Condition, Category, etc)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(listing.condition, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Description Block
                if (listing.description.isNotBlank()) {
                    Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(listing.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Seller Block (FB Marketplace style)
                Text("Seller Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = listing.sellerWallet.take(2).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                "User ${listing.sellerWallet.take(6)}...${listing.sellerWallet.takeLast(4)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            // Note: To display true seller join date, we would need to fetch the seller's profile
                            // For now, removing the hardcoded 2024 string to prevent false information.
                        }
                    }
                }

                // ── AI Trade-Risk Oracle ──────────────────────────────
                // Use the listing's cached seller data, NOT the current user's profile
                val riskAssessment = remember(listing) {
                    val sellerProxy = ProfileDto(
                        walletAddress = listing.sellerWallet,
                        auraScore = listing.sellerAuraScore,
                    )
                    TradeRiskOracle.evaluate(sellerProxy, listing)
                }

                val riskColor = when (riskAssessment.level) {
                    TradeRiskOracle.RiskLevel.LOW -> SolanaGreen
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

                val scope = rememberCoroutineScope()
                var isStartingTrade by remember { mutableStateOf(false) }
                var tradeError by remember { mutableStateOf<String?>(null) }
                var showBuyConfirm by remember { mutableStateOf(false) }
                val isSeller = walletAddress == listing.sellerWallet

                if (!isSeller) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onChatClicked,
                            enabled = walletAddress != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Message Seller", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { showBuyConfirm = true },
                            enabled = walletAddress != null && listing.mintedStatus != MintedStatus.SOLD && !isStartingTrade,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (isStartingTrade) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(
                                    when {
                                        listing.mintedStatus == MintedStatus.SOLD -> "Sold"
                                        walletAddress != null -> "Start Meetup / Buy"
                                        else -> "Connect Wallet First"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (showBuyConfirm) {
                        AlertDialog(
                            onDismissRequest = { showBuyConfirm = false },
                            title = { Text("Start Trade?") },
                            text = { Text("You'll be guided to meet the seller and verify the item before any payment.") },
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
                                                onStartMeetup()
                                            } catch (e: Exception) {
                                                tradeError = e.message ?: "Failed to start trade"
                                            } finally {
                                                isStartingTrade = false
                                            }
                                        }
                                    }
                                }) { Text("Start Meetup", color = SolanaGreen) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBuyConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    tradeError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                    ) {
                        Text(
                            "This is your listing.",
                            modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
