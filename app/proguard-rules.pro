# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep OpenCV classes
-keep class org.opencv.** { *; }

# Keep imgdiff-lib classes
-keep class com.imgdiff.lib.** { *; }

