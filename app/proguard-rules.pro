# Add project-specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.freeplex.android.core.model.** { *; }
