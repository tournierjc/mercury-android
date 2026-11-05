# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.tournierjc.mercury.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.tournierjc.mercury.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Tink (via androidx.security-crypto) references com.google.errorprone.annotations
# at compile time only; the annotations are not in any packaged dependency, so R8
# fails with "Missing classes detected" unless it is told to ignore them.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
