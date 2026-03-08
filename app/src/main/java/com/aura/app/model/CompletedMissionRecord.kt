package com.aura.app.model

import kotlinx.serialization.Serializable

@Serializable
data class CompletedMissionRecord(
    val id: String,
    val title: String,
    val emoji: String,
    val auraReward: Int,
    val aiFeedback: String,
    val completedAtMillis: Long
)
