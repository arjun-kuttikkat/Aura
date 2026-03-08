package com.aura.app.model

/**
 * RankSystem: The mathematical engine that converts a flat [auraScore] 
 * into a zero-sum, Mobile Legends (MLBB) style gamified rank system.
 * 
 * Logic:
 * - 100 Aura Points = 1 Star.
 * - [auraScore] is mathematically converted to a discrete [totalStars], 
 *   which is then mapped to a specific Rank (Ember -> Radiant) and Tier.
 */
object RankSystem {

    data class RankInfo(
        val rankName: String,
        val tierString: String,
        val currentStarsInTier: Int,
        val maxStarsInTier: Int,
        val absoluteStars: Int,
        val pointsToNextStar: Int,
        val isMaxRank: Boolean,
        val emoji: String
    )

    fun getRankInfo(auraScore: Int): RankInfo {
        // Prevent negative scores from breaking math
        val safeScore = auraScore.coerceAtLeast(0)
        
        val absoluteStars = safeScore / 100
        val pointsToNextStar = safeScore % 100

        return when {
            absoluteStars in 0..8 -> {
                // Ember: 9 Stars total. Tiers III, II, I. (3 stars per tier)
                val tierIdx = absoluteStars / 3
                val starsInTier = absoluteStars % 3
                RankInfo(
                    rankName = "Ember",
                    tierString = getTierNumerals(3 - tierIdx), // Ember III -> I
                    currentStarsInTier = starsInTier,
                    maxStarsInTier = 3,
                    absoluteStars = absoluteStars,
                    pointsToNextStar = pointsToNextStar,
                    isMaxRank = false,
                    emoji = "🔥"
                )
            }
            absoluteStars in 9..20 -> {
                // Spark: 12 Stars total. Tiers III, II, I. (4 stars per tier). Start at 9.
                val rankRelativeStars = absoluteStars - 9
                val tierIdx = rankRelativeStars / 4
                val starsInTier = rankRelativeStars % 4
                RankInfo(
                    rankName = "Spark",
                    tierString = getTierNumerals(3 - tierIdx), // Spark III -> I
                    currentStarsInTier = starsInTier,
                    maxStarsInTier = 4,
                    absoluteStars = absoluteStars,
                    pointsToNextStar = pointsToNextStar,
                    isMaxRank = false,
                    emoji = "✨"
                )
            }
            absoluteStars in 21..40 -> {
                // Flame: 20 Stars total. Tiers IV, III, II, I. (5 stars per tier). Start at 21.
                val rankRelativeStars = absoluteStars - 21
                val tierIdx = rankRelativeStars / 5
                val starsInTier = rankRelativeStars % 5
                RankInfo(
                    rankName = "Flame",
                    tierString = getTierNumerals(4 - tierIdx), // Flame IV -> I
                    currentStarsInTier = starsInTier,
                    maxStarsInTier = 5,
                    absoluteStars = absoluteStars,
                    pointsToNextStar = pointsToNextStar,
                    isMaxRank = false,
                    emoji = "☄️"
                )
            }
            absoluteStars in 41..65 -> {
                // Nova: 25 Stars total. Tiers V, IV, III, II, I. (5 stars per tier). Start at 41.
                val rankRelativeStars = absoluteStars - 41
                val tierIdx = rankRelativeStars / 5
                val starsInTier = rankRelativeStars % 5
                RankInfo(
                    rankName = "Nova",
                    tierString = getTierNumerals(5 - tierIdx), // Nova V -> I
                    currentStarsInTier = starsInTier,
                    maxStarsInTier = 5,
                    absoluteStars = absoluteStars,
                    pointsToNextStar = pointsToNextStar,
                    isMaxRank = false,
                    emoji = "🌟"
                )
            }
            else -> {
                // Radiant (Apex): Infinite stars. 66+ Absolute Stars.
                RankInfo(
                    rankName = "Radiant",
                    tierString = "",
                    currentStarsInTier = absoluteStars - 66,
                    maxStarsInTier = 0, // Infinite
                    absoluteStars = absoluteStars,
                    pointsToNextStar = pointsToNextStar,
                    isMaxRank = true,
                    emoji = "👑"
                )
            }
        }
    }

    private fun getTierNumerals(tierVal: Int): String {
        return when(tierVal) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            else -> ""
        }
    }
}
