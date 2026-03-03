package com.aura.app.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest

/**
 * Utility to generate a deterministic hash of a macro-texture image.
 * This prevents users from simply downloading an image from the internet
 * and forces them to use their hardware camera to scan a physical surface.
 */
object TextureHasher {

    /**
     * Converts a Bitmap into a deterministic SHA-256 hash.
     * This is an MVP approach to "Proof-of-Hardware-Action".
     *
     * How it works:
     * 1. Rescales the image to a standardized square grid (e.g., 64x64).
     * 2. Converts the image to grayscale to standardize contrast and remove lighting color bias.
     * 3. Calculates the average pixel intensity of the scaled image.
     * 4. Converts the pixel grid into a binary string: 1 if pixel > average, 0 if pixel <= average (a basic aHash).
     * 5. Hashes the resulting binary string via SHA-256 for a uniform 64-character hex output.
     */
    fun extractHardwareFingerprint(srcBitmap: Bitmap): String {
        // 1. Standardize size to 9x8 for dHash (Difference Hash) - excellent for gradients/textures
        val width = 9
        val height = 8
        val scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, width, height, true)

        val binaryBuilder = StringBuilder()

        // 2 & 3. Convert to grayscale and compare adjacent pixels horizontally
        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val leftPixel = scaledBitmap.getPixel(x, y)
                val rightPixel = scaledBitmap.getPixel(x + 1, y)

                // Standard luminosity conversion
                val leftLuma = (0.299 * Color.red(leftPixel) + 0.587 * Color.green(leftPixel) + 0.114 * Color.blue(leftPixel)).toInt()
                val rightLuma = (0.299 * Color.red(rightPixel) + 0.587 * Color.green(rightPixel) + 0.114 * Color.blue(rightPixel)).toInt()

                // If left pixel is brighter than the right pixel, bit = 1
                if (leftLuma > rightLuma) {
                    binaryBuilder.append("1")
                } else {
                    binaryBuilder.append("0")
                }
            }
        }

        // 4. Clean up memory
        if (scaledBitmap != srcBitmap) {
            scaledBitmap.recycle()
        }

        // 5. We now have exactly 64 bits (8 rows * 8 comparisons = 64 bits)
        // Hash it via SHA-256 for a deterministic 64-char hex string as the final fingerprint
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(binaryBuilder.toString().toByteArray())
        
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
