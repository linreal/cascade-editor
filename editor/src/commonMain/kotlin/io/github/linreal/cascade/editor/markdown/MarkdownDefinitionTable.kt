package io.github.linreal.cascade.editor.markdown

/** Internal resolved reference definition collected from the JetBrains AST. */
internal data class MarkdownLinkReferenceDefinition(
    val destination: String,
    val title: String?,
)

/**
 * Document-wide table of link-reference definitions collected during the
 * block phase, so the inline phase can resolve
 * forward references.
 *
 * Lookup keys are CommonMark-normalized labels ([normalizeLabel]): whitespace
 * trimmed and collapsed, then case-folded. On duplicate labels the **first**
 * definition wins (CommonMark); [register] reports the collision so the rule
 * can warn about the shadowed duplicate.
 */
internal class MarkdownDefinitionTable {

    private val entries = LinkedHashMap<String, MarkdownLinkReferenceDefinition>()

    /** Number of registered definitions (duplicates excluded). */
    val size: Int get() = entries.size

    /**
     * Register [definition] under the raw [label]. Returns `true` when the
     * definition was stored, `false` when an earlier definition already owns
     * the normalized label (first definition wins).
     */
    fun register(label: String, definition: MarkdownLinkReferenceDefinition): Boolean {
        val key = normalizeLabel(label)
        if (entries.containsKey(key)) return false
        entries[key] = definition
        return true
    }

    /** Look up the definition for the raw [label], or `null`. */
    fun lookup(label: String): MarkdownLinkReferenceDefinition? =
        entries[normalizeLabel(label)]

    companion object {

        /**
         * CommonMark label normalization: strip leading/trailing whitespace,
         * collapse internal whitespace runs (spaces, tabs, line terminators)
         * to one space, then case-fold. The uppercase-then-lowercase pass
         * approximates Unicode case folding in common code (it maps
         * `ß` → `ss` and similar one-to-many folds correctly).
         */
        fun normalizeLabel(label: String): String {
            val sb = StringBuilder(label.length)
            var pendingSpace = false
            for (ch in label) {
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                    if (sb.isNotEmpty()) pendingSpace = true
                    continue
                }
                if (pendingSpace) {
                    sb.append(' ')
                    pendingSpace = false
                }
                sb.append(ch)
            }
            return sb.toString().uppercase().lowercase()
        }
    }
}
