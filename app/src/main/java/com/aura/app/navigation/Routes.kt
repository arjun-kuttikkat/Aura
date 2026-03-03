package com.aura.app.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val REWARDS = "rewards"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SETTINGS_NOTIFICATIONS = "settings_notifications"
    const val SETTINGS_APPEARANCE = "settings_appearance"
    const val SETTINGS_SECURITY = "settings_security"
    const val SETTINGS_PRIVACY = "settings_privacy"
    const val CREATE_LISTING = "create_listing"
    const val LISTING_DETAIL = "listing_detail/{listingId}"
    const val MEET_SESSION = "meet_session"
    const val VERIFY_ITEM = "verify_item"
    const val ESCROW_PAY = "escrow_pay"

    const val TRADE_COMPLETE = "trade_complete"
    const val FACE_VERIFICATION = "face_verification"
    const val AURA_CHECK = "aura_check"
    const val P2P_EXCHANGE = "p2p_exchange"
    const val ZONE_REFINEMENT = "zone_refinement"
    const val DIRECTIVES = "directives"
    const val SECURITY = "security"
    const val PRIVACY = "privacy"

    fun listingDetail(listingId: String) = "listing_detail/$listingId"
}
