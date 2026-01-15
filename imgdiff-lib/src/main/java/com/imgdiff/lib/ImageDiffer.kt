package com.imgdiff.lib

import android.graphics.Bitmap
import com.imgdiff.lib.alignment.KeypointMatcher
import com.imgdiff.lib.alignment.SIFTAligner
import com.imgdiff.lib.comparison.DiffCalculator
import com.imgdiff.lib.models.AlignmentResult
import com.imgdiff.lib.models.DiffMode
import com.imgdiff.lib.models.DiffResult
import com.imgdiff.lib.models.Keypoint
import com.imgdiff.lib.models.KeypointPair
import com.imgdiff.lib.models.KeypointResult
import com.imgdiff.lib.models.ROI
import com.imgdiff.lib.roi.ROIProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main API class for image comparison operations.
 * Provides a simple interface for SIFT-based image alignment and difference detection.
 * 
 * Usage:
 * ```
 * val differ = ImageDiffer()
 * 
 * // Detect keypoints
 * val keypoints = differ.detectKeypoints(bitmap)
 * 
 * // Auto-align images
 * val alignment = differ.alignImagesAuto(source, target)
 * 
 * // Calculate differences
 * val diff = differ.calculateDiff(alignment)
 * ```
 */
class ImageDiffer {
    
    private val siftAligner = SIFTAligner()
    private val keypointMatcher = KeypointMatcher()
    private val diffCalculator = DiffCalculator()
    private val roiProcessor = ROIProcessor()
    
    /**
     * Detect SIFT keypoints in an image.
     * 
     * @param bitmap Input image
     * @param roi Optional region of interest to limit detection
     * @return KeypointResult containing detected keypoints
     */
    suspend fun detectKeypoints(
        bitmap: Bitmap,
        roi: ROI? = null
    ): KeypointResult = withContext(Dispatchers.Default) {
        siftAligner.detectKeypoints(bitmap, roi)
    }
    
    /**
     * Detect keypoints synchronously (for use from Java or non-coroutine contexts).
     */
    fun detectKeypointsSync(bitmap: Bitmap, roi: ROI? = null): KeypointResult {
        return siftAligner.detectKeypoints(bitmap, roi)
    }
    
    /**
     * Align target image to source using automatic keypoint detection and matching.
     * 
     * @param source Reference image
     * @param target Image to be aligned
     * @param sourceROI Optional ROI for source image
     * @param targetROI Optional ROI for target image
     * @return AlignmentResult with aligned image and metadata
     */
    suspend fun alignImagesAuto(
        source: Bitmap,
        target: Bitmap,
        sourceROI: ROI? = null,
        targetROI: ROI? = null
    ): AlignmentResult = withContext(Dispatchers.Default) {
        siftAligner.alignAuto(source, target, sourceROI, targetROI)
    }
    
    /**
     * Align images synchronously.
     */
    fun alignImagesAutoSync(
        source: Bitmap,
        target: Bitmap,
        sourceROI: ROI? = null,
        targetROI: ROI? = null
    ): AlignmentResult {
        return siftAligner.alignAuto(source, target, sourceROI, targetROI)
    }
    
    /**
     * Align target image to source using manually specified keypoint pairs.
     * 
     * @param source Reference image
     * @param target Image to be aligned
     * @param keypointPairs List of corresponding point pairs
     * @return AlignmentResult with aligned image and metadata
     */
    suspend fun alignImagesManual(
        source: Bitmap,
        target: Bitmap,
        keypointPairs: List<KeypointPair>
    ): AlignmentResult = withContext(Dispatchers.Default) {
        siftAligner.alignManual(source, target, keypointPairs)
    }
    
    /**
     * Align images manually, synchronously.
     */
    fun alignImagesManualSync(
        source: Bitmap,
        target: Bitmap,
        keypointPairs: List<KeypointPair>
    ): AlignmentResult {
        return siftAligner.alignManual(source, target, keypointPairs)
    }
    
    /**
     * Calculate differences between source and aligned target images.
     * 
     * @param alignmentResult Result from previous alignment operation
     * @param mode Difference calculation mode
     * @param threshold Pixel difference threshold
     * @return DiffResult with visualizations and statistics
     */
    suspend fun calculateDiff(
        alignmentResult: AlignmentResult,
        mode: DiffMode = DiffMode.OVERLAY,
        threshold: Int = 30
    ): DiffResult = withContext(Dispatchers.Default) {
        diffCalculator.calculateDiff(
            alignmentResult.sourceImage,
            alignmentResult.alignedImage,
            mode,
            threshold
        )
    }
    
    /**
     * Calculate differences between two images directly (without prior alignment).
     * 
     * @param source Source image
     * @param target Target image (should be same size as source)
     * @param mode Difference calculation mode
     * @param threshold Pixel difference threshold
     * @return DiffResult with visualizations and statistics
     */
    suspend fun calculateDiffDirect(
        source: Bitmap,
        target: Bitmap,
        mode: DiffMode = DiffMode.OVERLAY,
        threshold: Int = 30
    ): DiffResult = withContext(Dispatchers.Default) {
        diffCalculator.calculateDiff(source, target, mode, threshold)
    }
    
    /**
     * Calculate diff synchronously.
     */
    fun calculateDiffSync(
        alignmentResult: AlignmentResult,
        mode: DiffMode = DiffMode.OVERLAY,
        threshold: Int = 30
    ): DiffResult {
        return diffCalculator.calculateDiff(
            alignmentResult.sourceImage,
            alignmentResult.alignedImage,
            mode,
            threshold
        )
    }
    
    /**
     * Find the closest detected keypoint to a given position.
     */
    fun findClosestKeypoint(
        keypoints: List<Keypoint>,
        x: Float,
        y: Float,
        maxDistance: Float = Float.MAX_VALUE
    ): Keypoint? {
        return keypointMatcher.findClosestKeypoint(keypoints, x, y, maxDistance)
    }
    
    /**
     * Create keypoint pairs from manually selected corresponding points.
     */
    fun createManualKeypointPairs(
        sourceKeypoints: List<Keypoint>,
        targetKeypoints: List<Keypoint>
    ): List<KeypointPair> {
        return keypointMatcher.createManualPairs(sourceKeypoints, targetKeypoints)
    }
    
    /**
     * Crop image to ROI.
     */
    fun cropToROI(bitmap: Bitmap, roi: ROI): Bitmap {
        return roiProcessor.cropToROI(bitmap, roi)
    }
    
    /**
     * Apply ROI mask to image.
     */
    fun applyROIMask(bitmap: Bitmap, roi: ROI, keepOutside: Boolean = true): Bitmap {
        return roiProcessor.applyROIMask(bitmap, roi, keepOutside)
    }
    
    /**
     * Get the ROI processor for advanced ROI operations.
     */
    fun getROIProcessor(): ROIProcessor = roiProcessor
    
    /**
     * Get the SIFT aligner for advanced alignment operations.
     */
    fun getSIFTAligner(): SIFTAligner = siftAligner
    
    /**
     * Get the diff calculator for advanced diff operations.
     */
    fun getDiffCalculator(): DiffCalculator = diffCalculator
}

