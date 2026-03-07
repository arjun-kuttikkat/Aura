-- Migration: DB hardening — constraints, indexes, and data integrity
-- Phase 2.6: Production-grade database constraints

-- Ensure critical fields are NOT NULL where they should be
ALTER TABLE marketplace_listings
    ALTER COLUMN seller_wallet SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN price_lamports SET NOT NULL;

ALTER TABLE trade_sessions
    ALTER COLUMN listing_id SET NOT NULL,
    ALTER COLUMN buyer_wallet SET NOT NULL,
    ALTER COLUMN seller_wallet SET NOT NULL,
    ALTER COLUMN state SET NOT NULL;

-- Add CHECK constraints for valid state values
ALTER TABLE trade_sessions
    ADD CONSTRAINT chk_trade_session_state
    CHECK (state IN (
        'SESSION_CREATED', 'PAYMENT_PENDING', 'ESCROW_LOCKED', 'ESCROW_FUNDED',
        'BOTH_PRESENT', 'ITEM_VERIFIED', 'NFC_VERIFIED', 'ESCROW_RELEASED',
        'TRADE_COMPLETE', 'COMPLETED', 'CANCELLED', 'DISPUTED'
    ));

-- Ensure price is positive
ALTER TABLE marketplace_listings
    ADD CONSTRAINT chk_positive_price
    CHECK (price_lamports > 0);

-- Index for common queries
CREATE INDEX IF NOT EXISTS idx_listings_active
    ON marketplace_listings (is_active, is_published)
    WHERE sold_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_listings_seller
    ON marketplace_listings (seller_wallet);

CREATE INDEX IF NOT EXISTS idx_listings_emirate
    ON marketplace_listings (emirate)
    WHERE emirate IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trade_sessions_listing
    ON trade_sessions (listing_id);

CREATE INDEX IF NOT EXISTS idx_trade_sessions_buyer
    ON trade_sessions (buyer_wallet);

CREATE INDEX IF NOT EXISTS idx_trade_sessions_state
    ON trade_sessions (state);

-- Add updated_at trigger for trade_sessions
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_trade_sessions_updated_at ON trade_sessions;
CREATE TRIGGER trg_trade_sessions_updated_at
    BEFORE UPDATE ON trade_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
