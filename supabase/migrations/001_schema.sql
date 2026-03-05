-- ══════════════════════════════════════════════════════════════════════════════
-- Aura Production Database Schema — Consolidated Migration
-- Run against your Supabase project via SQL Editor or `supabase db push`
-- ══════════════════════════════════════════════════════════════════════════════

-- ┌─────────────────────────────────────────────────────────────────────────────
-- │ Helper: extract wallet address from Supabase JWT (app_metadata or raw_user_meta)
-- │ Aura uses Solana wallet auth, so wallet_address is set in the JWT claims.
-- │ Falls back to raw_user_meta_data for wallet connect flows.
-- └─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.requesting_wallet()
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(
    auth.jwt() ->> 'wallet_address',
    auth.jwt() -> 'app_metadata' ->> 'wallet_address',
    auth.jwt() -> 'user_metadata' ->> 'wallet_address'
  )
$$;

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. PROFILES
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS profiles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    wallet_address TEXT UNIQUE NOT NULL,
    aura_score INTEGER DEFAULT 50,
    streak_days INTEGER DEFAULT 0,
    last_scan_at TIMESTAMPTZ,
    total_trades INTEGER DEFAULT 0,
    apex_zones TEXT[] DEFAULT '{}',
    directives_completed INTEGER DEFAULT 0,
    nft_mint TEXT,
    face_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_wallet ON profiles(wallet_address);

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

-- Anyone can read profiles (marketplace needs seller info)
CREATE POLICY "profiles_select_public" ON profiles
    FOR SELECT USING (true);

-- Users can only insert their own profile
CREATE POLICY "profiles_insert_own" ON profiles
    FOR INSERT WITH CHECK (
        wallet_address = public.requesting_wallet()
    );

-- Users can only update their own profile
CREATE POLICY "profiles_update_own" ON profiles
    FOR UPDATE USING (
        wallet_address = public.requesting_wallet()
    );

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. LISTINGS
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS listings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    seller_wallet TEXT NOT NULL REFERENCES profiles(wallet_address),
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    price_lamports BIGINT NOT NULL,
    images TEXT[] DEFAULT '{}',
    condition TEXT DEFAULT 'Good',
    minted_status TEXT DEFAULT 'PENDING'
        CHECK (minted_status IN ('PENDING', 'MINTED', 'VERIFIED', 'SOLD')),
    mint_address TEXT,
    fingerprint_hash TEXT,
    category TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    sold_at TIMESTAMPTZ,
    buyer_wallet TEXT,
    emirate TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_listings_seller ON listings(seller_wallet);
CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(minted_status);
CREATE INDEX IF NOT EXISTS idx_listings_created ON listings(created_at DESC);

ALTER TABLE listings ENABLE ROW LEVEL SECURITY;

-- Anyone can browse listings
CREATE POLICY "listings_select_public" ON listings
    FOR SELECT USING (true);

-- Only the seller (matched by JWT wallet) can create listings
CREATE POLICY "listings_insert_seller" ON listings
    FOR INSERT WITH CHECK (
        seller_wallet = public.requesting_wallet()
    );

-- Only the seller can update their own listings
CREATE POLICY "listings_update_seller" ON listings
    FOR UPDATE USING (
        seller_wallet = public.requesting_wallet()
    );

-- Service role (Edge Functions) can update any listing (for mint_address, minted_status)
-- This is handled automatically since service_role bypasses RLS.

-- ══════════════════════════════════════════════════════════════════════════════
-- 3. TRADE SESSIONS
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS trade_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    buyer_wallet TEXT NOT NULL,
    seller_wallet TEXT NOT NULL,
    state TEXT DEFAULT 'SESSION_CREATED'
        CHECK (state IN (
            'SESSION_CREATED', 'HANDSHAKE_DONE', 'VERIFIED_PASS', 'VERIFIED_FAIL',
            'ESCROW_FUNDED', 'ESCROW_LOCKED', 'TRADE_COMPLETE', 'CANCELLED'
        )),
    escrow_tx_sig TEXT,
    nfc_sun_url TEXT,
    aura_tokens_awarded BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sessions_listing ON trade_sessions(listing_id);
CREATE INDEX IF NOT EXISTS idx_sessions_state ON trade_sessions(state);
CREATE INDEX IF NOT EXISTS idx_sessions_buyer ON trade_sessions(buyer_wallet);
CREATE INDEX IF NOT EXISTS idx_sessions_seller ON trade_sessions(seller_wallet);

ALTER TABLE trade_sessions ENABLE ROW LEVEL SECURITY;

-- Only trade participants can see their sessions
CREATE POLICY "sessions_select_participants" ON trade_sessions
    FOR SELECT USING (
        buyer_wallet = public.requesting_wallet()
        OR seller_wallet = public.requesting_wallet()
    );

-- Authenticated users can create sessions (buyer creates)
CREATE POLICY "sessions_insert_buyer" ON trade_sessions
    FOR INSERT WITH CHECK (
        buyer_wallet = public.requesting_wallet()
    );

-- Only participants can update session state
CREATE POLICY "sessions_update_participants" ON trade_sessions
    FOR UPDATE USING (
        buyer_wallet = public.requesting_wallet()
        OR seller_wallet = public.requesting_wallet()
    );

-- ══════════════════════════════════════════════════════════════════════════════
-- 4. AURA HISTORY (Engagement Ledger)
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS aura_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES profiles(id),
    change_amount INTEGER NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_history_user ON aura_history(user_id);

ALTER TABLE aura_history ENABLE ROW LEVEL SECURITY;

-- Users can read their own aura history
CREATE POLICY "history_select_own" ON aura_history
    FOR SELECT USING (
        user_id IN (
            SELECT id FROM profiles WHERE wallet_address = public.requesting_wallet()
        )
    );

-- Only system (service_role) inserts history — bypasses RLS automatically
CREATE POLICY "history_insert_service" ON aura_history
    FOR INSERT WITH CHECK (true);

-- ══════════════════════════════════════════════════════════════════════════════
-- 5. CHAT MESSAGES
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    sender_wallet TEXT NOT NULL,
    receiver_wallet TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_listing ON chat_messages(listing_id);
CREATE INDEX IF NOT EXISTS idx_chat_sender ON chat_messages(sender_wallet);
CREATE INDEX IF NOT EXISTS idx_chat_receiver ON chat_messages(receiver_wallet);
CREATE INDEX IF NOT EXISTS idx_chat_created ON chat_messages(created_at DESC);

ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;

-- Only sender/receiver can read their messages
CREATE POLICY "chat_select_participants" ON chat_messages
    FOR SELECT USING (
        sender_wallet = public.requesting_wallet()
        OR receiver_wallet = public.requesting_wallet()
    );

-- Sender can insert messages
CREATE POLICY "chat_insert_sender" ON chat_messages
    FOR INSERT WITH CHECK (
        sender_wallet = public.requesting_wallet()
    );

-- ══════════════════════════════════════════════════════════════════════════════
-- 6. REALTIME — Enable for trade_sessions (used by observeTradeSession)
-- ══════════════════════════════════════════════════════════════════════════════
ALTER PUBLICATION supabase_realtime ADD TABLE trade_sessions;

-- ══════════════════════════════════════════════════════════════════════════════
-- 7. UPDATED_AT TRIGGER — Auto-set updated_at on modification
-- ══════════════════════════════════════════════════════════════════════════════
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profiles_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER sessions_updated_at
    BEFORE UPDATE ON trade_sessions
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
