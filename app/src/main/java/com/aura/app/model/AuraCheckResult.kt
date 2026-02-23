package com.aura.app.model

data class AuraCheckResult(
    val rating: Int,
    val feedback: String,
    val streakMaintained: Boolean,
    val creditsEarned: Int
)
