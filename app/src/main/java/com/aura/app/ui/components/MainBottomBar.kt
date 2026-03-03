package com.aura.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.app.navigation.Routes
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Orange500

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
        BottomNavItem(Routes.HOME, Icons.Filled.Home, "Home"),
        BottomNavItem(Routes.FAVORITES, Icons.Filled.FavoriteBorder, "Favorites"),
        BottomNavItem(Routes.CREATE_LISTING, Icons.Filled.Add, "Place an ad", isCenter = true),
        BottomNavItem(Routes.CHATS, Icons.Filled.ChatBubbleOutline, "Chats"),
        BottomNavItem(Routes.PROFILE, Icons.Filled.Person, "Menu"),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp) // Dubizzle is edge-to-edge usually, but let's keep a bit of padding or edge-to-edge Glass
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main Bar Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        )

        // Items Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            navItems.forEach { item ->
                if (item.isCenter) {
                    CentralNavBarItem(
                        item = item,
                        onClick = { onNavigate(item.route) }
                    )
                } else {
                    BottomNavBarItem(
                        modifier = Modifier.weight(1f),
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CentralNavBarItem(
    item: BottomNavItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource, 
                indication = null, 
                onClick = onClick
            )
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Protruding Circle
        Box(
            modifier = Modifier
                .offset(y = (-4).dp)
                .size(48.dp)
                .shadow(8.dp, CircleShape, spotColor = Color.Red.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(Color(0xFFE50000)) // Exact Dubizzle Red
                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(28.dp),
                tint = Color.White,
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun BottomNavBarItem(
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
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .scale(itemScale)
            .clickable(
                interactionSource = interactionSource, 
                indication = null, 
                onClick = onClick
            )
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
