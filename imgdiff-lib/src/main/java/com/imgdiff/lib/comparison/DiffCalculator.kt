package com.imgdiff.lib.comparison

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.imgdiff.lib.models.DiffMode
import com.imgdiff.lib.models.DiffResult
import com.imgdiff.lib.utils.BitmapUtils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Calculator for image differences using various algorithms.
 */
class DiffCalculator {
    
    companion object {
        private const val TAG = "DiffCalculator"
        
        // Thresholds
        private const val DEFAULT_DIFF_THRESHOLD = 30
        private const val MORPH_KERNEL_SIZE = 3
        private const val MIN_CONTOUR_AREA = 100.0
        
        // Colors for visualization (RGBA)
        private val DIFF_COLOR_ADDED = Scalar(76.0, 175.0, 80.0, 180.0)    // Green
        private val DIFF_COLOR_REMOVED = Scalar(244.0, 67.0, 54.0, 180.0)  // Red
        private val DIFF_COLOR_CHANGED = Scalar(255.0, 152.0, 0.0, 180.0)  // Orange
    }
    
    /**
     * Calculate difference between two images.
     * 
     * @param source Source/reference image
     * @param target Target/comparison image (should be aligned with source)
     * @param mode Difference calculation mode
     * @param threshold Pixel difference threshold (0-255)
     * @return DiffResult containing visualizations and statistics
     */
    fun calculateDiff(
        source: Bitmap,
        target: Bitmap,
        mode: DiffMode = DiffMode.PIXEL_DIFF,
        threshold: Int = DEFAULT_DIFF_THRESHOLD
    ): DiffResult {
        // Ensure images have same dimensions
        val (src, tgt) = BitmapUtils.resizeToMatch(source, target)
        
        return when (mode) {
            DiffMode.PIXEL_DIFF -> calculatePixelDiff(src, tgt, threshold)
            DiffMode.STRUCTURAL -> calculateStructuralDiff(src, tgt, threshold)
            DiffMode.ABSOLUTE -> calculateAbsoluteDiff(src, tgt)
            DiffMode.OVERLAY -> calculateOverlayDiff(src, tgt, threshold)
        }
    }
    
    /**
     * Calculate pixel-by-pixel difference with thresholding.
     */
    private fun calculatePixelDiff(
        source: Bitmap,
        target: Bitmap,
        threshold: Int
    ): DiffResult {
        val srcMat = BitmapUtils.bitmapToMat(source)
        val tgtMat = BitmapUtils.bitmapToMat(target)
        
        // Convert to grayscale for comparison
        val srcGray = Mat()
        val tgtGray = Mat()
        Imgproc.cvtColor(srcMat, srcGray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(tgtMat, tgtGray, Imgproc.COLOR_RGBA2GRAY)
        
        // Calculate absolute difference
        val diffMat = Mat()
        Core.absdiff(srcGray, tgtGray, diffMat)
        
        // Apply threshold
        val threshMat = Mat()
        Imgproc.threshold(
            diffMat,
            threshMat,
            threshold.toDouble(),
            255.0,
            Imgproc.THRESH_BINARY
        )
        
        // Morphological operations to clean up noise
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(MORPH_KERNEL_SIZE.toDouble(), MORPH_KERNEL_SIZE.toDouble())
        )
        val cleanedMask = Mat()
        Imgproc.morphologyEx(threshMat, cleanedMask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(cleanedMask, cleanedMask, Imgproc.MORPH_CLOSE, kernel)
        
        // Find contours for bounding boxes
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            cleanedMask.clone(),
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        val diffRegions = contours
            .filter { Imgproc.contourArea(it) >= MIN_CONTOUR_AREA }
            .map { contour ->
                val rect = Imgproc.boundingRect(contour)
                Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
            }
        
        // Create visualization
        val visualization = createVisualization(srcMat, cleanedMask, DIFF_COLOR_CHANGED)
        
        // Calculate statistics
        val diffPixels = Core.countNonZero(cleanedMask)
        val totalPixels = cleanedMask.rows() * cleanedMask.cols()
        val diffPercentage = (diffPixels.toFloat() / totalPixels) * 100f
        val meanDiff = Core.mean(diffMat).`val`[0].toFloat()
        
        val diffMaskBitmap = BitmapUtils.matToBitmap(cleanedMask)
        val diffVisBitmap = BitmapUtils.matToBitmap(visualization)
        
        // Cleanup
        srcMat.release()
        tgtMat.release()
        srcGray.release()
        tgtGray.release()
        diffMat.release()
        threshMat.release()
        kernel.release()
        cleanedMask.release()
        hierarchy.release()
        visualization.release()
        contours.forEach { it.release() }
        
        return DiffResult(
            sourceImage = source,
            targetImage = target,
            diffVisualization = diffVisBitmap,
            diffMask = diffMaskBitmap,
            diffPercentage = diffPercentage,
            diffPixelCount = diffPixels,
            totalPixelCount = totalPixels,
            diffRegions = diffRegions,
            mode = DiffMode.PIXEL_DIFF,
            meanDifference = meanDiff
        )
    }
    
    /**
     * Calculate structural difference using edge detection.
     */
    private fun calculateStructuralDiff(
        source: Bitmap,
        target: Bitmap,
        threshold: Int
    ): DiffResult {
        val srcMat = BitmapUtils.bitmapToMat(source)
        val tgtMat = BitmapUtils.bitmapToMat(target)
        
        // Convert to grayscale
        val srcGray = Mat()
        val tgtGray = Mat()
        Imgproc.cvtColor(srcMat, srcGray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(tgtMat, tgtGray, Imgproc.COLOR_RGBA2GRAY)
        
        // Apply Gaussian blur
        val srcBlur = Mat()
        val tgtBlur = Mat()
        Imgproc.GaussianBlur(srcGray, srcBlur, Size(5.0, 5.0), 0.0)
        Imgproc.GaussianBlur(tgtGray, tgtBlur, Size(5.0, 5.0), 0.0)
        
        // Compute SSIM-like comparison using normalized cross-correlation
        val diffMat = Mat()
        Core.absdiff(srcBlur, tgtBlur, diffMat)
        
        // Threshold
        val threshMat = Mat()
        Imgproc.threshold(
            diffMat,
            threshMat,
            threshold.toDouble(),
            255.0,
            Imgproc.THRESH_BINARY
        )
        
        // Morphological cleanup
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(5.0, 5.0)
        )
        val cleanedMask = Mat()
        Imgproc.morphologyEx(threshMat, cleanedMask, Imgproc.MORPH_CLOSE, kernel)
        
        // Calculate approximate SSIM score (simplified)
        val ssim = calculateSimplifiedSSIM(srcBlur, tgtBlur)
        
        // Find regions
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            cleanedMask.clone(),
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        val diffRegions = contours
            .filter { Imgproc.contourArea(it) >= MIN_CONTOUR_AREA }
            .map { contour ->
                val rect = Imgproc.boundingRect(contour)
                Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
            }
        
        // Visualization
        val visualization = createVisualization(srcMat, cleanedMask, DIFF_COLOR_CHANGED)
        
        val diffPixels = Core.countNonZero(cleanedMask)
        val totalPixels = cleanedMask.rows() * cleanedMask.cols()
        val diffPercentage = (diffPixels.toFloat() / totalPixels) * 100f
        val meanDiff = Core.mean(diffMat).`val`[0].toFloat()
        
        val diffMaskBitmap = BitmapUtils.matToBitmap(cleanedMask)
        val diffVisBitmap = BitmapUtils.matToBitmap(visualization)
        
        // Cleanup
        srcMat.release()
        tgtMat.release()
        srcGray.release()
        tgtGray.release()
        srcBlur.release()
        tgtBlur.release()
        diffMat.release()
        threshMat.release()
        kernel.release()
        cleanedMask.release()
        hierarchy.release()
        visualization.release()
        contours.forEach { it.release() }
        
        return DiffResult(
            sourceImage = source,
            targetImage = target,
            diffVisualization = diffVisBitmap,
            diffMask = diffMaskBitmap,
            diffPercentage = diffPercentage,
            diffPixelCount = diffPixels,
            totalPixelCount = totalPixels,
            diffRegions = diffRegions,
            mode = DiffMode.STRUCTURAL,
            meanDifference = meanDiff,
            ssimScore = ssim
        )
    }
    
    /**
     * Calculate absolute difference (raw pixel difference visualization).
     */
    private fun calculateAbsoluteDiff(
        source: Bitmap,
        target: Bitmap
    ): DiffResult {
        val srcMat = BitmapUtils.bitmapToMat(source)
        val tgtMat = BitmapUtils.bitmapToMat(target)
        
        // Calculate absolute difference on color channels
        val diffMat = Mat()
        Core.absdiff(srcMat, tgtMat, diffMat)
        
        // Convert diff to grayscale for mask
        val diffGray = Mat()
        Imgproc.cvtColor(diffMat, diffGray, Imgproc.COLOR_RGBA2GRAY)
        
        // Threshold for mask
        val threshMat = Mat()
        Imgproc.threshold(diffGray, threshMat, 10.0, 255.0, Imgproc.THRESH_BINARY)
        
        val diffPixels = Core.countNonZero(threshMat)
        val totalPixels = threshMat.rows() * threshMat.cols()
        val diffPercentage = (diffPixels.toFloat() / totalPixels) * 100f
        val meanDiff = Core.mean(diffGray).`val`[0].toFloat()
        
        // Enhance visibility of differences
        val enhancedDiff = Mat()
        Core.multiply(diffMat, Scalar(3.0, 3.0, 3.0, 1.0), enhancedDiff)
        
        val diffVisBitmap = BitmapUtils.matToBitmap(enhancedDiff)
        val diffMaskBitmap = BitmapUtils.matToBitmap(threshMat)
        
        // Cleanup
        srcMat.release()
        tgtMat.release()
        diffMat.release()
        diffGray.release()
        threshMat.release()
        enhancedDiff.release()
        
        return DiffResult(
            sourceImage = source,
            targetImage = target,
            diffVisualization = diffVisBitmap,
            diffMask = diffMaskBitmap,
            diffPercentage = diffPercentage,
            diffPixelCount = diffPixels,
            totalPixelCount = totalPixels,
            diffRegions = emptyList(),
            mode = DiffMode.ABSOLUTE,
            meanDifference = meanDiff
        )
    }
    
    /**
     * Create overlay visualization with highlighted differences.
     */
    private fun calculateOverlayDiff(
        source: Bitmap,
        target: Bitmap,
        threshold: Int
    ): DiffResult {
        val srcMat = BitmapUtils.bitmapToMat(source)
        val tgtMat = BitmapUtils.bitmapToMat(target)
        
        // Convert to grayscale for comparison
        val srcGray = Mat()
        val tgtGray = Mat()
        Imgproc.cvtColor(srcMat, srcGray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(tgtMat, tgtGray, Imgproc.COLOR_RGBA2GRAY)
        
        // Calculate difference
        val diffMat = Mat()
        Core.absdiff(srcGray, tgtGray, diffMat)
        
        // Threshold
        val threshMat = Mat()
        Imgproc.threshold(diffMat, threshMat, threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
        
        // Create overlay with semi-transparent source and highlighted differences
        val overlay = srcMat.clone()
        
        // Blend with target
        Core.addWeighted(srcMat, 0.5, tgtMat, 0.5, 0.0, overlay)
        
        // Highlight differences in red
        for (row in 0 until overlay.rows()) {
            for (col in 0 until overlay.cols()) {
                val maskVal = threshMat.get(row, col)
                if (maskVal != null && maskVal[0] > 0) {
                    val pixel = overlay.get(row, col)
                    if (pixel != null) {
                        overlay.put(
                            row, col,
                            255.0,  // R
                            pixel[1] * 0.3,  // G dimmed
                            pixel[2] * 0.3,  // B dimmed
                            255.0   // A
                        )
                    }
                }
            }
        }
        
        val diffPixels = Core.countNonZero(threshMat)
        val totalPixels = threshMat.rows() * threshMat.cols()
        val diffPercentage = (diffPixels.toFloat() / totalPixels) * 100f
        val meanDiff = Core.mean(diffMat).`val`[0].toFloat()
        
        val diffVisBitmap = BitmapUtils.matToBitmap(overlay)
        val diffMaskBitmap = BitmapUtils.matToBitmap(threshMat)
        
        // Cleanup
        srcMat.release()
        tgtMat.release()
        srcGray.release()
        tgtGray.release()
        diffMat.release()
        threshMat.release()
        overlay.release()
        
        return DiffResult(
            sourceImage = source,
            targetImage = target,
            diffVisualization = diffVisBitmap,
            diffMask = diffMaskBitmap,
            diffPercentage = diffPercentage,
            diffPixelCount = diffPixels,
            totalPixelCount = totalPixels,
            diffRegions = emptyList(),
            mode = DiffMode.OVERLAY,
            meanDifference = meanDiff
        )
    }
    
    /**
     * Create visualization by overlaying colored mask on source image.
     */
    private fun createVisualization(
        source: Mat,
        mask: Mat,
        color: Scalar
    ): Mat {
        val result = source.clone()
        val overlay = Mat(source.size(), source.type(), color)
        
        // Create colored overlay where mask is non-zero
        val coloredMask = Mat()
        Imgproc.cvtColor(mask, coloredMask, Imgproc.COLOR_GRAY2RGBA)
        
        // Blend overlay with source where mask is active
        for (row in 0 until result.rows()) {
            for (col in 0 until result.cols()) {
                val maskVal = mask.get(row, col)
                if (maskVal != null && maskVal[0] > 0) {
                    val srcPixel = result.get(row, col)
                    if (srcPixel != null) {
                        result.put(
                            row, col,
                            srcPixel[0] * 0.5 + color.`val`[0] * 0.5,
                            srcPixel[1] * 0.5 + color.`val`[1] * 0.5,
                            srcPixel[2] * 0.5 + color.`val`[2] * 0.5,
                            255.0
                        )
                    }
                }
            }
        }
        
        overlay.release()
        coloredMask.release()
        
        return result
    }
    
    /**
     * Calculate simplified SSIM score.
     */
    private fun calculateSimplifiedSSIM(img1: Mat, img2: Mat): Float {
        val c1 = 6.5025  // (0.01 * 255)^2
        val c2 = 58.5225 // (0.03 * 255)^2
        
        val mu1 = Mat()
        val mu2 = Mat()
        Imgproc.GaussianBlur(img1, mu1, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(img2, mu2, Size(11.0, 11.0), 1.5)
        
        val mu1Sq = Mat()
        val mu2Sq = Mat()
        val mu1Mu2 = Mat()
        Core.multiply(mu1, mu1, mu1Sq)
        Core.multiply(mu2, mu2, mu2Sq)
        Core.multiply(mu1, mu2, mu1Mu2)
        
        val img1Sq = Mat()
        val img2Sq = Mat()
        val img1Img2 = Mat()
        Core.multiply(img1, img1, img1Sq)
        Core.multiply(img2, img2, img2Sq)
        Core.multiply(img1, img2, img1Img2)
        
        val sigma1Sq = Mat()
        val sigma2Sq = Mat()
        val sigma12 = Mat()
        Imgproc.GaussianBlur(img1Sq, sigma1Sq, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(img2Sq, sigma2Sq, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(img1Img2, sigma12, Size(11.0, 11.0), 1.5)
        
        Core.subtract(sigma1Sq, mu1Sq, sigma1Sq)
        Core.subtract(sigma2Sq, mu2Sq, sigma2Sq)
        Core.subtract(sigma12, mu1Mu2, sigma12)
        
        // SSIM calculation
        val meanSSIM = Core.mean(sigma12).`val`[0]
        val ssim = ((2 * Core.mean(mu1Mu2).`val`[0] + c1) * (2 * meanSSIM + c2)) /
                   ((Core.mean(mu1Sq).`val`[0] + Core.mean(mu2Sq).`val`[0] + c1) *
                    (Core.mean(sigma1Sq).`val`[0] + Core.mean(sigma2Sq).`val`[0] + c2))
        
        // Cleanup
        mu1.release()
        mu2.release()
        mu1Sq.release()
        mu2Sq.release()
        mu1Mu2.release()
        img1Sq.release()
        img2Sq.release()
        img1Img2.release()
        sigma1Sq.release()
        sigma2Sq.release()
        sigma12.release()
        
        return ssim.toFloat().coerceIn(0f, 1f)
    }
}

