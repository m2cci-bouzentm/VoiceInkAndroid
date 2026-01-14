# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.voiceink.android.**$$serializer { *; }
-keepclassmembers class com.voiceink.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.voiceink.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
