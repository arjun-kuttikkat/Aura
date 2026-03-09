package com.aura.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.aura.app.data.AuraRepository
import com.aura.app.data.GroqAIService
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectivesScreen(
    viewModel: DirectivesViewModel,
    onNavigateToMission: () -> Unit,
    onBack: () -> Unit,
    onCameraOpenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // ViewModel State
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val pendingMission by viewModel.pendingMission.collectAsState()
    val completedMissions by viewModel.completedMissions.collectAsState()

    val profile by AuraRepository.currentProfile.collectAsState()
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)

    var chatInput by remember { mutableStateOf("") }

    // Ensure profile is loaded for Aura display and mission claim
    LaunchedEffect(walletAddress) {
        walletAddress?.let { AuraRepository.loadProfile(it) }
    }
    var showFullChat by remember { mutableStateOf(false) }

<<<<<<< HEAD
    // Init load history (reload when wallet connects so we fetch from Supabase)
    LaunchedEffect(walletAddress) {
        viewModel.loadHistory(context)
=======
    // Init load history
    LaunchedEffect(Unit) {
        viewModel.refreshHistory(context)
>>>>>>> 520fdca (Finished updates for March 10)
        onCameraOpenChange(false) // Never full screen on this hub page
    }

    // Auto-scroll chat
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.size > 1) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            com.aura.app.ui.components.MainTopBar(title = "Directives")
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                reverseLayout = false
            ) {
                // Header (Stats Row)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Total Aura Card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                                .padding(16.dp)
                        ) {
                            Text("Total Aura", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${profile?.auraScore ?: 0}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold500)
                            }
                        }
                        // Active Streak Card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Orange500.copy(alpha=0.15f))
                                .padding(16.dp)
                        ) {
                            Text("Active Streak", style = MaterialTheme.typography.labelSmall, color = Orange500.copy(alpha=0.8f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Orange500, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${profile?.streakDays ?: 0} Days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Orange500)
                            }
                        }
                    }
                }

                // Daily Inspiration (Bug 5 enhancement)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(SolanaGreen.copy(alpha=0.2f), UltraViolet.copy(alpha=0.1f))))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "\"Every small step builds your Aura. What will you do today?\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── 1. AI Chat Block ─────────────────────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(UltraViolet.copy(alpha = 0.1f), DarkVoid.copy(alpha = 0.3f))))
                            .border(1.dp, UltraViolet.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        // AI Header
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Brush.linearGradient(listOf(UltraViolet, Gold500)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("Aura AI Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = UltraViolet)
                                Text("Your personal mission companion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Chat Logs Toggle Button
                        if (chatHistory.size > 2) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.clickable { showFullChat = !showFullChat },
                                    color = Color.Transparent
                                ) {
                                    Text(
                                        text = if (showFullChat) "Hide Chat Logs ⬆" else "View Chat Logs (${chatHistory.size - 1}) ⬇",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UltraViolet,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Chat messages
                        val messagesToShow = if (showFullChat) chatHistory else listOfNotNull(chatHistory.lastOrNull())
                        messagesToShow.forEach { msg ->
                            val isAi = msg.role == "assistant"
                            val displayText = msg.text.replace("[MISSION_READY]", "").trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .clip(RoundedCornerShape(
                                            topStart = if (isAi) 4.dp else 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = 18.dp,
                                            bottomEnd = if (isAi) 18.dp else 4.dp
                                        ))
                                        .background(if (isAi) MaterialTheme.colorScheme.surfaceVariant else UltraViolet.copy(alpha = 0.85f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(displayText, style = MaterialTheme.typography.bodySmall, color = if (isAi) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                                }
                            }
                        }

                        if (isAiThinking) {
                            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = UltraViolet)
                                Text("Aura is thinking...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Chat input row
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                placeholder = { Text("How are you feeling today?", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isAiThinking && phase == MissionPhase.IDLE
                            )
                            IconButton(
                                onClick = {
                                    viewModel.sendMessage(chatInput)
                                    chatInput = ""
                                },
                                enabled = !isAiThinking && chatInput.isNotBlank() && phase == MissionPhase.IDLE,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (!isAiThinking && chatInput.isNotBlank() && phase == MissionPhase.IDLE) UltraViolet
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                                    tint = if (!isAiThinking && chatInput.isNotBlank() && phase == MissionPhase.IDLE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // ── 2. Generating Mission Indicator ───────────────────────────
                if (phase == MissionPhase.GENERATING) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = UltraViolet)
                                Text("Building your mission...", style = MaterialTheme.typography.bodyMedium, color = UltraViolet)
                            }
                        }
                    }
                }

                // ── 3. Mission Proposal Card ───────────────────────────────────
                if (phase == MissionPhase.PROPOSED && pendingMission != null) {
                    item {
                        MissionProposalCard(
                            mission = pendingMission!!.mission,
                            onAccept = {
                                viewModel.acceptMission()
                                onNavigateToMission()
                            },
                            onDecline = { viewModel.declineMission() }
                        )
                    }
                }
                
                // If mission is ACTIVE, CAPTURING, VERIFYING, or COMPLETE, show a jump back in button
                if ((phase == MissionPhase.ACTIVE || phase == MissionPhase.CAPTURING || phase == MissionPhase.VERIFYING || phase == MissionPhase.COMPLETE) && pendingMission != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Orange500.copy(alpha=0.15f))
                                .border(1.dp, Orange500.copy(alpha=0.5f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mission in Progress", style = MaterialTheme.typography.labelSmall, color = Orange500)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(pendingMission!!.mission.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onNavigateToMission,
                                    colors = ButtonDefaults.buttonColors(containerColor = Orange500)
                                ) {
                                    Text("Resume")
                                }
                            }
                        }
                    }
                }

                // ── 4. Past Missions History ──────────────────────────────────
                if (completedMissions.isNotEmpty()) {
                    item {
                        var showPastMissions by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showPastMissions = !showPastMissions }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (showPastMissions) "Hide Past Missions ⬆" else "View Past Missions (${completedMissions.size}) ⬇",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        AnimatedVisibility(visible = showPastMissions) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                completedMissions.forEach { record ->
                                    val dateString = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(record.completedAtMillis))
                                    var expanded by remember { mutableStateOf(false) }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .clickable { expanded = !expanded }
                                            .padding(16.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(record.emoji, fontSize = 28.sp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(record.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(dateString, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("+${record.auraReward} ✦", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SolanaGreen)
                                            }
                                        }

                                        AnimatedVisibility(visible = expanded) {
                                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("🤖 AI verify result:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("\"${record.aiFeedback}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionProposalCard(
    mission: GroqAIService.AIMission,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 60 })) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(UltraViolet.copy(alpha = 0.18f), SolanaGreen.copy(alpha = 0.08f))))
                .border(1.5.dp, UltraViolet.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(mission.emoji, fontSize = 28.sp)
                Column {
                    Text("Mission Unlocked!", style = MaterialTheme.typography.labelMedium, color = UltraViolet, fontWeight = FontWeight.Bold)
                    Text(mission.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(mission.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(16.dp))
                Text("+${mission.auraReward} Aura points", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Gold500)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
                ) {
                    Text("Start Mission →", fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not Now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
