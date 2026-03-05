package com.aura.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Premium crypto price formatter for Solana (SOL).
 *
 * - Uses lamport precision (1 SOL = 1_000_000_000 lamports).
 * - Adapts decimal places by amount: small values get more precision, large values fewer.
 * - Caps at 9 decimals (max lamport precision).
 * - Trims trailing zeros for a premium, minimal look.
 * - Adds thousand separators for large amounts.
 */
object CryptoPriceFormatter {

    private const val LAMPORTS_PER_SOL = 1_000_000_000L
    private const val MAX_DECIMALS = 9

    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    /**
     * Format lamports as SOL string, e.g. "0.00123 SOL" or "1,234.56 SOL".
     */
    fun formatLamports(lamports: Long, suffix: String = " SOL"): String =
        formatSol(lamports.toDouble() / LAMPORTS_PER_SOL, suffix)

    /**
     * Format SOL (Double) as string, e.g. "0.00123 SOL" or "1,234.56 SOL".
     */
    fun formatSol(sol: Double, suffix: String = " SOL"): String {
        val value = if (sol.isNaN() || sol.isInfinite()) 0.0 else sol
        val decimals = decimalsFor(value)
        val pattern = if (decimals == 0) "#,###" else "#,###.${"#".repeat(decimals)}"
        val df = DecimalFormat(pattern, symbols)
        var formatted = df.format(value)
        if (decimals > 0) formatted = formatted.trimEnd('0').trimEnd('.')
        return formatted + suffix
    }

    /**
     * Format lamports as SOL string without suffix (for embedding in other text).
     */
    fun formatLamportsShort(lamports: Long): String = formatSol(lamports.toDouble() / LAMPORTS_PER_SOL, "")

    /**
     * Format SOL without suffix.
     */
    fun formatSolShort(sol: Double): String = formatSol(sol, "")

    /**
     * Convert SOL to lamports. Handles up to 9 decimal places.
     */
    fun solToLamports(sol: Double): Long = (sol * LAMPORTS_PER_SOL).toLong()

    /**
     * Convert lamports to SOL.
     */
    fun lamportsToSol(lamports: Long): Double = lamports.toDouble() / LAMPORTS_PER_SOL

    /**
     * Adaptive decimals: small values get more precision, large values fewer.
     * Cap at [MAX_DECIMALS].
     */
    private fun decimalsFor(sol: Double): Int {
        val absSol = abs(sol)
        if (absSol == 0.0) return 2
        return when {
            absSol >= 1_000   -> 0
            absSol >= 100     -> 1
            absSol >= 10      -> 2
            absSol >= 1       -> 2
            absSol >= 0.1     -> 3
            absSol >= 0.01    -> 4
            absSol >= 0.001   -> 5
            absSol >= 0.0001  -> 6
            absSol >= 0.00001 -> 7
            else              -> MAX_DECIMALS.coerceIn(2, 9)
        }
    }
}
