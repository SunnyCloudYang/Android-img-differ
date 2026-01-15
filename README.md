# Image Differ - Android Image Comparison App

An Android application for comparing two images using SIFT-based alignment and difference detection. Built with Material Design 3 and modern Android development practices.

## Features

- **SIFT-Based Image Alignment**: Automatically detects and matches features between two images using OpenCV's SIFT algorithm
- **Manual Keypoint Selection**: Option to manually select corresponding points for precise alignment
- **Customizable ROI**: Select a specific region of interest for feature detection and comparison
- **Multiple Visualization Modes**:
  - Overlay view with highlighted differences
  - Side-by-side comparison with synchronized pan/zoom
  - Slider view for before/after comparison
- **Material Design 3**: Modern UI with dynamic color support and dark theme

## Project Structure

```
Android-img-differ/
├── app/                    # Main Android application
│   └── src/main/
│       ├── java/com/imgdiff/app/
│       │   ├── MainActivity.kt          # Image selection
│       │   ├── CompareActivity.kt        # Main comparison screen
│       │   ├── viewmodel/                # ViewModels
│       │   ├── ui/components/            # Custom views
│       │   └── util/                     # Utilities
│       └── res/                          # Resources
│
└── imgdiff-lib/            # Reusable library module
    └── src/main/java/com/imgdiff/lib/
        ├── ImageDiffer.kt               # Main API entry point
        ├── ImageDifferLib.kt            # Library initialization
        ├── alignment/
        │   ├── SIFTAligner.kt           # SIFT feature detection & alignment
        │   └── KeypointMatcher.kt       # Keypoint matching utilities
        ├── comparison/
        │   └── DiffCalculator.kt        # Difference calculation
        ├── roi/
        │   └── ROIProcessor.kt          # Region of interest processing
        └── models/                       # Data models
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Minimum SDK 24 (Android 7.0)
- Kotlin 1.9+

## Building

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle and build

```bash
./gradlew assembleDebug
```

## Library Usage

The `imgdiff-lib` module can be integrated into other projects:

### Initialization

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ImageDifferLib.initialize(this)
    }
}
```

### Basic Usage

```kotlin
val imageDiffer = ImageDiffer()

// Detect keypoints
val keypoints = imageDiffer.detectKeypointsSync(bitmap)

// Auto-align images
val alignmentResult = imageDiffer.alignImagesAutoSync(sourceBitmap, targetBitmap)

// Calculate differences
val diffResult = imageDiffer.calculateDiffSync(alignmentResult, DiffMode.OVERLAY)
```

### Manual Alignment

```kotlin
// Create keypoint pairs from manually selected points
val pairs = imageDiffer.createManualKeypointPairs(sourceKeypoints, targetKeypoints)

// Align using manual keypoints
val result = imageDiffer.alignImagesManualSync(source, target, pairs)
```

### ROI-Based Processing

```kotlin
// Define ROI (normalized 0-1 coordinates)
val roi = ROI(left = 0.1f, top = 0.1f, right = 0.9f, bottom = 0.9f)

// Detect keypoints within ROI only
val keypoints = imageDiffer.detectKeypointsSync(bitmap, roi)

// Align with ROI
val result = imageDiffer.alignImagesAutoSync(source, target, roi, roi)
```

## Dependencies

- **OpenCV Android SDK 4.9.0**: Computer vision algorithms
- **AndroidX Core/AppCompat**: Core Android components
- **Material Components**: Material Design 3 UI
- **Coil**: Image loading
- **Kotlin Coroutines**: Asynchronous processing

## License

This project is provided as-is for educational and personal use.

## Acknowledgments

- [OpenCV](https://opencv.org/) for computer vision algorithms
- [Material Design](https://material.io/) for design guidelines

