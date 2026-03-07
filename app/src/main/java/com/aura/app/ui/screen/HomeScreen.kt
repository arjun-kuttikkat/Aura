package com.aura.app.ui.screen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import com.aura.app.ui.theme.SlateLight
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.util.springScale
import com.aura.app.util.CryptoPriceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onListingClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val listings by AuraRepository.listings.collectAsState(initial = emptyList())
    val profile by AuraRepository.currentProfile.collectAsState(initial = null)
    val walletAddress by com.aura.app.wallet.WalletConnectionState.walletAddress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val locationPermissionState = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    // Load profile when wallet is connected — fixes Aura card not showing on first visit
    LaunchedEffect(walletAddress) {
        walletAddress?.let { AuraRepository.loadProfile(it) }
    }
    // Refresh listings when HomeScreen appears — load all from Supabase so Global shows everything
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AuraRepository.refreshListingsAwait()
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isCompact = screenWidthDp < 380
    val contentPaddingHorizontal = if (isCompact) 12.dp else 16.dp
    val gridMinCellSize = if (isCompact) 128.dp else 148.dp

    Scaffold(
        topBar = {
            MainTopBar(
                title = "Aura",
                logoSize = if (isCompact) 36.dp else 44.dp,
                onZoneResourceClick = { onNavigate(com.aura.app.navigation.Routes.ZONE_REFINEMENT) },
            )
        },
    ) { padding ->
        val listState = rememberLazyGridState()
        val showTopFade by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    try {
                        AuraRepository.refreshListingsAwait()
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
        // ── Filter State ──
        var searchQuery by remember { mutableStateOf("") }
        var selectedScope by remember { mutableStateOf("Global") }
        val scopes = listOf("Nearby", "Explore", "Global")

        var selectedCondition by remember { mutableStateOf("All") }
        val conditions = listOf("All", "New", "Like New", "Good", "Fair")

        var sortOrder by remember { mutableStateOf("Newest") }
        val sortOptions = listOf("Newest", "Oldest", "Price↑", "Price↓")

        var showFilterSheet by remember { mutableStateOf(false) }
        val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        // ── Derive filtered + sorted listings ──
        val filteredListings = remember(listings, searchQuery, selectedScope, selectedCondition, sortOrder, locationPermissionState.status.isGranted) {
            var result = listings

            // Search filter
            if (searchQuery.isNotBlank()) {
                result = result.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
                }
            }

            // Distance filter — Nearby/Explore require location+distance and actually reduce the feed.
            result = when (selectedScope) {
                "Nearby" -> if (!locationPermissionState.status.isGranted) emptyList() else result.filter { it.distanceMeters != null && it.distanceMeters < 5_000 }
                "Explore" -> if (!locationPermissionState.status.isGranted) emptyList() else result.filter { it.distanceMeters != null && it.distanceMeters < 50_000 }
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
                "Price↑" -> result.sortedBy { it.priceLamports }
                "Price↓" -> result.sortedByDescending { it.priceLamports }
                else -> result
            }

            // Paid boosts override base sort until promotion expiry.
            val now = System.currentTimeMillis()
            result = result.sortedWith(
                compareByDescending<com.aura.app.model.Listing> { it.isPromoted && (it.promotedUntil ?: 0L) > now }
                    .thenByDescending { it.promotedAt ?: 0L }
            )

            result
        }

        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Adaptive(minSize = gridMinCellSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = contentPaddingHorizontal, end = contentPaddingHorizontal, top = contentPaddingHorizontal, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp),
        ) {
            // Hero Banner Card — show loading state when profile not yet loaded
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                val p = profile
                if (p != null) {
                    HeroBannerCard(
                        auraScore = p.auraScore,
                        streakDays = p.streakDays,
                        listingsCount = listings.size,
                        compact = isCompact,
                    )
                } else if (walletAddress != null) {
                    HeroBannerCardSkeleton(compact = isCompact)
                }
            }

            // Premium search bar
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateElevated)
                        .border(0.5.dp, SlateLight.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = if (isCompact) 12.dp else 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Orange500.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp),
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search listings...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            // Location scope + Filters row — all fit on one line (no cut-off on small phones)
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                val scopeTabPaddingH = if (isCompact) 8.dp else 14.dp
                val scopeTabPaddingV = if (isCompact) 7.dp else 10.dp
                val tabSpacing = if (isCompact) 6.dp else 10.dp

                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Scope tabs — flex to fill, equal width so Global never cuts off
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(tabSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        scopes.forEach { scopeLabel ->
                            val isSelected = scopeLabel == selectedScope
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Brush.linearGradient(listOf(Orange500, Gold500.copy(alpha = 0.9f)))
                                        else Brush.linearGradient(listOf(GlassSurface, GlassSurface))
                                    )
                                    .border(
                                        0.5.dp,
                                        if (isSelected) Color.Transparent else SlateLight.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        selectedScope = scopeLabel
                                        if ((scopeLabel == "Nearby" || scopeLabel == "Explore") && !locationPermissionState.status.isGranted) {
                                            locationPermissionState.launchPermissionRequest()
                                        } else {
                                            scope.launch { AuraRepository.refreshListingsAwait() }
                                        }
                                    }
                                    .padding(horizontal = scopeTabPaddingH, vertical = scopeTabPaddingV),
                            ) {
                                Text(
                                    scopeLabel,
                                    style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // Filters — icon-only on compact to save space for scope tabs
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassSurface)
                            .border(0.5.dp, SlateLight.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable { showFilterSheet = true }
                            .padding(
                                horizontal = if (isCompact) 12.dp else 14.dp,
                                vertical = scopeTabPaddingV,
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 0.dp else 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filters",
                                tint = Orange500,
                                modifier = Modifier.size(if (isCompact) 18.dp else 20.dp),
                            )
                            if (!isCompact) {
                                Text(
                                    "Filters",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (selectedCondition != "All") {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Orange500),
                                )
                            }
                        }
                    }
                }
            }

            // Sort + header row — compact-friendly
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Marketplace",
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(if (isCompact) 6.dp else 12.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp),
                    ) {
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
                                    .padding(horizontal = if (isCompact) 6.dp else 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Listing grid — batch 8 cards at a time for snappy loading
            itemsIndexed(
                items = filteredListings,
                key = { _, it -> it.id },
            ) { index, listing ->
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay((index / 8) * 30L)
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
                        imageUrl = listing.images.firstOrNull()?.takeIf { it.isNotBlank() },
                        location = listing.location ?: listing.emirate,
                        onClick = { onListingClick(listing.id) },
                        compact = isCompact,
                    )
                }
            }

            // Empty state
            if (filteredListings.isEmpty() && listings.isNotEmpty()) {
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(if (isCompact) 24.dp else 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Orange500.copy(alpha = 0.7f),
                        )
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
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    var ctaVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(800)
                        ctaVisible = true
                    }
                    if (!ctaVisible) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(if (isCompact) 32.dp else 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Orange500)
                        }
                    }
                    AnimatedVisibility(
                        visible = ctaVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { 60 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isCompact) 24.dp else 32.dp, horizontal = contentPaddingHorizontal),
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
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
        } // end LazyVerticalGrid

        // Filter bottom sheet — inside PullToRefreshBox so filter state is in scope
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = filterSheetState,
                containerColor = SlateElevated,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompact) 16.dp else 24.dp)
                        .padding(bottom = 32.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    Text(
                        "Condition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    conditions.forEach { cond ->
                        val isSelected = cond == selectedCondition
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Orange500.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .clickable { selectedCondition = cond; showFilterSheet = false }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                cond,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Orange500,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end PullToRefreshBox

        // Fade below Aura top bar — only when scrolled
        AnimatedVisibility(
            visible = showTopFade,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        }
        }
    }
}

@Composable
private fun HeroBannerCardSkeleton(compact: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Orange500.copy(alpha = 0.08f),
                        Gold500.copy(alpha = 0.05f),
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
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Box(
                    modifier = Modifier
                        .width(if (compact) 60.dp else 80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GlassSurface)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(if (compact) 80.dp else 100.dp)
                        .height(if (compact) 28.dp else 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Orange500.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(if (compact) 90.dp else 120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GlassSurface)
                )
            }
            Box(
                modifier = Modifier
                    .size(if (compact) 44.dp else 56.dp)
                    .clip(CircleShape)
                    .background(GlassSurface),
            )
        }
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp),
            color = Orange500,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun HeroBannerCard(
    auraScore: Int,
    streakDays: Int,
    listingsCount: Int,
    compact: Boolean = false,
) {
    val animatedScore by animateIntAsState(targetValue = auraScore, animationSpec = tween(1800, easing = FastOutSlowInEasing), label = "score")
    val animatedStreak by animateIntAsState(targetValue = streakDays, animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "streak")
    val animatedListings by animateIntAsState(targetValue = listingsCount, animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "listings")
    val pad = if (compact) 12.dp else 20.dp
    val iconSize = if (compact) 44.dp else 56.dp
    val starIconSize = if (compact) 22.dp else 28.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 16.dp else 20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Orange500.copy(alpha = 0.15f),
                        Gold500.copy(alpha = 0.1f),
                        Color.Transparent,
                    ),
                ),
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(if (compact) 16.dp else 20.dp))
            .padding(pad),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(2.dp, Orange500.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Gold500,
                        modifier = Modifier.size(starIconSize),
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
    location: String? = null,
    onClick: () -> Unit,
    compact: Boolean = false,
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
                                MintedStatus.SOLD -> com.aura.app.ui.theme.Orange500
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
                    .padding(if (compact) 6.dp else 12.dp),
            ) {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!location.isNullOrBlank()) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (compact) 2.dp else 4.dp),
                    )
                }
                Text(
                    text = CryptoPriceFormatter.formatSol(priceSol),
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = Orange500,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (compact) 2.dp else 4.dp),
                )
            }
        }
    }
}
