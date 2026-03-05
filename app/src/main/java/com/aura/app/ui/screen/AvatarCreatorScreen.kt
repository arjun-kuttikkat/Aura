package com.aura.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.app.data.AvatarPreferences
import com.aura.app.model.AvatarCatalog
import com.aura.app.model.AvatarConfig
import com.aura.app.ui.avatar.AvatarCanvas
import com.aura.app.ui.theme.*
import kotlinx.coroutines.launch

enum class AvatarCreatorTab(val label: String, val emoji: String) {
    SKIN("Skin", "🎨"),
    FACE("Face", "👁"),
    HAIR("Hair", "💇"),
    OUTFIT("Outfit", "👕"),
    ACCESSORY("Add-ons", "🎩"),
    BACKGROUND("BG", "🌄"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCreatorScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var config by remember { mutableStateOf(AvatarConfig()) }
    var selectedTab by remember { mutableStateOf(AvatarCreatorTab.SKIN) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Your Avatar", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    TextButton(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                AvatarPreferences.saveAvatarConfig(context, config)
                                AvatarPreferences.addCredits(context, 50) // welcome bonus
                                isSaving = false
                                onDone()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Done!", color = Orange500, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Avatar Preview ── top 40% of screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(
                        Brush.verticalGradient(listOf(
                            AvatarCatalog.BACKGROUNDS.getOrElse(config.background) { Color(0xFF121212) }.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surface
                        ))
                    ),
                contentAlignment = Alignment.Center
            ) {
                AvatarCanvas(
                    config  = config,
                    animate = true,
                    modifier = Modifier.size(230.dp)
                )
                // Welcome hint
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                    Text(
                        "Tap categories below to customize ✨",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Tab Row ──────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = AvatarCreatorTab.values().indexOf(selectedTab),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Orange500,
            ) {
                AvatarCreatorTab.values().forEach { tab ->
                    Tab(
                        selected  = selectedTab == tab,
                        onClick   = { selectedTab = tab },
                        text = { Text("${tab.emoji} ${tab.label}", fontSize = 12.sp) }
                    )
                }
            }

            // ── Customization Panels ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {

                    AvatarCreatorTab.SKIN -> {
                        SectionLabel("Skin Tone")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(AvatarCatalog.SKIN_TONES) { i, color ->
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (config.skinTone == i) 3.dp else 0.dp, Orange500, CircleShape)
                                        .clickable { config = config.copy(skinTone = i) }
                                )
                            }
                        }
                    }

                    AvatarCreatorTab.FACE -> {
                        // Eye shape row
                        SectionLabel("Eye Shape")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((0 until AvatarCatalog.EYE_SHAPE_COUNT).toList()) { i ->
                                OptionChip("Style ${i + 1}", selected = config.eyeShape == i) { config = config.copy(eyeShape = i) }
                            }
                        }
                        // Eye color
                        SectionLabel("Eye Color")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(AvatarCatalog.EYE_COLORS) { i, color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (config.eyeColor == i) 3.dp else 0.dp, Color.White, CircleShape)
                                        .clickable { config = config.copy(eyeColor = i) }
                                )
                            }
                        }
                        // Eyebrow style
                        SectionLabel("Eyebrows")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("Straight", "Arched", "Angry", "Thin", "Bushy")) { style ->
                                OptionChip(style, selected = config.eyebrowStyle == listOf("Straight","Arched","Angry","Thin","Bushy").indexOf(style)) {
                                    config = config.copy(eyebrowStyle = listOf("Straight","Arched","Angry","Thin","Bushy").indexOf(style))
                                }
                            }
                        }
                        // Expression
                        SectionLabel("Expression")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("😊 Smile","😉 Wink","😏 Smirk","😮 Surprised","😐 Neutral")) { e ->
                                OptionChip(e, selected = config.expression == listOf("😊 Smile","😉 Wink","😏 Smirk","😮 Surprised","😐 Neutral").indexOf(e)) {
                                    config = config.copy(expression = listOf("😊 Smile","😉 Wink","😏 Smirk","😮 Surprised","😐 Neutral").indexOf(e))
                                }
                            }
                        }
                    }

                    AvatarCreatorTab.HAIR -> {
                        SectionLabel("Hair Style")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("Natural", "Spiky", "Braids", "Long", "Bun", "Mohawk", "Afro", "Ponytail")) { style ->
                                OptionChip(style, selected = config.hairStyle == listOf("Natural","Spiky","Braids","Long","Bun","Mohawk","Afro","Ponytail").indexOf(style)) {
                                    config = config.copy(hairStyle = listOf("Natural","Spiky","Braids","Long","Bun","Mohawk","Afro","Ponytail").indexOf(style))
                                }
                            }
                        }
                        SectionLabel("Hair Color")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(AvatarCatalog.HAIR_COLORS) { i, color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (config.hairColor == i) 3.dp else 0.dp, Orange500, CircleShape)
                                        .clickable { config = config.copy(hairColor = i) }
                                )
                            }
                        }
                    }

                    AvatarCreatorTab.OUTFIT -> {
                        SectionLabel("Top Style")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("Tee", "Hoodie", "Bomber", "Brand Tee", "Blazer", "Tank", "Denim", "Drip Set")) { style ->
                                OptionChip(style, selected = config.outfitTop == listOf("Tee","Hoodie","Bomber","Brand Tee","Blazer","Tank","Denim","Drip Set").indexOf(style)) {
                                    config = config.copy(outfitTop = listOf("Tee","Hoodie","Bomber","Brand Tee","Blazer","Tank","Denim","Drip Set").indexOf(style))
                                }
                            }
                        }
                        SectionLabel("Outfit Color")
                        val outfitColors = listOf(Color(0xFF1A1A1A),Color(0xFF2D2D2D),Color(0xFFE17055),Color(0xFFE65100),Color(0xFFFF9800),Color(0xFFFFFFFF),Color(0xFFFF7675),Color(0xFFD63031))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(outfitColors) { i, color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (config.outfitColor == i) 3.dp else 0.dp, Orange500, CircleShape)
                                        .clickable { config = config.copy(outfitColor = i) }
                                )
                            }
                        }
                    }

                    AvatarCreatorTab.ACCESSORY -> {
                        SectionLabel("Hat")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { OptionChip("None", selected = config.hat == -1) { config = config.copy(hat = -1) } }
                            items(listOf("Bucket", "Snapback", "Flower Crown", "Beanie", "Halo")) { h ->
                                val i = listOf("Bucket","Snapback","Flower Crown","Beanie","Halo").indexOf(h)
                                OptionChip(h, selected = config.hat == i) { config = config.copy(hat = i) }
                            }
                        }
                        SectionLabel("Glasses")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { OptionChip("None", selected = config.glasses == -1) { config = config.copy(glasses = -1) } }
                            items(listOf("Round", "Aviators", "Cat Eye", "Cyber Visor")) { g ->
                                val i = listOf("Round","Aviators","Cat Eye","Cyber Visor").indexOf(g)
                                OptionChip(g, selected = config.glasses == i) { config = config.copy(glasses = i) }
                            }
                        }
                    }

                    AvatarCreatorTab.BACKGROUND -> {
                        SectionLabel("Background")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(AvatarCatalog.BACKGROUNDS) { i, color ->
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color)
                                        .border(if (config.background == i) 3.dp else 0.dp, Orange500, RoundedCornerShape(12.dp))
                                        .clickable { config = config.copy(background = i) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Quick-save floating hint
                Text(
                    "🎉 You'll get 50 free Aura credits when you save!",
                    style = MaterialTheme.typography.labelSmall,
                    color = SolanaGreen.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Orange500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 1.5.dp else 0.dp, Orange500, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
