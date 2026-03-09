-- Add rank_title to profiles (if missing) and create completed_missions for account-level persistence
-- Run: supabase db push or apply via SQL Editor

-- Add rank_title column if it doesn't exist (profiles updates use it)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'rank_title'
  ) THEN
    ALTER TABLE public.profiles ADD COLUMN rank_title TEXT;
  END IF;
END
$$;

-- Completed missions table — persists to account so missions survive logout/reinstall
CREATE TABLE IF NOT EXISTS public.completed_missions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  wallet_address TEXT NOT NULL,
  title TEXT NOT NULL,
  emoji TEXT DEFAULT '✨',
  aura_reward INT NOT NULL DEFAULT 0,
  ai_feedback TEXT,
  completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_completed_missions_wallet ON completed_missions(wallet_address);
CREATE INDEX IF NOT EXISTS idx_completed_missions_profile ON completed_missions(profile_id);
CREATE INDEX IF NOT EXISTS idx_completed_missions_completed_at ON completed_missions(completed_at DESC);

ALTER TABLE public.completed_missions ENABLE ROW LEVEL SECURITY;

-- Users can read their own completed missions
CREATE POLICY "completed_missions_select_own"
  ON public.completed_missions FOR SELECT
  USING (wallet_address = public.requesting_wallet());

-- Users can insert their own completed missions
CREATE POLICY "completed_missions_insert_own"
  ON public.completed_missions FOR INSERT
  WITH CHECK (wallet_address = public.requesting_wallet());
