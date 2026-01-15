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
 * A zoomable and pannable image view that can display one or two images as overlays.
 * Supports synchronized zoom/pan state sharing between instances.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var baseBitmap: Bitmap? = null
    private var overlayBitmap: Bitmap? = null
    
    private val basePaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    private val overlayPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    // Transformation state
    private var currentScale = 1f
    private var currentTranslateX = 0f
    private var currentTranslateY = 0f
    
    private val minScale = 0.5f
    private val maxScale = 10f
    
    // Calculated image bounds (where the image is drawn in view coordinates)
    private var imageBounds = RectF()
    
    // Base scale to fit image in view
    private var baseScale = 1f
    
    // Gesture detectors
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetectorCompat
    
    // Matrix for drawing
    private val drawMatrix = Matrix()
    
    // Overlay alpha (0-255)
    var overlayAlpha: Int = 128
        set(value) {
            field = value.coerceIn(0, 255)
            overlayPaint.alpha = field
            invalidate()
        }
    
    // Listener for transform changes (for syncing between views)
    var onTransformChangedListener: ((scale: Float, translateX: Float, translateY: Float) -> Unit)? = null
    
    // Listener for getting current image bounds (for keypoint overlay sync)
    var onImageBoundsChangedListener: ((RectF, Int, Int) -> Unit)? = null
    
    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetectorCompat(context, GestureListener())
        overlayPaint.alpha = overlayAlpha
    }
    
    /**
     * Set the base image.
     */
    fun setBaseImage(bitmap: Bitmap?) {
        baseBitmap = bitmap
        if (bitmap != null) {
            calculateBaseScale()
            resetTransformation()
        }
        invalidate()
    }
    
    /**
     * Set the overlay image with alpha.
     */
    fun setOverlayImage(bitmap: Bitmap?, alpha: Int = overlayAlpha) {
        overlayBitmap = bitmap
        overlayAlpha = alpha
        invalidate()
    }
    
    /**
     * Set transformation state (for syncing with other views).
     */
    fun setTransformation(scale: Float, translateX: Float, translateY: Float) {
        currentScale = scale.coerceIn(minScale, maxScale)
        currentTranslateX = translateX
        currentTranslateY = translateY
        updateImageBounds()
        invalidate()
    }
    
    /**
     * Get current transformation state.
     */
    fun getTransformation(): Triple<Float, Float, Float> {
        return Triple(currentScale, currentTranslateX, currentTranslateY)
    }
    
    /**
     * Reset zoom and pan to default.
     */
    fun resetTransformation() {
        currentScale = 1f
        currentTranslateX = 0f
        currentTranslateY = 0f
        updateImageBounds()
        onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
        invalidate()
    }
    
    /**
     * Get the current image bounds in view coordinates.
     */
    fun getImageBounds(): RectF = RectF(imageBounds)
    
    /**
     * Get the image dimensions.
     */
    fun getImageSize(): Pair<Int, Int> {
        val bmp = baseBitmap ?: return Pair(0, 0)
        return Pair(bmp.width, bmp.height)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateBaseScale()
        updateImageBounds()
    }
    
    private fun calculateBaseScale() {
        val bmp = baseBitmap ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth == 0f || viewHeight == 0f) return
        
        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()
        
        baseScale = minOf(viewWidth / bmpWidth, viewHeight / bmpHeight)
    }
    
    private fun updateImageBounds() {
        val bmp = baseBitmap ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth == 0f || viewHeight == 0f) return
        
        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()
        
        val scaledWidth = bmpWidth * baseScale * currentScale
        val scaledHeight = bmpHeight * baseScale * currentScale
        
        val left = (viewWidth - scaledWidth) / 2 + currentTranslateX
        val top = (viewHeight - scaledHeight) / 2 + currentTranslateY
        
        imageBounds.set(left, top, left + scaledWidth, top + scaledHeight)
        
        onImageBoundsChangedListener?.invoke(imageBounds, bmp.width, bmp.height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val bmp = baseBitmap ?: return
        
        // Build transformation matrix
        drawMatrix.reset()
        drawMatrix.setScale(baseScale * currentScale, baseScale * currentScale)
        drawMatrix.postTranslate(imageBounds.left, imageBounds.top)
        
        // Draw base image
        canvas.drawBitmap(bmp, drawMatrix, basePaint)
        
        // Draw overlay image if present
        overlayBitmap?.let { overlay ->
            canvas.drawBitmap(overlay, drawMatrix, overlayPaint)
        }
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
                
                // Calculate the point under the focus in image-relative coordinates
                // before scaling (relative to view center)
                val viewCenterX = width / 2f
                val viewCenterY = height / 2f
                
                // Distance from view center to focus point
                val focusOffsetX = focusX - viewCenterX
                val focusOffsetY = focusY - viewCenterY
                
                // Point in image space before scaling
                val imagePointX = (focusOffsetX - currentTranslateX) / currentScale
                val imagePointY = (focusOffsetY - currentTranslateY) / currentScale
                
                // Apply new scale
                currentScale = newScale
                
                // Adjust translation so the same image point stays under focus
                currentTranslateX = focusOffsetX - imagePointX * currentScale
                currentTranslateY = focusOffsetY - imagePointY * currentScale
                
                updateImageBounds()
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
            updateImageBounds()
            onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
            invalidate()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > 1.5f) {
                resetTransformation()
            } else {
                // Zoom to 2x centered on tap point
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
                
                updateImageBounds()
                onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
                invalidate()
            }
            return true
        }
    }
}
