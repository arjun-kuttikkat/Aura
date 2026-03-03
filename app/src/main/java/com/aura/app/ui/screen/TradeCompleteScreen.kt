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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.ui.components.AuraHaptics
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

@Composable
fun TradeCompleteScreen(
    onDone: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showCheck by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showBadge by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AuraHaptics.successPattern(context)
        showCheck = true
        delay(400)
        showText = true
        delay(300)
        showBadge = true
        delay(500)
        showButton = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "celebrate")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBase),
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .alpha(0.2f)
                .blur(100.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(SuccessGreen, Color.Transparent))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = showCheck,
                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { -40 }, animationSpec = tween(400)),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(100.dp).scale(pulse),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(300)),
            ) {
                Text(
                    "Trade Complete!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(300)),
            ) {
                Text(
                    "Both parties verified. Escrow released.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = showBadge,
                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 100 }, animationSpec = spring(dampingRatio = 0.6f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Gold500.copy(alpha = 0.2f), Orange500.copy(alpha = 0.15f)),
                            ),
                        )
                        .border(1.dp, Gold500.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Token, contentDescription = null, tint = Gold500, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "+10 \$AURA Earned",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Gold500,
                        )
                        Text(
                            "Tap-to-Earn reward for completing a verified trade",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 20 }, animationSpec = spring(dampingRatio = 0.7f)),
            ) {
                Button(
                    onClick = {
                        AuraHaptics.lightTap(haptic)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange500,
                        contentColor = Color.Black,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Text("Return to Marketplace", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
