package com.aura.app.data

import com.aura.app.model.Listing
import com.aura.app.model.ProfileDto

/**
 * On-device AI Trade-Risk Oracle — evaluates seller risk before buyer commits funds.
 *
 * Uses heuristic analysis of:
 * - Account age
 * - Aura score
 * - Trade history
 * - Price vs. experience mismatch
 *
 * No network calls required — runs entirely on-device for instant evaluation.
 */
object TradeRiskOracle {

    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    data class RiskAssessment(
        val level: RiskLevel,
        val score: Int,         // 0-100, higher = riskier
        val flags: List<String>,
        val recommendation: String,
    )

    /**
     * Evaluate a listing's seller for trade risk.
     * Returns a comprehensive risk assessment with actionable flags.
     */
    fun evaluate(seller: ProfileDto?, listing: Listing): RiskAssessment {
        var riskScore = 0
        val flags = mutableListOf<String>()

        // ── Account Age Check ────────────────────────────────────
        if (seller?.createdAt != null) {
            try {
                val created = java.time.OffsetDateTime.parse(seller.createdAt)
                val hoursOld = java.time.temporal.ChronoUnit.HOURS.between(created, java.time.OffsetDateTime.now())
                when {
                    hoursOld < 24 -> {
                        riskScore += 40
                        flags.add("⚠️ Account created less than 24 hours ago")
                    }
                    hoursOld < 168 -> { // 7 days
                        riskScore += 20
                        flags.add("Account less than 1 week old")
                    }
                    hoursOld < 720 -> { // 30 days
                        riskScore += 5
                    }
                }
            } catch (_: Exception) {
                riskScore += 10
                flags.add("Unable to verify account age")
            }
        }
        // No penalty for missing account creation date — not a reliable risk signal

        // ── Aura Score Check ─────────────────────────────────────
        val aura = seller?.auraScore ?: 0
        val rankInfo = com.aura.app.model.RankSystem.getRankInfo(aura)
        val absoluteStars = rankInfo.absoluteStars

        when {
            absoluteStars < 2 -> {
                riskScore += 35
                flags.add("⚠️ Critically low Aura (New Account)")
            }
            absoluteStars < 5 -> {
                riskScore += 20
                flags.add("Low Aura Rank (${rankInfo.rankName})")
            }
            absoluteStars < 10 -> {
                riskScore += 10
            }
            absoluteStars >= 20 -> {
                riskScore -= 10 // Reward high reputation
            }
        }

        // ── Trade History Check ──────────────────────────────────
        val trades = seller?.totalTrades ?: 0
        when {
            trades == 0 -> {
                riskScore += 20
                flags.add("First-time seller — no trade history")
            }
            trades < 3 -> {
                riskScore += 10
                flags.add("Limited trade history ($trades trades)")
            }
            trades >= 20 -> {
                riskScore -= 10 // Reward established sellers
            }
        }

        // ── Price vs. Experience Mismatch ────────────────────────
        val priceSOL = listing.priceLamports / 1_000_000_000.0
        if (priceSOL > 5.0 && trades < 3) {
            riskScore += 25
            flags.add("⚠️ High-value item (${com.aura.app.util.CryptoPriceFormatter.formatSolShort(priceSOL)} SOL) from low-history seller")
        }
        if (priceSOL > 10.0 && absoluteStars < 10) {
            riskScore += 15
            flags.add("Premium listing with below-average reputation")
        }

        // ── Streak Check ─────────────────────────────────────────
        if ((seller?.streakDays ?: 0) == 0 && trades > 0) {
            riskScore += 5
            flags.add("Streak broken — seller has been inactive")
        }

        // ── Clamp and classify ───────────────────────────────────
        riskScore = riskScore.coerceIn(0, 100)

        val level = when {
            riskScore >= 70 -> RiskLevel.CRITICAL
            riskScore >= 45 -> RiskLevel.HIGH
            riskScore >= 25 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val recommendation = when (level) {
            RiskLevel.LOW -> "This seller has a strong reputation. Proceed with confidence."
            RiskLevel.MEDIUM -> "Some risk factors detected. Verify item in person before completing escrow."
            RiskLevel.HIGH -> "Multiple risk flags detected. Exercise caution — enforce strict NFC verification."
            RiskLevel.CRITICAL -> "⚠️ High-risk transaction. Stricter escrow rules have been auto-applied."
        }

        return RiskAssessment(level, riskScore, flags, recommendation)
    }
}
