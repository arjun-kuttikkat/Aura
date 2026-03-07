-- Fix: Add is_promoted columns (fixes "Promotion failed" / schema cache error)
-- Fix: Add nfc_sun_url to store NFC SUN URL from publish flow for meetup verification
-- Run this in Supabase SQL editor if migrations haven't been applied.

-- 1. Promotion columns (pay-to-reach ranking)
ALTER TABLE IF EXISTS marketplace_listings
    ADD COLUMN IF NOT EXISTS is_promoted BOOLEAN DEFAULT false;
ALTER TABLE IF EXISTS marketplace_listings
    ADD COLUMN IF NOT EXISTS promoted_at TIMESTAMPTZ;
ALTER TABLE IF EXISTS marketplace_listings
    ADD COLUMN IF NOT EXISTS promoted_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_marketplace_promoted
    ON marketplace_listings(is_promoted, promoted_until DESC);

-- 2. NFC SUN URL from publish flow (verified at meetup)
ALTER TABLE IF EXISTS marketplace_listings
    ADD COLUMN IF NOT EXISTS nfc_sun_url TEXT;

COMMENT ON COLUMN marketplace_listings.nfc_sun_url IS 'SUN URL from NFC tap at publish; used to verify same physical tag at meetup';
