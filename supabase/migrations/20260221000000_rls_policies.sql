-- ══════════════════════════════════════════════════════════════════════════════
-- Aura RLS Policies (Supplementary — extends 001_schema.sql)
-- Uses public.requesting_wallet() helper from 001_schema.sql
-- ══════════════════════════════════════════════════════════════════════════════

-- Note: 001_schema.sql already creates all core RLS policies.
-- This migration adds supplementary policies for tables created by
-- the 20260303_init_aura_system.sql migration (hotzones, turf_streaks, directives).
-- Also adds reviews table if needed.

-- ── Reviews (optional — created here if not already existing) ────────────
CREATE TABLE IF NOT EXISTS reviews (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    listing_id UUID REFERENCES listings(id),
    reviewer_wallet TEXT NOT NULL,
    reviewee_wallet TEXT NOT NULL,
    rating INTEGER CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;

CREATE POLICY "reviews_select_public" ON reviews
    FOR SELECT USING (true);

CREATE POLICY "reviews_insert_reviewer" ON reviews
    FOR INSERT WITH CHECK (
        reviewer_wallet = public.requesting_wallet()
    );

-- ── Hotzones RLS (public read, service-role write) ──────────────────────
-- Already has ENABLE RLS from 20260303. Add proper policies:
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'hotzones' AND policyname = 'hotzones_select_public'
    ) THEN
        CREATE POLICY "hotzones_select_public" ON hotzones FOR SELECT USING (true);
    END IF;
END $$;

-- ── Turf Streaks RLS ────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'turf_streaks' AND policyname = 'turf_select_public'
    ) THEN
        CREATE POLICY "turf_select_public" ON turf_streaks FOR SELECT USING (true);
    END IF;
END $$;

-- ── Directives RLS (users see their own) ────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'directives' AND policyname = 'directives_select_own'
    ) THEN
        CREATE POLICY "directives_select_own" ON directives
            FOR SELECT USING (
                profile_id IN (
                    SELECT id FROM profiles WHERE wallet_address = public.requesting_wallet()
                )
            );
    END IF;
END $$;
