package com.aura.app.model

data class EscrowStatus(
    val txSig: String?,
    val state: EscrowState,
    val amount: Long,
)

enum class EscrowState {
    PENDING,
    LOCKED,
    RELEASED,
    FAILED,
}
