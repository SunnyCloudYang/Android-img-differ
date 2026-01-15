package com.imgdiff.lib.models

import android.graphics.Bitmap

/**
 * Result of image alignment operation.
 */
data class AlignmentResult(
    /** The source image (reference) */
    val sourceImage: Bitmap,
    
    /** The target image after alignment/warping to match source */
    val alignedImage: Bitmap,
    
    /** The homography matrix used for transformation (3x3) */
    val homographyMatrix: DoubleArray?,
    
    /** Matched keypoint pairs used for alignment */
    val matchedKeypoints: List<KeypointPair>,
    
    /** Number of inliers used in homography estimation */
    val inlierCount: Int,
    
    /** Whether alignment was successful */
    val isSuccessful: Boolean,
    
    /** Error message if alignment failed */
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlignmentResult

        if (sourceImage != other.sourceImage) return false
        if (alignedImage != other.alignedImage) return false
        if (homographyMatrix != null) {
            if (other.homographyMatrix == null) return false
            if (!homographyMatrix.contentEquals(other.homographyMatrix)) return false
        } else if (other.homographyMatrix != null) return false
        if (matchedKeypoints != other.matchedKeypoints) return false
        if (inlierCount != other.inlierCount) return false
        if (isSuccessful != other.isSuccessful) return false
        if (errorMessage != other.errorMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceImage.hashCode()
        result = 31 * result + alignedImage.hashCode()
        result = 31 * result + (homographyMatrix?.contentHashCode() ?: 0)
        result = 31 * result + matchedKeypoints.hashCode()
        result = 31 * result + inlierCount
        result = 31 * result + isSuccessful.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
    
    companion object {
        fun failure(source: Bitmap, target: Bitmap, error: String): AlignmentResult {
            return AlignmentResult(
                sourceImage = source,
                alignedImage = target,
                homographyMatrix = null,
                matchedKeypoints = emptyList(),
                inlierCount = 0,
                isSuccessful = false,
                errorMessage = error
            )
        }
    }
}

