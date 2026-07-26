package io.github.linreal.cascade.editor.ui

/**
 * Marks Cascade document-preview APIs that are still stabilizing.
 *
 * Preview rendering is usable, but its public contracts may change before the
 * feature is declared stable.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Cascade document-preview API is experimental and may change before stabilization.",
)
@MustBeDocumented
public annotation class ExperimentalCascadePreviewApi
