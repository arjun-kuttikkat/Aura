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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.app.data.MockBackend
import com.aura.app.model.Listing
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
    val listing = MockBackend.getListing(listingId)
    val pubkey by WalletConnectionState.pubkey.collectAsState()

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
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
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
                                    colors = listOf(Orange500.copy(alpha = 0.3f), Orange700.copy(alpha = 0.2f)),
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
                                MintedStatus.VERIFIED -> Orange700
                                MintedStatus.MINTED -> MaterialTheme.colorScheme.primary
                                MintedStatus.PENDING -> MaterialTheme.colorScheme.outline
                            },
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        text = "%.2f SOL".format(listing.priceLamports / 1_000_000_000.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Condition", style = MaterialTheme.typography.labelMedium)
                        Text(listing.condition, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Seller", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${listing.sellerWallet.take(6)}...${listing.sellerWallet.takeLast(4)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Button(
                    onClick = {
                        MockBackend.createTradeSession(
                            listingId = listing.id,
                            buyerWallet = pubkey ?: "BUYER_PLACEHOLDER",
                            sellerWallet = listing.sellerWallet,
                        )
                        onStartMeetup()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Start Meetup / Buy", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
