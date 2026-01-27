# ProGuard rules for cascade-editor library

# Compose compiler generates classes that should not be obfuscated
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
