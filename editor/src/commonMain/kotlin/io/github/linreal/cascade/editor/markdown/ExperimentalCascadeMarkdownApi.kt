package io.github.linreal.cascade.editor.markdown

/**
 * Marks CascadeEditor Markdown import/export APIs that are still stabilizing.
 *
 * The feature is usable, but public contracts in `markdown` may change before the
 * Markdown codec is declared stable.
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
    message = "CascadeEditor Markdown import/export API is experimental and may change before stabilization.",
)
@MustBeDocumented
public annotation class ExperimentalCascadeMarkdownApi
