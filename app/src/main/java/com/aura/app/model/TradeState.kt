package com.aura.app.model

enum class TradeState {
    IDLE,
    SESSION_CREATED,
    BOTH_PRESENT,
    VERIFIED_PASS,
    VERIFIED_FAIL,
    PAYMENT_PENDING,
    ESCROW_LOCKED,
    RELEASE_PENDING,
    COMPLETE,
    CANCELLED,
    DISPUTE,
}
