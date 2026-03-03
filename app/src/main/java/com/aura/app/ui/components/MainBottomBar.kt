package com.aura.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.navigation.Routes
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.SolanaGreen
import com.aura.app.ui.theme.DarkVoid

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val isCenter: Boolean = false,
)

@Composable
fun MainBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navItems = listOf(
        BottomNavItem(Routes.HOME, Icons.Filled.Store, "Market"),
        BottomNavItem(Routes.DIRECTIVES, Icons.Filled.AutoAwesome, "Directives"),
        BottomNavItem(Routes.CREATE_LISTING, Icons.Default.Add, "Create", isCenter = true),
        BottomNavItem(Routes.PROFILE, Icons.Default.Person, "Profile"),
        BottomNavItem(Routes.CHATS, Icons.Default.Chat, "Chat"),
    )

    val regularItems = navItems.filter { !it.isCenter }
    val centerItem = navItems.find { it.isCenter }!!

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 16.dp),
    ) {
        // Glassmorphism bar background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                regularItems.take(2).forEach { item ->
                    BottomNavBarItem(
                        modifier = Modifier.weight(1f),
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                regularItems.drop(2).forEach { item ->
                    BottomNavBarItem(
                        modifier = Modifier.weight(1f),
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }

        // Center FAB
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-14).dp)
                .size(56.dp)
                .shadow(20.dp, CircleShape, spotColor = SolanaGreen.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(SolanaGreen)
                .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .clickable { onNavigate(centerItem.route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = centerItem.icon,
                contentDescription = centerItem.label,
                modifier = Modifier.size(28.dp),
                tint = DarkVoid,
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavBarItem(
    modifier: Modifier = Modifier,
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val itemScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "navScale",
    )

    Column(
        modifier = modifier
            .scale(itemScale)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp),
            tint = if (selected) SolanaGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) SolanaGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        // Active indicator dot
        if (selected) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(SolanaGreen),
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
