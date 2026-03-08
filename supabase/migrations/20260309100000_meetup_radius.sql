-- Add meetup_radius_meters to marketplace_listings
-- Seller sets preferred radius for in-person meetups (default 50m)

ALTER TABLE marketplace_listings
ADD COLUMN IF NOT EXISTS meetup_radius_meters INT DEFAULT 50;

ALTER TABLE marketplace_listings
DROP CONSTRAINT IF EXISTS chk_meetup_radius;

ALTER TABLE marketplace_listings
ADD CONSTRAINT chk_meetup_radius
CHECK (meetup_radius_meters IS NULL OR (meetup_radius_meters >= 20 AND meetup_radius_meters <= 500));

COMMENT ON COLUMN marketplace_listings.meetup_radius_meters IS 'Seller-preferred radius in meters for meetup geofence (20-500m)';
