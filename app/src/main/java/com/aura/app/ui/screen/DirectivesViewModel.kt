package com.aura.app.ui.screen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.app.data.AuraRepository
import com.aura.app.data.AvatarPreferences
import com.aura.app.data.GroqAIService
import com.aura.app.data.MissionHistoryStore
import com.aura.app.model.CompletedMissionRecord
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMsg(val role: String, val text: String)

enum class MissionPhase {
    IDLE,          // No mission active, chatting
    PROPOSED,      // AI proposed a mission, showing Accept card
    GENERATING,    // Fetching structured mission from AI
    ACTIVE,        // User accepted — showing step-by-step flow
    CAPTURING,     // Camera open for photo proof
    VERIFYING,     // AI verifying submitted photo
    COMPLETE       // Mission done, showing celebration
}

data class ActiveMission(
    val mission: GroqAIService.AIMission,
    val currentStep: Int = 0,
    val proofPhotoPath: String? = null,
    val verificationResult: Triple<Boolean, String, Int>? = null  // passed, feedback, score
)

class DirectivesViewModel : ViewModel() {

    private val _chatHistory = MutableStateFlow(
        listOf(
            ChatMsg("assistant", "Hey! 👋 I'm your Aura guide. How are you feeling today? Tell me what's on your mind and I'll put together a mission just for you.")
        )
    )
    val chatHistory: StateFlow<List<ChatMsg>> = _chatHistory.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _phase = MutableStateFlow(MissionPhase.IDLE)
    val phase: StateFlow<MissionPhase> = _phase.asStateFlow()

    private val _pendingMission = MutableStateFlow<ActiveMission?>(null)
    val pendingMission: StateFlow<ActiveMission?> = _pendingMission.asStateFlow()

    private val _completedMissions = MutableStateFlow<List<CompletedMissionRecord>>(emptyList())
    val completedMissions: StateFlow<List<CompletedMissionRecord>> = _completedMissions.asStateFlow()

    // ── Initialization ──
    fun loadHistory(context: Context) {
        viewModelScope.launch {
            MissionHistoryStore.historyFlow(context).collect { records ->
                _completedMissions.value = records
            }
        }
    }

    // ── Chat Actions ──
    fun sendMessage(userMsg: String) {
        if (userMsg.isBlank() || _isAiThinking.value) return

        val newChat = _chatHistory.value + ChatMsg("user", userMsg)
        _chatHistory.value = newChat
        _isAiThinking.value = true

        viewModelScope.launch {
            val groqHistory = newChat.map { GroqAIService.ChatMessage(it.role, it.text) }
            val response = GroqAIService.chatWithDirectiveAI(groqHistory.dropLast(1), userMsg)
            _chatHistory.value = _chatHistory.value + ChatMsg("assistant", response)
            _isAiThinking.value = false

            // If AI signals mission is ready, transition to PROPOSED phase
            if (response.contains("[MISSION_READY]")) {
                _phase.value = MissionPhase.GENERATING
                val mission = GroqAIService.generateMission(
                    _chatHistory.value.map { GroqAIService.ChatMessage(it.role, it.text) }
                )
                _pendingMission.value = ActiveMission(mission)
                _phase.value = MissionPhase.PROPOSED
            }
        }
    }

    // ── Mission Actions ──
    fun acceptMission() {
        _phase.value = MissionPhase.ACTIVE
    }

    fun declineMission() {
        _phase.value = MissionPhase.IDLE
        _pendingMission.value = null
        _isAiThinking.value = false
        _chatHistory.value = _chatHistory.value + ChatMsg("assistant", "No worries! Let me know when you're ready or if you'd like a different kind of mission. 😊")
    }

    fun cancelMission() {
        _phase.value = MissionPhase.IDLE
        _pendingMission.value = null
        _isAiThinking.value = false
        _chatHistory.value = _chatHistory.value + ChatMsg("assistant", "Mission cancelled. Taking a break is completely fine! Want to try something else later?")
    }

    fun advanceMissionStep(step: Int) {
        val current = _pendingMission.value ?: return
        if (step < current.mission.steps.size - 1) {
            _pendingMission.value = current.copy(currentStep = step + 1)
        } else {
            _phase.value = MissionPhase.CAPTURING
        }
    }

    fun cancelCamera() {
        _phase.value = MissionPhase.ACTIVE
    }

    fun submitPhoto(path: String?) {
        val current = _pendingMission.value ?: return
        _pendingMission.value = current.copy(proofPhotoPath = path)
        _phase.value = MissionPhase.VERIFYING
        
        viewModelScope.launch {
            val mission = current.mission
            if (path != null) {
                val photoBytes = java.io.File(path).readBytes()
                val (passed, feedback, score) = GroqAIService.verifyMissionCompletion(
                    missionDescription = mission.description,
                    imageBytes = photoBytes
                )
                _pendingMission.value = _pendingMission.value?.copy(
                    verificationResult = Triple(passed, feedback, score)
                )
                _phase.value = if (passed) MissionPhase.COMPLETE else MissionPhase.CAPTURING
                
                if (!passed) {
                    _chatHistory.value = _chatHistory.value + ChatMsg("assistant", "Hmm, I couldn't verify that one. Try again! 📸")
                }
            } else {
                _phase.value = MissionPhase.COMPLETE
            }
        }
    }

    fun claimRewardsAndComplete(context: Context) {
        val missionData = _pendingMission.value ?: return
        val appContext = context.applicationContext // Use app context to avoid memory leaks and invalidation
        
        viewModelScope.launch {
            try {
                val scoreResult = missionData.verificationResult?.third ?: 50
                val shopCreditsEarned = (scoreResult * 0.5f).toInt().coerceAtLeast(1)
                val scaledAuraReward = (missionData.mission.auraReward * (scoreResult / 100f)).toInt().coerceAtLeast(1)
                
                // 1. Add Shop Credits 
                AvatarPreferences.addCredits(appContext, shopCreditsEarned)
                
                // 2. Add Aura Points & update streak in DB
                val wallet = WalletConnectionState.walletAddress.value
                if (wallet != null) {
                    AuraRepository.addMissionAuraPoints(
                        walletAddress = wallet,
                        auraReward = scaledAuraReward,
                        missionTitle = missionData.mission.title
                    )
                }
                
                // 3. Persist to Mission History 
                val record = CompletedMissionRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    title = missionData.mission.title,
                    emoji = missionData.mission.emoji,
                    auraReward = scaledAuraReward,
                    aiFeedback = missionData.verificationResult?.second ?: "Completed outside camera context.",
                    completedAtMillis = System.currentTimeMillis()
                )
                MissionHistoryStore.addRecord(appContext, record)
                
                // 4. Reset state
                _pendingMission.value = null
                _phase.value = MissionPhase.IDLE
                _chatHistory.value = _chatHistory.value + ChatMsg(
                    "assistant",
                    "Amazing work! 🎉 You just earned $scaledAuraReward Aura points with a $scoreResult% photo score. Ready for another mission? Tell me how you're feeling!"
                )
            } catch (e: Exception) {
                // Catch DataStore IOExceptions, context crashes, and any other unhandled errors
                android.util.Log.e("DirectivesViewModel", "Error claiming rewards", e)
                
                // Still reset state so the user isn't stuck holding a pending mission
                _pendingMission.value = null
                _phase.value = MissionPhase.IDLE
                _chatHistory.value = _chatHistory.value + ChatMsg(
                    "assistant",
                    "Ah, there was a tiny glitch saving your rewards, but your mission is marked complete! Want to try another?"
                )
            }
        }
    }
}
