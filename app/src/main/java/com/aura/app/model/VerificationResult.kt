package com.aura.app.model

data class VerificationResult(
    val score: Float, // 0.0 - 1.0 similarity
    val pass: Boolean,
    val reason: String,
)
