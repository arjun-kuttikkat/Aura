package com.aura.app.utils

import com.uber.h3core.H3Core
import java.io.IOException

/**
 * Singleton to handle Hexagonal Hierarchical Spatial Indexing via Uber's H3 library.
 */
object SpatialManager {
    
    private var h3: H3Core? = null

    init {
        try {
            h3 = H3Core.newInstance()
        } catch (e: IOException) {
            e.printStackTrace()
            // In a production app, we would handle this critical native library failure gracefully.
        }
    }

    /**
     * Converts a raw GPS coordinate into an H3 Hexagon string identifier.
     * @param lat Latitude
     * @param lng Longitude
     * @param resolution The H3 resolution scale (0-15). 
     *                   Res 9 is approx 174 meters (ideal for hyper-local "Hotzones").
     * @return The 15-character H3 hex index string, or null if initialization failed.
     */
    fun getHotzoneIndex(lat: Double, lng: Double, resolution: Int = 9): String? {
        return try {
            h3?.latLngToCellAddress(lat, lng, resolution)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * (Optional/Future) Retrieve the surrounding hexagon ring to expand the search radius
     * for users with higher Aura scores without abandoning the H3 indexing paradigm.
     */
    fun getSurroundingHotzones(h3Address: String, kRings: Int): List<String>? {
        return try {
            h3?.gridDisk(h3Address, kRings)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
