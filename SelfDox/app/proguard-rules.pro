# Self Dox ProGuard rules
# Add project specific keep rules here

# Keep Ktor server classes
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }

# Keep RootEncoder/RTSP-Server classes
-keep class com.pedro.** { *; }
