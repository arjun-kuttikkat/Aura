package com.aura.app.ui.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.ui.components.AppLogo
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.wallet.WalletService
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onWalletConnected: () -> Unit,
) {
    val walletService = remember { WalletService() }
    val scope = rememberCoroutineScope()
    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(modifier = Modifier.scale(scale), size = 80.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Aura",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "P2P marketplace with trust & escrow",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                isLoading = true
                errorMsg = null
                scope.launch {
                    walletService.connect()
                        .onSuccess { pubkey ->
                            WalletConnectionState.setConnected(pubkey)
                            onWalletConnected()
                        }
                        .onFailure {
                            errorMsg = it.message ?: "Connection failed"
                            isLoading = false
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Connect Wallet", fontWeight = FontWeight.SemiBold)
            }
        }
        errorMsg?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
