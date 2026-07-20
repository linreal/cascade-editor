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
 * Link URL contracts split by call site intent.
 *
 * [validate] is user-entry normalization for link creation chrome (toolbar, link
 * popup), modeled on Slack's link entry: anything non-blank is accepted, and inputs
 * without a `<scheme>://` prefix are rewritten by prepending `https://`, so bare
 * hosts like `example.com/path` become `https://example.com/path`.
 *
 * [validateStoredTarget] is stored-target validation for codec and persistence
 * paths (JSON [io.github.linreal.cascade.editor.serialization.RichTextSchema],
 * HTML/Markdown import). It only trims and rejects blank input; the target string
 * is otherwise preserved exactly, so relative (`../guide.md`), absolute-path
 * (`/docs`), fragment (`#heading`), `mailto:`, `tel:`, and custom-scheme targets
 * survive save/load cycles byte-identically.
 *
 * Choosing the wrong contract is a correctness bug: entry normalization applied to
 * a stored target rewrites `../guide.md` into `https://../guide.md` on the next
 * save; stored-target validation applied to toolbar input stops normalizing bare
 * hosts. The only [LinkValidationError] in both contracts is [LinkValidationError.Blank].
 */
public object LinkUrlPolicy {

    /**
     * User-entry normalization for link creation. May rewrite the input
     * (prepends `https://` to values without a `<scheme>://` prefix).
     */
    public fun validate(input: String): LinkValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return LinkValidationResult.Invalid(LinkValidationError.Blank)
        }

        val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return LinkValidationResult.Valid(normalized)
    }

    /**
     * Stored-target validation for codec/persistence paths. Trims surrounding
     * whitespace and rejects blank values; never rewrites the target otherwise.
     */
    public fun validateStoredTarget(input: String): LinkValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return LinkValidationResult.Invalid(LinkValidationError.Blank)
        }
        return LinkValidationResult.Valid(trimmed)
    }
}
