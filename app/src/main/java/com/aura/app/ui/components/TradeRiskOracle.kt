package com.aura.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.ErrorRed
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.SuccessGreen

enum class RiskLevel {
    SAFE,
    MODERATE,
    HIGH_RISK
}

@Composable
fun TradeRiskOracle(
    sellerWallet: String,
    sellerAuraScore: Int,
    sellerAccountAgeDays: Int,
    modifier: Modifier = Modifier
) {
    val riskLevel = when {
        sellerAuraScore >= 80 && sellerAccountAgeDays > 30 -> RiskLevel.SAFE
        sellerAuraScore >= 50 && sellerAccountAgeDays > 7 -> RiskLevel.MODERATE
        else -> RiskLevel.HIGH_RISK
    }

    val (icon, tintColor, backgroundColor, title, description) = when (riskLevel) {
        RiskLevel.SAFE -> listOf(
            Icons.Default.VerifiedUser,
            SuccessGreen,
            SuccessGreen.copy(alpha = 0.1f),
            "AI Oracle: Safe Trade",
            "Seller has a high Aura score ($sellerAuraScore) and established history. Escrow release radius is flexible."
        )
        RiskLevel.MODERATE -> listOf(
            Icons.Default.Info,
            Gold500,
            Gold500.copy(alpha = 0.1f),
            "AI Oracle: Moderate Risk",
            "Seller has average Aura ($sellerAuraScore). Standard face-to-face meet required for escrow release."
        )
        RiskLevel.HIGH_RISK -> listOf(
            Icons.Default.Warning,
            ErrorRed,
            ErrorRed.copy(alpha = 0.1f),
            "AI Oracle: High Risk Detected",
            "Seller has low Aura ($sellerAuraScore) or is a new account. Strict NFC physical handover required to release escrow!"
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor as Color)
            .border(1.dp, (tintColor as Color).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title as String,
                    style = MaterialTheme.typography.titleMedium,
                    color = tintColor,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = description as String,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
