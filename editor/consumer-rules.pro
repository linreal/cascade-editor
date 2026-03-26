# Consumer ProGuard rules for cascade-editor library
# Applied by the consuming app's R8 pass.
# R8 traces from these entry points to internal dependencies automatically.

# Public API surface
# Keep all public classes and their public/protected members.
# Internal classes are reachable via code references and will be kept by R8's tree-shaker.
-keep public class io.github.linreal.cascade.editor.** {
    public *;
    protected *;
}

# Compose runtime dispatch
# @Composable methods in ALL classes (including internal) must survive —
# the Compose runtime dispatches them and R8 may not see every call site.
-keepclassmembers class io.github.linreal.cascade.editor.** {
    @androidx.compose.runtime.Composable <methods>;
}

# Compose stability annotations — full class preservation for compiler optimizations
-keep @androidx.compose.runtime.Immutable class io.github.linreal.cascade.editor.** { *; }
-keep @androidx.compose.runtime.Stable class io.github.linreal.cascade.editor.** { *; }

# Kotlin metadata
-keepattributes RuntimeVisibleAnnotations, InnerClasses, EnclosingMethod, Signature
