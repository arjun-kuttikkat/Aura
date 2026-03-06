package com.aura.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first

private val Context.officialSeederStore by preferencesDataStore("official_seeder")
private val SEEDED_KEY = booleanPreferencesKey("listings_seeded_v2")

/**
 * Seeds 10 official Aura listings for the UAE marketplace distributed across all 7 emirates.
 * Run once on first app launch. Uses real Supabase Storage image URLs.
 */
object OfficialListingSeeder {

    private const val TAG = "OfficialListingSeeder"
    const val AURA_OFFICIAL_WALLET = AiChatResponder.AURA_OFFICIAL_WALLET

    // High-quality placeholder images hosted on Unsplash (public CDN, no auth needed)
    private val SEED_LISTINGS = listOf(
        SeedListing(
            title = "Sony WH-1000XM5 Headphones",
            description = "Premium noise-cancelling headphones. Barely used, original box included. Perfect condition.",
            priceSol = 0.8,
            condition = "Like New",
            category = "Electronics",
            emirate = "Dubai",
            imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&q=80",
            lat = 25.2048, lng = 55.2708
        ),
        SeedListing(
            title = "Nike Air Jordan 1 Retro - Size 42",
            description = "Bred colourway. Never worn, deadstock with original receipt. Authenticated.",
            priceSol = 1.2,
            condition = "New",
            category = "Fashion",
            emirate = "Dubai",
            imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80",
            lat = 25.1972, lng = 55.2744
        ),
        SeedListing(
            title = "MacBook Pro M2 16-inch",
            description = "Space Grey, 16GB RAM, 512GB SSD. AppleCare until 2026. Comes with MagSafe charger.",
            priceSol = 4.5,
            condition = "Good",
            category = "Electronics",
            emirate = "Dubai",
            imageUrl = "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&q=80",
            lat = 25.2077, lng = 55.2668
        ),
        SeedListing(
            title = "Rolex Submariner Date",
            description = "2022 model, full set with papers and box. Green dial and ceramic bezel. Stunning condition.",
            priceSol = 18.0,
            condition = "Like New",
            category = "Collectibles",
            emirate = "Abu Dhabi",
            imageUrl = "https://images.unsplash.com/photo-1587836374828-4dbafa94cf0e?w=600&q=80",
            lat = 24.4539, lng = 54.3773
        ),
        SeedListing(
            title = "Dyson V15 Detect Cordless",
            description = "Next-gen laser dust detection. Used 3 months, all attachments included. Charges perfectly.",
            priceSol = 0.6,
            condition = "Good",
            category = "Home",
            emirate = "Abu Dhabi",
            imageUrl = "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600&q=80",
            lat = 24.4625, lng = 54.3822
        ),
        SeedListing(
            title = "Canon EOS R6 Mark II + 24-70mm",
            description = "Full-frame mirrorless kit. Under 10k shutter clicks. Comes with 2 batteries and bag.",
            priceSol = 3.8,
            condition = "Good",
            category = "Electronics",
            emirate = "Sharjah",
            imageUrl = "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=600&q=80",
            lat = 25.3462, lng = 55.4209
        ),
        SeedListing(
            title = "Lego Technic Bugatti Chiron 42151",
            description = "Rare limited set. Sealed in box, collector's piece. Verified authentic Lego.",
            priceSol = 0.35,
            condition = "New",
            category = "Collectibles",
            emirate = "Ajman",
            imageUrl = "https://images.unsplash.com/photo-1608889825205-eebdb9fc5806?w=600&q=80",
            lat = 25.4111, lng = 55.4354
        ),
        SeedListing(
            title = "Herman Miller Aeron Chair (Size B)",
            description = "Remastered edition, fully adjustable. Posture-fit SL lumbar. Purchased from UAE store.",
            priceSol = 1.1,
            condition = "Like New",
            category = "Home",
            emirate = "Ras Al Khaimah",
            imageUrl = "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600&q=80",
            lat = 25.7895, lng = 55.9432
        ),
        SeedListing(
            title = "PS5 Console + 2 Controllers",
            description = "Disc edition, includes God of War Ragnarok. Mint condition, barely 50 hours on it.",
            priceSol = 1.4,
            condition = "Good",
            category = "Electronics",
            emirate = "Fujairah",
            imageUrl = "https://images.unsplash.com/photo-1606813907291-d86efa9b94db?w=600&q=80",
            lat = 25.1288, lng = 56.3265
        ),
        SeedListing(
            title = "Trek Marlin 7 Mountain Bike",
            description = "29\" wheels, Shimano Deore 1x12 drivetrain. Serviced last month. Barely ridden.",
            priceSol = 0.9,
            condition = "Good",
            category = "Sports",
            emirate = "Umm Al Quwain",
            imageUrl = "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600&q=80",
            lat = 25.5641, lng = 55.7108
        ),
    )

    data class SeedListing(
        val title: String,
        val description: String,
        val priceSol: Double,
        val condition: String,
        val category: String,
        val emirate: String,
        val imageUrl: String,
        val lat: Double,
        val lng: Double
    )

    suspend fun clearSeededFlag(context: Context) {
        context.officialSeederStore.edit { it[SEEDED_KEY] = false }
    }

    suspend fun seedIfNeeded(context: Context) {
        try {
            val prefs = context.officialSeederStore.data.first()
            if (prefs[SEEDED_KEY] == true) return

            Log.d(TAG, "Seeding 10 official UAE listings...")
            val db = SupabaseClient.client

            // 1) Ensure the official seller profile exists to prevent foreign key errors
            try {
                // Ignore conflicts or just try insert
                val profileRow = mapOf(
                    "wallet_address" to AURA_OFFICIAL_WALLET,
                    "aura_score" to 100,
                    "streak_days" to 365
                )
                // UPSERT the profile so it exists
                db.from("profiles").upsert(profileRow) {
                    onConflict = "wallet_address"
                }
                Log.d(TAG, "Official profile verified.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upsert official profile: ${e.message}")
            }

            // 2) Seed listings
            SEED_LISTINGS.forEach { seed ->
                try {
                    val row = mapOf(
                        "id" to java.util.UUID.randomUUID().toString(),
                        "seller_wallet" to AURA_OFFICIAL_WALLET,
                        "title" to seed.title,
                        "description" to seed.description,
                        "price_lamports" to (seed.priceSol * 1_000_000_000).toLong(),
                        "images" to listOf(seed.imageUrl),
                        "condition" to seed.condition,
                        "minted_status" to "VERIFIED",
                        "fingerprint_hash" to "aura_official_${seed.emirate.lowercase().replace(" ", "_")}",
                        "latitude" to seed.lat,
                        "longitude" to seed.lng,
                        "location" to "${seed.emirate}",
                        "emirate" to seed.emirate,
                        "seller_aura_score" to 95,
                        "is_active" to true,
                        "is_published" to true
                    )
                    db.from("marketplace_listings").insert(row)
                    Log.d(TAG, "Seeded: ${seed.title} in ${seed.emirate}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to seed ${seed.title}: ${e.message}")
                }
            }

            context.officialSeederStore.edit { it[SEEDED_KEY] = true }
            Log.d(TAG, "Seeding complete!")
        } catch (e: Exception) {
            Log.e(TAG, "Seeder error", e)
        }
    }
}
