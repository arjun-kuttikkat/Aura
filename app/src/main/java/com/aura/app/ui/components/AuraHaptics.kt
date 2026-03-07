package com.aura.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Haptic feedback for every meaningful action.
 * Safe — all methods catch exceptions so they never crash the app.
 *
 * Usage: AuraHaptics.subtleTap(LocalHapticFeedback.current)  // chips, toggles
 *        AuraHaptics.lightTap(LocalHapticFeedback.current)   // buttons
 *        AuraHaptics.heavyImpact(LocalContext.current)       // NFC, confirm
 */
object AuraHaptics {

    /** Subtle tick — chips, toggles, lightweight selections. Lighter than lightTap. */
    fun subtleTap(haptic: HapticFeedback) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Exception) { }
    }

    /** Button presses, tab switches */
    fun lightTap(haptic: HapticFeedback) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) { }
    }

    /** Card confirms, nav switches */
    fun mediumImpact(haptic: HapticFeedback) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) { }
    }

    /** NFC tap success, escrow lock */
    fun heavyImpact(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (_: Exception) { /* VIBRATE permission or device limitation */ }
    }

    /** Trade complete, aura check success */
    fun successPattern(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), intArrayOf(0, 128, 0, 255), -1))
                }
            }
        } catch (_: Exception) { /* VIBRATE permission or device limitation */ }
    }

    /** NFC fail, verification fail */
    fun errorPattern(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1))
                }
            }
        } catch (_: Exception) { /* VIBRATE permission or device limitation */ }
    }
}
