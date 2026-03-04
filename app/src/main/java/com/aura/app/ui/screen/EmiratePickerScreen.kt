package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.*
import kotlinx.coroutines.delay

data class UaeEmirate(
    val name: String,
    val emoji: String,
    val description: String
)

val UAE_EMIRATES = listOf(
    UaeEmirate("Dubai", "🏙️", "The City of Gold — largest marketplace"),
    UaeEmirate("Abu Dhabi", "🕌", "Capital & cultural hub"),
    UaeEmirate("Sharjah", "📚", "City of Culture & Arts"),
    UaeEmirate("Ajman", "🌊", "Coastal pearl of the UAE"),
    UaeEmirate("Ras Al Khaimah", "⛰️", "Mountains & adventure"),
    UaeEmirate("Fujairah", "🏔️", "Gateway to the East Coast"),
    UaeEmirate("Umm Al Quwain", "🎣", "Tranquil lagoon living"),
)

@Composable
fun EmiratePickerScreen(
    onEmirateSelected: (String) -> Unit
) {
    var selectedEmirate by remember { mutableStateOf<String?>(null) }
    var titleVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        titleVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = titleVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -40 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🇦🇪",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your Marketplace",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Select your emirate to see local listings first.\nYou can always browse all of UAE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(UAE_EMIRATES) { index, emirate ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(300 + index * 60L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { 80 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    ) {
                        val isSelected = selectedEmirate == emirate.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) Orange500.copy(alpha = 0.12f) else GlassSurface
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Orange500 else GlassBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedEmirate = emirate.name }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(emirate.emoji, style = MaterialTheme.typography.headlineSmall)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    emirate.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Orange500 else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    emirate.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Orange500,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { selectedEmirate?.let { onEmirateSelected(it) } },
                enabled = selectedEmirate != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange500,
                    disabledContainerColor = GlassSurface,
                )
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedEmirate != null) "Set My Marketplace: $selectedEmirate"
                    else "Select an Emirate",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { onEmirateSelected("Global") }) {
                Text(
                    "Browse all of UAE instead",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
