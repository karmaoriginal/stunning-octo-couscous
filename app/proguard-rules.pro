-keepattributes *Annotation*
-keepattributes Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.kromiv.core.model.**$$serializer { *; }
-keepclassmembers class app.kromiv.core.model.** {
    *** Companion;
}

# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# AVIF/HEIF Coil Decoder
-keep class com.github.awxkee.** { *; }
-dontwarn com.github.awxkee.**

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Strip verbose debug logs in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
