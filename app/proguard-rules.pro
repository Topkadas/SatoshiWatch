# --- Retrofit / OkHttp ---
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- kotlinx.serialization ---
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.satoshiwatch.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.satoshiwatch.**$$serializer { *; }
-keepclassmembers class com.satoshiwatch.** { *** Companion; }

# --- SQLCipher ---
-keep class net.zetetic.database.** { *; }
