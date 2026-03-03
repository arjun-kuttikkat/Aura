package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.SlateElevated
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen() {
    val profile by AuraRepository.currentProfile.collectAsState()
    val streakRaw = profile?.streakDays ?: 0
    val totalAura = com.aura.app.data.AuraPreferences.totalAuraEarned.collectAsState().value
    val completedToday = com.aura.app.data.DirectivesManager.completedToday.collectAsState().value
    
    val animatedAura by animateIntAsState(
        targetValue = totalAura,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "aura"
    )
    val animatedScore by animateIntAsState(
        targetValue = auraScoreRaw,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "score"
    )
    val animatedStreak by animateIntAsState(
        targetValue = streakRaw,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "streak"
    )
    var contentVisible by androidx.compose.runtime.remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
    }

    Scaffold(
        topBar = { MainTopBar(title = "Rewards") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Token balance hero
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Orange500.copy(alpha = 0.15f), Gold500.copy(alpha = 0.1f)),
                        ),
                    )
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Token, contentDescription = null, tint = Gold500, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$animatedAura \$AURA",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Gold500,
                    )
                    Text(
                        if (totalAura == 0) "Complete directives to earn tokens" else "$completedToday directives completed today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }

            // Stats row
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    value = "$animatedScore",
                    label = "Aura Score",
                    tint = Orange500,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    value = "$animatedStreak 🔥",
                    label = "Day Streak",
                    tint = Orange500,
                )
            }
            }

            // Reward cards
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(400, delayMillis = 300)) + slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RewardCard(
                        icon = Icons.Default.EmojiEvents,
                        title = "Trade Rewards",
                        subtitle = "Earn \$AURA on trades",
                        description = "Complete NFC-verified physical trades to earn tokens and boost your Aura Score. Both buyer and seller earn rewards.",
                        gradient = listOf(Orange500.copy(alpha = 0.1f), Gold500.copy(alpha = 0.06f)),
                    )
                    RewardCard(
                        icon = Icons.Default.CardGiftcard,
                        title = "Streak Multiplier",
                        subtitle = "${streakRaw}x bonus active",
                        description = "Maintain your daily Aura Check streak to unlock multiplied token rewards. Your streak resets at midnight.",
                        gradient = listOf(Gold500.copy(alpha = 0.1f), Orange500.copy(alpha = 0.06f)),
                    )
                    RewardCard(
                        icon = Icons.Default.Star,
                        title = "NFT Evolution",
                        subtitle = "Level up your Aura",
                        description = "Your on-chain Aura NFT evolves as your streak grows: Seed → Sprout → Tree → Aura. Higher tiers unlock exclusive features.",
                        gradient = listOf(Color(0xFF9945FF).copy(alpha = 0.1f), Color(0xFF14F195).copy(alpha = 0.06f)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RewardCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    gradient: List<Color>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateElevated),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gold500,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Gold500,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
