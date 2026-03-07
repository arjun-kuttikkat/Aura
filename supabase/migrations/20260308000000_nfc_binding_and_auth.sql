-- Migration: NFC tag binding + wallet-auth nonce table
-- Phase 0.2: Bind NFC tags to listings for trusted escrow release
-- Phase 0.3: Auth nonces table for wallet-based authentication

-- ═══════════════════════════════════════════════════════════════
-- 1. Add NFC tag UID column to marketplace_listings
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE marketplace_listings
    ADD COLUMN IF NOT EXISTS nfc_tag_uid TEXT;

-- Unique constraint: one tag per listing binding prevents reuse
CREATE UNIQUE INDEX IF NOT EXISTS idx_marketplace_listings_nfc_tag_uid
    ON marketplace_listings (nfc_tag_uid)
    WHERE nfc_tag_uid IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════
-- 2. Auth nonces table for wallet signature verification
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS auth_nonces (
    wallet_address TEXT PRIMARY KEY REFERENCES profiles(wallet_address) ON DELETE CASCADE,
    nonce          TEXT NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '5 minutes'),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Auto-cleanup expired nonces (optional — Edge Function handles this too)
CREATE INDEX IF NOT EXISTS idx_auth_nonces_expires
    ON auth_nonces (expires_at);

-- RLS: auth_nonces should only be accessible by service role (Edge Functions)
ALTER TABLE auth_nonces ENABLE ROW LEVEL SECURITY;
-- No anon/authenticated policies = only service_role can access

-- ═══════════════════════════════════════════════════════════════
-- 3. Add aura_tokens_awarded column to trade_sessions for double-mint prevention
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE trade_sessions
    ADD COLUMN IF NOT EXISTS aura_tokens_awarded BIGINT DEFAULT 0;
