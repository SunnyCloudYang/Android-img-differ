package com.imgdiff.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imgdiff.lib.ImageDiffer
import com.imgdiff.lib.models.AlignmentResult
import com.imgdiff.lib.models.DiffMode
import com.imgdiff.lib.models.DiffResult
import com.imgdiff.lib.models.Keypoint
import com.imgdiff.lib.models.KeypointPair
import com.imgdiff.lib.models.KeypointResult
import com.imgdiff.lib.models.ROI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for CompareActivity.
 * Manages image loading, alignment, and diff calculation state.
 */
class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val imageDiffer = ImageDiffer()
    
    // Image state
    private val _sourceImage = MutableStateFlow<Bitmap?>(null)
    val sourceImage: StateFlow<Bitmap?> = _sourceImage.asStateFlow()
    
    private val _targetImage = MutableStateFlow<Bitmap?>(null)
    val targetImage: StateFlow<Bitmap?> = _targetImage.asStateFlow()
    
    private val _alignedImage = MutableStateFlow<Bitmap?>(null)
    val alignedImage: StateFlow<Bitmap?> = _alignedImage.asStateFlow()
    
    // ROI state
    private val _roi = MutableStateFlow<ROI?>(null)
    val roi: StateFlow<ROI?> = _roi.asStateFlow()
    
    // Keypoints state
    private val _sourceKeypoints = MutableStateFlow<List<Keypoint>>(emptyList())
    val sourceKeypoints: StateFlow<List<Keypoint>> = _sourceKeypoints.asStateFlow()
    
    private val _targetKeypoints = MutableStateFlow<List<Keypoint>>(emptyList())
    val targetKeypoints: StateFlow<List<Keypoint>> = _targetKeypoints.asStateFlow()
    
    private val _selectedSourceKeypoints = MutableStateFlow<List<Keypoint>>(emptyList())
    val selectedSourceKeypoints: StateFlow<List<Keypoint>> = _selectedSourceKeypoints.asStateFlow()
    
    private val _selectedTargetKeypoints = MutableStateFlow<List<Keypoint>>(emptyList())
    val selectedTargetKeypoints: StateFlow<List<Keypoint>> = _selectedTargetKeypoints.asStateFlow()
    
    private val _manualSourceKeypoints = MutableStateFlow<List<Keypoint>>(emptyList())
    val manualSourceKeypoints: StateFlow<List<Keypoint>> = _manualSourceKeypoints.asStateFlow()
    
    private val _manualTargetKeypoints = MutableStateFlow<List<Keypoint>>(emptyList())
    val manualTargetKeypoints: StateFlow<List<Keypoint>> = _manualTargetKeypoints.asStateFlow()
    
    // Alignment state
    private val _alignmentResult = MutableStateFlow<AlignmentResult?>(null)
    val alignmentResult: StateFlow<AlignmentResult?> = _alignmentResult.asStateFlow()
    
    // Diff state
    private val _diffResult = MutableStateFlow<DiffResult?>(null)
    val diffResult: StateFlow<DiffResult?> = _diffResult.asStateFlow()
    
    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _alignmentMode = MutableStateFlow(AlignmentMode.AUTO)
    val alignmentMode: StateFlow<AlignmentMode> = _alignmentMode.asStateFlow()
    
    private val _viewMode = MutableStateFlow(ViewMode.OVERLAY)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()
    
    private val _isROIMode = MutableStateFlow(false)
    val isROIMode: StateFlow<Boolean> = _isROIMode.asStateFlow()
    
    enum class AlignmentMode {
        AUTO,
        MANUAL
    }
    
    enum class ViewMode {
        OVERLAY,
        SIDE_BY_SIDE,
        SLIDER
    }
    
    /**
     * Load images from URIs.
     */
    fun loadImages(sourceUri: Uri, targetUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Loading images..."
            
            try {
                val context = getApplication<Application>()
                
                val source = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                
                val target = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(targetUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                
                if (source != null && target != null) {
                    _sourceImage.value = source
                    _targetImage.value = target
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Failed to load one or both images"
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Error loading images: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Set the ROI.
     */
    fun setROI(roi: ROI?) {
        _roi.value = roi
    }
    
    /**
     * Toggle ROI selection mode.
     */
    fun toggleROIMode() {
        _isROIMode.value = !_isROIMode.value
        if (_isROIMode.value && _roi.value == null) {
            // Use centered ROI by default (25% margin on each side)
            _roi.value = ROI(0.25f, 0.25f, 0.75f, 0.75f)
        }
    }
    
    /**
     * Clear the ROI.
     */
    fun clearROI() {
        _roi.value = null
        _isROIMode.value = false
    }
    
    /**
     * Set alignment mode.
     */
    fun setAlignmentMode(mode: AlignmentMode) {
        _alignmentMode.value = mode
        // Clear keypoints when switching modes
        _selectedSourceKeypoints.value = emptyList()
        _selectedTargetKeypoints.value = emptyList()
        _manualSourceKeypoints.value = emptyList()
        _manualTargetKeypoints.value = emptyList()
    }
    
    /**
     * Set view mode.
     */
    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }
    
    /**
     * Detect keypoints in both images.
     */
    fun detectKeypoints() {
        val source = _sourceImage.value ?: return
        val target = _targetImage.value ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Detecting features..."
            
            try {
                val sourceResult = imageDiffer.detectKeypoints(source, _roi.value)
                val targetResult = imageDiffer.detectKeypoints(target, _roi.value)
                
                _sourceKeypoints.value = sourceResult.keypoints
                _targetKeypoints.value = targetResult.keypoints
                _errorMessage.value = null
                
            } catch (e: Exception) {
                _errorMessage.value = "Error detecting keypoints: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update selected source keypoints.
     */
    fun setSelectedSourceKeypoints(keypoints: List<Keypoint>) {
        _selectedSourceKeypoints.value = keypoints
    }
    
    /**
     * Update selected target keypoints.
     */
    fun setSelectedTargetKeypoints(keypoints: List<Keypoint>) {
        _selectedTargetKeypoints.value = keypoints
    }
    
    /**
     * Add a manual source keypoint.
     */
    fun addManualSourceKeypoint(keypoint: Keypoint) {
        _manualSourceKeypoints.value = _manualSourceKeypoints.value + keypoint
    }
    
    /**
     * Add a manual target keypoint.
     */
    fun addManualTargetKeypoint(keypoint: Keypoint) {
        _manualTargetKeypoints.value = _manualTargetKeypoints.value + keypoint
    }
    
    /**
     * Clear manual keypoints.
     */
    fun clearManualKeypoints() {
        _manualSourceKeypoints.value = emptyList()
        _manualTargetKeypoints.value = emptyList()
    }
    
    /**
     * Align images.
     */
    fun alignImages() {
        val source = _sourceImage.value ?: return
        val target = _targetImage.value ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Aligning images..."
            
            try {
                val result = when (_alignmentMode.value) {
                    AlignmentMode.AUTO -> {
                        imageDiffer.alignImagesAuto(source, target, _roi.value, _roi.value)
                    }
                    AlignmentMode.MANUAL -> {
                        val sourcePts = _manualSourceKeypoints.value.ifEmpty { 
                            _selectedSourceKeypoints.value 
                        }
                        val targetPts = _manualTargetKeypoints.value.ifEmpty { 
                            _selectedTargetKeypoints.value 
                        }
                        
                        if (sourcePts.size < 4 || targetPts.size < 4) {
                            _errorMessage.value = "At least 4 keypoint pairs required"
                            return@launch
                        }
                        
                        val pairs = imageDiffer.createManualKeypointPairs(sourcePts, targetPts)
                        imageDiffer.alignImagesManual(source, target, pairs)
                    }
                }
                
                if (result.isSuccessful) {
                    _alignmentResult.value = result
                    _alignedImage.value = result.alignedImage
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = result.errorMessage ?: "Alignment failed"
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Error aligning images: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Calculate differences.
     */
    fun calculateDiff(mode: DiffMode = DiffMode.OVERLAY) {
        val alignment = _alignmentResult.value
        
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Calculating differences..."
            
            try {
                val result = if (alignment != null && alignment.isSuccessful) {
                    imageDiffer.calculateDiff(alignment, mode)
                } else {
                    // Calculate diff without alignment
                    val source = _sourceImage.value ?: return@launch
                    val target = _targetImage.value ?: return@launch
                    imageDiffer.calculateDiffDirect(source, target, mode)
                }
                
                _diffResult.value = result
                _errorMessage.value = null
                
            } catch (e: Exception) {
                _errorMessage.value = "Error calculating diff: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Reset all state.
     */
    fun reset() {
        _alignedImage.value = null
        _alignmentResult.value = null
        _diffResult.value = null
        _sourceKeypoints.value = emptyList()
        _targetKeypoints.value = emptyList()
        _selectedSourceKeypoints.value = emptyList()
        _selectedTargetKeypoints.value = emptyList()
        _manualSourceKeypoints.value = emptyList()
        _manualTargetKeypoints.value = emptyList()
        _roi.value = null
        _isROIMode.value = false
        _errorMessage.value = null
    }
}

