package com.imgdiff.lib.models

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Mode for calculating and visualizing differences.
 */
enum class DiffMode {
    /** Pixel-by-pixel difference with threshold */
    PIXEL_DIFF,
    
    /** Structural similarity comparison */
    STRUCTURAL,
    
    /** Absolute difference visualization */
    ABSOLUTE,
    
    /** Blend overlay showing differences */
    OVERLAY,
    
    /** Highlight different pixels with distinct colors based on difference magnitude */
    HIGHLIGHT,
    
    /** Subtract images (source - target), showing signed difference */
    MINUS
}

/**
 * Result of difference calculation between two aligned images.
 */
data class DiffResult(
    /** The source/reference image */
    val sourceImage: Bitmap,
    
    /** The target/compared image (aligned) */
    val targetImage: Bitmap,
    
    /** Visualization of differences */
    val diffVisualization: Bitmap,
    
    /** Binary mask of difference regions (white = different) */
    val diffMask: Bitmap,
    
    /** Percentage of pixels that are different (0-100) */
    val diffPercentage: Float,
    
    /** Number of pixels that are different */
    val diffPixelCount: Int,
    
    /** Total number of pixels compared */
    val totalPixelCount: Int,
    
    /** Bounding rectangles of detected difference regions */
    val diffRegions: List<Rect>,
    
    /** The mode used for calculation */
    val mode: DiffMode,
    
    /** Mean difference value */
    val meanDifference: Float,
    
    /** Structural similarity index (0-1, 1 = identical) */
    val ssimScore: Float? = null
)

