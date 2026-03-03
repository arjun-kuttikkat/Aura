package com.aura.app.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraPreferences
import com.aura.app.navigation.LocalBottomNavInset
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.components.MainTopBar
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Orange500
import com.aura.app.wallet.WalletConnectionState

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
) {
    val isDarkMode by AuraPreferences.isDarkMode.collectAsState()
    val notificationsEnabled by AuraPreferences.notificationsEnabled.collectAsState()
    val transactionAlerts by AuraPreferences.transactionAlerts.collectAsState()
    val biometricsEnabled by AuraPreferences.biometricsEnabled.collectAsState()
    val seedBackedUp by AuraPreferences.seedBackedUp.collectAsState()
    val publicProfile by AuraPreferences.publicProfile.collectAsState()
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { MainTopBar(title = "Settings") },
        containerColor = DarkBase,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Notifications ──
            SettingsSectionTitle(icon = Icons.Default.Notifications, title = "Notifications")
            SettingsPanel {
                SettingToggleRow(
                    title = "Push Notifications",
                    subtitle = "Allow wallet and app updates",
                    checked = notificationsEnabled,
                    onCheckedChange = { AuraPreferences.setNotifications(it) },
                )
                HorizontalDivider(color = GlassBorder)
                SettingToggleRow(
                    title = "Transaction Alerts",
                    subtitle = "Escrow, trade, and payout updates",
                    checked = transactionAlerts,
                    onCheckedChange = { AuraPreferences.setTransactionAlerts(it) },
                )
            }
            Button(
                onClick = { openAppNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "Open System Notification Settings", modifier = Modifier.padding(start = 8.dp))
            }

            // ── Appearance ──
            SettingsSectionTitle(icon = Icons.Default.Palette, title = "Appearance")
            SettingsPanel {
                SettingToggleRow(
                    title = "Global Dark Mode",
                    subtitle = if (isDarkMode) "Dark mode active" else "Light mode active",
                    checked = isDarkMode,
                    onCheckedChange = { AuraPreferences.setDarkMode(it) },
                )
            }

            // ── Security ──
            SettingsSectionTitle(icon = Icons.Default.Security, title = "Security")
            SettingsPanel {
                SettingToggleRow(
                    title = "Require Biometrics",
                    subtitle = "Use device biometrics before app access",
                    checked = biometricsEnabled,
                    onCheckedChange = { AuraPreferences.setBiometrics(it) },
                )
                HorizontalDivider(color = GlassBorder)
                SettingToggleRow(
                    title = "Seed Phrase Backed Up",
                    subtitle = "Mark when your recovery phrase is stored safely",
                    checked = seedBackedUp,
                    onCheckedChange = { isChecked ->
                        if (isChecked) showQrDialog = true else AuraPreferences.setSeedBackedUp(false)
                    },
                )
            }
            if (showQrDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showQrDialog = false },
                    title = { Text("Wallet Backup QR Code") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val address = WalletConnectionState.walletAddress.value
                            if (address != null) {
                                coil.compose.AsyncImage(
                                    model = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$address",
                                    contentDescription = "Wallet QR Code",
                                    modifier = Modifier.size(200.dp),
                                )
                                Text(
                                    "Scan this QR code or save it to safely backup your Aura identity and wallet seed.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            } else {
                                Text("Please connect a wallet first.")
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showQrDialog = false
                            AuraPreferences.setSeedBackedUp(true)
                        }) {
                            Text("I've Saved This QR Code")
                        }
                    },
                )
            }

            // ── Privacy ──
            SettingsSectionTitle(icon = Icons.Default.PrivacyTip, title = "Privacy")
            SettingsPanel {
                SettingToggleRow(
                    title = "Public Profile Visibility",
                    subtitle = if (publicProfile) "Profile is public" else "Profile is private",
                    checked = publicProfile,
                    onCheckedChange = { AuraPreferences.setPublicProfile(it) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    WalletConnectionState.disconnect()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (walletAddress != null) "Disconnect Wallet" else "No Wallet Connected",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(LocalBottomNavInset.current))
        }
    }
}

@Composable
private fun SettingsSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Orange500)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openAppNotificationSettings(context: Context) {
    val packageName = context.packageName
    val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(notificationIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(fallbackIntent)
    }
}
