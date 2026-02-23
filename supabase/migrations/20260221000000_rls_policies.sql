-- Enable Row Level Security (RLS) on all public tables
ALTER TABLE public.trade_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;

-- Note: Because Aura uses Solana Wallet authentication, the client uses the anon key.
-- A proper zero-trust implementation requires Edge Functions to mint custom JWTs 
-- representing the verified wallet address. These policies assume the `wallet_address`
-- is present in the JWT claims under `app_metadata.wallet_address`.

-- ── Trade Sessions ────────────────────────────────────────────────────────
-- Read: Buyers and Sellers involved in the trade
CREATE POLICY "Trade sessions visible to participants" ON public.trade_sessions
  FOR SELECT USING (
    buyer_wallet = current_setting('request.jwt.claim.wallet_address', true) OR 
    seller_wallet = current_setting('request.jwt.claim.wallet_address', true)
  );

-- Insert: Anyone can create a session 
CREATE POLICY "Trade sessions insertable by authenticated wallets" ON public.trade_sessions
  FOR INSERT WITH CHECK (
    current_setting('request.jwt.claim.wallet_address', true) IS NOT NULL
  );

-- Update: Only participants
CREATE POLICY "Trade sessions updatable by participants" ON public.trade_sessions
  FOR UPDATE USING (
    buyer_wallet = current_setting('request.jwt.claim.wallet_address', true) OR 
    seller_wallet = current_setting('request.jwt.claim.wallet_address', true)
  );

-- ── Listings ─────────────────────────────────────────────────────────────
-- Read: Viewable by everyone
CREATE POLICY "Listings viewable by everyone" ON public.listings
  FOR SELECT USING (true);

-- Insert/Update: Only actual seller
CREATE POLICY "Listings mutable by seller" ON public.listings
  FOR ALL USING (
    seller_wallet = current_setting('request.jwt.claim.wallet_address', true)
  );

-- ── Reviews ──────────────────────────────────────────────────────────────
-- Read: Viewable by everyone
CREATE POLICY "Reviews viewable by everyone" ON public.reviews
  FOR SELECT USING (true);

-- Insert: Only the reviewer
CREATE POLICY "Reviews insertable by reviewer" ON public.reviews
  FOR INSERT WITH CHECK (
    reviewer_wallet = current_setting('request.jwt.claim.wallet_address', true)
  );
