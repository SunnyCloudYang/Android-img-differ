package com.imgdiff.lib.utils

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Utility functions for Bitmap and OpenCV Mat conversions.
 */
object BitmapUtils {
    
    /**
     * Convert an Android Bitmap to OpenCV Mat.
     * 
     * @param bitmap Input bitmap (ARGB_8888 format)
     * @return OpenCV Mat in RGBA format
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        val bmp = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        Utils.bitmapToMat(bmp, mat)
        if (bmp !== bitmap) {
            bmp.recycle()
        }
        return mat
    }
    
    /**
     * Convert an OpenCV Mat to Android Bitmap.
     * 
     * @param mat OpenCV Mat (supports various formats)
     * @return Android Bitmap in ARGB_8888 format
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
    
    /**
     * Create a copy of the bitmap.
     */
    fun copyBitmap(bitmap: Bitmap): Bitmap {
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }
    
    /**
     * Scale bitmap to fit within max dimensions while maintaining aspect ratio.
     */
    fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val scaleX = maxWidth.toFloat() / width
        val scaleY = maxHeight.toFloat() / height
        val scale = minOf(scaleX, scaleY)
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Resize both bitmaps to have the same dimensions (using the smaller of each dimension).
     */
    fun resizeToMatch(bitmap1: Bitmap, bitmap2: Bitmap): Pair<Bitmap, Bitmap> {
        val width = minOf(bitmap1.width, bitmap2.width)
        val height = minOf(bitmap1.height, bitmap2.height)
        
        val resized1 = if (bitmap1.width != width || bitmap1.height != height) {
            Bitmap.createScaledBitmap(bitmap1, width, height, true)
        } else {
            bitmap1
        }
        
        val resized2 = if (bitmap2.width != width || bitmap2.height != height) {
            Bitmap.createScaledBitmap(bitmap2, width, height, true)
        } else {
            bitmap2
        }
        
        return Pair(resized1, resized2)
    }
}

