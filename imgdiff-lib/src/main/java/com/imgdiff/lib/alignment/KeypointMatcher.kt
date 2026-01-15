package com.imgdiff.lib.alignment

import com.imgdiff.lib.models.Keypoint
import com.imgdiff.lib.models.KeypointPair
import org.opencv.core.Core
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.DescriptorMatcher

/**
 * Keypoint matching utilities using various matching strategies.
 */
class KeypointMatcher {
    
    companion object {
        private const val DEFAULT_LOWE_RATIO = 0.75f
    }
    
    private val bfMatcher = BFMatcher.create(Core.NORM_L2, false)
    private val flannMatcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED)
    
    /**
     * Match types for keypoint matching.
     */
    enum class MatchType {
        BRUTE_FORCE,
        FLANN
    }
    
    /**
     * Match keypoints using descriptors with Lowe's ratio test.
     * 
     * @param sourceDescriptors Descriptors from source image
     * @param targetDescriptors Descriptors from target image
     * @param sourceKeypoints Source keypoints list
     * @param targetKeypoints Target keypoints list
     * @param loweRatio Ratio threshold for Lowe's test (default 0.75)
     * @param matchType Type of matcher to use
     * @return List of matched keypoint pairs
     */
    fun matchKeypoints(
        sourceDescriptors: Mat,
        targetDescriptors: Mat,
        sourceKeypoints: List<Keypoint>,
        targetKeypoints: List<Keypoint>,
        loweRatio: Float = DEFAULT_LOWE_RATIO,
        matchType: MatchType = MatchType.BRUTE_FORCE
    ): List<KeypointPair> {
        if (sourceDescriptors.empty() || targetDescriptors.empty()) {
            return emptyList()
        }
        
        val matcher = when (matchType) {
            MatchType.BRUTE_FORCE -> bfMatcher
            MatchType.FLANN -> flannMatcher
        }
        
        val knnMatches = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(sourceDescriptors, targetDescriptors, knnMatches, 2)
        
        val goodMatches = mutableListOf<DMatch>()
        
        for (matchPair in knnMatches) {
            val matches = matchPair.toList()
            if (matches.size >= 2) {
                val m = matches[0]
                val n = matches[1]
                if (m.distance < loweRatio * n.distance) {
                    goodMatches.add(m)
                }
            }
            matchPair.release()
        }
        
        // Convert to KeypointPair list
        return goodMatches.mapNotNull { match ->
            val srcIdx = match.queryIdx
            val tgtIdx = match.trainIdx
            
            if (srcIdx < sourceKeypoints.size && tgtIdx < targetKeypoints.size) {
                KeypointPair(
                    sourceKeypoint = sourceKeypoints[srcIdx],
                    targetKeypoint = targetKeypoints[tgtIdx],
                    matchDistance = match.distance
                )
            } else {
                null
            }
        }
    }
    
    /**
     * Filter matches based on distance threshold.
     * Keeps only matches with distance below the threshold.
     */
    fun filterByDistance(
        pairs: List<KeypointPair>,
        maxDistance: Float
    ): List<KeypointPair> {
        return pairs.filter { it.matchDistance <= maxDistance }
    }
    
    /**
     * Filter matches to keep only the best N matches.
     */
    fun keepBestMatches(
        pairs: List<KeypointPair>,
        count: Int
    ): List<KeypointPair> {
        return pairs.sortedBy { it.matchDistance }.take(count)
    }
    
    /**
     * Find the closest keypoint to a given position.
     * 
     * @param keypoints List of keypoints to search
     * @param x X coordinate
     * @param y Y coordinate
     * @param maxDistance Maximum distance to consider (returns null if no keypoint within this distance)
     * @return The closest keypoint, or null if none within maxDistance
     */
    fun findClosestKeypoint(
        keypoints: List<Keypoint>,
        x: Float,
        y: Float,
        maxDistance: Float = Float.MAX_VALUE
    ): Keypoint? {
        var closest: Keypoint? = null
        var minDist = maxDistance
        
        for (kp in keypoints) {
            val dist = kp.distanceTo(x, y)
            if (dist < minDist) {
                minDist = dist
                closest = kp
            }
        }
        
        return closest
    }
    
    /**
     * Create manual keypoint pairs from two lists of manually placed points.
     * Points are paired by their order in the lists.
     */
    fun createManualPairs(
        sourcePoints: List<Keypoint>,
        targetPoints: List<Keypoint>
    ): List<KeypointPair> {
        val count = minOf(sourcePoints.size, targetPoints.size)
        return (0 until count).map { i ->
            KeypointPair(
                sourceKeypoint = sourcePoints[i],
                targetKeypoint = targetPoints[i],
                matchDistance = 0f
            )
        }
    }
}

