-- Aura Project Initial Schema 
-- Run this in your Supabase SQL Editor

-- 1. Profiles Table (Extending what you likely already have for Users)
-- Note: Assuming you are using wallet addresses for authentication.
CREATE TABLE IF NOT EXISTS public.profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_address TEXT UNIQUE NOT NULL,
  aura_score INT DEFAULT 50,
  streak_days INT DEFAULT 0,
  last_scan_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- Turn on Row Level Security
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- 2. Hotzones Table
CREATE TABLE IF NOT EXISTS public.hotzones (
  h3_index TEXT PRIMARY KEY, -- Resolution 9 H3 index
  name TEXT NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.hotzones ENABLE ROW LEVEL SECURITY;

-- 3. Turf Streaks Table (Tracks who is the Apex logic)
CREATE TABLE IF NOT EXISTS public.turf_streaks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
  h3_index TEXT REFERENCES public.hotzones(h3_index) ON DELETE CASCADE,
  local_streak INT DEFAULT 0,
  is_apex BOOLEAN DEFAULT FALSE,
  last_activity_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE(profile_id, h3_index)
);

ALTER TABLE public.turf_streaks ENABLE ROW LEVEL SECURITY;

-- 4. Directives Table (Quests)
CREATE TYPE directive_type AS ENUM ('SPATIAL_SWEEP', 'GUARDIAN_WITNESS', 'TEXTURE_ARCHIVE');
CREATE TYPE directive_status AS ENUM ('PENDING', 'COMPLETED', 'EXPIRED');

CREATE TABLE IF NOT EXISTS public.directives (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
  type directive_type NOT NULL,
  target_h3_index TEXT REFERENCES public.hotzones(h3_index),
  status directive_status DEFAULT 'PENDING',
  created_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE public.directives ENABLE ROW LEVEL SECURITY;

-- Basic Read Policies (For Hackathon ease)
CREATE POLICY "Public profiles are viewable by everyone." 
  ON public.profiles FOR SELECT USING (true);

CREATE POLICY "Hotzones are public." 
  ON public.hotzones FOR SELECT USING (true);

CREATE POLICY "Turf streaks are public." 
  ON public.turf_streaks FOR SELECT USING (true);

CREATE POLICY "Users can see their own directives."
  ON public.directives FOR SELECT USING (
    -- You should replace this with auth.uid() if using Supabase Auth.
    -- For hackathon, if using raw wallet addresses from app:
    true 
  );
