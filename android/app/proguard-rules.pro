# Add project specific ProGuard rules here.

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.ComponentManager { *; }
-keep class * extends dagger.hilt.internal.ComponentManager$ComponentImpl { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ML Kit
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Keep annotations
-keepattributes *Annotation*, InnerClasses, EnclosingMethod