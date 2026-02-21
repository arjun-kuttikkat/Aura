package com.aura.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.model.TrustTier
import com.aura.app.ui.components.MainTopBar
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.data.AuraRepository
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onVerifyIdentity: () -> Unit
) {
    val pubkey by WalletConnectionState.walletAddress.collectAsState()
    val profile by AuraRepository.currentProfile.collectAsState()

    LaunchedEffect(pubkey) {
        pubkey?.let { 
            AuraRepository.loadProfile(it)
        }
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

    Scaffold(
        topBar = {
            MainTopBar(title = "Profile")
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Wallet", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = pubkey ?: "Not connected",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Trust Score", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$trustScore / 100",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Tier: ${tier.name}", style = MaterialTheme.typography.bodyMedium)
                    Text("Active Streak: $streak 🔥", style = MaterialTheme.typography.bodyMedium)
                }
            }

            androidx.compose.material3.Button(
                onClick = onVerifyIdentity,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Verify Identity (KYC)")
            }
        }
    }
}
