-- Add missing columns to listings table (fixes schema mismatch errors)
-- Run in Supabase SQL Editor: Dashboard → SQL Editor → New query
ALTER TABLE listings ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '';
ALTER TABLE listings ADD COLUMN IF NOT EXISTS images TEXT[] DEFAULT '{}';
ALTER TABLE listings ADD COLUMN IF NOT EXISTS condition TEXT DEFAULT 'Good';
ALTER TABLE listings ADD COLUMN IF NOT EXISTS minted_status TEXT DEFAULT 'PENDING';
-- Ensure minted_status has default (fixes NOT NULL violation when client omits field)
ALTER TABLE listings ALTER COLUMN minted_status SET DEFAULT 'PENDING';
-- Ensure condition has default (fixes NOT NULL violation)
ALTER TABLE listings ALTER COLUMN condition SET DEFAULT 'Good';
ALTER TABLE listings ADD COLUMN IF NOT EXISTS mint_address TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS fingerprint_hash TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE listings ADD COLUMN IF NOT EXISTS category TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS sold_at TIMESTAMPTZ;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS buyer_wallet TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS emirate TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS seller_aura_score INTEGER DEFAULT 50;
