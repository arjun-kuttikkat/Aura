package com.aura.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.aura.app.data.AuraRepository
import com.aura.app.navigation.LocalBottomNavInset
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.Orange700

@Composable
fun RewardsScreen() {
    val profile by AuraRepository.currentProfile.collectAsState(initial = null)
    val auraScoreRaw = profile?.auraScore ?: 50
    val streakRaw = profile?.streakDays ?: 0
    val totalAura = com.aura.app.data.AuraPreferences.totalAuraEarned.collectAsState(initial = 0).value
    val completedToday = com.aura.app.data.DirectivesManager.completedToday.collectAsState(initial = 0).value

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
    val infiniteTransition = rememberInfiniteTransition(label = "rewards")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )

    Scaffold(
        topBar = { MainTopBar(title = "Rewards") },
        containerColor = DarkBase,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = Gold500) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.Center)
                            .alpha(0.15f + pulse * 0.1f)
                            .blur(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Gold500, Color.Transparent),
                                ),
                            ),
                    )
                    Icon(
                        Icons.Default.Token,
                        contentDescription = null,
                        tint = Gold500,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .offset(y = 8.dp * (floatY - 0.5f)),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "$animatedAura \$AURA",
                            style = MaterialTheme.typography.displayMedium,
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatGlassCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    value = "$animatedScore",
                    label = "Aura Score",
                    tint = Orange500,
                )
                StatGlassCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    value = "$animatedStreak 🔥",
                    label = "Day Streak",
                    tint = Orange500,
                )
            }

            RewardGlassCard(
                icon = Icons.Default.EmojiEvents,
                title = "Trade Rewards",
                subtitle = "Earn \$AURA on trades",
                description = "Complete NFC-verified physical trades to earn tokens and boost your Aura Score.",
                leftBorderColor = Orange500,
            )
            RewardGlassCard(
                icon = Icons.Default.CardGiftcard,
                title = "Streak Multiplier",
                subtitle = "${streakRaw}x bonus active",
                description = "Maintain your daily Aura Check streak to unlock multiplied token rewards.",
                leftBorderColor = Gold500,
            )
            RewardGlassCard(
                icon = Icons.Default.Star,
                title = "NFT Evolution",
                subtitle = "Level up your Aura",
                description = "Your on-chain Aura NFT evolves as your streak grows: Seed → Sprout → Tree → Aura. Higher tiers unlock exclusive features.",
                leftBorderColor = Orange700,
            )

            Spacer(modifier = Modifier.height(LocalBottomNavInset.current))
        }
    }
}

@Composable
private fun StatGlassCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
) {
    GlassCard(modifier = modifier, glowColor = tint, cornerRadius = 16.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RewardGlassCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    leftBorderColor: Color,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = leftBorderColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gold500,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(leftBorderColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Gold500)
            }
        }
    }
}
