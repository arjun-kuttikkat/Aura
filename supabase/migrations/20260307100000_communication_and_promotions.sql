-- Communication & Marketplace quality-of-life upgrades
-- - Chat: read receipts + optional image payload
-- - Listings: promoted listing metadata for pay-to-reach ranking

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS is_read BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS image_url TEXT;

CREATE INDEX IF NOT EXISTS idx_chat_listing_read
    ON chat_messages(listing_id, is_read);

ALTER TABLE IF EXISTS marketplace_listings
    ADD COLUMN IF NOT EXISTS is_promoted BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS promoted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS promoted_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_marketplace_promoted
    ON marketplace_listings(is_promoted, promoted_until DESC);
