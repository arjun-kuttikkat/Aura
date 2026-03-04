package com.aura.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.app.data.AuraRepository
import com.aura.app.data.AvatarPreferences
import com.aura.app.data.GroqAIService
import com.aura.app.ui.theme.*
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ── Data models ────────────────────────────────────────────────────────────────

private data class ChatMsg(val role: String, val text: String)

enum class MissionPhase {
    IDLE,          // No mission active, chatting
    PROPOSED,      // AI proposed a mission, showing Accept card
    GENERATING,    // Fetching structured mission from AI
    ACTIVE,        // User accepted — showing step-by-step flow
    CAPTURING,     // Camera open for photo proof
    VERIFYING,     // AI verifying submitted photo
    COMPLETE       // Mission done, showing celebration
}

private data class ActiveMission(
    val mission: GroqAIService.AIMission,
    val currentStep: Int = 0,
    val proofPhotoPath: String? = null,
    val verificationResult: Triple<Boolean, String, Int>? = null  // passed, feedback, score
)

// ── Main Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectivesScreen(onBack: () -> Unit) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // ── Chat State ──
    var chatHistory by remember {
        mutableStateOf(listOf(
            ChatMsg("assistant", "Hey! 👋 I'm your Aura guide. How are you feeling today? Tell me what's on your mind and I'll put together a mission just for you.")
        ))
    }
    var chatInput by remember { mutableStateOf("") }
    var isAiThinking by remember { mutableStateOf(false) }

    // ── Mission State ──
    var phase by remember { mutableStateOf(MissionPhase.IDLE) }
    var pendingMission by remember { mutableStateOf<ActiveMission?>(null) }
    var completedMissions by remember { mutableStateOf(listOf<ActiveMission>()) }

    // ── Camera ──
    val imageCapture = remember { ImageCapture.Builder().build() }
    val hasCameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) phase = MissionPhase.CAPTURING
    }

    // Auto-scroll chat when new messages arrive
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
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                reverseLayout = false
            ) {

                // ── 1. Completed Missions History ──────────────────────────────
                if (completedMissions.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 8.dp)
                        ) {
                            Text("Your Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(Orange500.copy(alpha = 0.12f), Gold500.copy(alpha = 0.08f))))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Missions Done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${completedMissions.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Orange500)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("+${completedMissions.sumOf { it.mission.auraReward }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SolanaGreen)
                                    Text("Aura Earned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(completedMissions) { cm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(cm.mission.emoji, fontSize = 16.sp)
                            Text(cm.mission.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("+${cm.mission.auraReward} ✦", style = MaterialTheme.typography.labelMedium, color = SolanaGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── 2. AI Chat ─────────────────────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(UltraViolet.copy(alpha = 0.1f), Color(0xFF3B82F6).copy(alpha = 0.05f))))
                            .border(1.dp, UltraViolet.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        // AI Header
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Brush.linearGradient(listOf(UltraViolet, Color(0xFF3B82F6))), CircleShape),
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

                        // Chat messages
                        chatHistory.takeLast(8).forEach { msg ->
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
                                    val userMsg = chatInput.trim()
                                    if (userMsg.isBlank()) return@IconButton
                                    val newChat = chatHistory + ChatMsg("user", userMsg)
                                    chatHistory = newChat
                                    chatInput = ""
                                    isAiThinking = true
                                    scope.launch {
                                        val groqHistory = newChat.map { GroqAIService.ChatMessage(it.role, it.text) }
                                        val response = GroqAIService.chatWithDirectiveAI(groqHistory.dropLast(1), userMsg)
                                        chatHistory = chatHistory + ChatMsg("assistant", response)
                                        isAiThinking = false
                                        // If AI signals mission is ready, transition to PROPOSED phase
                                        if (response.contains("[MISSION_READY]")) {
                                            phase = MissionPhase.GENERATING
                                            val mission = GroqAIService.generateMission(
                                                chatHistory.map { GroqAIService.ChatMessage(it.role, it.text) }
                                            )
                                            pendingMission = ActiveMission(mission)
                                            phase = MissionPhase.PROPOSED
                                        }
                                    }
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

                // ── 3. Generating Mission Indicator ───────────────────────────
                if (phase == MissionPhase.GENERATING) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = UltraViolet)
                                Text("Building your mission...", style = MaterialTheme.typography.bodyMedium, color = UltraViolet)
                            }
                        }
                    }
                }

                // ── 4. Mission Proposal Card ───────────────────────────────────
                if (phase == MissionPhase.PROPOSED && pendingMission != null) {
                    item {
                        MissionProposalCard(
                            mission = pendingMission!!.mission,
                            onAccept = { phase = MissionPhase.ACTIVE },
                            onDecline = {
                                phase = MissionPhase.IDLE
                                pendingMission = null
                                chatHistory = chatHistory + ChatMsg("assistant", "No worries! Let me know when you're ready or if you'd like a different kind of mission. 😊")
                            }
                        )
                    }
                }

                // ── 5. Active Mission Steps ────────────────────────────────────
                if (phase == MissionPhase.ACTIVE && pendingMission != null) {
                    item {
                        ActiveMissionCard(
                            activeMission = pendingMission!!,
                            onStepComplete = { step ->
                                val current = pendingMission!!
                                if (step < current.mission.steps.size - 1) {
                                    // Move to next step
                                    pendingMission = current.copy(currentStep = step + 1)
                                } else {
                                    // Final step = take photo
                                    if (hasCameraPermission) {
                                        phase = MissionPhase.CAPTURING
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            }
                        )
                    }
                }

                // ── 6. Mission Complete Celebration ────────────────────────────
                if (phase == MissionPhase.COMPLETE && pendingMission != null) {
                    item {
                        MissionCompleteCard(
                            mission = pendingMission!!,
                            onDone = {
                                completedMissions = completedMissions + pendingMission!!
                                // Award Aura points
                                scope.launch {
                                    walletAddress?.let { wallet ->
                                        AuraRepository.loadProfile(wallet)
                                    }
                                }
                                pendingMission = null
                                phase = MissionPhase.IDLE
                                chatHistory = chatHistory + ChatMsg(
                                    "assistant",
                                    "Amazing work! 🎉 You just earned ${completedMissions.lastOrNull()?.mission?.auraReward ?: 0} Aura points. Ready for another mission? Tell me how you're feeling!"
                                )
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            // ── Camera Overlay for Photo Proof ────────────────────────────────
            if (phase == MissionPhase.CAPTURING) {
                CameraProofOverlay(
                    imageCapture = imageCapture,
                    lifecycleOwner = lifecycleOwner,
                    context = context,
                    onPhotoTaken = { path ->
                        pendingMission = pendingMission?.copy(proofPhotoPath = path)
                        phase = MissionPhase.VERIFYING
                        scope.launch {
                            delay(500)
                            val mission = pendingMission?.mission
                            if (mission != null && path != null) {
                                val photoBytes = java.io.File(path).readBytes()
                                val (passed, feedback, score) = GroqAIService.verifyMissionCompletion(
                                    missionDescription = mission.description,
                                    imageBytes = photoBytes
                                )
                                pendingMission = pendingMission?.copy(
                                    verificationResult = Triple(passed, feedback, score)
                                )
                                phase = if (passed) MissionPhase.COMPLETE else MissionPhase.CAPTURING
                                if (!passed) {
                                    chatHistory = chatHistory + ChatMsg("assistant", "Hmm, I couldn't verify that one. $feedback Try again! 📸")
                                } else {
                                    // Add credits for completion
                                    val creditsEarned = (score * 0.5f).toInt().coerceAtLeast(1)
                                    AvatarPreferences.addCredits(context, creditsEarned)
                                }
                            } else {
                                phase = MissionPhase.COMPLETE
                            }
                        }
                    },
                    onCancel = { phase = MissionPhase.ACTIVE }
                )
            }

            // ── Verifying overlay ─────────────────────────────────────────────
            if (phase == MissionPhase.VERIFYING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(60.dp), color = UltraViolet, strokeWidth = 5.dp)
                        Text("Aura AI is verifying your mission...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Hold tight! ✨", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Mission Proposal Card ──────────────────────────────────────────────────────

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
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
            Spacer(modifier = Modifier.height(12.dp))

            // Steps preview
            mission.steps.forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(UltraViolet.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = UltraViolet, fontWeight = FontWeight.Bold)
                    }
                    Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(16.dp))
                Text("+${mission.auraReward} Aura points", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Gold500)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Not Now")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accept Mission", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Active Mission Step-by-Step Card ──────────────────────────────────────────

@Composable
private fun ActiveMissionCard(
    activeMission: ActiveMission,
    onStepComplete: (Int) -> Unit
) {
    val mission = activeMission.mission
    val currentStep = activeMission.currentStep
    val progress by animateFloatAsState(
        targetValue = (currentStep.toFloat() + 1f) / mission.steps.size.toFloat(),
        animationSpec = tween(600), label = "progress"
    )
    val isLastStep = currentStep == mission.steps.size - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Orange500.copy(alpha = 0.1f), Gold500.copy(alpha = 0.06f))))
            .border(1.5.dp, Orange500.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mission.emoji, fontSize = 22.sp)
                Text(mission.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text("${currentStep + 1}/${mission.steps.size}", style = MaterialTheme.typography.labelMedium, color = Orange500, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Orange500,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // All steps — past ones struck through, current one highlighted
        mission.steps.forEachIndexed { i, step ->
            val isDone = i < currentStep
            val isCurrent = i == currentStep
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrent) Orange500.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .padding(if (isCurrent) 10.dp else 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            when {
                                isDone -> SolanaGreen.copy(alpha = 0.2f)
                                isCurrent -> Orange500.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = SolanaGreen, modifier = Modifier.size(14.dp))
                    } else {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    step,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isCurrent -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onStepComplete(currentStep) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLastStep) UltraViolet else Orange500
            )
        ) {
            when {
                isLastStep -> {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take Proof Photo 📸", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                else -> {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Done — Next Step ➜", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Camera Proof Overlay ──────────────────────────────────────────────────────

@Composable
private fun CameraProofOverlay(
    imageCapture: ImageCapture,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: android.content.Context,
    onPhotoTaken: (String?) -> Unit,
    onCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // TOP bar instruction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("📸 Mission Proof", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Capture a photo to prove you completed the mission", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }

        // BOTTOM controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(30.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                // Shutter
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(Color.White, CircleShape)
                        .clickable {
                            val photoFile = File(context.cacheDir, "mission_proof_${System.currentTimeMillis()}.jpg")
                            val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            imageCapture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        onPhotoTaken(photoFile.absolutePath)
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        onPhotoTaken(null)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.Black, modifier = Modifier.size(30.dp))
                }

                // Spacer for symmetry
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}

// ── Mission Complete Card ────────────────────────────────────────────────────

@Composable
private fun MissionCompleteCard(
    mission: ActiveMission,
    onDone: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(500)) + slideInVertically(initialOffsetY = { 80 })) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(SolanaGreen.copy(alpha = 0.15f), UltraViolet.copy(alpha = 0.08f))))
                .border(2.dp, SolanaGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Mission Complete!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SolanaGreen)
            Text(mission.mission.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            mission.verificationResult?.let { (_, feedback, score) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    Column {
                        Text("🤖 AI says: $feedback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Photo Quality Score: $score/100", style = MaterialTheme.typography.labelMedium, color = Orange500, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Gold500, modifier = Modifier.size(22.dp))
                Text("+${mission.mission.auraReward} Aura Earned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Gold500)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolanaGreen)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Claim Aura Points", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}
