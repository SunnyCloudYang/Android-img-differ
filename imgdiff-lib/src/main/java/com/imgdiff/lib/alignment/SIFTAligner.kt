package com.imgdiff.lib.alignment

import android.graphics.Bitmap
import android.util.Log
import com.imgdiff.lib.models.AlignmentResult
import com.imgdiff.lib.models.Keypoint
import com.imgdiff.lib.models.KeypointPair
import com.imgdiff.lib.models.KeypointResult
import com.imgdiff.lib.models.ROI
import com.imgdiff.lib.utils.BitmapUtils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.SIFT
import org.opencv.imgproc.Imgproc

/**
 * SIFT-based image alignment processor.
 * Uses SIFT features to find correspondences between images and compute homography.
 */
class SIFTAligner {
    
    companion object {
        private const val TAG = "SIFTAligner"
        
        // SIFT parameters - increased contrast threshold for fewer, more important keypoints
        private const val SIFT_N_FEATURES = 500  // Limit max features
        private const val SIFT_N_OCTAVE_LAYERS = 3
        private const val SIFT_CONTRAST_THRESHOLD = 0.08  // Higher = fewer keypoints (default 0.04)
        private const val SIFT_EDGE_THRESHOLD = 10.0
        private const val SIFT_SIGMA = 1.6
        
        // Default response threshold for filtering keypoints (higher = fewer, stronger keypoints)
        const val DEFAULT_RESPONSE_THRESHOLD = 0.01f
        
        // Matching parameters
        private const val LOWE_RATIO_THRESHOLD = 0.75f
        private const val MIN_MATCH_COUNT = 4
        private const val RANSAC_REPROJ_THRESHOLD = 5.0
    }
    
    private val sift: SIFT by lazy {
        SIFT.create(
            SIFT_N_FEATURES,
            SIFT_N_OCTAVE_LAYERS,
            SIFT_CONTRAST_THRESHOLD,
            SIFT_EDGE_THRESHOLD,
            SIFT_SIGMA
        )
    }
    
    // Configurable response threshold
    var responseThreshold: Float = DEFAULT_RESPONSE_THRESHOLD
    
    private val matcher: BFMatcher by lazy {
        BFMatcher.create(Core.NORM_L2, false)
    }
    
    /**
     * Detect SIFT keypoints in an image.
     * 
     * @param bitmap The input image
     * @param roi Optional region of interest to limit detection
     * @param minResponse Minimum response value to filter keypoints (higher = fewer, stronger keypoints)
     * @param maxKeypoints Maximum number of keypoints to return (0 = no limit)
     * @return KeypointResult containing detected keypoints and descriptors
     */
    fun detectKeypoints(
        bitmap: Bitmap, 
        roi: ROI? = null,
        minResponse: Float = responseThreshold,
        maxKeypoints: Int = 100
    ): KeypointResult {
        val mat = BitmapUtils.bitmapToMat(bitmap)
        val grayMat = Mat()
        
        // Convert to grayscale
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        
        // Apply ROI mask if specified
        val workingMat = if (roi != null && roi.isValid) {
            val roiRect = roi.toRect(bitmap.width, bitmap.height)
            val mask = Mat.zeros(grayMat.size(), CvType.CV_8UC1)
            Imgproc.rectangle(
                mask,
                Point(roiRect.left.toDouble(), roiRect.top.toDouble()),
                Point(roiRect.right.toDouble(), roiRect.bottom.toDouble()),
                Scalar(255.0),
                -1  // Filled
            )
            grayMat
        } else {
            grayMat
        }
        
        // Detect keypoints
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        
        if (roi != null && roi.isValid) {
            val mask = Mat.zeros(grayMat.size(), CvType.CV_8UC1)
            val roiRect = roi.toRect(bitmap.width, bitmap.height)
            Imgproc.rectangle(
                mask,
                Point(roiRect.left.toDouble(), roiRect.top.toDouble()),
                Point(roiRect.right.toDouble(), roiRect.bottom.toDouble()),
                Scalar(255.0),
                -1
            )
            sift.detectAndCompute(workingMat, mask, keypoints, descriptors)
            mask.release()
        } else {
            sift.detectAndCompute(workingMat, Mat(), keypoints, descriptors)
        }
        
        // Convert to our Keypoint model and filter by response
        var keypointList = keypoints.toList()
            .mapIndexed { index, kp ->
                Keypoint(
                    id = index,
                    x = kp.pt.x.toFloat(),
                    y = kp.pt.y.toFloat(),
                    size = kp.size.toFloat(),
                    angle = kp.angle.toFloat(),
                    response = kp.response.toFloat(),
                    octave = kp.octave
                )
            }
            .filter { it.response >= minResponse }  // Filter by response threshold
            .sortedByDescending { it.response }      // Sort by strongest first
        
        // Limit to maxKeypoints if specified
        if (maxKeypoints > 0 && keypointList.size > maxKeypoints) {
            keypointList = keypointList.take(maxKeypoints)
        }
        
        // Re-assign IDs after filtering
        keypointList = keypointList.mapIndexed { index, kp -> kp.copy(id = index) }
        
        Log.d(TAG, "Detected ${keypoints.toList().size} keypoints, filtered to ${keypointList.size}")
        
        // Cleanup
        mat.release()
        grayMat.release()
        keypoints.release()
        
        return KeypointResult(keypointList, descriptors)
    }
    
    /**
     * Align target image to source image using automatic keypoint matching.
     * 
     * @param source The reference image
     * @param target The image to be aligned
     * @param sourceROI Optional ROI for source image keypoint detection
     * @param targetROI Optional ROI for target image keypoint detection
     * @return AlignmentResult with aligned image and metadata
     */
    fun alignAuto(
        source: Bitmap,
        target: Bitmap,
        sourceROI: ROI? = null,
        targetROI: ROI? = null
    ): AlignmentResult {
        try {
            // Detect keypoints in both images
            val sourceResult = detectKeypoints(source, sourceROI)
            val targetResult = detectKeypoints(target, targetROI)
            
            if (sourceResult.count < MIN_MATCH_COUNT || targetResult.count < MIN_MATCH_COUNT) {
                return AlignmentResult.failure(
                    source, target,
                    "Not enough keypoints detected. Source: ${sourceResult.count}, Target: ${targetResult.count}"
                )
            }
            
            // Match descriptors
            val matches = matchDescriptors(sourceResult.descriptors!!, targetResult.descriptors!!)
            
            if (matches.size < MIN_MATCH_COUNT) {
                sourceResult.descriptors.release()
                targetResult.descriptors.release()
                return AlignmentResult.failure(
                    source, target,
                    "Not enough matches found: ${matches.size}"
                )
            }
            
            // Create keypoint pairs from matches
            val keypointPairs = matches.map { match ->
                KeypointPair(
                    sourceKeypoint = sourceResult.keypoints[match.queryIdx],
                    targetKeypoint = targetResult.keypoints[match.trainIdx],
                    matchDistance = match.distance
                )
            }
            
            // Compute alignment
            val result = computeAlignment(source, target, keypointPairs)
            
            // Cleanup
            sourceResult.descriptors.release()
            targetResult.descriptors.release()
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Alignment failed", e)
            return AlignmentResult.failure(source, target, "Alignment error: ${e.message}")
        }
    }
    
    /**
     * Align target image to source using manually specified keypoint pairs.
     * 
     * @param source The reference image
     * @param target The image to be aligned
     * @param keypointPairs Manually specified corresponding point pairs
     * @return AlignmentResult with aligned image and metadata
     */
    fun alignManual(
        source: Bitmap,
        target: Bitmap,
        keypointPairs: List<KeypointPair>
    ): AlignmentResult {
        if (keypointPairs.size < MIN_MATCH_COUNT) {
            return AlignmentResult.failure(
                source, target,
                "At least $MIN_MATCH_COUNT point pairs required. Got: ${keypointPairs.size}"
            )
        }
        
        return computeAlignment(source, target, keypointPairs)
    }
    
    /**
     * Match descriptors using BFMatcher with Lowe's ratio test.
     */
    private fun matchDescriptors(desc1: Mat, desc2: Mat): List<DMatch> {
        val knnMatches = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(desc1, desc2, knnMatches, 2)
        
        // Apply Lowe's ratio test
        val goodMatches = mutableListOf<DMatch>()
        for (matchPair in knnMatches) {
            val matches = matchPair.toList()
            if (matches.size >= 2) {
                val m = matches[0]
                val n = matches[1]
                if (m.distance < LOWE_RATIO_THRESHOLD * n.distance) {
                    goodMatches.add(m)
                }
            }
            matchPair.release()
        }
        
        Log.d(TAG, "Found ${goodMatches.size} good matches after ratio test")
        return goodMatches
    }
    
    /**
     * Compute homography and warp target image to align with source.
     */
    private fun computeAlignment(
        source: Bitmap,
        target: Bitmap,
        keypointPairs: List<KeypointPair>
    ): AlignmentResult {
        try {
            // Extract point arrays
            val srcPoints = keypointPairs.map { 
                Point(it.sourceKeypoint.x.toDouble(), it.sourceKeypoint.y.toDouble()) 
            }
            val dstPoints = keypointPairs.map { 
                Point(it.targetKeypoint.x.toDouble(), it.targetKeypoint.y.toDouble()) 
            }
            
            val srcMat = MatOfPoint2f()
            srcMat.fromList(srcPoints)
            
            val dstMat = MatOfPoint2f()
            dstMat.fromList(dstPoints)
            
            // Compute homography with RANSAC
            val mask = Mat()
            val homography = Calib3d.findHomography(
                dstMat, srcMat,  // Note: dst -> src for warping target to match source
                Calib3d.RANSAC,
                RANSAC_REPROJ_THRESHOLD,
                mask
            )
            
            if (homography.empty()) {
                srcMat.release()
                dstMat.release()
                mask.release()
                return AlignmentResult.failure(source, target, "Failed to compute homography")
            }
            
            // Count inliers
            val inlierCount = Core.countNonZero(mask)
            Log.d(TAG, "Homography computed with $inlierCount inliers out of ${keypointPairs.size} matches")
            
            // Convert homography to double array
            val homographyData = DoubleArray(9)
            homography.get(0, 0, homographyData)
            
            // Warp target image
            val targetMat = BitmapUtils.bitmapToMat(target)
            val warpedMat = Mat()
            
            Imgproc.warpPerspective(
                targetMat,
                warpedMat,
                homography,
                Size(source.width.toDouble(), source.height.toDouble())
            )
            
            val alignedBitmap = BitmapUtils.matToBitmap(warpedMat)
            
            // Cleanup
            srcMat.release()
            dstMat.release()
            mask.release()
            homography.release()
            targetMat.release()
            warpedMat.release()
            
            return AlignmentResult(
                sourceImage = source,
                alignedImage = alignedBitmap,
                homographyMatrix = homographyData,
                matchedKeypoints = keypointPairs,
                inlierCount = inlierCount,
                isSuccessful = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Alignment computation failed", e)
            return AlignmentResult.failure(source, target, "Computation error: ${e.message}")
        }
    }
}

