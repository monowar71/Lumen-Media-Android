# Add project-specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.lumenmedia.android.core.model.** { *; }

# kotlinx.serialization: keep generated serializers and their lookup entry
# points for API models so R8 does not strip reflective serializer resolution.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lumenmedia.android.core.model.**$$serializer { *; }
-keepclassmembers class com.lumenmedia.android.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.lumenmedia.android.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
