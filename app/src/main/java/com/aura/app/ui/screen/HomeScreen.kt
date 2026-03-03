package com.aura.app.ui.screen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.model.MintedStatus
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.SlateElevated
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.util.springScale
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onListingClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val listings by AuraRepository.listings.collectAsState(initial = emptyList())
    val profile by AuraRepository.currentProfile.collectAsState()

    Scaffold(
        topBar = {
            MainTopBar(
                title = "Aura",
                logoSize = 44.dp,
                onZoneResourceClick = { onNavigate(com.aura.app.navigation.Routes.ZONE_REFINEMENT) },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigate(com.aura.app.navigation.Routes.DIRECTIVES) },
                    icon = { Icon(Icons.Filled.Star, "Directives") },
                    text = { Text("Directives", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                    containerColor = com.aura.app.ui.theme.UltraViolet,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                )
                ExtendedFloatingActionButton(
                    onClick = { onNavigate(com.aura.app.navigation.Routes.P2P_EXCHANGE) },
                    icon = { Icon(Icons.Filled.Send, "Quick Pay") },
                    text = { Text("Quick Pay", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                    containerColor = com.aura.app.ui.theme.DarkVoid,
                    contentColor = com.aura.app.ui.theme.SolanaGreen,
                    shape = RoundedCornerShape(16.dp),
                )
                ExtendedFloatingActionButton(
                    onClick = { onNavigate(com.aura.app.navigation.Routes.CREATE_LISTING) },
                    icon = { Icon(Icons.Filled.Star, "Create Listing") },
                    text = { Text("Create Listing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                    containerColor = com.aura.app.ui.theme.SolanaGreen,
                    contentColor = com.aura.app.ui.theme.DarkVoid,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
    ) { padding ->
        // ── Filter State ──
        var selectedScope by remember { mutableStateOf("Global") }
        val scopes = listOf("Nearby", "Explore", "Global")

        var selectedCondition by remember { mutableStateOf("All") }
        val conditions = listOf("All", "New", "Like New", "Good", "Fair")

        var sortOrder by remember { mutableStateOf("Newest") }
        val sortOptions = listOf("Newest", "Oldest", "Price ↑", "Price ↓")

        // ── Derive filtered + sorted listings ──
        val filteredListings = remember(listings, selectedScope, selectedCondition, sortOrder) {
            var result = listings

            // Distance filter
            result = when (selectedScope) {
                "Nearby" -> result.filter { (it.distanceMeters ?: Int.MAX_VALUE) < 5_000 }
                "Explore" -> result.filter { (it.distanceMeters ?: Int.MAX_VALUE) < 50_000 }
                else -> result // Global = all
            }

            // Condition filter
            if (selectedCondition != "All") {
                result = result.filter { it.condition.equals(selectedCondition, ignoreCase = true) }
            }

            // Sort
            result = when (sortOrder) {
                "Newest" -> result.sortedByDescending { it.createdAt }
                "Oldest" -> result.sortedBy { it.createdAt }
                "Price ↑" -> result.sortedBy { it.priceLamports }
                "Price ↓" -> result.sortedByDescending { it.priceLamports }
                else -> result
            }

            result
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero card spanning full width
            item(span = { GridItemSpan(2) }) {
                HeroBannerCard(
                    auraScore = profile?.auraScore ?: 50,
                    streakDays = profile?.streakDays ?: 0,
                    listingsCount = listings.size,
                )
            }

            // Marketplace scope tabs
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    scopes.forEach { scope ->
                        val isSelected = scope == selectedScope
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Orange500 else GlassSurface
                                )
                                .clickable { selectedScope = scope }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                scope,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Condition category chips
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    conditions.forEach { cond ->
                        val isSelected = cond == selectedCondition
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) com.aura.app.ui.theme.SolanaGreen.copy(alpha = 0.9f)
                                    else com.aura.app.ui.theme.GlassSurface
                                )
                                .clickable { selectedCondition = cond }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                cond,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Sort + header row
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Marketplace",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        sortOptions.forEach { opt ->
                            val isSelected = opt == sortOrder
                            Text(
                                opt,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { sortOrder = opt }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Listing grid
            itemsIndexed(
                items = filteredListings,
                key = { _, it -> it.id },
            ) { index, listing ->
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 40L)
                    isVisible = true
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInVertically(
                        initialOffsetY = { 100 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
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

            // Empty state
            if (filteredListings.isEmpty() && listings.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🔍", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No listings match your filters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // First-run empty state with CTA
            if (listings.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    var ctaVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(600)
                        ctaVisible = true
                    }
                    AnimatedVisibility(
                        visible = ctaVisible,
                        enter = fadeIn(tween(500)) + slideInVertically(
                            initialOffsetY = { 60 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = com.aura.app.ui.theme.SolanaGreen,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Your marketplace is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "List your first item and let the Aura ecosystem verify it on-chain.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { onNavigate(com.aura.app.navigation.Routes.CREATE_LISTING) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.aura.app.ui.theme.SolanaGreen,
                                    contentColor = com.aura.app.ui.theme.DarkVoid,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Create Your First Listing", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
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
    val animatedScore by animateIntAsState(targetValue = auraScore, animationSpec = tween(1800, easing = FastOutSlowInEasing), label = "score")
    val animatedStreak by animateIntAsState(targetValue = streakDays, animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "streak")
    val animatedListings by animateIntAsState(targetValue = listingsCount, animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "listings")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Orange500.copy(alpha = 0.15f),
                        Gold500.copy(alpha = 0.1f),
                        Color.Transparent,
                    ),
                ),
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Your Aura",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$animatedScore",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Orange500,
                    )
                    Text(
                        "/100",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Orange500,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "$animatedStreak day streak",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(2.dp, Orange500.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Gold500,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$listingsCount items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.25f))
            .springScale(isPressed = isPressed, scaleDown = 0.94f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SlateElevated),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (imageUrl != null && imageUrl.isNotBlank()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val modelData = if (imageUrl.startsWith("/")) "file://$imageUrl" else imageUrl
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(modelData)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Orange500.copy(alpha = 0.2f),
                                                Gold500.copy(alpha = 0.15f),
                                            ),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = Orange500.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        },
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Orange500.copy(alpha = 0.08f),
                                                Gold500.copy(alpha = 0.05f),
                                            ),
                                        ),
                                    ),
                            )
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Orange500.copy(alpha = 0.3f),
                                        Gold500.copy(alpha = 0.2f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Orange500.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                // Status badge
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
                                MintedStatus.SOLD -> Color(0xFF4CAF50)
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = when (status) {
                            MintedStatus.PENDING -> "Pending"
                            MintedStatus.MINTED -> "Minted"
                            MintedStatus.VERIFIED -> "✓ Verified"
                            MintedStatus.SOLD -> "Sold"
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
                    text = "%.2f SOL".format(priceSol),
                    style = MaterialTheme.typography.titleMedium,
                    color = Orange500,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
