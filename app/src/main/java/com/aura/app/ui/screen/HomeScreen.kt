package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.model.MintedStatus
import com.aura.app.ui.components.AppLogo
import com.aura.app.ui.components.AuraHaptics
import com.aura.app.ui.components.AuraScoreRing
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.AuraAnimations
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onListingClick: (String) -> Unit,
) {
    val listings by AuraRepository.listings.collectAsState(initial = emptyList())
    val profile by AuraRepository.currentProfile.collectAsState()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {},
        containerColor = DarkBase,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .drawWithContent {
                        drawContent()
                        val topFadePx = 100.dp.toPx()
                        val bottomFadePx = 120.dp.toPx()
                        // Top fade — content fades when scrolling under bar
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(DarkBase, Color.Transparent),
                                startY = 0f,
                                endY = topFadePx,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, topFadePx),
                        )
                        // Bottom fade
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, DarkBase),
                                startY = size.height - bottomFadePx,
                                endY = size.height,
                            ),
                            topLeft = Offset(0f, size.height - bottomFadePx),
                            size = Size(size.width, bottomFadePx),
                        )
                    },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 72.dp,
                    bottom = 0.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    HeroBannerCard(
                        auraScore = profile?.auraScore ?: 50,
                        streakDays = profile?.streakDays ?: 0,
                        listingsCount = listings.size,
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    Text(
                        "Marketplace",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                itemsIndexed(
                    items = listings,
                    key = { _, it -> it.id },
                ) { index, listing ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = AuraAnimations.AuraEaseOut) +
                            slideInVertically(
                                initialOffsetY = { 24 },
                                animationSpec = spring(dampingRatio = 0.7f),
                            ),
                    ) {
                        ListingCard(
                            title = listing.title,
                            priceSol = listing.priceLamports / 1_000_000_000.0,
                            status = listing.mintedStatus,
                            imageUrl = listing.images.firstOrNull(),
                            onClick = {
                                AuraHaptics.lightTap(haptic)
                                onListingClick(listing.id)
                            },
                        )
                    }
                }
            }

            MainTopBar(
                modifier = Modifier.align(Alignment.TopStart),
                title = "Aura",
                logoSize = 44.dp,
                onZoneResourceClick = { onListingClick(com.aura.app.navigation.Routes.ZONE_REFINEMENT) },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 108.dp)
                    .scale(0.9f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        AuraHaptics.lightTap(haptic)
                        onListingClick(com.aura.app.navigation.Routes.P2P_EXCHANGE)
                    },
                    icon = { Icon(Icons.Filled.Send, "Quick Pay", modifier = Modifier.size(18.dp)) },
                    text = { Text("Quick Pay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) },
                    containerColor = Gold500,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        AuraHaptics.lightTap(haptic)
                        onListingClick(com.aura.app.navigation.Routes.AURA_CHECK)
                    },
                    icon = { Icon(Icons.Filled.Star, "Aura Check", modifier = Modifier.size(18.dp)) },
                    text = { Text("Aura Check", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) },
                    containerColor = Orange500,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroBannerCard(
    auraScore: Int,
    streakDays: Int,
    listingsCount: Int,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = Orange500,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Your Aura",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AuraScoreRing(
                            score = auraScore,
                            size = 68.dp,
                            animate = true,
                            showNumber = true,
                            strokeWidth = 5.dp,
                        )
                        Text(
                            "/100",
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = 0.3.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Orange500,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "$streakDays day streak",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.5.dp, Orange500.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        "$listingsCount items",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
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
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .scale(scale)
            .clickable(onClick = onClick),
        glowColor = Orange500,
        cornerRadius = 16.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                        Orange500.copy(alpha = 0.2f),
                                        Gold500.copy(alpha = 0.1f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Orange500.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (status) {
                                MintedStatus.VERIFIED -> SuccessGreen
                                MintedStatus.MINTED -> Gold500
                                MintedStatus.PENDING -> Color.Gray.copy(alpha = 0.8f)
                            },
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
                        fontWeight = FontWeight.Bold,
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
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%.2f SOL ◎".format(priceSol),
                    style = MaterialTheme.typography.titleMedium,
                    color = Orange500,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}