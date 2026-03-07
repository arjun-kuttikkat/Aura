-- Migration: User favorites table
-- Phase 1.5: Enable Favorites tab functionality

CREATE TABLE IF NOT EXISTS favorites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_address TEXT NOT NULL REFERENCES profiles(wallet_address) ON DELETE CASCADE,
    listing_id  TEXT NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (wallet_address, listing_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_wallet ON favorites (wallet_address);
CREATE INDEX IF NOT EXISTS idx_favorites_listing ON favorites (listing_id);

ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;

-- Users can only read/write their own favorites
CREATE POLICY "favorites_select_own" ON favorites
    FOR SELECT USING (
        wallet_address = current_setting('request.jwt.claims', true)::json->>'wallet_address'
        OR wallet_address = (current_setting('request.jwt.claims', true)::json->'app_metadata'->>'wallet_address')
    );

CREATE POLICY "favorites_insert_own" ON favorites
    FOR INSERT WITH CHECK (
        wallet_address = current_setting('request.jwt.claims', true)::json->>'wallet_address'
        OR wallet_address = (current_setting('request.jwt.claims', true)::json->'app_metadata'->>'wallet_address')
    );

CREATE POLICY "favorites_delete_own" ON favorites
    FOR DELETE USING (
        wallet_address = current_setting('request.jwt.claims', true)::json->>'wallet_address'
        OR wallet_address = (current_setting('request.jwt.claims', true)::json->'app_metadata'->>'wallet_address')
    );
