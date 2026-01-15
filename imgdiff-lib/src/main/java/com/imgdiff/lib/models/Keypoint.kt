package com.imgdiff.lib.models

import android.graphics.PointF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a keypoint detected in an image.
 * Contains position, scale, orientation, and response strength.
 */
@Parcelize
data class Keypoint(
    val id: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val angle: Float,
    val response: Float,
    val octave: Int,
    val isSelected: Boolean = false,
    val isManual: Boolean = false
) : Parcelable {
    
    val point: PointF
        get() = PointF(x, y)
    
    fun distanceTo(other: Keypoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    fun distanceTo(px: Float, py: Float): Float {
        val dx = x - px
        val dy = y - py
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    companion object {
        /**
         * Create a manual keypoint at the specified position
         */
        fun manual(id: Int, x: Float, y: Float): Keypoint {
            return Keypoint(
                id = id,
                x = x,
                y = y,
                size = 10f,
                angle = 0f,
                response = 1f,
                octave = 0,
                isSelected = true,
                isManual = true
            )
        }
    }
}

/**
 * Represents a pair of corresponding keypoints between two images.
 */
@Parcelize
data class KeypointPair(
    val sourceKeypoint: Keypoint,
    val targetKeypoint: Keypoint,
    val matchDistance: Float = 0f
) : Parcelable

/**
 * Result of keypoint detection on an image.
 */
data class KeypointResult(
    val keypoints: List<Keypoint>,
    val descriptors: org.opencv.core.Mat? = null
) {
    val count: Int
        get() = keypoints.size
}

