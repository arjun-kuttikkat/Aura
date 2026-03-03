package com.aura.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.model.TrustTier
import com.aura.app.ui.components.AuraHaptics
import com.aura.app.ui.components.AuraScoreRing
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.AuraAnimations
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.wallet.WalletConnectionState

@Composable
fun ProfileScreen(
    onVerifyIdentity: () -> Unit,
) {
    val pubkey by WalletConnectionState.walletAddress.collectAsState()
    val profile by AuraRepository.currentProfile.collectAsState(initial = null)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

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
    val tierGlowColor = when (tier) {
        TrustTier.PLATINUM -> Color(0xFFE040FB)
        TrustTier.GOLD -> Gold500
        TrustTier.SILVER -> Color(0xFFC0C0C0)
        TrustTier.BRONZE -> Color(0xFFCD7F32)
        TrustTier.NEW -> Orange500
    }

    Scaffold(
        topBar = { MainTopBar(title = "Profile") },
        containerColor = DarkBase,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Hero: Avatar with trust ring
                Box(contentAlignment = Alignment.Center) {
                    AuraScoreRing(
                        score = trustScore,
                        size = 120.dp,
                        animate = true,
                        showNumber = false,
                        strokeWidth = 6.dp,
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(GlassSurface)
                            .border(2.dp, Orange500.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            pubkey?.take(2)?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Orange500,
                        )
                    }
                }

                // Wallet address + copy
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        pubkey?.let { "${it.take(6)}...${it.takeLast(4)}" } ?: "Not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    pubkey?.let { addr ->
                        IconButton(
                            onClick = {
                                AuraHaptics.lightTap(haptic)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("address", addr))
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Trust Score card
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = Orange500) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "TRUST SCORE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$trustScore/100",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Orange500,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val progress by animateFloatAsState(
                            targetValue = trustScore / 100f,
                            animationSpec = tween(800, easing = FastOutSlowInEasing),
                            label = "progress",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Orange500, Gold500),
                                        ),
                                    ),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$tierEmoji ${tier.name} • Tier progress → ${when (tier) {
                                TrustTier.NEW -> "50 to BRONZE"
                                TrustTier.BRONZE -> "70 to SILVER"
                                TrustTier.SILVER -> "80 to GOLD"
                                TrustTier.GOLD -> "90 to PLATINUM"
                                TrustTier.PLATINUM -> "Max"
                            }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Stats row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatGlassCard(title = "Score", value = "$trustScore", icon = Icons.Default.Star)
                    StatGlassCard(title = "Streak", value = "$streak", icon = Icons.Default.LocalFireDepartment)
                    StatGlassCard(title = "Trades", value = "0", icon = Icons.Default.Verified)
                }

                // NFT Evolution card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = tierGlowColor,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(tierGlowColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when {
                                    streak >= 90 -> "✨"
                                    streak >= 31 -> "🌳"
                                    streak >= 8 -> "🌿"
                                    else -> "🌱"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Aura NFT Evolution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Current stage: $nftStage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Verify Identity
                Button(
                    onClick = {
                        AuraHaptics.mediumImpact(haptic)
                        onVerifyIdentity()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange500,
                        contentColor = Color.Black,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Verify Identity (KYC)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Button(
                    onClick = {
                        AuraHaptics.lightTap(haptic)
                        pubkey?.let { addr ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "My Aura profile — Trust Score: $trustScore | ${addr.take(8)}...${addr.takeLast(4)}")
                                putExtra(Intent.EXTRA_TITLE, "Aura Profile")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                        }
                    },
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

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun RowScope.StatGlassCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    GlassCard(
        modifier = Modifier.weight(1f),
        glowColor = Orange500,
        cornerRadius = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Orange500,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Orange500,
            )
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
