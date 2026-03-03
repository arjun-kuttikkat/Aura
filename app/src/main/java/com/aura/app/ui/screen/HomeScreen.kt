package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.model.MintedStatus
import com.aura.app.navigation.Routes
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.aura.app.utils.SpatialManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onListingClick: (String) -> Unit,
) {
    val listings by AuraRepository.listings.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val currentProfile by AuraRepository.currentProfile.collectAsState()
    var currentHotzone by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                @SuppressLint("MissingPermission")
                val loc: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    val h3Index = SpatialManager.getHotzoneIndex(loc.latitude, loc.longitude)
                    Log.d("HomeScreen", "User is in Hotzone: $h3Index")
                    currentHotzone = h3Index
                    AuraRepository.refreshListings(h3Index)
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val filteredListings = listings.filter { listing -> 
        val matchesSearch = listing.title.contains(searchQuery, ignoreCase = true)
        val matchesCategory = if (selectedCategory == null) {
            true
        } else if (selectedCategory == "Everything Else") {
            val otherCategories = listOf("Motors", "Property", "Jobs", "Furniture")
            otherCategories.none { listing.title.contains(it, ignoreCase = true) }
        } else {
            listing.title.contains(selectedCategory!!, ignoreCase = true)
        }
        matchesSearch && matchesCategory
    }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filteredListings.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.LocationOn, 
                        contentDescription = null, 
                        tint = Color.Gray.copy(alpha=0.5f), 
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Hotzone $currentHotzone is Quiet", 
                        style = MaterialTheme.typography.titleLarge, 
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No authentic items detected within your localized H3 Hexagon cell. Increase your Aura to expand your Turf reach into adjacent zones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { filteredListings.size })
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val listing = filteredListings[page]
                    ImmersiveListingScreen(
                        listing = listing,
                        onClick = { onListingClick(listing.id) }
                    )
                }
            }

            // Overlay the Header (Search, Location, Categories) at the top so it floats over the immersive feed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha=0.8f), Color.Transparent)
                        )
                    )
            ) {
                HomeHeader(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it },
                    onAuraCheckClick = { onListingClick(Routes.AURA_CHECK) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit,
    onAuraCheckClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        // Top Row: Location & Aura Check Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = Orange500, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Aura Network", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            // Replaced Quick Pay / Aura Check FABs with a top icon for Aura Check
            IconButton(
                onClick = onAuraCheckClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(GlassSurface)
            ) {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = "Aura Check", tint = Gold500)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp)),
            placeholder = { Text("What are you looking for?", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = GlassSurface,
                unfocusedContainerColor = GlassSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Categories mimicking Dubizzle 
        val categories = listOf(
            "Motors" to Icons.Default.DirectionsCar,
            "Property" to Icons.Default.Home,
            "Jobs" to Icons.Default.BusinessCenter,
            "Furniture" to Icons.Default.Weekend,
            "Everything Else" to Icons.Default.Category
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories) { (name, icon) ->
                CategoryItem(
                    name = name, 
                    icon = icon,
                    isSelected = name == selectedCategory,
                    onClick = { 
                        if (selectedCategory == name) onCategorySelect(null) 
                        else onCategorySelect(name) 
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(name: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isSelected) Orange500.copy(alpha=0.3f) else GlassSurface)
                .border(2.dp, if (isSelected) Orange500 else GlassBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (isSelected) Orange500 else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = if (isSelected) Orange500 else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ImmersiveListingScreen(
    listing: com.aura.app.model.Listing,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
    ) {
        val imageUrl = listing.images.firstOrNull()
        if (imageUrl != null) {
            AsyncImage(
                model = if (imageUrl.startsWith("http")) imageUrl else "file://$imageUrl",
                contentDescription = listing.title,
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
                                Orange500.copy(alpha = 0.5f),
                                Gold500.copy(alpha = 0.3f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Orange500.copy(alpha = 0.4f),
                    modifier = Modifier.size(120.dp),
                )
            }
        }

        // Bottom gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // Overlay Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (listing.mintedStatus) {
                            MintedStatus.VERIFIED -> SuccessGreen
                            MintedStatus.MINTED -> Gold500
                            MintedStatus.PENDING -> Color.Gray.copy(alpha = 0.8f)
                            MintedStatus.ARCHIVED -> Color.DarkGray
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = when (listing.mintedStatus) {
                        MintedStatus.PENDING -> "Pending Verif"
                        MintedStatus.MINTED -> "Minted"
                        MintedStatus.VERIFIED -> "✓ Hardware Verified"
                        MintedStatus.ARCHIVED -> "Archived Museum"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = listing.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.2f SOL".format(listing.priceLamports / 1_000_000_000.0),
                style = MaterialTheme.typography.headlineSmall,
                color = Orange500,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
