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
import com.imgdiff.lib.models.Keypoint

/**
 * Custom view for displaying and selecting keypoints.
 * Supports both tap-to-select detected keypoints and tap-to-place manual keypoints.
 */
class KeypointOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        /** Tap to select from detected keypoints */
        SELECT,
        /** Tap to place new keypoints */
        DRAW,
        /** View only, no interaction */
        VIEW
    }
    
    var mode: Mode = Mode.VIEW
        set(value) {
            field = value
            invalidate()
        }
    
    // Keypoints to display
    private var _keypoints: List<Keypoint> = emptyList()
    val keypoints: List<Keypoint> get() = _keypoints
    
    // Selected keypoint indices
    private val _selectedIndices = mutableSetOf<Int>()
    val selectedKeypoints: List<Keypoint>
        get() = _selectedIndices.mapNotNull { idx -> _keypoints.getOrNull(idx) }
    
    // Manually placed keypoints (for DRAW mode)
    private val _manualKeypoints = mutableListOf<Keypoint>()
    val manualKeypoints: List<Keypoint> get() = _manualKeypoints.toList()
    
    // Image bounds
    private var imageBounds = RectF()
    private var imageWidth = 0
    private var imageHeight = 0
    
    // Paints
    private val autoKeypointPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keypoint_auto)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.keypoint_stroke_width)
        isAntiAlias = true
    }
    
    private val autoKeypointFillPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keypoint_auto)
        alpha = 80
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val manualKeypointPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keypoint_manual)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.keypoint_stroke_width)
        isAntiAlias = true
    }
    
    private val manualKeypointFillPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keypoint_manual)
        alpha = 80
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val selectedPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keypoint_selected)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.keypoint_stroke_width) * 1.5f
        isAntiAlias = true
    }
    
    private val selectedFillPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.keypoint_selected)
        alpha = 120
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = resources.getDimension(R.dimen.keypoint_radius)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    private val keypointRadius = resources.getDimension(R.dimen.keypoint_radius)
    private val touchTolerance = keypointRadius * 2.5f
    
    // Listeners
    var onKeypointSelectedListener: ((Keypoint, Boolean) -> Unit)? = null
    var onKeypointPlacedListener: ((Keypoint) -> Unit)? = null
    var onSelectionChangedListener: ((List<Keypoint>) -> Unit)? = null
    
    /**
     * Set detected keypoints to display.
     */
    fun setKeypoints(keypoints: List<Keypoint>) {
        _keypoints = keypoints
        _selectedIndices.clear()
        invalidate()
    }
    
    /**
     * Add a detected keypoint.
     */
    fun addKeypoint(keypoint: Keypoint) {
        _keypoints = _keypoints + keypoint
        invalidate()
    }
    
    /**
     * Toggle keypoint selection.
     */
    fun toggleSelection(keypointId: Int) {
        val index = _keypoints.indexOfFirst { it.id == keypointId }
        if (index >= 0) {
            if (_selectedIndices.contains(index)) {
                _selectedIndices.remove(index)
            } else {
                _selectedIndices.add(index)
            }
            onSelectionChangedListener?.invoke(selectedKeypoints)
            invalidate()
        }
    }
    
    /**
     * Select a specific keypoint.
     */
    fun selectKeypoint(keypointId: Int) {
        val index = _keypoints.indexOfFirst { it.id == keypointId }
        if (index >= 0 && !_selectedIndices.contains(index)) {
            _selectedIndices.add(index)
            onSelectionChangedListener?.invoke(selectedKeypoints)
            invalidate()
        }
    }
    
    /**
     * Clear all selections.
     */
    fun clearSelection() {
        _selectedIndices.clear()
        onSelectionChangedListener?.invoke(emptyList())
        invalidate()
    }
    
    /**
     * Clear manual keypoints.
     */
    fun clearManualKeypoints() {
        _manualKeypoints.clear()
        invalidate()
    }
    
    /**
     * Add a manual keypoint at the specified image coordinates.
     */
    fun addManualKeypoint(x: Float, y: Float) {
        val keypoint = Keypoint.manual(
            id = _manualKeypoints.size,
            x = x,
            y = y
        )
        _manualKeypoints.add(keypoint)
        onKeypointPlacedListener?.invoke(keypoint)
        invalidate()
    }
    
    /**
     * Set image bounds for coordinate conversion.
     */
    fun setImageBounds(bounds: RectF, imgWidth: Int, imgHeight: Int) {
        imageBounds = bounds
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }
    
    /**
     * Set image bounds from dimensions.
     */
    fun setImageBounds(imgWidth: Int, imgHeight: Int) {
        imageWidth = imgWidth
        imageHeight = imgHeight
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
        
        if (viewWidth == 0f || viewHeight == 0f || imageWidth == 0 || imageHeight == 0) {
            imageBounds = RectF(0f, 0f, viewWidth, viewHeight)
            return
        }
        
        val imageAspect = imageWidth.toFloat() / imageHeight
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
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (imageBounds.isEmpty) {
            imageBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        }
        
        // Draw detected keypoints
        _keypoints.forEachIndexed { index, keypoint ->
            val isSelected = _selectedIndices.contains(index)
            val viewX = imageToViewX(keypoint.x)
            val viewY = imageToViewY(keypoint.y)
            
            val radius = if (isSelected) keypointRadius * 1.3f else keypointRadius
            
            // Draw fill
            canvas.drawCircle(
                viewX, viewY, radius,
                if (isSelected) selectedFillPaint else autoKeypointFillPaint
            )
            
            // Draw stroke
            canvas.drawCircle(
                viewX, viewY, radius,
                if (isSelected) selectedPaint else autoKeypointPaint
            )
            
            // Draw keypoint index for selected points
            if (isSelected) {
                val selectionOrder = _selectedIndices.toList().indexOf(index) + 1
                canvas.drawText(
                    selectionOrder.toString(),
                    viewX,
                    viewY + textPaint.textSize / 3,
                    textPaint
                )
            }
        }
        
        // Draw manual keypoints
        _manualKeypoints.forEachIndexed { index, keypoint ->
            val viewX = imageToViewX(keypoint.x)
            val viewY = imageToViewY(keypoint.y)
            
            // Draw fill
            canvas.drawCircle(viewX, viewY, keypointRadius, manualKeypointFillPaint)
            
            // Draw stroke
            canvas.drawCircle(viewX, viewY, keypointRadius, manualKeypointPaint)
            
            // Draw number
            canvas.drawText(
                (index + 1).toString(),
                viewX,
                viewY + textPaint.textSize / 3,
                textPaint
            )
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == Mode.VIEW) {
            return false
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                val touchX = event.x
                val touchY = event.y
                
                when (mode) {
                    Mode.SELECT -> {
                        // Find closest keypoint
                        val closest = findClosestKeypoint(touchX, touchY)
                        if (closest != null) {
                            toggleSelection(closest.id)
                            onKeypointSelectedListener?.invoke(
                                closest,
                                _selectedIndices.contains(_keypoints.indexOfFirst { it.id == closest.id })
                            )
                        }
                    }
                    
                    Mode.DRAW -> {
                        // Check if touch is within image bounds
                        if (imageBounds.contains(touchX, touchY)) {
                            val imageX = viewToImageX(touchX)
                            val imageY = viewToImageY(touchY)
                            addManualKeypoint(imageX, imageY)
                        }
                    }
                    
                    Mode.VIEW -> { /* No action */ }
                }
                
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    private fun findClosestKeypoint(viewX: Float, viewY: Float): Keypoint? {
        var closest: Keypoint? = null
        var minDist = touchTolerance
        
        for (keypoint in _keypoints) {
            val kpViewX = imageToViewX(keypoint.x)
            val kpViewY = imageToViewY(keypoint.y)
            
            val dx = viewX - kpViewX
            val dy = viewY - kpViewY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            
            if (dist < minDist) {
                minDist = dist
                closest = keypoint
            }
        }
        
        return closest
    }
    
    private fun imageToViewX(imageX: Float): Float {
        if (imageWidth == 0) return imageX
        return imageBounds.left + (imageX / imageWidth) * imageBounds.width()
    }
    
    private fun imageToViewY(imageY: Float): Float {
        if (imageHeight == 0) return imageY
        return imageBounds.top + (imageY / imageHeight) * imageBounds.height()
    }
    
    private fun viewToImageX(viewX: Float): Float {
        if (imageBounds.width() == 0f) return viewX
        return ((viewX - imageBounds.left) / imageBounds.width()) * imageWidth
    }
    
    private fun viewToImageY(viewY: Float): Float {
        if (imageBounds.height() == 0f) return viewY
        return ((viewY - imageBounds.top) / imageBounds.height()) * imageHeight
    }
}

