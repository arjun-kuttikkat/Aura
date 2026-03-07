-- Migration: Restore proper wallet-scoped Row Level Security
-- Phase 0.4: Replaces the relaxed WITH CHECK (true) hackathon policies
-- Requires wallet-auth JWT with wallet_address in claims

-- ═══════════════════════════════════════════════════════════════
-- Helper: Extract wallet_address from JWT claims
-- (Already exists from 001_schema.sql, recreate if missing)
-- ═══════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION requesting_wallet() RETURNS TEXT AS $$
    SELECT COALESCE(
        current_setting('request.jwt.claims', true)::json->>'wallet_address',
        current_setting('request.jwt.claims', true)::json->'app_metadata'->>'wallet_address'
    );
$$ LANGUAGE sql STABLE;

-- ═══════════════════════════════════════════════════════════════
-- 1. PROFILES — wallet-scoped policies
-- ═══════════════════════════════════════════════════════════════

-- Drop relaxed policies
DROP POLICY IF EXISTS "profiles_insert_own" ON profiles;
DROP POLICY IF EXISTS "profiles_update_own" ON profiles;
DROP POLICY IF EXISTS "profiles_select_all" ON profiles;
DROP POLICY IF EXISTS "anon_profiles_select_all" ON profiles;
DROP POLICY IF EXISTS "anon_profiles_insert_own" ON profiles;
DROP POLICY IF EXISTS "anon_profiles_update_own" ON profiles;

-- Public read for marketplace browsing (seller aura scores, etc.)
CREATE POLICY "profiles_select_public"
    ON profiles FOR SELECT
    USING (true);

-- Insert: only your own wallet address
CREATE POLICY "profiles_insert_own"
    ON profiles FOR INSERT
    WITH CHECK (wallet_address = requesting_wallet());

-- Update: only your own profile
CREATE POLICY "profiles_update_own"
    ON profiles FOR UPDATE
    USING (wallet_address = requesting_wallet())
    WITH CHECK (wallet_address = requesting_wallet());

-- ═══════════════════════════════════════════════════════════════
-- 2. MARKETPLACE_LISTINGS — seller-scoped writes, public reads
-- ═══════════════════════════════════════════════════════════════

DROP POLICY IF EXISTS "listings_select_all" ON marketplace_listings;
DROP POLICY IF EXISTS "listings_insert_own" ON marketplace_listings;
DROP POLICY IF EXISTS "listings_update_own" ON marketplace_listings;
DROP POLICY IF EXISTS "anon_listings_select_all" ON marketplace_listings;
DROP POLICY IF EXISTS "anon_listings_insert_own" ON marketplace_listings;
DROP POLICY IF EXISTS "anon_listings_update_own" ON marketplace_listings;

-- Public read: browsing the marketplace
CREATE POLICY "listings_select_public"
    ON marketplace_listings FOR SELECT
    USING (true);

-- Insert: only if you're the seller
CREATE POLICY "listings_insert_own"
    ON marketplace_listings FOR INSERT
    WITH CHECK (seller_wallet = requesting_wallet());

-- Update: only your own listings
CREATE POLICY "listings_update_own"
    ON marketplace_listings FOR UPDATE
    USING (seller_wallet = requesting_wallet())
    WITH CHECK (seller_wallet = requesting_wallet());

-- ═══════════════════════════════════════════════════════════════
-- 3. TRADE_SESSIONS — participant-scoped
-- ═══════════════════════════════════════════════════════════════

DROP POLICY IF EXISTS "sessions_select_all" ON trade_sessions;
DROP POLICY IF EXISTS "sessions_insert_all" ON trade_sessions;
DROP POLICY IF EXISTS "sessions_update_all" ON trade_sessions;
DROP POLICY IF EXISTS "anon_sessions_select" ON trade_sessions;
DROP POLICY IF EXISTS "anon_sessions_insert" ON trade_sessions;
DROP POLICY IF EXISTS "anon_sessions_update" ON trade_sessions;

-- Select: only if you're the buyer or seller
CREATE POLICY "sessions_select_participant"
    ON trade_sessions FOR SELECT
    USING (
        buyer_wallet = requesting_wallet()
        OR seller_wallet = requesting_wallet()
    );

-- Insert: only if you're the buyer (buyer initiates trade)
CREATE POLICY "sessions_insert_buyer"
    ON trade_sessions FOR INSERT
    WITH CHECK (buyer_wallet = requesting_wallet());

-- Update: only participants can update
CREATE POLICY "sessions_update_participant"
    ON trade_sessions FOR UPDATE
    USING (
        buyer_wallet = requesting_wallet()
        OR seller_wallet = requesting_wallet()
    )
    WITH CHECK (
        buyer_wallet = requesting_wallet()
        OR seller_wallet = requesting_wallet()
    );

-- ═══════════════════════════════════════════════════════════════
-- 4. CHAT_MESSAGES — sender/receiver scoped
-- ═══════════════════════════════════════════════════════════════

DROP POLICY IF EXISTS "chat_select_all" ON chat_messages;
DROP POLICY IF EXISTS "chat_insert_all" ON chat_messages;
DROP POLICY IF EXISTS "chat_update_all" ON chat_messages;
DROP POLICY IF EXISTS "anon_chat_select" ON chat_messages;
DROP POLICY IF EXISTS "anon_chat_insert" ON chat_messages;
DROP POLICY IF EXISTS "anon_chat_update" ON chat_messages;

-- Select: only if you're the sender or receiver
CREATE POLICY "chat_select_participant"
    ON chat_messages FOR SELECT
    USING (
        sender_wallet = requesting_wallet()
        OR receiver_wallet = requesting_wallet()
    );

-- Insert: only if you're the sender
CREATE POLICY "chat_insert_sender"
    ON chat_messages FOR INSERT
    WITH CHECK (sender_wallet = requesting_wallet());

-- Update: only sender can update (e.g., edit message) — or receiver for read receipts
CREATE POLICY "chat_update_participant"
    ON chat_messages FOR UPDATE
    USING (
        sender_wallet = requesting_wallet()
        OR receiver_wallet = requesting_wallet()
    );
