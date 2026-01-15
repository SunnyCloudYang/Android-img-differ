package com.imgdiff.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extension functions and utilities for the app.
 */

/**
 * Load a bitmap from a URI with optional downsampling.
 */
suspend fun Context.loadBitmapFromUri(
    uri: Uri,
    maxWidth: Int = 2048,
    maxHeight: Int = 2048
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        // First, decode bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        
        // Calculate sample size
        val sampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
        
        // Decode with sample size
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Calculate optimal sample size for downsampling.
 */
private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while ((halfHeight / inSampleSize) >= reqHeight &&
               (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

/**
 * Fade in animation.
 */
fun View.fadeIn(duration: Long = 300) {
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .start()
}

/**
 * Fade out animation.
 */
fun View.fadeOut(duration: Long = 300, onComplete: (() -> Unit)? = null) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withEndAction {
            visibility = View.GONE
            onComplete?.invoke()
        }
        .start()
}

/**
 * Scale pulse animation.
 */
fun View.pulse(scale: Float = 1.1f, duration: Long = 200) {
    animate()
        .scaleX(scale)
        .scaleY(scale)
        .setDuration(duration / 2)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration / 2)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        .start()
}

/**
 * Slide up animation.
 */
fun View.slideUp(duration: Long = 300) {
    translationY = height.toFloat()
    visibility = View.VISIBLE
    animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .start()
}

/**
 * Slide down animation.
 */
fun View.slideDown(duration: Long = 300, onComplete: (() -> Unit)? = null) {
    animate()
        .translationY(height.toFloat())
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withEndAction {
            visibility = View.GONE
            translationY = 0f
            onComplete?.invoke()
        }
        .start()
}

