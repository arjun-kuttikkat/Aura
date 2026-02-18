package com.aura.app.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val REWARDS = "rewards"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val CREATE_LISTING = "create_listing"
    const val LISTING_DETAIL = "listing_detail/{listingId}"
    const val MEET_SESSION = "meet_session"
    const val VERIFY_ITEM = "verify_item"
    const val ESCROW_PAY = "escrow_pay"
    const val TRADE_COMPLETE = "trade_complete"

    fun listingDetail(listingId: String) = "listing_detail/$listingId"
}
