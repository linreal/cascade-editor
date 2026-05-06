package io.github.linreal.cascade.editor.htmlserialization

/**
 * Marks CascadeEditor HTML import/export APIs that are still stabilizing.
 *
 * The feature is usable, but public contracts in `htmlserialization` may change
 * before the HTML codec is declared stable.
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
    message = "CascadeEditor HTML import/export API is experimental and may change before stabilization.",
)
@MustBeDocumented
public annotation class ExperimentalCascadeHtmlApi
