---
name: Location Services, Distance, and Trust Zones
description: Using Android Location APIs to calculate 300m separation and build privacy-preserving Trust Zones.
---

# Location & Trust Zones

## 1. Fused Location Provider
Use Google's Fused Location Provider API for accurate foreground/background location.
Ensure proper permissions (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, and `ACCESS_BACKGROUND_LOCATION` if necessary) are requested via Compose or the Activity.

## 2. Distance Calculation
For the "Stand-Off Escrow", capture the coordinates at the time of the trade.
Continually (or periodically) use `Location.distanceTo()` to check if the user has moved 300 meters from the origin point.
Once `distance >= 300f`, trigger the transaction release.

## 3. Geohashing (Privacy-First Heatmaps)
Do not send exact lat/lng coordinates to the backend or to other users.
Hash the coordinates into a Geohash string (e.g., 5-6 characters precision) locally.
Group users by Geohash to display "Trust Zones" (heatmaps) on the map instead of individual identifying dots.
