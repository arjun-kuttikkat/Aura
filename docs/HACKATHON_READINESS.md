# Aura — Hackathon Readiness & Rough Edges

**TL;DR:** App is **demo-ready for “core” flows** (wallet, listings, profiles, trade sessions, meet session). **No Edge Functions are deployed** — Aura Check, Verify Item, mint-NFT, and NFC escrow release will fail until you deploy. Plan assumes current DB schema (listings, profiles, trade_sessions); chat needs `chat_messages` table.

---

## Current state

- **DB (Supabase):** `listings`, `profiles`, `trade_sessions` as per schema below. **No Edge Functions deployed** (`verify-photo`, `mint-nft`, `verify-sun` exist in repo but are not live).
- **Schema summary:**
  - `listings`: id, seller_wallet (FK → profiles), title, price_lamports, images (array), description, condition, minted_status, mint_address, fingerprint_hash, latitude/longitude, sold_at, buyer_wallet, emirate, seller_aura_score, created_at (bigint), etc.
  - `profiles`: id, wallet_address (unique), aura_score, streak_days, last_scan_at, created_at, updated_at.
  - `trade_sessions`: id, listing_id (FK → listings), buyer_wallet, seller_wallet (FK → profiles), state, created_at, last_updated (both bigint in schema).
- **Chat:** App expects a `chat_messages` table; if it doesn’t exist, chat will fail.

---

## What works vs what breaks (no Edge Functions)

| Works | Breaks / needs functions |
|-------|---------------------------|
| Wallet connect (MWA) | Aura Check (needs `verify-photo`) |
| Listings CRUD, browse, create with images | Verify Item in trade (needs `verify-photo`) |
| Profiles, Aura Score (local + DB) | Mint listing / NFT (needs `mint-nft`) |
| Trade sessions create + Realtime | NFC escrow release (needs `verify-sun`) |
| Meet session (NFC/QR handshake UI) | — |
| Escrow pay (non-NFC path uses Anchor + MWA only; depends on program + RPC) | — |
| Chat (if `chat_messages` exists) | — |

---

## Can it win?

**Yes, with a focused demo:**

- **Without deploying functions:** Demo wallet → create listing → browse → start trade → meet session. Skip or stub Aura Check, Verify Item, and NFC release; optionally demo non-NFC escrow if Anchor program is live.
- **If you deploy at least `verify-photo`:** Add Aura Check + Verify Item to the story.
- **Avoid** showing Favorites, Zone Refinement “radar”, or Settings sub-pages until they’re implemented.

**To increase chances:** Fix the “Blockers” below and add at least one user-visible error message (e.g. create listing / escrow fail).

---

## Blockers (fix before demo)

| Item | Where | What’s wrong |
|------|--------|----------------|
| **Settings sub-routes do nothing** | `SettingsScreen` → Notifications / Security / Privacy | Routes exist in `Routes.kt` but **no composables** in `NavGraph.kt` and **no handlers** passed from `NavGraph` to `SettingsScreen`. Tapping those rows does nothing. |
| **Favorites are empty** | `FavoritesScreen` | `favoriteListings = emptyList<Any>()` — “Phase 2” only. Either implement (e.g. Supabase `favorites` table + heart on listing detail) or hide the Favorites tab for the demo. |
| **Duplicate migrations** | `supabase/migrations/` | `20260305120000_storage_listing_images 2.sql` and `20260305200000_add_listings_description 2.sql` are duplicates; remove or rename to avoid confusion. |

---

## Still “mock” or placeholder

| Area | What’s mock | Notes |
|------|-------------|--------|
| **WalletService** | `WalletService.kt` | Placeholder connect/sign/signAndSend. **Not used** — app uses `WalletConnectionState` (real MWA). Safe to delete or leave; no impact on demo. |
| **Zone Refinement “radar”** | `ZoneRefinementScreen` | Comment: “Radar/Map Mock” — static circles/dots, no real map or zone data. Either add a simple map or present as “coming soon”. |
| **SpatialSweeper** | `SpatialSweeper.kt` | Comment: “MVP Mock of the Spatial Sweeper using ML Kit” — actually uses ML Kit for labels; “mock” here means simplified logic, not fake. Fine for demo. |
| **MockBackend** | `MockBackend.kt` | Unused (app uses `AuraRepository` + Supabase). Can remove to avoid confusion; `.agent/rules.md` updated accordingly. |

---

## Rough edges (polish for a stronger impression)

- **Error handling / feedback**
  - Many `try/catch` blocks only log; user sees nothing. Add Snackbar or inline error text for: listing create fail, escrow init/sign fail, Aura Check / verify-photo failure.
  - `FaceVerificationScreen`: clarify what “verification” does (e.g. “Verified for Aura Score”) and show success state briefly before popping.
- **Empty states**
  - Home: if `listings.isEmpty()` after refresh, show “No listings yet” + CTA to create one (and ensure pull-to-refresh is obvious).
  - Chats: empty state is fine; ensure “Start a trade from a listing” is clear when there are no conversations.
- **Navigation / backstack**
  - After “Create listing” you do `navigate(HOME) { popUpTo(HOME) { inclusive = true } }` — clears back stack. Confirm this doesn’t feel jarring (e.g. user expected to stay in “Place ad” flow).
  - Trade complete → “Done” goes to onboarding route with `popUpTo(0) { inclusive = true }`; confirm you intend full reset (no back to trade).
- **Listing not found**
  - `ListingDetailScreen`: if `AuraRepository.getListing(id)` is null (e.g. stale id or not in current list), you show “Listing not found” + “Go Back”. Good; consider refreshing listings once on detail load so a newly created listing is found.
- **WalletScreen**
  - `WalletScreen.kt` exists but is **not in NavGraph**; dead code. Remove or hook up if you want a dedicated wallet screen.
- **RewardsScreen**
  - Has UI (rewards/streak); ensure data comes from `AuraRepository.currentProfile` / aura_history so it’s not empty for a new user.
- **Duplicate / redundant**
  - `MeetSessionScreen`: duplicate imports (`AnimatedContent`, `fadeIn`, `clip`).
  - `RewardsScreen`: duplicate `import androidx.compose.ui.unit.dp`.
- **Theme**
  - Single dark theme only; sufficient for hackathon. Typography/contrast look consistent.

---

## Schema vs app (watch for)

- **listings.created_at:** DB is `bigint`; app may use ISO strings in places — ensure inserts/selects match schema.
- **listings.seller_wallet:** FK to `profiles(wallet_address)`. App already calls `loadProfile(sellerWallet)` before create — good.
- **trade_sessions:** Schema has `created_at bigint`, `last_updated bigint`. App’s `TradeSessionRow` uses string `created_at`/`updated_at` — verify inserts/updates don’t break.
- **chat_messages:** Not in the schema you shared. If missing, add table or chat will fail.

---

## What’s in good shape

- **Wallet:** Real MWA in `WalletConnectionState`; onboarding connect works.
- **Data (no functions needed):** Listings, profiles, trade sessions via Supabase; listing create with image upload to Storage; Realtime for trade sessions.
- **Flows that work now:** Create listing, listing detail, start trade, meet session (NFC/QR). Escrow pay (non-NFC) works only if Anchor program + RPC are live.
- **Mirror Ritual / Aura Score:** Profile load and score updates in DB.
- **Chat:** Wired in app; needs `chat_messages` table in DB.
- **Edge Functions (in repo, not deployed):** `verify-photo`, `mint-nft`, `verify-sun` — deploy when you want Aura Check, Verify Item, mint, NFC release.

---

## Pre-demo checklist

1. **DB:** Confirm `chat_messages` exists if you show chat; fix duplicate migrations (`* 2.sql`).
2. **No functions path:** Test flow: wallet → create listing → browse → start trade → meet session. Don’t demo Aura Check / Verify Item / NFC release (or show “coming soon”).
3. **Optional (stronger demo):** Deploy at least `verify-photo` so Aura Check + Verify Item work.
4. Install Phantom or Solflare (MWA) on device/emulator; test once.
5. Either implement Favorites or hide Favorites tab for demo.
6. Wire Settings sub-pages (placeholders) or remove those rows for demo.
7. Add at least one user-visible error (e.g. “Could not create listing” / “Transaction failed”).
8. Optional: Remove duplicate migrations; remove dead `WalletScreen` / `MockBackend` if you want a cleaner codebase.

---

*Generated from codebase review. Update this doc as you fix items.*
