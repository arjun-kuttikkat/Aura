# Aura Deployment Guide

## Architecture Overview

```
┌─────────────────┐     ┌─────────────────────┐     ┌──────────────────┐
│  Android App     │────▶│  Supabase Backend    │────▶│  Solana Network  │
│  (Kotlin/Compose)│     │  (Edge Functions)    │     │  (Anchor Escrow) │
└─────────────────┘     └─────────────────────┘     └──────────────────┘
        │                       │                           │
   MWA Wallet ◀────────  PostgreSQL + RLS  ────────  Helius RPC
```

## Prerequisites

- **Android Studio** (Hedgehog+) with JDK 17
- **Supabase CLI** (`npm i -g supabase`)
- **Solana CLI** + **Anchor CLI** v0.29.0
- **Node.js** 18+ (for contract tests)
- **Phantom** or **Solflare** wallet app installed on test device

## Environment Setup

### 1. Local Properties (`local.properties`)

Copy `local.properties.example` and fill in:

```properties
HELIUS_API_KEY=your_helius_key
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your_anon_key
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=meta-llama/llama-4-scout-17b-16e-instruct
TREASURY_WALLET=your_treasury_wallet_base58
RELEASE_AUTHORITY_PUBKEY=your_release_authority_pubkey_base58
SUPABASE_JWT_SECRET=your_supabase_jwt_secret
```

### 2. GitHub Secrets (for CI/CD)

Set the same keys as repository secrets in GitHub:
- `HELIUS_API_KEY`
- `SUPABASE_URL`
- `SUPABASE_KEY`
- `GROQ_API_KEY`
- `TREASURY_WALLET`
- `RELEASE_AUTHORITY_PUBKEY`

## Supabase Deployment

### Database Migrations

Run all migrations in order:

```bash
supabase db push
```

Migration files in `supabase/migrations/`:
1. `001_schema.sql` — Base schema
2. `002_storage_bucket.sql` — Storage setup
3. `20260221000000_rls_policies.sql` — Initial RLS
4. `20260303_init_aura_system.sql` — Core tables
5. `20260305_relax_anon_rls.sql` — (deprecated, superseded by 20260308100000)
6. `20260305120000_storage_listing_images.sql` — Image storage
7. `20260305200000_add_listings_description.sql` — Descriptions
8. `20260306180000_marketplace_listings.sql` — Marketplace redesign
9. `20260307100000_communication_and_promotions.sql` — Chat + promotions
10. `20260308000000_nfc_binding_and_auth.sql` — NFC binding + auth nonces + aura_tokens_awarded
11. `20260308100000_restore_rls.sql` — Wallet-scoped RLS policies
12. `20260308200000_favorites.sql` — Favorites table
13. `20260308300000_db_hardening.sql` — Constraints + indexes

### Edge Functions

Deploy all functions:

```bash
supabase functions deploy wallet-auth
supabase functions deploy verify-sun
supabase functions deploy verify-photo
supabase functions deploy mint-nft
supabase functions deploy mint-aura-token
supabase functions deploy aura-core-nft
supabase functions deploy blinks-action
supabase functions deploy rpc-proxy
```

### Edge Function Secrets

```bash
supabase secrets set HELIUS_RPC_URL="https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
supabase secrets set HELIUS_API_KEY="YOUR_KEY"
supabase secrets set SOLANA_AUTHORITY_KEY="[...keypair_bytes...]"
supabase secrets set SUPABASE_JWT_SECRET="your_supabase_jwt_secret"
supabase secrets set AES_MASTER_KEY_HEX="your_ntag424_aes_key_hex"
supabase secrets set GROQ_API_KEY="your_groq_key"
supabase secrets set RELEASE_AUTHORITY_PUBKEY="your_authority_pubkey_base58"
supabase secrets set AURA_TOKEN_MINT="your_spl_token_mint_address"
```

## Solana Contract

### Build

```bash
cd smart_contracts/aura_escrow/programs/aura_escrow
anchor build
```

### Test

```bash
cd smart_contracts/aura_escrow
anchor test
```

### Deploy

```bash
# Devnet (testing)
anchor deploy --provider.cluster devnet

# Mainnet
anchor deploy --provider.cluster mainnet-beta
```

**Program ID:** `BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM`

## Android Build

### Debug

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### Run Tests

```bash
./gradlew testDebugUnitTest
```

## Security Checklist

Before production deployment, verify:

- [ ] `RELEASE_AUTHORITY_PUBKEY` matches the keypair stored in Supabase secrets
- [ ] `SOLANA_AUTHORITY_KEY` is a secure keypair (not shared, not committed)
- [ ] `AES_MASTER_KEY_HEX` matches the NTAG 424 DNA tags
- [ ] `SUPABASE_JWT_SECRET` matches Supabase project's JWT secret
- [ ] All Edge Functions use `SUPABASE_SERVICE_ROLE_KEY` (auto-injected)
- [ ] RLS policies applied (migration 20260308100000)
- [ ] DB constraints applied (migration 20260308300000)
- [ ] RPC proxy method whitelist in rpc-proxy/index.ts
- [ ] No API keys committed to version control
- [ ] GitHub secrets configured for CI/CD

## Auth Flow

```
Client                    Edge Function              Supabase
  │                           │                        │
  │──── GET nonce ───────────▶│                        │
  │◀─── { nonce } ───────────│── upsert auth_nonces ─▶│
  │                           │                        │
  │── MWA signMessage(nonce)──│                        │
  │                           │                        │
  │──── POST verify ─────────▶│── verify Ed25519 ─────│
  │                           │── upsert profile ─────▶│
  │◀─── { token: JWT } ──────│                        │
  │                           │                        │
  │── importAuthToken(JWT) ──▶│   (RLS now enforced)   │
```

## Trade Flow

```
1. Buyer creates trade session → SESSION_CREATED
2. Buyer locks SOL into escrow PDA → ESCROW_LOCKED
3. Both parties meet (QR + Geofence) → BOTH_PRESENT
4. AI verifies item (verify-photo) → ITEM_VERIFIED
5. NFC tap triggers verify-sun → NFC_VERIFIED + escrow released
6. NFT minted to seller → TRADE_COMPLETE
7. $AURA tokens minted to both → COMPLETED
```
