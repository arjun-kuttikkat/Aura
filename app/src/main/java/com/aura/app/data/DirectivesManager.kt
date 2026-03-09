package com.aura.app.data

import android.util.Log
import com.aura.app.model.Directive
import com.aura.app.model.DirectiveType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Generates and manages hardware-bound daily Directives.
 *
 * Every 24 hours, 1-3 localized missions are generated. Completing them
 * feeds the Aura Core and extends the streak. Ignoring them causes Core
 * degradation and Gravity drop.
 */
object DirectivesManager {
    private const val TAG = "DirectivesManager"

    private val _activeDirectives = MutableStateFlow<List<Directive>>(emptyList())
    val activeDirectives: StateFlow<List<Directive>> = _activeDirectives.asStateFlow()

    private val _completedToday = MutableStateFlow(0)
    val completedToday: StateFlow<Int> = _completedToday.asStateFlow()

    /**
     * Generate daily directives based on user context.
     * @param hotzoneId Optional linked zone for localized missions
     * @param hotzoneLabel Human label for the zone
     */
    fun generateDailyDirectives(hotzoneId: String? = null, hotzoneLabel: String? = null) {
        val now = System.currentTimeMillis()
        val expiresIn24h = now + (24 * 60 * 60 * 1000L)

        val directives = mutableListOf<Directive>()

        // Always generate a Spatial Sweep
        directives.add(
            Directive(
                id = UUID.randomUUID().toString(),
                type = DirectiveType.SPATIAL_SWEEP,
                title = "Spatial Sweep",
                description = if (hotzoneLabel != null)
                    "Verify the safety of $hotzoneLabel. Walk to the zone and complete a 5-second panoramic camera scan."
                else
                    "Scan your nearest Hotzone to verify environmental safety and earn Aura points.",
                rewardAura = 15,
                expiresAt = expiresIn24h,
                isCompleted = false,
                hotzoneId = hotzoneId,
                hotzoneLabel = hotzoneLabel,
            )
        )

        // 60% chance of Guardian Witness
        if (Math.random() < 0.6) {
            directives.add(
                Directive(
                    id = UUID.randomUUID().toString(),
                    type = DirectiveType.GUARDIAN_WITNESS,
                    title = "Guardian Witness",
                    description = "A nearby trade needs consensus. Tap your phone via NFC to provide cryptographic attestation as a neutral witness.",
                    rewardAura = 25,
                    expiresAt = expiresIn24h,
                    isCompleted = false,
                    hotzoneId = hotzoneId,
                    hotzoneLabel = hotzoneLabel,
                )
            )
        }

        // 40% chance of Texture Archive
        if (Math.random() < 0.4) {
            directives.add(
                Directive(
                    id = UUID.randomUUID().toString(),
                    type = DirectiveType.TEXTURE_ARCHIVE,
                    title = "Texture Archive",
                    description = "Capture a high-fidelity macro scan of an item you're selling. The hardware AI will hash the texture to verify physical possession.",
                    rewardAura = 20,
                    expiresAt = expiresIn24h,
                    isCompleted = false,
                    hotzoneId = hotzoneId,
                    hotzoneLabel = hotzoneLabel,
                )
            )
        }

        _activeDirectives.value = directives
        _completedToday.value = 0
        Log.d(TAG, "Generated ${directives.size} daily directives")
    }

    /**
     * Mark a directive as completed. Returns the Aura reward earned.
     */
    fun completeDirective(directiveId: String): Int {
        val current = _activeDirectives.value.toMutableList()
        val index = current.indexOfFirst { it.id == directiveId }
        if (index == -1) return 0

        val directive = current[index]
        if (directive.isCompleted) return 0

        current[index] = directive.copy(isCompleted = true)
        _activeDirectives.value = current
        _completedToday.value = _completedToday.value + 1

        AuraRepository.addAuraToProfile(directive.rewardAura, "Directive: ${directive.title}")
        Log.d(TAG, "Directive completed: ${directive.title}, reward: ${directive.rewardAura}")
        return directive.rewardAura
    }

    /** Check if any active directives have expired without completion. */
    fun hasExpiredUncompleted(): Boolean {
        val now = System.currentTimeMillis()
        return _activeDirectives.value.any { !it.isCompleted && it.expiresAt < now }
    }

    /** Get count of pending (not yet completed, not expired) directives. */
    fun pendingCount(): Int {
        val now = System.currentTimeMillis()
        return _activeDirectives.value.count { !it.isCompleted && it.expiresAt > now }
    }
}
