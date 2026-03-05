package com.aura.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.app.data.AvatarPreferences
import com.aura.app.model.ItemCategory
import com.aura.app.model.StoreCatalog
import com.aura.app.model.StoreItem
import com.aura.app.ui.avatar.AvatarCanvas
import com.aura.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarStoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val credits by AvatarPreferences.creditsFlow(context).collectAsState(initial = 0)
    val unlockedIds by AvatarPreferences.unlockedItemsFlow(context).collectAsState(initial = emptySet())
    val avatarConfig by AvatarPreferences.avatarConfigFlow(context).collectAsState(initial = com.aura.app.model.AvatarConfig())

    var selectedCategory by remember { mutableStateOf(ItemCategory.HAIR) }
    var purchaseMsg by remember { mutableStateOf<String?>(null) }
    var showNotEnoughCredits by remember { mutableStateOf(false) }

    // Toasty snack
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(purchaseMsg) {
        purchaseMsg?.let {
            snackbarHostState.showSnackbar(it)
            purchaseMsg = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Aura Shop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Credits badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Gold500.copy(alpha = 0.15f))
                            .border(1.dp, Gold500.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(16.dp))
                        Text("$credits credits", fontWeight = FontWeight.Bold, color = Gold500, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Avatar Preview with current equipped items ──────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Brush.verticalGradient(listOf(UltraViolet.copy(alpha = 0.12f), MaterialTheme.colorScheme.surface))),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarCanvas(config = avatarConfig, modifier = Modifier.size(180.dp), animate = false)
                    Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text("Your Avatar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Not enough credits dialog ───────────────────────────────────
            if (showNotEnoughCredits) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE53935))
                            Text("Not enough credits. Complete Directives to earn more!", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
                        }
                    }
                }
            }

            // ── Category Tabs ────────────────────────────────────────────────
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ItemCategory.values()) { cat ->
                        val selected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) UltraViolet.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (selected) 1.5.dp else 0.dp, UltraViolet, RoundedCornerShape(20.dp))
                                .clickable { selectedCategory = cat; showNotEnoughCredits = false }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("${cat.emoji} ${cat.label}", fontSize = 13.sp, color = if (selected) UltraViolet else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            // ── Section header ───────────────────────────────────────────────
            item {
                Text(
                    "${selectedCategory.emoji} ${selectedCategory.label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // ── Items Grid (two columns) ─────────────────────────────────────
            val categoryItems = StoreCatalog.byCategory(selectedCategory)
            items(categoryItems.chunked(2)) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        StoreItemCard(
                            item       = item,
                            isUnlocked = item.id in unlockedIds,
                            isEquipped = item.id in avatarConfig.equippedItems,
                            modifier   = Modifier.weight(1f),
                            onBuy = {
                                scope.launch {
                                    val success = AvatarPreferences.purchaseItem(context, item)
                                    if (success) {
                                        purchaseMsg = "🎉 ${item.name} unlocked!"
                                        showNotEnoughCredits = false
                                    } else {
                                        showNotEnoughCredits = true
                                    }
                                }
                            },
                            onEquip = {
                                scope.launch {
                                    AvatarPreferences.equipItem(context, item)
                                    purchaseMsg = "✅ ${item.name} equipped!"
                                }
                            }
                        )
                    }
                    // Fill odd row
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StoreItemCard(
    item: StoreItem,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    modifier: Modifier = Modifier,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    val borderColor = when {
        isEquipped -> SolanaGreen
        isUnlocked -> UltraViolet.copy(alpha = 0.5f)
        else       -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (isEquipped) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Emoji/Icon with lock overlay
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isUnlocked) UltraViolet.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(item.emoji, fontSize = 30.sp)
                if (!isUnlocked) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp).padding(3.dp))
                    }
                }
            }

            Text(item.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(item.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)

            when {
                isEquipped -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SolanaGreen.copy(alpha = 0.15f))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("✅ Equipped", fontSize = 12.sp, color = SolanaGreen, fontWeight = FontWeight.Bold) }
                }
                isUnlocked -> {
                    Button(
                        onClick  = onEquip,
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = UltraViolet),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Equip", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                else -> {
                    Button(
                        onClick  = onBuy,
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Gold500),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${item.creditCost}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}
