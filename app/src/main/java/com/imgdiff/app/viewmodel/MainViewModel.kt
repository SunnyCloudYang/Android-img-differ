package com.imgdiff.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * ViewModel for MainActivity.
 * Manages the state of selected images.
 */
class MainViewModel : ViewModel() {

    private val _firstImageUri = MutableStateFlow<Uri?>(null)
    val firstImageUri: StateFlow<Uri?> = _firstImageUri.asStateFlow()

    private val _secondImageUri = MutableStateFlow<Uri?>(null)
    val secondImageUri: StateFlow<Uri?> = _secondImageUri.asStateFlow()

    private val _canCompare = MutableStateFlow(false)
    val canCompare: StateFlow<Boolean> = _canCompare.asStateFlow()

    init {
        // Update canCompare whenever either image changes
        // We use a simple approach here
    }

    fun setFirstImage(uri: Uri) {
        _firstImageUri.value = uri
        updateCanCompare()
    }

    fun setSecondImage(uri: Uri) {
        _secondImageUri.value = uri
        updateCanCompare()
    }

    fun clearFirstImage() {
        _firstImageUri.value = null
        updateCanCompare()
    }

    fun clearSecondImage() {
        _secondImageUri.value = null
        updateCanCompare()
    }

    private fun updateCanCompare() {
        _canCompare.value = _firstImageUri.value != null && _secondImageUri.value != null
    }
}

