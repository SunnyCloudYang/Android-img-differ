package com.imgdiff.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.imgdiff.app.R

/**
 * Custom view for slider-based before/after image comparison with zoom and pan support.
 * Displays two images with a draggable divider to reveal the difference.
 */
class DiffSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null
    
    private val beforePaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    private val afterPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    private val dividerPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, 0x80000000.toInt())
    }
    
    private val handlePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_primary)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val handleArrowPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val labelPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = resources.getDimension(R.dimen.spacing_md)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(2f, 1f, 1f, 0xFF000000.toInt())
    }
    
    // Slider position (0-1)
    private var sliderPosition = 0.5f
    
    private val dividerWidth = resources.getDimension(R.dimen.slider_divider_width)
    private val handleSize = resources.getDimension(R.dimen.slider_handle_size)
    
    // Image bounds (current view coordinates after transform)
    private var imageBounds = RectF()
    
    // Transformation state
    private var currentScale = 1f
    private var currentTranslateX = 0f
    private var currentTranslateY = 0f
    private var baseScale = 1f
    
    private val minScale = 0.5f
    private val maxScale = 10f
    
    // Gesture detectors
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetectorCompat
    
    // Touch handling
    private var isDraggingSlider = false
    private var isTransforming = false
    
    // Matrix for drawing
    private val drawMatrix = Matrix()
    
    // Labels
    var beforeLabel = "Before"
    var afterLabel = "After"
    var showLabels = true
    
    // Listeners
    var onSliderPositionChangedListener: ((Float) -> Unit)? = null
    var onTransformChangedListener: ((scale: Float, translateX: Float, translateY: Float) -> Unit)? = null
    var onImageBoundsChangedListener: ((RectF, Int, Int) -> Unit)? = null
    
    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetectorCompat(context, GestureListener())
    }
    
    /**
     * Set the before (left) image.
     */
    fun setBeforeImage(bitmap: Bitmap?) {
        beforeBitmap = bitmap
        calculateBaseScale()
        updateImageBounds()
        invalidate()
    }
    
    /**
     * Set the after (right) image.
     */
    fun setAfterImage(bitmap: Bitmap?) {
        afterBitmap = bitmap
        calculateBaseScale()
        updateImageBounds()
        invalidate()
    }
    
    /**
     * Set both images at once.
     */
    fun setImages(before: Bitmap?, after: Bitmap?) {
        beforeBitmap = before
        afterBitmap = after
        calculateBaseScale()
        resetTransformation()
        invalidate()
    }
    
    /**
     * Set slider position (0-1).
     */
    fun setSliderPosition(position: Float) {
        sliderPosition = position.coerceIn(0f, 1f)
        onSliderPositionChangedListener?.invoke(sliderPosition)
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
     * Reset slider and transformation.
     */
    fun reset() {
        sliderPosition = 0.5f
        resetTransformation()
        onSliderPositionChangedListener?.invoke(sliderPosition)
        invalidate()
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
    }
    
    /**
     * Get current image bounds.
     */
    fun getImageBounds(): RectF = RectF(imageBounds)
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateBaseScale()
        updateImageBounds()
    }
    
    private fun calculateBaseScale() {
        val bitmap = beforeBitmap ?: afterBitmap ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth == 0f || viewHeight == 0f) return
        
        val bmpWidth = bitmap.width.toFloat()
        val bmpHeight = bitmap.height.toFloat()
        
        baseScale = minOf(viewWidth / bmpWidth, viewHeight / bmpHeight)
    }
    
    private fun updateImageBounds() {
        val bitmap = beforeBitmap ?: afterBitmap ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth == 0f || viewHeight == 0f) return
        
        val bmpWidth = bitmap.width.toFloat()
        val bmpHeight = bitmap.height.toFloat()
        
        val scaledWidth = bmpWidth * baseScale * currentScale
        val scaledHeight = bmpHeight * baseScale * currentScale
        
        val left = (viewWidth - scaledWidth) / 2 + currentTranslateX
        val top = (viewHeight - scaledHeight) / 2 + currentTranslateY
        
        imageBounds.set(left, top, left + scaledWidth, top + scaledHeight)
        
        onImageBoundsChangedListener?.invoke(imageBounds, bitmap.width, bitmap.height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (beforeBitmap == null && afterBitmap == null) return
        
        val sliderX = imageBounds.left + sliderPosition * imageBounds.width()
        
        // Build transformation matrix
        drawMatrix.reset()
        drawMatrix.setScale(baseScale * currentScale, baseScale * currentScale)
        drawMatrix.postTranslate(imageBounds.left, imageBounds.top)
        
        // Draw after image (right side / revealed side)
        afterBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(sliderX, imageBounds.top, imageBounds.right, imageBounds.bottom)
            canvas.drawBitmap(bmp, drawMatrix, afterPaint)
            canvas.restore()
        }
        
        // Draw before image (left side)
        beforeBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(imageBounds.left, imageBounds.top, sliderX, imageBounds.bottom)
            canvas.drawBitmap(bmp, drawMatrix, beforePaint)
            canvas.restore()
        }
        
        // Draw divider line
        canvas.drawRect(
            sliderX - dividerWidth / 2,
            imageBounds.top,
            sliderX + dividerWidth / 2,
            imageBounds.bottom,
            dividerPaint
        )
        
        // Draw handle
        val handleY = imageBounds.centerY()
        canvas.drawCircle(sliderX, handleY, handleSize / 2, handlePaint)
        
        // Draw arrows on handle
        val arrowSize = handleSize / 4
        // Left arrow
        val leftArrowPath = Path().apply {
            moveTo(sliderX - arrowSize / 2, handleY)
            lineTo(sliderX - arrowSize / 2 + arrowSize / 2, handleY - arrowSize / 2)
            lineTo(sliderX - arrowSize / 2 + arrowSize / 2, handleY + arrowSize / 2)
            close()
        }
        canvas.drawPath(leftArrowPath, handleArrowPaint)
        
        // Right arrow
        val rightArrowPath = Path().apply {
            moveTo(sliderX + arrowSize / 2, handleY)
            lineTo(sliderX + arrowSize / 2 - arrowSize / 2, handleY - arrowSize / 2)
            lineTo(sliderX + arrowSize / 2 - arrowSize / 2, handleY + arrowSize / 2)
            close()
        }
        canvas.drawPath(rightArrowPath, handleArrowPaint)
        
        // Draw labels
        if (showLabels) {
            val labelY = imageBounds.top + labelPaint.textSize + 8
            
            // Before label (left side)
            if (sliderPosition > 0.15f) {
                val beforeLabelX = imageBounds.left + (sliderX - imageBounds.left) / 2
                canvas.drawText(beforeLabel, beforeLabelX, labelY, labelPaint)
            }
            
            // After label (right side)
            if (sliderPosition < 0.85f) {
                val afterLabelX = sliderX + (imageBounds.right - sliderX) / 2
                canvas.drawText(afterLabel, afterLabelX, labelY, labelPaint)
            }
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Check if touch is on slider handle first
        if (event.action == MotionEvent.ACTION_DOWN) {
            val sliderX = imageBounds.left + sliderPosition * imageBounds.width()
            val touchDist = kotlin.math.abs(event.x - sliderX)
            
            if (touchDist < handleSize * 1.5f) {
                isDraggingSlider = true
                isTransforming = false
                updateSliderPosition(event.x)
                return true
            }
        }
        
        if (isDraggingSlider) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    updateSliderPosition(event.x)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingSlider = false
                    return true
                }
            }
            return true
        }
        
        // Handle zoom/pan gestures
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }
    
    private fun updateSliderPosition(touchX: Float) {
        val newPosition = ((touchX - imageBounds.left) / imageBounds.width()).coerceIn(0f, 1f)
        if (newPosition != sliderPosition) {
            sliderPosition = newPosition
            onSliderPositionChangedListener?.invoke(sliderPosition)
            invalidate()
        }
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
                val viewCenterX = width / 2f
                val viewCenterY = height / 2f
                
                val focusOffsetX = focusX - viewCenterX
                val focusOffsetY = focusY - viewCenterY
                
                val imagePointX = (focusOffsetX - currentTranslateX) / currentScale
                val imagePointY = (focusOffsetY - currentTranslateY) / currentScale
                
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
            if (!isDraggingSlider) {
                currentTranslateX -= distanceX
                currentTranslateY -= distanceY
                updateImageBounds()
                onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
                invalidate()
            }
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
            updateImageBounds()
            onTransformChangedListener?.invoke(currentScale, currentTranslateX, currentTranslateY)
            invalidate()
            return true
        }
    }
}
