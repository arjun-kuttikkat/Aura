package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.app.data.MockBackend
import com.aura.app.model.MintedStatus
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.Orange700


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onListingClick: (String) -> Unit,
) {
    val listings by MockBackend.listings.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            MainTopBar(title = "Aura", logoSize = 44.dp)
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = listings,
                key = { it.id },
            ) { listing ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    ),
                ) {
                    ListingCard(
                        title = listing.title,
                        priceSol = listing.priceLamports / 1_000_000_000.0,
                        status = listing.mintedStatus,
                        imageUrl = listing.images.firstOrNull(),
                        onClick = { onListingClick(listing.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListingCard(
    title: String,
    priceSol: Double,
    status: MintedStatus,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Orange500.copy(alpha = 0.3f),
                        Color.Transparent,
                    ),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = if (imageUrl.startsWith("http")) imageUrl else "file://$imageUrl",
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Orange500.copy(alpha = 0.4f),
                                        Gold500.copy(alpha = 0.3f),
                                    ),
                                ),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            when (status) {
                                MintedStatus.VERIFIED -> Orange700
                                MintedStatus.MINTED -> Gold500
                                MintedStatus.PENDING -> Color.Gray
                            },
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = when (status) {
                            MintedStatus.PENDING -> "Pending"
                            MintedStatus.MINTED -> "Minted"
                            MintedStatus.VERIFIED -> "✓ Verified"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = "%.2f SOL".format(priceSol),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
