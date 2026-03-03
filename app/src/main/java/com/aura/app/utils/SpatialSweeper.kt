package com.aura.app.utils

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * MVP Mock of the Spatial Sweeper using ML Kit. 
 * This simulates a client-side AI analysis of a physical object via the 
 * device's Hardware Camera feed. 
 */
object SpatialSweeper {
    private const val TAG = "SpatialSweeper"

    enum class SweepResult {
        AWAITING_FOCUS,
        DETECTING,
        VALIDATED,
        REJECTED_FAKE
    }
    
    // Maintain a singleton labeler mapping standard objects locally
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    /**
     * Feeds the CameraX frame into ML Kit Object Labeling to ensure the physical item matches
     * the target category. If high confidence is reached, it passes the sweep.
     */
    fun analyzeFrame(bitmap: Bitmap, targetCategory: String, onResult: (SweepResult, confidence: Float) -> Unit) {
        // Derive structural hash in the background (used for duplicate listing prevention)
        val hash = TextureHasher.extractHardwareFingerprint(bitmap)
        Log.d(TAG, "Sweeping frame... derived structural hash: $hash")
        
        val image = InputImage.fromBitmap(bitmap, 0)
        
        labeler.process(image)
            .addOnSuccessListener { labels ->
                var highestConfidenceForTarget = 0f
                var isDetected = false
                
                for (label in labels) {
                    val text = label.text.lowercase()
                    // Simple ontology mapping for MVP categories
                    val isMatch = when (targetCategory.lowercase()) {
                        "sneaker", "footwear", "shoe" -> text.contains("shoe") || text.contains("sneaker") || text.contains("footwear")
                        "watch" -> text.contains("watch") || text.contains("clock") || text.contains("analog")
                        "electronics", "laptop" -> text.contains("computer") || text.contains("laptop") || text.contains("electronics") || text.contains("phone") || text.contains("gadget")
                        "handbag" -> text.contains("bag") || text.contains("luggage") || text.contains("purse")
                        else -> text.contains(targetCategory.lowercase()) || label.confidence > 0.85f // fallback for generic domains
                    }
                    
                    if (isMatch) {
                        isDetected = true
                        if (label.confidence > highestConfidenceForTarget) {
                            highestConfidenceForTarget = label.confidence
                        }
                    }
                }
                
                if (isDetected) {
                    if (highestConfidenceForTarget > 0.70f) {
                        Log.d(TAG, "ML Kit Object Validated: $targetCategory (conf: $highestConfidenceForTarget)")
                        onResult(SweepResult.VALIDATED, highestConfidenceForTarget)
                    } else {
                        onResult(SweepResult.DETECTING, highestConfidenceForTarget)
                    }
                } else {
                    onResult(SweepResult.AWAITING_FOCUS, 0f)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit failure", e)
                onResult(SweepResult.AWAITING_FOCUS, 0f)
            }
    }
}
