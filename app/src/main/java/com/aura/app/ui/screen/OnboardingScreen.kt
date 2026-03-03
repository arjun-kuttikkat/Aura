package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SolanaGradientEnd
import com.aura.app.ui.theme.SolanaGradientStart
import com.aura.app.ui.theme.SuccessGreen
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private val steps = listOf(
    OnboardingStep(Icons.Default.NearMe, "Sell Anything", "List physical items on-chain with one tap"),
    OnboardingStep(Icons.Default.Nfc, "Tap to Verify", "NFC cryptographic proof of real-world handover"),
    OnboardingStep(Icons.Default.Shield, "Trustless Escrow", "Solana smart contract holds funds until verified"),
)

@Composable
fun OnboardingScreen(
    onWalletConnected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var connectedAddress by remember { mutableStateOf<String?>(null) }
    var currentStep by remember { mutableIntStateOf(0) }

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )

    // Auto-rotate onboarding steps
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            currentStep = (currentStep + 1) % steps.size
        }
    }

    // Auto-navigate after confirming connection
    LaunchedEffect(connectedAddress) {
        if (connectedAddress != null) {
            delay(1200)
            onWalletConnected()
        }
    }

    // Animated aurora background
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.aura.app.ui.theme.DarkBase),
    ) {
        // Aurora glow orbs
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .scale(1f + auraPulse * 0.3f)
                .alpha(0.15f)
                .blur(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Orange500, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomStart)
                .scale(1f + (1f - auraPulse) * 0.2f)
                .alpha(0.12f)
                .blur(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Gold500, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo
            com.aura.app.ui.components.AppLogo(modifier = Modifier.scale(scale), size = 80.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Aura",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "The Physical-to-Digital Marketplace",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Onboarding step carousel
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(500)) + slideInVertically(initialOffsetY = { 40 }),
            ) {
                val step = steps[currentStep]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(0.5.dp, Orange500.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                ) {
                    Icon(
                        step.icon, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Orange500,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        step.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Step indicator dots
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                steps.forEachIndexed { idx, _ ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (idx == currentStep) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == currentStep) Orange500
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (connectedAddress != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Connected",
                    tint = SuccessGreen,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Wallet Connected!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                )
                val addr = connectedAddress!!
                Text(
                    text = "${addr.take(4)}…${addr.takeLast(4)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        errorMsg = null
                        WalletConnectionState.connect(
                            scope = scope,
                            onSuccess = { address ->
                                isLoading = false
                                connectedAddress = address
                            },
                            onError = {
                                isLoading = false
                                errorMsg = it.message ?: "Connection failed"
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange500,
                        contentColor = Color.Black,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Black,
                        )
                    } else {
                        Text("Connect Wallet", fontWeight = FontWeight.Bold)
                    }
                }

                // Powered by Solana badge
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.alpha(0.5f),
                ) {
                    Text(
                        "Powered by ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Solana",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SolanaGradientEnd,
                    )
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
    }
}
