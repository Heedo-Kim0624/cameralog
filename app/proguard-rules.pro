# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# SQLCipher native libraries are included by dependency
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Room schema
-keep class com.example.camerawatch.data.** { *; }

# For Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
