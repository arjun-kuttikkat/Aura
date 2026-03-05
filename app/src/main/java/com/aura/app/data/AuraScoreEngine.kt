package com.aura.app.data

import android.util.Log
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * AuraScoreEngine — multi-dimensional, decay-aware scoring algorithm.
 *
 * Overall Aura Score = streakMultiplier × geometricMean(weighted pillars)
 *
 * Four pillars (each 0–100):
 *   PhysicalWellness  — missions like walks, outdoor activities        weight 30%
 *   SocialTrust       — trade history, reviews, listing quality         weight 25%
 *   CreativeExpression — marketplace photos, listing creativity          weight 25%
 *   StreakMomentum    — consecutive active days                         weight 20%
 *
 * Streak multiplier: 1.0 + min(streak / 100f, 0.5f)  →  max 1.5×
 * Credits earned: 0.5 × score delta per activity event
 */
object AuraScoreEngine {

    private const val TAG = "AuraScoreEngine"

    // Pillar weights (must sum to 1.0)
    private const val W_PHYSICAL  = 0.30f
    private const val W_SOCIAL    = 0.25f
    private const val W_CREATIVE  = 0.25f
    private const val W_STREAK    = 0.20f

    // Decay per inactive day
    private const val DECAY_PER_DAY = 1.5f

    // AI photo score → pillar point conversion
    private const val PHOTO_SCORE_SCALING = 0.35f

    data class AuraPillars(
        val physicalWellness: Float = 10f,
        val socialTrust:      Float = 10f,
        val creativeExpr:     Float = 10f,
        val streakMomentum:   Float = 10f,
    ) {
        fun clamp() = copy(
            physicalWellness = physicalWellness.clamp01(),
            socialTrust       = socialTrust.clamp01(),
            creativeExpr      = creativeExpr.clamp01(),
            streakMomentum    = streakMomentum.clamp01(),
        )
        private fun Float.clamp01() = max(0f, min(100f, this))
    }

    enum class TrustTier(val label: String, val emoji: String, val threshold: Float) {
        NEWCOMER  ("Newcomer",   "🌱", 0f),
        BRONZE    ("Bronze",     "🥉", 20f),
        SILVER    ("Silver",     "🥈", 40f),
        GOLD      ("Gold",       "🥇", 60f),
        PLATINUM  ("Platinum",   "💎", 80f),
        AURA_LORD ("Aura Lord",  "⚡", 95f),
    }

    enum class MissionType { OUTDOOR_WALK, PHOTO_DOC, SOCIAL_TRADE, CREATIVE_LISTING, STREAK_DAY }

    // ── Core Computation ──────────────────────────────────────────────────────

    /**
     * Computes overall Aura score (0–100) from the four pillars.
     */
    fun computeScore(pillars: AuraPillars): Float {
        val p = pillars.clamp()
        // Weighted geometric mean — punishes zeros harder than arithmetic mean
        val logSum = W_PHYSICAL  * ln(p.physicalWellness.safe()) +
                     W_SOCIAL    * ln(p.socialTrust.safe())      +
                     W_CREATIVE  * ln(p.creativeExpr.safe())     +
                     W_STREAK    * ln(p.streakMomentum.safe())
        val geometricMean = exp(logSum)

        // Streak momentum multiplier
        val streakMultiplier = 1f + min(p.streakMomentum / 100f, 0.5f)
        return min(100f, geometricMean * streakMultiplier)
    }

    /**
     * Determines trust tier based on composite score.
     */
    fun getTier(score: Float): TrustTier {
        return TrustTier.values().lastOrNull { score >= it.threshold } ?: TrustTier.NEWCOMER
    }

    /**
     * Converts score delta into credits earned.
     * Rate: 0.5 credits per score point gained, minimum 1 credit per event.
     */
    fun toCredits(scoreDelta: Float): Int = max(1, (scoreDelta * 0.5f).toInt())

    // ── Event Handlers ────────────────────────────────────────────────────────

    /**
     * Apply rewards from completing an AI mission.
     * @param photoScore 0–100 score from AI vision verification. Higher = bigger boost.
     */
    fun applyMissionReward(
        pillars: AuraPillars,
        type: MissionType,
        photoScore: Int = 75
    ): AuraPillars {
        val boost = photoScore * PHOTO_SCORE_SCALING  // 0–35 pts
        Log.d(TAG, "Mission reward: type=$type photoScore=$photoScore boost=$boost")
        return when (type) {
            MissionType.OUTDOOR_WALK      -> pillars.copy(physicalWellness = pillars.physicalWellness + boost)
            MissionType.PHOTO_DOC         -> pillars.copy(creativeExpr = pillars.creativeExpr + boost * 0.8f, physicalWellness = pillars.physicalWellness + boost * 0.2f)
            MissionType.SOCIAL_TRADE      -> pillars.copy(socialTrust = pillars.socialTrust + boost)
            MissionType.CREATIVE_LISTING  -> pillars.copy(creativeExpr = pillars.creativeExpr + boost)
            MissionType.STREAK_DAY        -> pillars.copy(streakMomentum = pillars.streakMomentum + 8f)
        }.clamp()
    }

    /**
     * Apply daily decay when user is inactive.
     * Each inactive day reduces each pillar by DECAY_PER_DAY.
     */
    fun applyDecay(pillars: AuraPillars, inactiveDays: Int): AuraPillars {
        val totalDecay = inactiveDays * DECAY_PER_DAY
        return pillars.copy(
            physicalWellness = max(0f, pillars.physicalWellness - totalDecay),
            socialTrust       = max(0f, pillars.socialTrust - totalDecay),
            creativeExpr      = max(0f, pillars.creativeExpr - totalDecay),
            streakMomentum    = max(0f, pillars.streakMomentum - totalDecay * 1.5f), // streak decays faster
        )
    }

    /**
     * Apply streak increment: if user active today, boost StreakMomentum.
     */
    fun applyStreakDay(pillars: AuraPillars, currentStreak: Int): AuraPillars {
        val streakBonus = min(10f, 2f + currentStreak * 0.3f)
        return pillars.copy(streakMomentum = min(100f, pillars.streakMomentum + streakBonus))
    }

    /**
     * Apply reward for completing a listing (CreativeExpression + SocialTrust bump).
     */
    fun applyListingCreated(pillars: AuraPillars): AuraPillars {
        return pillars.copy(
            creativeExpr = min(100f, pillars.creativeExpr + 6f),
            socialTrust  = min(100f, pillars.socialTrust  + 2f),
        )
    }

    /**
     * Apply reward for completing a trade.
     */
    fun applyTradeCompleted(pillars: AuraPillars): AuraPillars {
        return pillars.copy(socialTrust = min(100f, pillars.socialTrust + 12f))
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    fun pillarsFromJson(json: String?): AuraPillars? {
        if (json.isNullOrBlank()) return null
        return try {
            val parts = json.split(",").map { it.trim().toFloat() }
            if (parts.size < 4) null
            else AuraPillars(parts[0], parts[1], parts[2], parts[3])
        } catch (e: Exception) { null }
    }

    fun pillarsToJson(pillars: AuraPillars): String =
        "${pillars.physicalWellness},${pillars.socialTrust},${pillars.creativeExpr},${pillars.streakMomentum}"

    // ── Utility ───────────────────────────────────────────────────────────────
    private fun Float.safe() = max(0.1f, this)  // prevent ln(0)
}
