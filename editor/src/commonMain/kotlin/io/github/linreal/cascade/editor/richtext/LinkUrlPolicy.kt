package io.github.linreal.cascade.editor.richtext

/**
 * Stable, non-localized URL rejection reasons for link creation.
 */
public enum class LinkValidationError {
    Blank,
}

/**
 * Result returned by [LinkUrlPolicy].
 */
public sealed interface LinkValidationResult {
    public val normalizedUrl: String?
    public val error: LinkValidationError?

    public data class Valid(
        override val normalizedUrl: String,
    ) : LinkValidationResult {
        override val error: LinkValidationError? = null
    }

    public data class Invalid(
        override val error: LinkValidationError,
    ) : LinkValidationResult {
        override val normalizedUrl: String? = null
    }
}

/**
 * Permissive URL normalization for link spans, modeled on Slack's link entry.
 *
 * Anything non-blank is accepted. Inputs that already carry a `<scheme>://`
 * prefix are preserved verbatim after trimming; everything else is normalized
 * by prepending `https://`, so bare hosts like `example.com/path` become
 * `https://example.com/path`.
 */
public object LinkUrlPolicy {

    public fun validate(input: String): LinkValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return LinkValidationResult.Invalid(LinkValidationError.Blank)
        }

        val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return LinkValidationResult.Valid(normalized)
    }
}
