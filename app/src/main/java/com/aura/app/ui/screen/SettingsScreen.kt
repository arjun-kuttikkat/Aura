package com.aura.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.aura.app.data.AuraPreferences
import com.aura.app.ui.components.MainTopBar
import com.aura.app.wallet.WalletConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNotificationsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onSecurityClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val isDarkMode by AuraPreferences.isDarkMode.collectAsState()
    val notificationsEnabled by AuraPreferences.notificationsEnabled.collectAsState()
    val transactionAlerts by AuraPreferences.transactionAlerts.collectAsState()
    val biometricsEnabled by AuraPreferences.biometricsEnabled.collectAsState()
    val seedBackedUp by AuraPreferences.seedBackedUp.collectAsState()
    val publicProfile by AuraPreferences.publicProfile.collectAsState()
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()

    val notificationsSubtitle = when {
        notificationsEnabled && transactionAlerts -> "Push + transaction alerts enabled"
        notificationsEnabled -> "Push notifications enabled"
        transactionAlerts -> "Transaction alerts enabled"
        else -> "All notifications disabled"
    }
    val securitySubtitle = when {
        biometricsEnabled && seedBackedUp -> "Biometrics on, seed backed up"
        biometricsEnabled -> "Biometrics on, backup pending"
        seedBackedUp -> "Seed backed up, biometrics off"
        else -> "Biometrics off, backup pending"
    }

    Scaffold(
        topBar = { MainTopBar(title = "Settings") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = notificationsSubtitle,
                onClick = onNotificationsClick,
            )
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = if (isDarkMode) "Dark mode active" else "Light mode active",
                onClick = onAppearanceClick,
            )
            SettingsItem(
                icon = Icons.Default.Security,
                title = "Security",
                subtitle = securitySubtitle,
                onClick = onSecurityClick,
            )
            SettingsItem(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy",
                subtitle = if (publicProfile) "Profile is public" else "Profile is private",
                onClick = onPrivacyClick,
            )

            Spacer(modifier = Modifier.height(24.dp))
            var showDisconnectDialog by remember { mutableStateOf(false) }
            Button(
                onClick = { showDisconnectDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    if (walletAddress != null) "Disconnect Wallet" else "No Wallet Connected",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (showDisconnectDialog) {
                AlertDialog(
                    onDismissRequest = { showDisconnectDialog = false },
                    title = { Text("Disconnect Wallet?") },
                    text = { Text("Are you sure you want to disconnect? Active trade sessions may be affected.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDisconnectDialog = false
                            WalletConnectionState.disconnect()
                            onLogout()
                        }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
                    }
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "$title settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
