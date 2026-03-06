-- ══════════════════════════════════════════════════════════════════════════════
-- marketplace_listings: Clean table for marketplace — all required columns
-- No data migration. Drops old listings and recreates with correct schema.
-- Self-contained: creates requesting_wallet() if not present (so migration works standalone).
-- ══════════════════════════════════════════════════════════════════════════════

-- 0. Ensure requesting_wallet() exists (required by trade_sessions/chat_messages policies)
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

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 1. Drop dependent tables (no data carry-over)
DROP TABLE IF EXISTS trade_sessions CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS listings CASCADE;

-- 2. Create marketplace_listings — single source of truth for marketplace
CREATE TABLE marketplace_listings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    seller_wallet TEXT NOT NULL REFERENCES profiles(wallet_address),
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    price_lamports BIGINT NOT NULL DEFAULT 0,
    images TEXT[] DEFAULT '{}',
    condition TEXT DEFAULT 'Good',
    minted_status TEXT DEFAULT 'PENDING'
        CHECK (minted_status IN ('PENDING', 'MINTED', 'VERIFIED', 'SOLD')),
    mint_address TEXT,
    fingerprint_hash TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    location TEXT,
    emirate TEXT,
    sold_at TIMESTAMPTZ,
    buyer_wallet TEXT,
    seller_aura_score INT DEFAULT 50,
    is_active BOOLEAN DEFAULT true,
    is_published BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_marketplace_listings_seller ON marketplace_listings(seller_wallet);
CREATE INDEX idx_marketplace_listings_status ON marketplace_listings(minted_status);
CREATE INDEX idx_marketplace_listings_created ON marketplace_listings(created_at DESC);
CREATE INDEX idx_marketplace_listings_published ON marketplace_listings(is_published) WHERE is_published = true;
CREATE INDEX idx_marketplace_listings_active ON marketplace_listings(is_active) WHERE is_active = true;
CREATE INDEX idx_marketplace_listings_location ON marketplace_listings(latitude, longitude) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

ALTER TABLE marketplace_listings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "marketplace_listings_select_public" ON marketplace_listings
    FOR SELECT USING (true);

-- Insert/update open for anon key (hackathon / MVP mode — no dependency on requesting_wallet())
CREATE POLICY "marketplace_listings_insert_seller" ON marketplace_listings
    FOR INSERT WITH CHECK (true);

CREATE POLICY "marketplace_listings_update_seller" ON marketplace_listings
    FOR UPDATE USING (true) WITH CHECK (true);

-- 3. Recreate trade_sessions referencing marketplace_listings
CREATE TABLE trade_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
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

CREATE INDEX idx_sessions_listing ON trade_sessions(listing_id);
CREATE INDEX idx_sessions_state ON trade_sessions(state);
CREATE INDEX idx_sessions_buyer ON trade_sessions(buyer_wallet);
CREATE INDEX idx_sessions_seller ON trade_sessions(seller_wallet);

ALTER TABLE trade_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "sessions_select_participants" ON trade_sessions
    FOR SELECT USING (true);

CREATE POLICY "sessions_insert_buyer" ON trade_sessions
    FOR INSERT WITH CHECK (true);

CREATE POLICY "sessions_update_participants" ON trade_sessions
    FOR UPDATE USING (true) WITH CHECK (true);

CREATE TRIGGER sessions_updated_at
    BEFORE UPDATE ON trade_sessions
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- 4. Recreate chat_messages referencing marketplace_listings
CREATE TABLE chat_messages (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
    sender_wallet TEXT NOT NULL,
    receiver_wallet TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_chat_listing ON chat_messages(listing_id);
CREATE INDEX idx_chat_sender ON chat_messages(sender_wallet);
CREATE INDEX idx_chat_receiver ON chat_messages(receiver_wallet);
CREATE INDEX idx_chat_created ON chat_messages(created_at DESC);

ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "chat_select_participants" ON chat_messages
    FOR SELECT USING (true);

CREATE POLICY "chat_insert_sender" ON chat_messages
    FOR INSERT WITH CHECK (true);

-- 5. Realtime for trade_sessions
ALTER PUBLICATION supabase_realtime ADD TABLE trade_sessions;
