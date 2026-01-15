package com.imgdiff.lib.roi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.imgdiff.lib.models.ROI
import com.imgdiff.lib.utils.BitmapUtils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Processor for Region of Interest (ROI) operations.
 * Handles cropping, masking, and ROI-based feature extraction.
 */
class ROIProcessor {
    
    companion object {
        private const val DEFAULT_ROI_COLOR = 0xFF2196F3.toInt()  // Blue
        private const val DEFAULT_ROI_STROKE_WIDTH = 3f
    }
    
    /**
     * Crop a bitmap to the specified ROI.
     * 
     * @param bitmap Source bitmap
     * @param roi Region of interest (in normalized coordinates)
     * @return Cropped bitmap
     */
    fun cropToROI(bitmap: Bitmap, roi: ROI): Bitmap {
        if (!roi.isValid) {
            return bitmap
        }
        
        val rect = roi.toRect(bitmap.width, bitmap.height)
        
        // Clamp to image bounds
        val left = rect.left.coerceIn(0, bitmap.width)
        val top = rect.top.coerceIn(0, bitmap.height)
        val right = rect.right.coerceIn(0, bitmap.width)
        val bottom = rect.bottom.coerceIn(0, bitmap.height)
        
        val width = right - left
        val height = bottom - top
        
        if (width <= 0 || height <= 0) {
            return bitmap
        }
        
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
    
    /**
     * Create a binary mask for the ROI.
     * 
     * @param width Mask width
     * @param height Mask height
     * @param roi Region of interest
     * @return OpenCV Mat mask (255 inside ROI, 0 outside)
     */
    fun createROIMask(width: Int, height: Int, roi: ROI): Mat {
        val mask = Mat.zeros(height, width, CvType.CV_8UC1)
        
        if (roi.isValid) {
            val rect = roi.toRect(width, height)
            Imgproc.rectangle(
                mask,
                Point(rect.left.toDouble(), rect.top.toDouble()),
                Point(rect.right.toDouble(), rect.bottom.toDouble()),
                Scalar(255.0),
                -1  // Filled
            )
        }
        
        return mask
    }
    
    /**
     * Apply ROI mask to an image, making areas outside the ROI transparent or black.
     * 
     * @param bitmap Source bitmap
     * @param roi Region of interest
     * @param keepOutside If true, area outside ROI is dimmed; if false, it's made transparent
     * @return Masked bitmap
     */
    fun applyROIMask(bitmap: Bitmap, roi: ROI, keepOutside: Boolean = true): Bitmap {
        if (!roi.isValid) {
            return bitmap
        }
        
        val result = BitmapUtils.copyBitmap(bitmap)
        val rect = roi.toRect(bitmap.width, bitmap.height)
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        result.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) {
                    val idx = y * bitmap.width + x
                    if (keepOutside) {
                        // Dim the pixel
                        val color = pixels[idx]
                        val a = (color shr 24) and 0xFF
                        val r = ((color shr 16) and 0xFF) / 3
                        val g = ((color shr 8) and 0xFF) / 3
                        val b = (color and 0xFF) / 3
                        pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    } else {
                        // Make transparent
                        pixels[idx] = 0
                    }
                }
            }
        }
        
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
    
    /**
     * Draw ROI overlay on a bitmap.
     * 
     * @param bitmap Source bitmap
     * @param roi Region of interest
     * @param color Border color
     * @param strokeWidth Border stroke width
     * @param showHandles Whether to draw resize handles at corners
     * @return Bitmap with ROI overlay drawn
     */
    fun drawROIOverlay(
        bitmap: Bitmap,
        roi: ROI,
        color: Int = DEFAULT_ROI_COLOR,
        strokeWidth: Float = DEFAULT_ROI_STROKE_WIDTH,
        showHandles: Boolean = true
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        if (!roi.isValid) {
            return result
        }
        
        val rectF = roi.toRectF(bitmap.width.toFloat(), bitmap.height.toFloat())
        
        // Draw semi-transparent overlay outside ROI
        val dimPaint = Paint().apply {
            this.color = 0x80000000.toInt()
            style = Paint.Style.FILL
        }
        
        // Top region
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), rectF.top, dimPaint)
        // Bottom region
        canvas.drawRect(0f, rectF.bottom, bitmap.width.toFloat(), bitmap.height.toFloat(), dimPaint)
        // Left region
        canvas.drawRect(0f, rectF.top, rectF.left, rectF.bottom, dimPaint)
        // Right region
        canvas.drawRect(rectF.right, rectF.top, bitmap.width.toFloat(), rectF.bottom, dimPaint)
        
        // Draw ROI border
        val borderPaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            isAntiAlias = true
        }
        canvas.drawRect(rectF, borderPaint)
        
        // Draw corner handles
        if (showHandles) {
            val handleRadius = strokeWidth * 3
            val handlePaint = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            // Corner handles
            canvas.drawCircle(rectF.left, rectF.top, handleRadius, handlePaint)
            canvas.drawCircle(rectF.right, rectF.top, handleRadius, handlePaint)
            canvas.drawCircle(rectF.left, rectF.bottom, handleRadius, handlePaint)
            canvas.drawCircle(rectF.right, rectF.bottom, handleRadius, handlePaint)
            
            // Edge midpoint handles
            val midX = (rectF.left + rectF.right) / 2
            val midY = (rectF.top + rectF.bottom) / 2
            canvas.drawCircle(midX, rectF.top, handleRadius * 0.7f, handlePaint)
            canvas.drawCircle(midX, rectF.bottom, handleRadius * 0.7f, handlePaint)
            canvas.drawCircle(rectF.left, midY, handleRadius * 0.7f, handlePaint)
            canvas.drawCircle(rectF.right, midY, handleRadius * 0.7f, handlePaint)
        }
        
        return result
    }
    
    /**
     * Check if a touch point is on a ROI handle.
     * 
     * @param x Touch X coordinate (in image space)
     * @param y Touch Y coordinate (in image space)
     * @param roi Current ROI
     * @param imageWidth Image width
     * @param imageHeight Image height
     * @param touchTolerance Touch tolerance in pixels
     * @return Handle type if touching a handle, null otherwise
     */
    fun getHandleAtPoint(
        x: Float,
        y: Float,
        roi: ROI,
        imageWidth: Int,
        imageHeight: Int,
        touchTolerance: Float = 30f
    ): HandleType? {
        if (!roi.isValid) return null
        
        val rectF = roi.toRectF(imageWidth.toFloat(), imageHeight.toFloat())
        val midX = (rectF.left + rectF.right) / 2
        val midY = (rectF.top + rectF.bottom) / 2
        
        // Check corners first (higher priority)
        if (isNear(x, y, rectF.left, rectF.top, touchTolerance)) return HandleType.TOP_LEFT
        if (isNear(x, y, rectF.right, rectF.top, touchTolerance)) return HandleType.TOP_RIGHT
        if (isNear(x, y, rectF.left, rectF.bottom, touchTolerance)) return HandleType.BOTTOM_LEFT
        if (isNear(x, y, rectF.right, rectF.bottom, touchTolerance)) return HandleType.BOTTOM_RIGHT
        
        // Check edges
        if (isNear(x, y, midX, rectF.top, touchTolerance)) return HandleType.TOP
        if (isNear(x, y, midX, rectF.bottom, touchTolerance)) return HandleType.BOTTOM
        if (isNear(x, y, rectF.left, midY, touchTolerance)) return HandleType.LEFT
        if (isNear(x, y, rectF.right, midY, touchTolerance)) return HandleType.RIGHT
        
        // Check if inside ROI (for dragging)
        if (rectF.contains(x, y)) return HandleType.MOVE
        
        return null
    }
    
    private fun isNear(x: Float, y: Float, targetX: Float, targetY: Float, tolerance: Float): Boolean {
        val dx = x - targetX
        val dy = y - targetY
        return (dx * dx + dy * dy) <= tolerance * tolerance
    }
    
    /**
     * Types of handles for ROI manipulation.
     */
    enum class HandleType {
        TOP_LEFT,
        TOP,
        TOP_RIGHT,
        LEFT,
        RIGHT,
        BOTTOM_LEFT,
        BOTTOM,
        BOTTOM_RIGHT,
        MOVE
    }
    
    /**
     * Update ROI based on handle drag.
     * 
     * @param roi Current ROI
     * @param handle Handle being dragged
     * @param deltaX X movement in normalized coordinates
     * @param deltaY Y movement in normalized coordinates
     * @param minSize Minimum ROI size (normalized)
     * @return Updated ROI
     */
    fun updateROI(
        roi: ROI,
        handle: HandleType,
        deltaX: Float,
        deltaY: Float,
        minSize: Float = 0.05f
    ): ROI {
        var left = roi.left
        var top = roi.top
        var right = roi.right
        var bottom = roi.bottom
        
        when (handle) {
            HandleType.TOP_LEFT -> {
                left = (left + deltaX).coerceIn(0f, right - minSize)
                top = (top + deltaY).coerceIn(0f, bottom - minSize)
            }
            HandleType.TOP -> {
                top = (top + deltaY).coerceIn(0f, bottom - minSize)
            }
            HandleType.TOP_RIGHT -> {
                right = (right + deltaX).coerceIn(left + minSize, 1f)
                top = (top + deltaY).coerceIn(0f, bottom - minSize)
            }
            HandleType.LEFT -> {
                left = (left + deltaX).coerceIn(0f, right - minSize)
            }
            HandleType.RIGHT -> {
                right = (right + deltaX).coerceIn(left + minSize, 1f)
            }
            HandleType.BOTTOM_LEFT -> {
                left = (left + deltaX).coerceIn(0f, right - minSize)
                bottom = (bottom + deltaY).coerceIn(top + minSize, 1f)
            }
            HandleType.BOTTOM -> {
                bottom = (bottom + deltaY).coerceIn(top + minSize, 1f)
            }
            HandleType.BOTTOM_RIGHT -> {
                right = (right + deltaX).coerceIn(left + minSize, 1f)
                bottom = (bottom + deltaY).coerceIn(top + minSize, 1f)
            }
            HandleType.MOVE -> {
                val width = right - left
                val height = bottom - top
                
                left = (left + deltaX).coerceIn(0f, 1f - width)
                top = (top + deltaY).coerceIn(0f, 1f - height)
                right = left + width
                bottom = top + height
            }
        }
        
        return ROI(left, top, right, bottom)
    }
}

