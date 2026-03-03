package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.data.DirectivesManager
import com.aura.app.model.Directive
import com.aura.app.model.DirectiveType
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.util.HapticEngine
import com.aura.app.ui.util.springScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectivesScreen(onBack: () -> Unit) {
    val directives by DirectivesManager.activeDirectives.collectAsState()
    val completedToday by DirectivesManager.completedToday.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Generate directives if none exist
    LaunchedEffect(Unit) {
        if (directives.isEmpty()) {
            DirectivesManager.generateDailyDirectives()
        }
    }

    // ── Camera / NFC overlay state ──
    var pendingDirectiveId by remember { mutableStateOf<String?>(null) }
    var pendingDirectiveType by remember { mutableStateOf<DirectiveType?>(null) }
    var showCameraOverlay by remember { mutableStateOf(false) }
    var showNfcOverlay by remember { mutableStateOf(false) }
    var cameraCountdown by remember { mutableStateOf(5) }
    var nfcProgress by remember { mutableStateOf(0f) }

    // Camera countdown timer
    LaunchedEffect(showCameraOverlay) {
        if (showCameraOverlay) {
            cameraCountdown = 5
            while (cameraCountdown > 0) {
                delay(1000)
                cameraCountdown--
            }
            // Auto-complete after scan finishes
            pendingDirectiveId?.let { id ->
                val reward = DirectivesManager.completeDirective(id)
                if (reward > 0) HapticEngine.triggerSuccess(view)
            }
            showCameraOverlay = false
            pendingDirectiveId = null
        }
    }

    // NFC scanning animation
    LaunchedEffect(showNfcOverlay) {
        if (showNfcOverlay) {
            nfcProgress = 0f
            repeat(10) {
                delay(300)
                nfcProgress += 0.1f
                HapticEngine.triggerLight(view)
            }
            pendingDirectiveId?.let { id ->
                val reward = DirectivesManager.completeDirective(id)
                if (reward > 0) HapticEngine.triggerSuccess(view)
            }
            showNfcOverlay = false
            pendingDirectiveId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Directives") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
            ) {
                // Header stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Orange500.copy(alpha = 0.12f), Gold500.copy(alpha = 0.08f))
                            )
                        )
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Missions Today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$completedToday / ${directives.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Orange500,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "+${directives.filter { it.isCompleted }.sumOf { it.rewardAura }}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Orange500,
                        )
                        Text(
                            "Aura Earned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Active Directives",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(directives) { index, directive ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 100L)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 60 }),
                        ) {
                            DirectiveCard(
                                directive = directive,
                                onComplete = {
                                    pendingDirectiveId = directive.id
                                    pendingDirectiveType = directive.type
                                    when (directive.type) {
                                        DirectiveType.SPATIAL_SWEEP,
                                        DirectiveType.TEXTURE_ARCHIVE -> {
                                            showCameraOverlay = true
                                        }
                                        DirectiveType.GUARDIAN_WITNESS -> {
                                            showNfcOverlay = true
                                        }
                                    }
                                },
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            // ── Camera Overlay ──
            if (showCameraOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Orange500,
                            modifier = Modifier.size(80.dp),
                        )
                        Text(
                            if (pendingDirectiveType == DirectiveType.SPATIAL_SWEEP)
                                "Scanning Environment..."
                            else
                                "Capturing Texture...",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        // Countdown ring
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (5 - cameraCountdown) / 5f },
                                modifier = Modifier.size(100.dp),
                                color = Orange500,
                                strokeWidth = 6.dp,
                            )
                            Text(
                                "${cameraCountdown}s",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Text(
                            "Hold your device steady",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // ── NFC Overlay ──
            if (showNfcOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Nfc,
                            contentDescription = null,
                            tint = Orange500,
                            modifier = Modifier.size(80.dp),
                        )
                        Text(
                            "NFC Attestation",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        LinearProgressIndicator(
                            progress = { nfcProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Orange500,
                        )
                        Text(
                            "Scanning for nearby trade signal...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectiveCard(
    directive: Directive,
    onComplete: () -> Unit,
) {
    val icon = when (directive.type) {
        DirectiveType.SPATIAL_SWEEP -> Icons.Default.CameraAlt
        DirectiveType.GUARDIAN_WITNESS -> Icons.Default.Nfc
        DirectiveType.TEXTURE_ARCHIVE -> Icons.Default.PhotoCamera
    }

    val gradient = when (directive.type) {
        DirectiveType.SPATIAL_SWEEP -> listOf(Orange500.copy(alpha = 0.12f), Gold500.copy(alpha = 0.06f))
        DirectiveType.GUARDIAN_WITNESS -> listOf(Orange500.copy(alpha = 0.12f), Gold500.copy(alpha = 0.06f))
        DirectiveType.TEXTURE_ARCHIVE -> listOf(Gold500.copy(alpha = 0.12f), Orange500.copy(alpha = 0.06f))
    }

    val accentColor = when (directive.type) {
        DirectiveType.SPATIAL_SWEEP -> Orange500
        DirectiveType.GUARDIAN_WITNESS -> Gold500
        DirectiveType.TEXTURE_ARCHIVE -> Gold500
    }

    // Countdown
    var timeLeft by remember { mutableStateOf("") }
    LaunchedEffect(directive.expiresAt) {
        while (true) {
            val remaining = directive.expiresAt - System.currentTimeMillis()
            if (remaining <= 0) {
                timeLeft = "EXPIRED"
                break
            }
            val hours = remaining / (1000 * 60 * 60)
            val minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60)
            timeLeft = "${hours}h ${minutes}m"
            delay(60_000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (directive.isCompleted)
                Orange500.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            directive.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (timeLeft == "EXPIRED") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                timeLeft,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (timeLeft == "EXPIRED") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Reward badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "+${directive.rewardAura} Aura",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                directive.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (directive.hotzoneLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "📍 ${directive.hotzoneLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (directive.isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Completed ✓",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Orange500,
                    )
                }
            } else {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (directive.type) {
                            DirectiveType.SPATIAL_SWEEP -> "Start Camera Scan"
                            DirectiveType.GUARDIAN_WITNESS -> "Tap NFC to Witness"
                            DirectiveType.TEXTURE_ARCHIVE -> "Capture Macro Scan"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
