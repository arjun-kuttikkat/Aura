package com.aura.app.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.navigation.Routes

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
        BottomNavItem(Routes.HOME, Icons.Filled.Store, "Marketplace"),
        BottomNavItem(Routes.REWARDS, Icons.Filled.Star, "Rewards"),
        BottomNavItem(Routes.CREATE_LISTING, Icons.Default.Add, "Create", isCenter = true),
        BottomNavItem(Routes.PROFILE, Icons.Default.Person, "Profile"),
        BottomNavItem(Routes.SETTINGS, Icons.Default.Settings, "Settings"),
    )

    val regularItems = navItems.filter { !it.isCenter }
    val centerItem = navItems.find { it.isCenter }!!

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 16.dp),
    ) {
        // No background - just icons and FAB (removes white rectangle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp),
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

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-10).dp)
                .size(56.dp)
                .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                .clickable { onNavigate(centerItem.route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = centerItem.icon,
                contentDescription = centerItem.label,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
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
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
