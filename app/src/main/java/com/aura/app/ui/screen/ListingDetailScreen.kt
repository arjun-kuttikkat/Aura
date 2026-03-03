package com.aura.app.ui.screen

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.theme.DarkBase
import com.aura.app.model.MintedStatus
import com.aura.app.model.TradeSession
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.Orange700
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
                                Orange500.copy(alpha = 0.15f),
                                DarkBase,
                            ),
                        ),
                    )
                    .border(0.5.dp, Orange500.copy(alpha = 0.2f), RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp)),
            ) {
                val imageUrl = listing.images.firstOrNull()
                val imageModel = when {
                    imageUrl == null -> null
                    imageUrl.startsWith("http") -> imageUrl
                    else -> "file://$imageUrl"
                }
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = listing.title,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Orange500.copy(alpha = 0.25f), Orange700.copy(alpha = 0.12f)),
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
                            when (listing.mintedStatus) {
                                MintedStatus.VERIFIED -> Orange500
                                MintedStatus.MINTED -> com.aura.app.ui.theme.Gold500
                                MintedStatus.PENDING -> MaterialTheme.colorScheme.outline
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
                        if (listing.mintedStatus == MintedStatus.VERIFIED) {
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
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "%.2f SOL ◎".format(listing.priceLamports / 1_000_000_000.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Orange500,
                        fontWeight = FontWeight.Bold,
                    )
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = Orange500,
                    cornerRadius = 16.dp,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Condition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listing.condition, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Seller", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${listing.sellerWallet.take(6)}...${listing.sellerWallet.takeLast(4)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Button(
                    onClick = {
                        walletAddress?.let { wallet ->
                            AuraRepository.createTradeSession(
                                listingId = listing.id,
                                buyerWallet = wallet,
                                sellerWallet = listing.sellerWallet,
                            )
                            onStartMeetup()
                        }
                    },
                    enabled = walletAddress != null,
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
                        if (walletAddress != null) "Start Meetup / Buy" else "Connect Wallet First",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
