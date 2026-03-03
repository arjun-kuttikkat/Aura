package com.aura.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.RadicalRed
import com.aura.app.ui.theme.SlateElevated
import com.aura.app.ui.theme.SolanaGreen
import com.aura.app.ui.theme.Typography
import com.aura.app.ui.util.HapticEngine
import com.aura.app.ui.util.shimmerBorder
import com.aura.app.ui.util.springScale

// ── 150ms micro-interaction compliant primary Button ──
@Composable
fun AuraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    
    // Tactile confirmation on press down
    LaunchedEffect(isPressed) {
        if (isPressed) {
            HapticEngine.triggerClick(view)
        }
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .springScale(isPressed = isPressed, scaleDown = 0.96f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SolanaGreen,
            contentColor = DarkVoid,
            disabledContainerColor = SlateElevated,
            disabledContentColor = Color.Gray
        ),
        contentPadding = PaddingValues(16.dp),
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        Text(
            text = text,
            style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

// ── Ghost-bordered secondary button with light haptic ──
@Composable
fun AuraSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            HapticEngine.triggerLight(view)
        }
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .springScale(isPressed = isPressed, scaleDown = 0.97f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = SolanaGreen,
            disabledContentColor = Color.Gray,
        ),
        contentPadding = PaddingValues(16.dp),
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

// ── Glassmorphism card with animated shimmer border ──
@Composable
fun AuraGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .shimmerBorder(shimmerColor = SolanaGreen.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface,
        ),
        border = BorderStroke(1.dp, GlassBorder),
        content = { content() },
    )
}

// ── Structurally elevated Asset Card with press spring ──
@Composable
fun AuraAssetCard(
    modifier: Modifier = Modifier,
    isPressed: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.springScale(isPressed = isPressed, scaleDown = 0.98f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SlateElevated
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        content = { content() }
    )
}

// ── Input Field with dynamic focus borders ──
@Composable
fun AuraInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            { Text(errorMessage, color = RadicalRed) }
        } else null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SlateElevated,
            unfocusedContainerColor = SlateElevated,
            focusedBorderColor = SolanaGreen,
            unfocusedBorderColor = Color(0xFF334155),
            errorBorderColor = RadicalRed
        ),
        textStyle = Typography.bodyLarge
    )
}
