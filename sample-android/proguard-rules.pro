# ProGuard/R8 rules for the CascadeEditor sample app
# Most library rules are bundled automatically (Compose, AndroidX, kotlinx).

-keepattributes RuntimeVisibleAnnotations, InnerClasses, EnclosingMethod, Signature

# Compose
# Safety net for composable functions not covered by bundled library rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Enums
-keepclassmembers enum * {
    **[] $VALUES;
    public *;
}

# Suppress warnings for KMP dependencies
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.datetime.**
