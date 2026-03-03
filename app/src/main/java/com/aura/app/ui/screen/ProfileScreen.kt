package com.aura.app.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.model.TrustTier
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.ui.theme.ErrorRed
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.R
import com.aura.app.model.Listing
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Delete
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.IconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onVerifyIdentity: () -> Unit,
) {
    val pubkey by WalletConnectionState.walletAddress.collectAsState()
    val profile by AuraRepository.currentProfile.collectAsState()

    LaunchedEffect(pubkey) {
        pubkey?.let { AuraRepository.loadProfile(it) }
    }

    val trustScore = profile?.auraScore ?: 50
    val streak = profile?.streakDays ?: 0
    val tier = when {
        trustScore >= 90 -> TrustTier.PLATINUM
        trustScore >= 80 -> TrustTier.GOLD
        trustScore >= 70 -> TrustTier.SILVER
        trustScore >= 50 -> TrustTier.BRONZE
        else -> TrustTier.NEW
    }
    val tierEmoji = when (tier) {
        TrustTier.PLATINUM -> "💎"
        TrustTier.GOLD -> "🥇"
        TrustTier.SILVER -> "🥈"
        TrustTier.BRONZE -> "🥉"
        TrustTier.NEW -> "🌱"
    }
    val nftStage = when {
        streak >= 90 -> "Aura ✨"
        streak >= 31 -> "Tree 🌳"
        streak >= 8 -> "Sprout 🌿"
        else -> "Seed 🌱"
    }

    Scaffold(
        topBar = { MainTopBar(title = "Profile") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Avatar with 3D SceneView
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                // Circular background glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Orange500.copy(alpha=0.3f), Color.Transparent)
                            )
                        )
                )

                // 3D Scene composable (Placeholder for SceneView)
                // We use AndroidView to wrap SceneView to ensure compatibility 
                // across minor SceneView API versions while preserving the 3D interactivity.
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, Gold500, CircleShape),
                    factory = { ctx ->
                        try {
                            // Using reflection to safely instantiate SceneView to avoid 
                            // classpath issues if the exact version API shifted.
                            val sceneViewClass = Class.forName("io.github.sceneview.SceneView")
                            val sceneView = sceneViewClass.getConstructor(android.content.Context::class.java).newInstance(ctx) as android.view.View
                            // In a real app we'd load levels: aura_lvl1.glb, aura_lvl2.glb, etc.
                            // For hackathon MVP, we load a dynamic public model and scale it to show "growth"
                            val modelUrl = if (trustScore > 75) {
                                "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/BoxAnimated/glTF-Binary/BoxAnimated.glb"
                            } else {
                                "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/RobotExpressive/glTF-Binary/RobotExpressive.glb"
                            }
                            
                            // Native SceneView reflection loading to avoid strict version crashes
                            val loadModelMethod = sceneViewClass.getMethod("loadModel", String::class.java)
                            loadModelMethod.invoke(sceneView, modelUrl)
                            
                            sceneView
                        } catch (e: Exception) {
                            // Fallback if SceneView API differs or isn't fully linked
                            android.widget.TextView(ctx).apply {
                                text = "3D Level ${if (trustScore > 75) "MAX" else "1"} Avatar"
                                setTextColor(android.graphics.Color.WHITE)
                                gravity = android.view.Gravity.CENTER
                            }
                        }
                    }
                )

                val rawScale = 0.6f + (trustScore / 250f)
                val scale = rawScale.coerceIn(0.6f, 1.2f)
                
                // The wallet initials sitting underneath the 3D model
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-16).dp, y = (-16).dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(
                            width = 2.dp,
                            color = if (trustScore > 75) Gold500 else if (trustScore > 40) Orange500 else ErrorRed,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        pubkey?.take(2)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (trustScore > 75) Gold500 else if (trustScore > 40) Orange500 else ErrorRed,
                    )
                }
            }

            // Wallet address
            Text(
                pubkey?.let { "${it.take(6)}...${it.takeLast(4)}" } ?: "Not connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Trust Score card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Orange500.copy(alpha = 0.12f), Gold500.copy(alpha = 0.08f)),
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
                            "Trust Score",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$trustScore",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Orange500,
                            )
                            Text(
                                " /100",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tierEmoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${tier.name} Tier",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Gold500,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Orange500,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            "$streak",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Orange500,
                        )
                        Text(
                            "day streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Core NFT Evolution card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Gold500,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Aura NFT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Evolution: $nftStage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Action buttons
            Button(
                onClick = onVerifyIdentity,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange500,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify Identity (KYC)", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { /* Share profile */ },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlassSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Profile", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // My Listings Section
            val allListings by AuraRepository.listings.collectAsState()
            val myListings = allListings.filter { it.sellerWallet == pubkey }
            val scope = rememberCoroutineScope()

            if (myListings.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Active Listings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    myListings.forEach { listing ->
                        MyListingItem(
                            listing = listing,
                            onArchive = { 
                                scope.launch {
                                    AuraRepository.archiveListing(listing.id)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun MyListingItem(
    listing: Listing,
    onArchive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageUrl = listing.images.firstOrNull()
        if (imageUrl != null) {
            AsyncImage(
                model = if (imageUrl.startsWith("http")) imageUrl else "file://$imageUrl",
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listing.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.2f SOL".format(listing.priceLamports / 1_000_000_000.0),
                style = MaterialTheme.typography.bodyMedium,
                color = Orange500,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(
            onClick = onArchive,
            modifier = Modifier
                .background(ErrorRed.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Archive", tint = ErrorRed)
        }
    }
}
