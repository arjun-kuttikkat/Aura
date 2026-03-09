package com.aura.app.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AuraRepository pure functions and data mapping.
 */
class AuraRepositoryTest {

    @Test
    fun `getListing returns null for unknown id`() {
        val result = AuraRepository.getListing("unknown-id-12345")
        assertNull("Should return null for unknown listing", result)
    }

    @Test
    fun `haversine distance calculation is reasonable`() {
        // Dubai coordinates: approx 25.2048, 55.2708
        // Abu Dhabi: approx 24.4539, 54.3773
        // Expected distance: ~130 km
        // Using reflection to test the private method
        val method = AuraRepository::class.java.getDeclaredMethod(
            "haversineMeters",
            Double::class.java, Double::class.java,
            Double::class.java, Double::class.java
        )
        method.isAccessible = true
        val distance = method.invoke(AuraRepository, 25.2048, 55.2708, 24.4539, 54.3773) as Double
        // Should be approximately 130km (allow 10% tolerance)
        assertTrue("Distance Dubai-Abu Dhabi should be ~130km",
            distance > 100_000 && distance < 160_000)
    }
}
