package com.imgdiff.app

import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.imgdiff.app.databinding.ActivityCompareBinding
import com.imgdiff.app.ui.components.KeypointOverlayView
import com.imgdiff.app.viewmodel.CompareViewModel
import com.imgdiff.lib.models.DiffMode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for comparing two images.
 * Provides tools for ROI selection, keypoint detection/selection,
 * image alignment, and difference visualization.
 */
class CompareActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_IMAGE_URI = "first_image_uri"
        const val EXTRA_SECOND_IMAGE_URI = "second_image_uri"
    }

    private lateinit var binding: ActivityCompareBinding
    private val viewModel: CompareViewModel by viewModels()
    
    // Current editing target for keypoints (source or target)
    private var isEditingSourceKeypoints = true
    
    // Current overlay alpha (0-100)
    private var overlayAlpha = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        binding = ActivityCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupViews()
        setupClickListeners()
        observeViewModel()
        
        // Load images from intent
        loadImages()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = systemBars.left, right = systemBars.right)
            insets
        }
        
        // Apply bottom inset to control panel
        ViewCompat.setOnApplyWindowInsetsListener(binding.controlPanel) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = view.paddingBottom + systemBars.bottom)
            insets
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupViews() {
        // Setup zoomable image view transform listener
        binding.zoomableImageView.onTransformChangedListener = { scale, translateX, translateY ->
            // Sync transform to other views
            binding.sideBySideView.setTransformation(scale, translateX, translateY)
            binding.diffSlider.setTransformation(scale, translateX, translateY)
            
            // Update overlays
            updateOverlayBounds()
        }
        
        binding.zoomableImageView.onImageBoundsChangedListener = { bounds, imgWidth, imgHeight ->
            syncOverlayBounds(bounds, imgWidth, imgHeight)
        }
        
        // Setup side by side view transform listener
        binding.sideBySideView.onTransformChangedListener = { scale, translateX, translateY ->
            binding.zoomableImageView.setTransformation(scale, translateX, translateY)
            binding.diffSlider.setTransformation(scale, translateX, translateY)
        }
        
        binding.sideBySideView.onImageBoundsChangedListener = { bounds, imgWidth, imgHeight ->
            syncOverlayBounds(bounds, imgWidth, imgHeight)
        }
        
        // Setup slider view transform listener
        binding.diffSlider.onTransformChangedListener = { scale, translateX, translateY ->
            binding.zoomableImageView.setTransformation(scale, translateX, translateY)
            binding.sideBySideView.setTransformation(scale, translateX, translateY)
        }
        
        binding.diffSlider.onImageBoundsChangedListener = { bounds, imgWidth, imgHeight ->
            syncOverlayBounds(bounds, imgWidth, imgHeight)
        }
        
        // Setup overlay alpha slider
        binding.sliderOverlayAlpha.addOnChangeListener { _, value, _ ->
            overlayAlpha = value.toInt()
            binding.textOverlayAlphaValue.text = "${overlayAlpha}%"
            binding.zoomableImageView.overlayAlpha = (overlayAlpha * 255 / 100)
        }
        binding.textOverlayAlphaValue.text = "${overlayAlpha}%"
    }
    
    private fun syncOverlayBounds(bounds: RectF, imgWidth: Int, imgHeight: Int) {
        binding.keypointOverlay.setImageBounds(bounds, imgWidth, imgHeight)
        binding.roiSelector.setImageBounds(bounds)
    }
    
    private fun updateOverlayBounds() {
        val bounds = binding.zoomableImageView.getImageBounds()
        val (imgWidth, imgHeight) = binding.zoomableImageView.getImageSize()
        if (imgWidth > 0 && imgHeight > 0) {
            syncOverlayBounds(bounds, imgWidth, imgHeight)
        }
    }
    
    private fun setupClickListeners() {
        // Alignment mode toggle
        binding.toggleAlignmentMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnAutoAlign -> {
                        viewModel.setAlignmentMode(CompareViewModel.AlignmentMode.AUTO)
                        binding.keypointOverlay.mode = KeypointOverlayView.Mode.VIEW
                    }
                    R.id.btnManualAlign -> {
                        viewModel.setAlignmentMode(CompareViewModel.AlignmentMode.MANUAL)
                        binding.keypointOverlay.mode = KeypointOverlayView.Mode.DRAW
                        binding.keypointOverlay.visibility = View.VISIBLE
                    }
                }
            }
        }
        
        // ROI button
        binding.btnSelectROI.setOnClickListener {
            viewModel.toggleROIMode()
        }
        
        // Detect keypoints button
        binding.btnDetectKeypoints.setOnClickListener {
            viewModel.detectKeypoints()
        }
        
        // Align button
        binding.btnAlign.setOnClickListener {
            viewModel.alignImages()
        }
        
        // Show diff button
        binding.btnShowDiff.setOnClickListener {
            val mode = when {
                binding.chipOverlay.isChecked -> DiffMode.OVERLAY
                binding.chipSideBySide.isChecked -> DiffMode.PIXEL_DIFF
                binding.chipSlider.isChecked -> DiffMode.ABSOLUTE
                else -> DiffMode.OVERLAY
            }
            viewModel.calculateDiff(mode)
        }
        
        // View mode chips
        binding.chipGroupViewMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (checkedId) {
                R.id.chipOverlay -> viewModel.setViewMode(CompareViewModel.ViewMode.OVERLAY)
                R.id.chipSideBySide -> viewModel.setViewMode(CompareViewModel.ViewMode.SIDE_BY_SIDE)
                R.id.chipSlider -> viewModel.setViewMode(CompareViewModel.ViewMode.SLIDER)
            }
        }
        
        // ROI selector callback
        binding.roiSelector.onROIChangedListener = { roi ->
            viewModel.setROI(roi)
        }
        
        // Keypoint overlay callbacks
        binding.keypointOverlay.onKeypointPlacedListener = { keypoint ->
            if (isEditingSourceKeypoints) {
                viewModel.addManualSourceKeypoint(keypoint)
                // Switch to target for next keypoint
                isEditingSourceKeypoints = false
                updateStatusText()
            } else {
                viewModel.addManualTargetKeypoint(keypoint)
                isEditingSourceKeypoints = true
                updateStatusText()
            }
        }
        
        binding.keypointOverlay.onSelectionChangedListener = { selected ->
            if (isEditingSourceKeypoints) {
                viewModel.setSelectedSourceKeypoints(selected)
            } else {
                viewModel.setSelectedTargetKeypoints(selected)
            }
            updateStatusText()
        }
    }
    
    private fun loadImages() {
        val firstUri = intent.getStringExtra(EXTRA_FIRST_IMAGE_URI)?.let { Uri.parse(it) }
        val secondUri = intent.getStringExtra(EXTRA_SECOND_IMAGE_URI)?.let { Uri.parse(it) }
        
        if (firstUri != null && secondUri != null) {
            viewModel.loadImages(firstUri, secondUri)
        } else {
            Toast.makeText(this, R.string.error_no_images, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Source image
                launch {
                    viewModel.sourceImage.collectLatest { bitmap ->
                        bitmap?.let {
                            binding.zoomableImageView.setBaseImage(it)
                            
                            // Update other views
                            val target = viewModel.targetImage.value
                            binding.sideBySideView.setImages(it, target)
                            binding.diffSlider.setImages(it, target)
                            
                            // Use post to ensure the view has been laid out
                            binding.zoomableImageView.post {
                                updateOverlayBounds()
                                binding.roiSelector.setImageBounds(it.width, it.height)
                                binding.keypointOverlay.setImageBounds(it.width, it.height)
                            }
                        }
                    }
                }
                
                // Target image
                launch {
                    viewModel.targetImage.collectLatest { bitmap ->
                        val source = viewModel.sourceImage.value
                        if (source != null && bitmap != null) {
                            binding.zoomableImageView.setOverlayImage(bitmap, overlayAlpha * 255 / 100)
                            binding.sideBySideView.setImages(source, bitmap)
                            binding.diffSlider.setImages(source, bitmap)
                        }
                    }
                }
                
                // Aligned image
                launch {
                    viewModel.alignedImage.collectLatest { bitmap ->
                        val source = viewModel.sourceImage.value
                        if (source != null && bitmap != null) {
                            binding.zoomableImageView.setOverlayImage(bitmap, overlayAlpha * 255 / 100)
                            binding.sideBySideView.setImages(source, bitmap)
                            binding.diffSlider.setImages(source, bitmap)
                        }
                    }
                }
                
                // Diff result
                launch {
                    viewModel.diffResult.collectLatest { result ->
                        if (result != null) {
                            updateDiffVisualization()
                        }
                    }
                }
                
                // View mode
                launch {
                    viewModel.viewMode.collectLatest { mode ->
                        updateViewMode(mode)
                    }
                }
                
                // ROI mode
                launch {
                    viewModel.isROIMode.collectLatest { isActive ->
                        binding.roiSelector.visibility = if (isActive) View.VISIBLE else View.GONE
                        binding.btnSelectROI.text = if (isActive) {
                            getString(R.string.clear_roi)
                        } else {
                            getString(R.string.select_roi)
                        }
                    }
                }
                
                // ROI value
                launch {
                    viewModel.roi.collectLatest { roi ->
                        if (roi != null) {
                            binding.roiSelector.setROI(roi)
                        }
                    }
                }
                
                // Keypoints
                launch {
                    viewModel.sourceKeypoints.collectLatest { keypoints ->
                        if (viewModel.alignmentMode.value == CompareViewModel.AlignmentMode.AUTO) {
                            binding.keypointOverlay.setKeypoints(keypoints)
                            binding.keypointOverlay.visibility = 
                                if (keypoints.isNotEmpty()) View.VISIBLE else View.GONE
                        }
                        updateStatusText()
                    }
                }
                
                // Loading state
                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                // Loading message
                launch {
                    viewModel.loadingMessage.collectLatest { message ->
                        binding.loadingText.text = message
                    }
                }
                
                // Error messages
                launch {
                    viewModel.errorMessage.collectLatest { error ->
                        if (error != null) {
                            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                                .setAction("Dismiss") { viewModel.clearError() }
                                .show()
                        }
                    }
                }
                
                // Alignment result
                launch {
                    viewModel.alignmentResult.collectLatest { result ->
                        if (result != null && result.isSuccessful) {
                            val count = result.matchedKeypoints.size
                            val inliers = result.inlierCount
                            binding.statusText.text = "Aligned with $inliers/$count matches"
                            binding.statusText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
    
    private fun updateStatusText() {
        val mode = viewModel.alignmentMode.value
        val sourceKps = viewModel.sourceKeypoints.value.size
        val manualSource = viewModel.manualSourceKeypoints.value.size
        val manualTarget = viewModel.manualTargetKeypoints.value.size
        
        val text = when (mode) {
            CompareViewModel.AlignmentMode.AUTO -> {
                if (sourceKps > 0) {
                    getString(R.string.keypoints_detected, sourceKps)
                } else {
                    ""
                }
            }
            CompareViewModel.AlignmentMode.MANUAL -> {
                val editingLabel = if (isEditingSourceKeypoints) "source" else "target"
                "Manual mode: $manualSource source, $manualTarget target pts. Now editing: $editingLabel"
            }
        }
        
        binding.statusText.text = text
        binding.statusText.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun updateViewMode(mode: CompareViewModel.ViewMode) {
        val source = viewModel.sourceImage.value
        val aligned = viewModel.alignedImage.value ?: viewModel.targetImage.value
        
        // Hide all visualization views first
        binding.zoomableImageView.visibility = View.GONE
        binding.sideBySideView.visibility = View.GONE
        binding.diffSlider.visibility = View.GONE
        binding.overlayAlphaContainer.visibility = View.GONE
        
        when (mode) {
            CompareViewModel.ViewMode.OVERLAY -> {
                binding.zoomableImageView.visibility = View.VISIBLE
                binding.overlayAlphaContainer.visibility = View.VISIBLE
                
                if (source != null) {
                    binding.zoomableImageView.setBaseImage(source)
                    if (aligned != null) {
                        binding.zoomableImageView.setOverlayImage(aligned, overlayAlpha * 255 / 100)
                    }
                }
            }
            
            CompareViewModel.ViewMode.SIDE_BY_SIDE -> {
                binding.sideBySideView.visibility = View.VISIBLE
                binding.sideBySideView.setImages(source, aligned)
            }
            
            CompareViewModel.ViewMode.SLIDER -> {
                binding.diffSlider.visibility = View.VISIBLE
                binding.diffSlider.setImages(source, aligned)
                binding.diffSlider.beforeLabel = "Source"
                binding.diffSlider.afterLabel = "Target"
            }
        }
        
        // Update overlay bounds after mode change
        binding.zoomableImageView.post {
            updateOverlayBounds()
        }
    }
    
    private fun updateDiffVisualization() {
        val diffResult = viewModel.diffResult.value ?: return
        val mode = viewModel.viewMode.value
        
        when (mode) {
            CompareViewModel.ViewMode.OVERLAY -> {
                binding.zoomableImageView.setBaseImage(diffResult.diffVisualization)
                binding.zoomableImageView.setOverlayImage(null)
                binding.overlayAlphaContainer.visibility = View.GONE
            }
            
            CompareViewModel.ViewMode.SLIDER -> {
                binding.diffSlider.setImages(
                    diffResult.sourceImage,
                    diffResult.diffVisualization
                )
                binding.diffSlider.beforeLabel = "Source"
                binding.diffSlider.afterLabel = "Diff: ${String.format("%.1f", diffResult.diffPercentage)}%"
            }
            
            CompareViewModel.ViewMode.SIDE_BY_SIDE -> {
                binding.sideBySideView.setImages(diffResult.sourceImage, diffResult.diffVisualization)
            }
        }
        
        // Show stats
        val statsText = "Diff: ${String.format("%.1f", diffResult.diffPercentage)}% | " +
                       "${diffResult.diffPixelCount} pixels"
        binding.statusText.text = statsText
        binding.statusText.visibility = View.VISIBLE
    }
}
