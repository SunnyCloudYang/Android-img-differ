package com.imgdiff.lib

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Library initialization and entry point for imgdiff-lib.
 * This library provides image comparison functionality using SIFT-based alignment.
 */
object ImageDifferLib {
    
    private const val TAG = "ImageDifferLib"
    
    @Volatile
    private var isInitialized = false
    
    /**
     * Initialize the library. Must be called before using any other functionality.
     * Typically called from Application.onCreate()
     * 
     * @param context Application context
     * @return true if initialization was successful
     */
    @JvmStatic
    fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Library already initialized")
            return true
        }
        
        synchronized(this) {
            if (isInitialized) return true
            
            // Initialize OpenCV
            val openCvLoaded = OpenCVLoader.initLocal()
            if (openCvLoaded) {
                Log.i(TAG, "OpenCV initialized successfully")
                isInitialized = true
            } else {
                Log.e(TAG, "Failed to initialize OpenCV")
            }
            
            return openCvLoaded
        }
    }
    
    /**
     * Check if the library is initialized
     */
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Get the OpenCV version string
     */
    @JvmStatic
    fun getOpenCVVersion(): String {
        return if (isInitialized) {
            org.opencv.core.Core.VERSION
        } else {
            "OpenCV not initialized"
        }
    }
}

