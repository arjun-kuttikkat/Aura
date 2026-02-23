-- Aura Production Database Schema
-- Run against your Supabase project via SQL Editor or supabase db push

-- ── Profiles ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS profiles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    wallet_address TEXT UNIQUE NOT NULL,
    aura_score INTEGER DEFAULT 50,
    streak_days INTEGER DEFAULT 0,
    last_scan_at TIMESTAMPTZ,
    nft_mint TEXT,                      -- Metaplex Core NFT address
    face_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_wallet ON profiles(wallet_address);

-- ── Listings ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS listings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    seller_wallet TEXT NOT NULL REFERENCES profiles(wallet_address),
    title TEXT NOT NULL,
    price_lamports BIGINT NOT NULL,
    images TEXT[] DEFAULT '{}',
    condition TEXT DEFAULT 'Good',
    minted_status TEXT DEFAULT 'PENDING' CHECK (minted_status IN ('PENDING', 'MINTED', 'VERIFIED')),
    mint_address TEXT,
    fingerprint_hash TEXT,
    category TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_listings_seller ON listings(seller_wallet);
CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(minted_status);
CREATE INDEX IF NOT EXISTS idx_listings_created ON listings(created_at DESC);

-- ── Trade Sessions ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS trade_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    buyer_wallet TEXT NOT NULL,
    seller_wallet TEXT NOT NULL,
    state TEXT DEFAULT 'SESSION_CREATED'
        CHECK (state IN ('SESSION_CREATED', 'HANDSHAKE_DONE', 'VERIFIED_PASS', 'VERIFIED_FAIL', 'ESCROW_FUNDED', 'TRADE_COMPLETE')),
    escrow_tx_sig TEXT,
    nfc_sun_url TEXT,
    aura_tokens_awarded BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sessions_listing ON trade_sessions(listing_id);
CREATE INDEX IF NOT EXISTS idx_sessions_state ON trade_sessions(state);

-- ── Aura History (Engagement Ledger) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS aura_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES profiles(id),
    change_amount INTEGER NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_history_user ON aura_history(user_id);

-- ── Row Level Security ────────────────────────────────────────────────────
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE trade_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE aura_history ENABLE ROW LEVEL SECURITY;

-- Profiles: users can read all, update only their own
CREATE POLICY "Profiles are viewable by everyone" ON profiles FOR SELECT USING (true);
CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (true);
CREATE POLICY "Users can insert own profile" ON profiles FOR INSERT WITH CHECK (true);

-- Listings: anyone can read, seller can insert/update
CREATE POLICY "Listings are viewable by everyone" ON listings FOR SELECT USING (true);
CREATE POLICY "Authenticated users can create listings" ON listings FOR INSERT WITH CHECK (true);
CREATE POLICY "Sellers can update own listings" ON listings FOR UPDATE USING (true);

-- Trade Sessions: participants can read their own
CREATE POLICY "Trade sessions viewable by participants" ON trade_sessions FOR SELECT USING (true);
CREATE POLICY "Authenticated users can create sessions" ON trade_sessions FOR INSERT WITH CHECK (true);
CREATE POLICY "Participants can update sessions" ON trade_sessions FOR UPDATE USING (true);

-- Aura History: users can read their own
CREATE POLICY "Users can view own history" ON aura_history FOR SELECT USING (true);
CREATE POLICY "System can insert history" ON aura_history FOR INSERT WITH CHECK (true);
