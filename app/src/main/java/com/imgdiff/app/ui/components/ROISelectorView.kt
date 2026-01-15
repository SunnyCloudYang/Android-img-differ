package com.imgdiff.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.imgdiff.app.R
import com.imgdiff.lib.models.ROI
import com.imgdiff.lib.roi.ROIProcessor

/**
 * Custom view for selecting and manipulating a Region of Interest (ROI).
 * Supports touch-based resizing and moving of the ROI rectangle.
 */
class ROISelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val roiProcessor = ROIProcessor()
    
    // Current ROI in normalized coordinates (0-1) - centered by default
    private var _roi: ROI = ROI(0.25f, 0.25f, 0.75f, 0.75f)
    val roi: ROI get() = _roi
    
    // Image bounds (the area where the actual image is displayed)
    private var imageBounds = RectF()
    private var storedImageWidth = 0
    private var storedImageHeight = 0
    
    // Paints
    private val dimPaint = Paint().apply {
        color = 0x80000000.toInt()
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.roi_border)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.roi_stroke_width)
        isAntiAlias = true
    }
    
    private val handlePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.roi_border)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val handleStrokePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    // Touch handling
    private var activeHandle: ROIProcessor.HandleType? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchTolerance = resources.getDimension(R.dimen.roi_handle_size)
    private val handleRadius = resources.getDimension(R.dimen.roi_handle_size) / 2
    
    // Listener
    var onROIChangedListener: ((ROI) -> Unit)? = null
    
    /**
     * Set the ROI to display and edit.
     */
    fun setROI(roi: ROI) {
        _roi = roi
        invalidate()
    }
    
    /**
     * Set the bounds of the actual image within this view.
     * This is needed when the image doesn't fill the entire view.
     */
    fun setImageBounds(bounds: RectF) {
        imageBounds = bounds
        invalidate()
    }
    
    /**
     * Set image bounds from image dimensions and view dimensions.
     */
    fun setImageBounds(imageWidth: Int, imageHeight: Int) {
        storedImageWidth = imageWidth
        storedImageHeight = imageHeight
        recalculateImageBounds()
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateImageBounds()
    }
    
    private fun recalculateImageBounds() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth == 0f || viewHeight == 0f || storedImageWidth == 0 || storedImageHeight == 0) {
            imageBounds = RectF(0f, 0f, viewWidth, viewHeight)
            return
        }
        
        val imageAspect = storedImageWidth.toFloat() / storedImageHeight
        val viewAspect = viewWidth / viewHeight
        
        if (imageAspect > viewAspect) {
            // Image is wider - fit to width
            val scaledHeight = viewWidth / imageAspect
            val top = (viewHeight - scaledHeight) / 2
            imageBounds = RectF(0f, top, viewWidth, top + scaledHeight)
        } else {
            // Image is taller - fit to height
            val scaledWidth = viewHeight * imageAspect
            val left = (viewWidth - scaledWidth) / 2
            imageBounds = RectF(left, 0f, left + scaledWidth, viewHeight)
        }
    }
    
    /**
     * Reset ROI to default (centered).
     */
    fun resetROI() {
        _roi = ROI(0.25f, 0.25f, 0.75f, 0.75f)
        onROIChangedListener?.invoke(_roi)
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (imageBounds.isEmpty) {
            imageBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        }
        
        val roiRect = roiToViewRect(_roi)
        
        // Draw dimmed areas outside ROI
        // Top
        canvas.drawRect(
            imageBounds.left, imageBounds.top,
            imageBounds.right, roiRect.top,
            dimPaint
        )
        // Bottom
        canvas.drawRect(
            imageBounds.left, roiRect.bottom,
            imageBounds.right, imageBounds.bottom,
            dimPaint
        )
        // Left
        canvas.drawRect(
            imageBounds.left, roiRect.top,
            roiRect.left, roiRect.bottom,
            dimPaint
        )
        // Right
        canvas.drawRect(
            roiRect.right, roiRect.top,
            imageBounds.right, roiRect.bottom,
            dimPaint
        )
        
        // Draw ROI border
        canvas.drawRect(roiRect, borderPaint)
        
        // Draw handles
        drawHandle(canvas, roiRect.left, roiRect.top)      // Top-left
        drawHandle(canvas, roiRect.right, roiRect.top)     // Top-right
        drawHandle(canvas, roiRect.left, roiRect.bottom)   // Bottom-left
        drawHandle(canvas, roiRect.right, roiRect.bottom)  // Bottom-right
        
        // Edge handles
        val midX = (roiRect.left + roiRect.right) / 2
        val midY = (roiRect.top + roiRect.bottom) / 2
        drawHandle(canvas, midX, roiRect.top, handleRadius * 0.7f)     // Top
        drawHandle(canvas, midX, roiRect.bottom, handleRadius * 0.7f)  // Bottom
        drawHandle(canvas, roiRect.left, midY, handleRadius * 0.7f)    // Left
        drawHandle(canvas, roiRect.right, midY, handleRadius * 0.7f)   // Right
    }
    
    private fun drawHandle(canvas: Canvas, x: Float, y: Float, radius: Float = handleRadius) {
        canvas.drawCircle(x, y, radius, handlePaint)
        canvas.drawCircle(x, y, radius, handleStrokePaint)
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                
                // Convert touch to image coordinates
                val imageX = viewToImageX(event.x)
                val imageY = viewToImageY(event.y)
                
                activeHandle = roiProcessor.getHandleAtPoint(
                    imageX, imageY, _roi,
                    imageBounds.width().toInt(),
                    imageBounds.height().toInt(),
                    viewToImageScale(touchTolerance)
                )
                
                return activeHandle != null
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != null) {
                    val deltaX = (event.x - lastTouchX) / imageBounds.width()
                    val deltaY = (event.y - lastTouchY) / imageBounds.height()
                    
                    _roi = roiProcessor.updateROI(_roi, activeHandle!!, deltaX, deltaY)
                    onROIChangedListener?.invoke(_roi)
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = null
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    private fun roiToViewRect(roi: ROI): RectF {
        return RectF(
            imageBounds.left + roi.left * imageBounds.width(),
            imageBounds.top + roi.top * imageBounds.height(),
            imageBounds.left + roi.right * imageBounds.width(),
            imageBounds.top + roi.bottom * imageBounds.height()
        )
    }
    
    private fun viewToImageX(viewX: Float): Float {
        return (viewX - imageBounds.left) / imageBounds.width() * imageBounds.width()
    }
    
    private fun viewToImageY(viewY: Float): Float {
        return (viewY - imageBounds.top) / imageBounds.height() * imageBounds.height()
    }
    
    private fun viewToImageScale(viewDist: Float): Float {
        return viewDist / imageBounds.width() * imageBounds.width()
    }
}

