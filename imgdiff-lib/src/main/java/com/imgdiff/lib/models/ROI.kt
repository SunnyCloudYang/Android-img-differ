package com.imgdiff.lib.models

import android.graphics.Rect
import android.graphics.RectF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a Region of Interest (ROI) in an image.
 * The ROI is defined in normalized coordinates (0-1) relative to image dimensions.
 */
@Parcelize
data class ROI(
    /** Left edge (0-1) */
    val left: Float,
    
    /** Top edge (0-1) */
    val top: Float,
    
    /** Right edge (0-1) */
    val right: Float,
    
    /** Bottom edge (0-1) */
    val bottom: Float
) : Parcelable {
    
    val width: Float
        get() = right - left
    
    val height: Float
        get() = bottom - top
    
    val centerX: Float
        get() = (left + right) / 2f
    
    val centerY: Float
        get() = (top + bottom) / 2f
    
    val isValid: Boolean
        get() = left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
                left < right && top < bottom
    
    /**
     * Convert to pixel coordinates for a given image size.
     */
    fun toRect(imageWidth: Int, imageHeight: Int): Rect {
        return Rect(
            (left * imageWidth).toInt(),
            (top * imageHeight).toInt(),
            (right * imageWidth).toInt(),
            (bottom * imageHeight).toInt()
        )
    }
    
    /**
     * Convert to RectF in pixel coordinates.
     */
    fun toRectF(imageWidth: Float, imageHeight: Float): RectF {
        return RectF(
            left * imageWidth,
            top * imageHeight,
            right * imageWidth,
            bottom * imageHeight
        )
    }
    
    /**
     * Check if a point (in normalized coordinates) is inside the ROI.
     */
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }
    
    companion object {
        /**
         * Full image ROI
         */
        val FULL = ROI(0f, 0f, 1f, 1f)
        
        /**
         * Create ROI from pixel coordinates
         */
        fun fromRect(rect: Rect, imageWidth: Int, imageHeight: Int): ROI {
            return ROI(
                left = rect.left.toFloat() / imageWidth,
                top = rect.top.toFloat() / imageHeight,
                right = rect.right.toFloat() / imageWidth,
                bottom = rect.bottom.toFloat() / imageHeight
            )
        }
        
        /**
         * Create ROI from RectF in pixel coordinates
         */
        fun fromRectF(rectF: RectF, imageWidth: Float, imageHeight: Float): ROI {
            return ROI(
                left = rectF.left / imageWidth,
                top = rectF.top / imageHeight,
                right = rectF.right / imageWidth,
                bottom = rectF.bottom / imageHeight
            )
        }
    }
}

