-- Ensure listing-images bucket exists and is PUBLIC for marketplace image display.
-- Without this, Supabase Storage URLs return 404 and cards show broken images.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'listing-images',
  'listing-images',
  true,
  5242880,  -- 5MB
  ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
  public = true,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Allow anon/authenticated to upload when creating listings (app uses anon key)
DROP POLICY IF EXISTS "listing_images_insert" ON storage.objects;
CREATE POLICY "listing_images_insert"
  ON storage.objects FOR INSERT
  WITH CHECK (bucket_id = 'listing-images');

-- Upsert requires SELECT and UPDATE; without these, "new row violates row-level security" on upload
DROP POLICY IF EXISTS "listing_images_select" ON storage.objects;
CREATE POLICY "listing_images_select"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'listing-images');

DROP POLICY IF EXISTS "listing_images_update" ON storage.objects;
CREATE POLICY "listing_images_update"
  ON storage.objects FOR UPDATE
  USING (bucket_id = 'listing-images')
  WITH CHECK (bucket_id = 'listing-images');
