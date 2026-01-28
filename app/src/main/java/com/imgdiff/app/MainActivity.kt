package com.imgdiff.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.imgdiff.app.databinding.ActivityMainBinding
import com.imgdiff.app.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main activity for selecting images to compare.
 * Provides a Material 3 UI for selecting two images from the device.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Image picker for first image
    private val pickFirstImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.setFirstImage(it)
            // Take persistable URI permission
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    // Image picker for second image
    private val pickSecondImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.setSecondImage(it)
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                right = systemBars.right
            )
            insets
        }
        
        // Apply bottom padding to scrollable content for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnCompare) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as? android.widget.LinearLayout.LayoutParams
            params?.bottomMargin = systemBars.bottom
            view.layoutParams = params
            insets
        }
    }

    private fun setupClickListeners() {
        // First image selection
        binding.containerFirstImage.setOnClickListener {
            pickFirstImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Second image selection
        binding.containerSecondImage.setOnClickListener {
            pickSecondImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Clear first image
        binding.btnClearFirst.setOnClickListener {
            viewModel.clearFirstImage()
        }

        // Clear second image
        binding.btnClearSecond.setOnClickListener {
            viewModel.clearSecondImage()
        }

        // Compare button
        binding.btnCompare.setOnClickListener {
            val firstUri = viewModel.firstImageUri.value
            val secondUri = viewModel.secondImageUri.value
            
            if (firstUri != null && secondUri != null) {
                val intent = Intent(this, CompareActivity::class.java).apply {
                    putExtra(CompareActivity.EXTRA_FIRST_IMAGE_URI, firstUri.toString())
                    putExtra(CompareActivity.EXTRA_SECOND_IMAGE_URI, secondUri.toString())
                }
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_no_images),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.firstImageUri.collectLatest { uri ->
                        updateFirstImageUI(uri)
                    }
                }
                
                launch {
                    viewModel.secondImageUri.collectLatest { uri ->
                        updateSecondImageUI(uri)
                    }
                }
                
                launch {
                    viewModel.canCompare.collectLatest { canCompare ->
                        binding.btnCompare.isEnabled = canCompare
                        binding.textHint.visibility = if (canCompare) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun updateFirstImageUI(uri: Uri?) {
        if (uri != null) {
            binding.imageFirst.visibility = View.VISIBLE
            binding.placeholderFirst.visibility = View.GONE
            binding.btnClearFirst.visibility = View.VISIBLE
            binding.textFirstFileName.visibility = View.VISIBLE
            binding.textFirstFileName.text = getFileName(uri)
            binding.imageFirst.load(uri) {
                crossfade(true)
            }
        } else {
            binding.imageFirst.visibility = View.GONE
            binding.placeholderFirst.visibility = View.VISIBLE
            binding.btnClearFirst.visibility = View.GONE
            binding.textFirstFileName.visibility = View.GONE
        }
    }

    private fun updateSecondImageUI(uri: Uri?) {
        if (uri != null) {
            binding.imageSecond.visibility = View.VISIBLE
            binding.placeholderSecond.visibility = View.GONE
            binding.btnClearSecond.visibility = View.VISIBLE
            binding.textSecondFileName.visibility = View.VISIBLE
            binding.textSecondFileName.text = getFileName(uri)
            binding.imageSecond.load(uri) {
                crossfade(true)
            }
        } else {
            binding.imageSecond.visibility = View.GONE
            binding.placeholderSecond.visibility = View.VISIBLE
            binding.btnClearSecond.visibility = View.GONE
            binding.textSecondFileName.visibility = View.GONE
        }
    }

    /**
     * Get the display name of a file from its URI.
     */
    private fun getFileName(uri: Uri): String {
        var fileName = "Unknown"
        
        // Try to get the display name from content resolver
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        
        // Fallback to path segment
        if (fileName == "Unknown") {
            fileName = uri.lastPathSegment ?: "Unknown"
        }
        
        return fileName
    }
}

