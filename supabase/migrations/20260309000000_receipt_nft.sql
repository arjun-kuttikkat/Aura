-- Add receipt NFT mint addresses to trade_sessions
-- Minted instantly after escrow release, one to buyer and one to seller
ALTER TABLE trade_sessions
    ADD COLUMN IF NOT EXISTS receipt_mint_buyer TEXT,
    ADD COLUMN IF NOT EXISTS receipt_mint_seller TEXT;

COMMENT ON COLUMN trade_sessions.receipt_mint_buyer IS 'Metaplex Core NFT mint address - receipt minted to buyer after escrow release';
COMMENT ON COLUMN trade_sessions.receipt_mint_seller IS 'Metaplex Core NFT mint address - receipt minted to seller after escrow release';

-- Allow PHOTO_VERIFIED state (used by release-escrow-photo for non-NFC listings)
ALTER TABLE trade_sessions DROP CONSTRAINT IF EXISTS chk_trade_session_state;
ALTER TABLE trade_sessions
    ADD CONSTRAINT chk_trade_session_state
    CHECK (state IN (
        'SESSION_CREATED', 'PAYMENT_PENDING', 'ESCROW_LOCKED', 'ESCROW_FUNDED',
        'BOTH_PRESENT', 'ITEM_VERIFIED', 'NFC_VERIFIED', 'PHOTO_VERIFIED',
        'ESCROW_RELEASED', 'TRADE_COMPLETE', 'COMPLETED', 'CANCELLED', 'DISPUTED'
    ));
