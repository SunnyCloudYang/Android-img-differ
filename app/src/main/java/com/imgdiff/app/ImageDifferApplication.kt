package com.imgdiff.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.imgdiff.lib.ImageDifferLib

/**
 * Application class for Image Differ.
 * Initializes OpenCV and applies dynamic colors.
 */
class ImageDifferApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Apply dynamic colors for Material You support
        DynamicColors.applyToActivitiesIfAvailable(this)
        
        // Initialize the image differ library (which loads OpenCV)
        ImageDifferLib.initialize(this)
    }
}

