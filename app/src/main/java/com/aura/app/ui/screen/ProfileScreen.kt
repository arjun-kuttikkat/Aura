package com.aura.app.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aura.app.ui.util.springScale
import com.aura.app.wallet.WalletConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onVerifyIdentity: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val pubkey by WalletConnectionState.walletAddress.collectAsState()
    val profile by AuraRepository.currentProfile.collectAsState()

    LaunchedEffect(pubkey) {
        pubkey?.let { AuraRepository.loadProfile(it) }
    }

    val trustScoreRaw = profile?.auraScore ?: 50
    val streakRaw = profile?.streakDays ?: 0

    val trustScore by animateIntAsState(targetValue = trustScoreRaw, animationSpec = tween(1800, easing = FastOutSlowInEasing), label = "score")
    val streak by animateIntAsState(targetValue = streakRaw, animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "streak")

    val tier = when {
        trustScoreRaw >= 90 -> TrustTier.PLATINUM
        trustScoreRaw >= 80 -> TrustTier.GOLD
        trustScoreRaw >= 70 -> TrustTier.SILVER
        trustScoreRaw >= 50 -> TrustTier.BRONZE
        else -> TrustTier.NEW
    }
    val tierEmoji = when (tier) {
        TrustTier.PLATINUM -> "💎"
        TrustTier.GOLD -> "🥇"
        TrustTier.SILVER -> "🥈"
        TrustTier.BRONZE -> "🥉"
        TrustTier.NEW -> "🌱"
    }

    // NFT Evolution stages
    val nftStageIndex = when {
        streakRaw >= 90 -> 3
        streakRaw >= 31 -> 2
        streakRaw >= 8 -> 1
        else -> 0
    }
    val nftStages = listOf("Seed 🌱", "Sprout 🌿", "Tree 🌳", "Aura ✨")
    val nftStageThresholds = listOf(0, 8, 31, 90)
    val nftStageColors = listOf(
        Color(0xFF4CAF50), // Seed - green
        Color(0xFF00BCD4), // Sprout - cyan
        Color(0xFFFF9800), // Tree - orange
        Color(0xFF9C27B0), // Aura - purple
    )
    val nextThreshold = if (nftStageIndex < 3) nftStageThresholds[nftStageIndex + 1] else 90
    val currentThreshold = nftStageThresholds[nftStageIndex]
    val nftProgress = if (nftStageIndex >= 3) 1f
        else ((streakRaw - currentThreshold).toFloat() / (nextThreshold - currentThreshold).toFloat()).coerceIn(0f, 1f)
    val daysToNext = if (nftStageIndex < 3) (nextThreshold - streakRaw).coerceAtLeast(0) else 0

    // Profile customization state
    val displayName by com.aura.app.data.AuraPreferences.displayName.collectAsState()
    val userBio by com.aura.app.data.AuraPreferences.bio.collectAsState()
    val avatarColorIndex by com.aura.app.data.AuraPreferences.avatarColorIndex.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf("") }

    val avatarPalette = listOf(
        Color(0xFFFF6B35), // Ember
        Color(0xFF9945FF), // Solana Purple
        Color(0xFF14F195), // Solana Green
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Rose
    )

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
            // ── Dynamic Aura Core ──
            val isDegraded = remember(profile?.lastScanAt) {
                profile?.lastScanAt?.let { lastScan ->
                    try {
                        val last = java.time.OffsetDateTime.parse(lastScan)
                        val hours = java.time.temporal.ChronoUnit.HOURS.between(last, java.time.OffsetDateTime.now())
                        hours > 48
                    } catch (_: Exception) { false }
                } ?: false
            }

            Box(contentAlignment = Alignment.Center) {
                com.aura.app.ui.components.AuraCoreRenderer(
                    streakDays = streakRaw,
                    auraScore = trustScoreRaw,
                    isDegraded = isDegraded,
                    size = 160.dp,
                )
                Text(
                    pubkey?.take(2)?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            // ── Display Name (tap to edit) ──
            Text(
                text = displayName.ifBlank { "Tap to set name" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (displayName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable {
                    editingText = displayName
                    showNameDialog = true
                },
            )

            // ── Bio (tap to edit) ──
            Text(
                text = userBio.ifBlank { "Tap to add bio" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    editingText = userBio
                    showBioDialog = true
                },
            )

            // Wallet address
            Text(
                pubkey?.let { "${it.take(6)}...${it.takeLast(4)}" } ?: "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // ── Avatar Color Picker ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aura Tint: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                avatarPalette.forEachIndexed { index, color ->
                    val isSelected = index == avatarColorIndex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 36.dp else 28.dp)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { com.aura.app.data.AuraPreferences.setAvatarColorIndex(index) },
                    )
                    if (index < avatarPalette.size - 1) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // ── Trust Score card ──
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
                        Text("Trust Score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$trustScore", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Orange500)
                            Text(" /100", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tierEmoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${tier.name} Tier", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Gold500)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Orange500, modifier = Modifier.size(32.dp))
                        Text("$streak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Orange500)
                        Text("day streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── NFT Evolution card (visual progress) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(nftStageColors[nftStageIndex].copy(alpha = 0.12f), Gold500.copy(alpha = 0.06f))
                        )
                    )
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Aura NFT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(nftStages[nftStageIndex], style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = nftStageColors[nftStageIndex])
                        }
                        Icon(Icons.Default.Star, contentDescription = null, tint = nftStageColors[nftStageIndex], modifier = Modifier.size(40.dp))
                    }
                    // Stage progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(GlassSurface),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(nftProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(nftStageColors[nftStageIndex], nftStageColors[nftStageIndex].copy(alpha = 0.6f))
                                    )
                                ),
                        )
                    }
                    // Stage labels row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        nftStages.forEachIndexed { idx, stage ->
                            Text(
                                stage.split(" ").last(), // just the emoji
                                style = MaterialTheme.typography.bodySmall,
                                color = if (idx <= nftStageIndex) nftStageColors[idx] else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                    if (daysToNext > 0) {
                        Text(
                            "Next evolution in $daysToNext days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Maximum evolution reached! ✨",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = nftStageColors[3],
                        )
                    }
                }
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val isVerified by com.aura.app.data.AuraPreferences.identityVerified.collectAsState()

            if (isVerified) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Identity Verified", fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                }
            } else {
                val verifyInteraction = remember { MutableInteractionSource() }
                val isVerifyPressed by verifyInteraction.collectIsPressedAsState()

                Button(
                    onClick = onVerifyIdentity,
                    interactionSource = verifyInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .springScale(isVerifyPressed),
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
            }

            val shareInteraction = remember { MutableInteractionSource() }
            val isSharePressed by shareInteraction.collectIsPressedAsState()

            Button(
                onClick = {
                    if (pubkey == null) {
                        android.widget.Toast.makeText(context, "Wallet not connected yet.", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out my Aura Profile and Trade History! https://aura.so/profile/$pubkey")
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                interactionSource = shareInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .springScale(isSharePressed),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlassSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Profile", fontWeight = FontWeight.Medium)
            }

            // ── Quick Actions Grid ──
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Directives
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.aura.app.ui.theme.UltraViolet.copy(alpha = 0.12f))
                        .border(1.dp, com.aura.app.ui.theme.UltraViolet.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { onNavigate(com.aura.app.navigation.Routes.DIRECTIVES) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = com.aura.app.ui.theme.UltraViolet, modifier = Modifier.size(28.dp))
                        Text("Directives", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = com.aura.app.ui.theme.UltraViolet)
                    }
                }
                // Quick Pay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.aura.app.ui.theme.SolanaGreen.copy(alpha = 0.12f))
                        .border(1.dp, com.aura.app.ui.theme.SolanaGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { onNavigate(com.aura.app.navigation.Routes.P2P_EXCHANGE) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = com.aura.app.ui.theme.SolanaGreen, modifier = Modifier.size(28.dp))
                        Text("Quick Pay", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = com.aura.app.ui.theme.SolanaGreen)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Create Listing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Orange500.copy(alpha = 0.12f))
                        .border(1.dp, Orange500.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { onNavigate(com.aura.app.navigation.Routes.CREATE_LISTING) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Orange500, modifier = Modifier.size(28.dp))
                        Text("Create Listing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Orange500)
                    }
                }
                // Settings
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .clickable { onNavigate(com.aura.app.navigation.Routes.SETTINGS) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                        Text("Settings", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Edit Name Dialog ──
    if (showNameDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Display Name") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it.take(24) },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.aura.app.data.AuraPreferences.setDisplayName(editingText.trim())
                    showNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Edit Bio Dialog ──
    if (showBioDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Bio") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it.take(120) },
                    label = { Text("About you") },
                    maxLines = 3,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.aura.app.data.AuraPreferences.setBio(editingText.trim())
                    showBioDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBioDialog = false }) { Text("Cancel") }
            },
        )
    }
}
