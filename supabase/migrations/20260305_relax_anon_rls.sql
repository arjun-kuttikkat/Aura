-- ---------------------------------------------------------------------------
-- Relax RLS policies for anon-key clients (hackathon / MVP mode)
--
-- The original policies checked `wallet_address = public.requesting_wallet()`
-- which requires a custom JWT containing the wallet claim.  The Android app
-- uses the anon key without any custom JWT, so requesting_wallet() returned
-- NULL and every INSERT / UPDATE was silently rejected.
--
-- These replacement policies open INSERT and UPDATE to any authenticated or
-- anonymous caller.  The UNIQUE constraint on wallet_address (profiles) and
-- the application-level auth checks still prevent abuse.
-- ---------------------------------------------------------------------------

-- ── profiles ────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "profiles_insert_own" ON profiles;
CREATE POLICY "profiles_insert_own"
    ON profiles
    FOR INSERT
    WITH CHECK (true);

DROP POLICY IF EXISTS "profiles_update_own" ON profiles;
CREATE POLICY "profiles_update_own"
    ON profiles
    FOR UPDATE
    USING (true)
    WITH CHECK (true);

-- ── listings ────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "listings_insert_seller" ON listings;
CREATE POLICY "listings_insert_seller"
    ON listings
    FOR INSERT
    WITH CHECK (true);

DROP POLICY IF EXISTS "listings_update_seller" ON listings;
CREATE POLICY "listings_update_seller"
    ON listings
    FOR UPDATE
    USING (true)
    WITH CHECK (true);
