package com.aura.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen() {
    Scaffold(
        topBar = {
            MainTopBar(title = "Rewards")
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RewardCard(
                icon = Icons.Default.Star,
                title = "Aura Points",
                subtitle = "1,250 pts",
                description = "Earn points on every trade",
            )
            RewardCard(
                icon = Icons.Default.CardGiftcard,
                title = "Streak Bonus",
                subtitle = "7 day streak",
                description = "Complete 7 trades this week for a bonus",
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RewardCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Orange500.copy(alpha = 0.08f),
                            Gold500.copy(alpha = 0.05f),
                        ),
                    ),
                )
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
