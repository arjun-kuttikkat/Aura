package com.aura.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SlateElevated
import com.aura.app.ui.theme.Typography
import com.aura.app.ui.util.HapticEngine

data class HelpCentreItem(val title: String, val content: String)

@Composable
fun HelpCentreDialog(
    items: List<HelpCentreItem>,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val dialogWidth = (screenWidthDp * 0.9f).dp.coerceAtMost(400.dp)
    val expandedState = remember { mutableStateMapOf<Int, Boolean>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .shadow(48.dp, RoundedCornerShape(24.dp), ambientColor = Orange500.copy(alpha = 0.12f), spotColor = Orange500.copy(alpha = 0.2f))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DarkCard,
                            DarkVoid,
                        ),
                    ),
                )
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Orange500.copy(alpha = 0.5f),
                            Gold500.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                    ),
                    shape = RoundedCornerShape(24.dp),
                ),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                // Header with title and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Help Centre",
                        style = Typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = {
                            HapticEngine.triggerLight(view)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateElevated),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Expandable items
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        val isExpanded = expandedState[index] ?: false
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SlateElevated.copy(alpha = 0.8f))
                                .border(
                                    0.5.dp,
                                    Orange500.copy(alpha = if (isExpanded) 0.35f else 0.12f),
                                    RoundedCornerShape(14.dp),
                                )
                                .animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                .clickable {
                                    HapticEngine.triggerLight(view)
                                    expandedState[index] = !isExpanded
                                }
                                .padding(16.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.title,
                                        style = Typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.2.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Orange500,
                                    )
                                }
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                    ),
                                    exit = shrinkVertically(
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                    ),
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = item.content,
                                        style = Typography.bodyMedium.copy(
                                            lineHeight = 22.sp,
                                            letterSpacing = 0.15.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
