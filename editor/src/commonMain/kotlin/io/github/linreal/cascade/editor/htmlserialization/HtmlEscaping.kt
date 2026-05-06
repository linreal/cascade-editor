package io.github.linreal.cascade.editor.htmlserialization

/**
 * Top-level helpers for HTML escaping.
 *
 * Default encoders and consumer-authored encoders should always go through these
 * helpers rather than hand-rolling `replace(...)` chains, so escaping stays
 * consistent across profiles.
 */
@ExperimentalCascadeHtmlApi
public object Html {

    /**
     * Escape a string for use as HTML element text content.
     *
     * Escapes `&`, `<`, and `>`. Quotes and apostrophes are intentionally left
     * unescaped because they are safe in element-text context and escaping them
     * inflates the payload unnecessarily.
     */
    public fun escapeText(s: String): String {
        if (s.isEmpty()) return s
        // Fast path: scan once and only allocate when an escape is actually needed.
        var requiresEscape = false
        for (i in s.indices) {
            val ch = s[i]
            if (ch == '&' || ch == '<' || ch == '>') {
                requiresEscape = true
                break
            }
        }
        if (!requiresEscape) return s

        val out = StringBuilder(s.length + 8)
        for (i in s.indices) {
            when (val ch = s[i]) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Escape a string for use as the value of an HTML attribute.
     *
     * Escapes `&`, `<`, `>`, `"`, and `'`. Both quote characters are escaped so the
     * caller may pick either delimiter without breaking the attribute.
     */
    public fun escapeAttr(s: String): String {
        if (s.isEmpty()) return s
        var requiresEscape = false
        for (i in s.indices) {
            val ch = s[i]
            if (ch == '&' || ch == '<' || ch == '>' || ch == '"' || ch == '\'') {
                requiresEscape = true
                break
            }
        }
        if (!requiresEscape) return s

        val out = StringBuilder(s.length + 8)
        for (i in s.indices) {
            when (val ch = s[i]) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
