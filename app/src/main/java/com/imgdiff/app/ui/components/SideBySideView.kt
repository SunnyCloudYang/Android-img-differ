package com.imgdiff.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.view.GestureDetectorCompat

/**
 * Custom view for side-by-side image comparison with synchronized pan and zoom.
 */
class SideBySideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var leftBitmap: Bitmap? = null
    private var rightBitmap: Bitmap? = null
    
    private val leftPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    private val rightPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    private val dividerPaint = Paint().apply {
        color = 0xFF424242.toInt()
        strokeWidth = 2f
    }
    
    // Transformation state (shared between both images)
    private var currentScale = 1f
    private var currentTranslateX = 0f
    private var currentTranslateY = 0f
    
    private val minScale = 0.5f
    private val maxScale = 10f
    
    // Base scale to fit image in half view
    private var baseScale = 1f
    
    // Calculated image bounds for each side
    private var leftImageBounds = RectF()
    private var rightImageBounds = RectF()
    
    // Gesture detectors
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetectorCompat
    
    // Matrices
    private val leftMatrix = Matrix()
    private val rightMatrix = Matrix()
    
    // Listeners
    var onTransformChangedListener: ((scale: Float, translateX: Float, translateY: Float) -> Unit)? = null
    var onImageBoundsChangedListener: ((RectF, Int, Int) -> Unit)? = null
    
    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetectorCompat(context, GestureListener())
    }
    
    /**
     * Set the left (source) image.
     */
    fun setLeftImage(bitmap: Bitmap?) {
        leftBitmap = bitmap
        calculateBaseScale()
        updateBounds()
        invalidate()
    }
    
    /**
     * Set the right (target/aligned) image.
     */
    fun setRightImage(bitmap: Bitmap?) {
        rightBitmap = bitmap
        calculateBaseScale()
        updateBounds()
        invalidate()
    }
    
    /**
     * Set both images.
     */
    fun setImages(left: Bitmap?, right: Bitmap?) {
        leftBitmap = left
        rightBitmap = right
        calculateBaseScale()
        resetTransformation()
        invalidate()
    }
    
    /**
     * Set transformation state (for syncing with other views).
     */
    fun setTransformation(scale: Float, translateX: Float, translateY: Float) {
        currentScale = scale.coerceIn(minScale, maxScale)
        currentTranslateX = translateX
        currentTranslateY = translateY
        updateBounds()
        invalidate()
    }
    
    /**
     * Reset zoom and pan to default.
     */
    fun resetTransformation() {
        currentScale = 1f
        currentTranslateX = 0f
        currentTranslateY = 0f
        updateBounds()
        onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
    }
    
    /**
     * Get left image bounds.
     */
    fun getLeftImageBounds(): RectF = RectF(leftImageBounds)
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateBaseScale()
        updateBounds()
    }
    
    private fun calculateBaseScale() {
        val bmp = leftBitmap ?: rightBitmap ?: return
        
        // Each image gets half the view width
        val halfWidth = width / 2f
        val viewHeight = height.toFloat()
        
        if (halfWidth == 0f || viewHeight == 0f) return
        
        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()
        
        baseScale = minOf(halfWidth / bmpWidth, viewHeight / bmpHeight)
    }
    
    private fun updateBounds() {
        val halfWidth = width / 2f
        val viewHeight = height.toFloat()
        
        leftBitmap?.let { bmp ->
            val scaledWidth = bmp.width * baseScale * currentScale
            val scaledHeight = bmp.height * baseScale * currentScale
            
            val left = (halfWidth - scaledWidth) / 2 + currentTranslateX
            val top = (viewHeight - scaledHeight) / 2 + currentTranslateY
            
            leftImageBounds.set(left, top, left + scaledWidth, top + scaledHeight)
        }
        
        rightBitmap?.let { bmp ->
            val scaledWidth = bmp.width * baseScale * currentScale
            val scaledHeight = bmp.height * baseScale * currentScale
            
            val left = halfWidth + (halfWidth - scaledWidth) / 2 + currentTranslateX
            val top = (viewHeight - scaledHeight) / 2 + currentTranslateY
            
            rightImageBounds.set(left, top, left + scaledWidth, top + scaledHeight)
        }
        
        // Notify bounds changed for keypoint overlay sync
        leftBitmap?.let { bmp ->
            onImageBoundsChangedListener?.invoke(leftImageBounds, bmp.width, bmp.height)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val halfWidth = width / 2f
        
        // Draw left image
        leftBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(0f, 0f, halfWidth, height.toFloat())
            
            leftMatrix.reset()
            leftMatrix.setScale(baseScale * currentScale, baseScale * currentScale)
            leftMatrix.postTranslate(leftImageBounds.left, leftImageBounds.top)
            
            canvas.drawBitmap(bmp, leftMatrix, leftPaint)
            canvas.restore()
        }
        
        // Draw right image
        rightBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(halfWidth, 0f, width.toFloat(), height.toFloat())
            
            rightMatrix.reset()
            rightMatrix.setScale(baseScale * currentScale, baseScale * currentScale)
            rightMatrix.postTranslate(rightImageBounds.left, rightImageBounds.top)
            
            canvas.drawBitmap(bmp, rightMatrix, rightPaint)
            canvas.restore()
        }
        
        // Draw divider
        canvas.drawLine(halfWidth, 0f, halfWidth, height.toFloat(), dividerPaint)
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val oldScale = currentScale
            val newScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
            
            if (newScale != oldScale) {
                val focusX = detector.focusX
                val focusY = detector.focusY
                
                // For side-by-side, use the center of view as reference
                val viewCenterX = width / 2f
                val viewCenterY = height / 2f
                
                val focusOffsetX = focusX - viewCenterX
                val focusOffsetY = focusY - viewCenterY
                
                val imagePointX = (focusOffsetX - currentTranslateX) / currentScale
                val imagePointY = (focusOffsetY - currentTranslateY) / currentScale
                
                currentScale = newScale
                
                currentTranslateX = focusOffsetX - imagePointX * currentScale
                currentTranslateY = focusOffsetY - imagePointY * currentScale
                
                updateBounds()
                onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
                invalidate()
            }
            
            return true
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            currentTranslateX -= distanceX
            currentTranslateY -= distanceY
            updateBounds()
            onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
            invalidate()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > 1.5f) {
                currentScale = 1f
                currentTranslateX = 0f
                currentTranslateY = 0f
            } else {
                val tapX = e.x
                val tapY = e.y
                val viewCenterX = width / 2f
                val viewCenterY = height / 2f
                
                val focusOffsetX = tapX - viewCenterX
                val focusOffsetY = tapY - viewCenterY
                
                val imagePointX = (focusOffsetX - currentTranslateX) / currentScale
                val imagePointY = (focusOffsetY - currentTranslateY) / currentScale
                
                currentScale = 2.5f
                
                currentTranslateX = focusOffsetX - imagePointX * currentScale
                currentTranslateY = focusOffsetY - imagePointY * currentScale
            }
            updateBounds()
            onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
            invalidate()
            return true
        }
    }
}
