-- Atomic Aura Score updates — prevents lost points from race conditions
-- RPC functions for reliable client calls with retry support

-- Add aura points (mission, trade bonus, etc). Returns new aura_score or NULL if no row updated.
CREATE OR REPLACE FUNCTION add_aura_points_atomic(
  p_wallet_address TEXT,
  p_amount INT,
  p_reason TEXT DEFAULT ''
)
RETURNS INT
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
DECLARE
  new_score INT;
  profile_uuid UUID;
BEGIN
  IF p_amount <= 0 THEN
    RETURN NULL;
  END IF;
  UPDATE profiles
  SET aura_score = aura_score + p_amount,
      updated_at = now()
  WHERE wallet_address = p_wallet_address
  RETURNING aura_score, id INTO new_score, profile_uuid;
  IF profile_uuid IS NOT NULL AND p_reason != '' THEN
    INSERT INTO aura_history (user_id, change_amount, reason)
    VALUES (profile_uuid, p_amount, p_reason);
  END IF;
  RETURN new_score;
END;
$$;

-- Deduct aura points (chat unlock, promote, etc). Returns new aura_score, or -1 if insufficient balance.
CREATE OR REPLACE FUNCTION deduct_aura_points_atomic(
  p_wallet_address TEXT,
  p_amount INT,
  p_reason TEXT DEFAULT ''
)
RETURNS INT
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
DECLARE
  new_score INT;
  profile_uuid UUID;
  current_score INT;
BEGIN
  IF p_amount <= 0 THEN
    RETURN NULL;
  END IF;
  SELECT aura_score, id INTO current_score, profile_uuid
  FROM profiles
  WHERE wallet_address = p_wallet_address
  FOR UPDATE;
  IF profile_uuid IS NULL OR current_score < p_amount THEN
    RETURN -1;
  END IF;
  new_score := current_score - p_amount;
  UPDATE profiles
  SET aura_score = new_score,
      updated_at = now()
  WHERE wallet_address = p_wallet_address;
  INSERT INTO aura_history (user_id, change_amount, reason)
  VALUES (profile_uuid, -p_amount, p_reason);
  RETURN new_score;
END;
$$;
