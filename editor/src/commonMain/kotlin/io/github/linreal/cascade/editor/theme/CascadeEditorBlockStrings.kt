package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.Immutable

/**
 * Localized display strings for a single block type.
 *
 * @property displayName Human-readable name shown in slash command popup.
 * @property description Brief description shown in slash command popup.
 * @property keywords Additional search keywords for slash command filtering.
 *   These are **additive** — at slash item generation time they are merged
 *   with the English keywords from [BlockDescriptor][io.github.linreal.cascade.editor.registry.BlockDescriptor],
 *   so English search always works regardless of locale.
 */
@Immutable
public data class BlockLocalizedStrings(
    val displayName: String,
    val description: String,
    val keywords: List<String> = emptyList(),
)

/**
 * Localized block names, descriptions, and keywords for all block types.
 *
 * Keyed by `typeId` (e.g. `"paragraph"`, `"heading_1"`, `"todo"`).
 * Entries are looked up during slash item generation — missing entries
 * fall back to the English values in [BlockDescriptor][io.github.linreal.cascade.editor.registry.BlockDescriptor].
 *
 * Use [default] for the built-in English preset.
 */
@Immutable
public data class CascadeEditorBlockStrings(
    val blocks: Map<String, BlockLocalizedStrings>,
) {
    /** Returns localized strings for [typeId], or `null` if not present. */
    public fun forType(typeId: String): BlockLocalizedStrings? = blocks[typeId]

    public companion object {
        /** Default English block strings matching current [BlockRegistry][io.github.linreal.cascade.editor.registry.BlockRegistry] values. */
        public fun default(): CascadeEditorBlockStrings = CascadeEditorBlockStrings(
            blocks = mapOf(
                "paragraph" to BlockLocalizedStrings(
                    displayName = "Paragraph",
                    description = "Plain text paragraph",
                    keywords = listOf("text", "p"),
                ),
                "heading_1" to BlockLocalizedStrings(
                    displayName = "Heading 1",
                    description = "Heading level 1",
                    keywords = listOf("h1", "heading", "title"),
                ),
                "heading_2" to BlockLocalizedStrings(
                    displayName = "Heading 2",
                    description = "Heading level 2",
                    keywords = listOf("h2", "heading", "title"),
                ),
                "heading_3" to BlockLocalizedStrings(
                    displayName = "Heading 3",
                    description = "Heading level 3",
                    keywords = listOf("h3", "heading", "title"),
                ),
                "heading_4" to BlockLocalizedStrings(
                    displayName = "Heading 4",
                    description = "Heading level 4",
                    keywords = listOf("h4", "heading", "title"),
                ),
                "heading_5" to BlockLocalizedStrings(
                    displayName = "Heading 5",
                    description = "Heading level 5",
                    keywords = listOf("h5", "heading", "title"),
                ),
                "heading_6" to BlockLocalizedStrings(
                    displayName = "Heading 6",
                    description = "Heading level 6",
                    keywords = listOf("h6", "heading", "title"),
                ),
                "todo" to BlockLocalizedStrings(
                    displayName = "To-do",
                    description = "Task with checkbox",
                    keywords = listOf("checkbox", "task", "check", "todo"),
                ),
                "bullet_list" to BlockLocalizedStrings(
                    displayName = "Bullet List",
                    description = "Unordered list item",
                    keywords = listOf("list", "bullet", "ul", "unordered"),
                ),
                "numbered_list" to BlockLocalizedStrings(
                    displayName = "Numbered List",
                    description = "Ordered list item",
                    keywords = listOf("list", "number", "ol", "ordered"),
                ),
                "quote" to BlockLocalizedStrings(
                    displayName = "Quote",
                    description = "Quoted text block",
                    keywords = listOf("blockquote", "citation"),
                ),
                "code" to BlockLocalizedStrings(
                    displayName = "Code",
                    description = "Code block with syntax highlighting",
                    keywords = listOf("code", "snippet", "programming"),
                ),
                "divider" to BlockLocalizedStrings(
                    displayName = "Divider",
                    description = "Horizontal line separator",
                    keywords = listOf("hr", "line", "separator", "horizontal"),
                ),
                "image" to BlockLocalizedStrings(
                    displayName = "Image",
                    description = "Embedded image",
                    keywords = listOf("picture", "photo", "img"),
                ),
            )
        )
    }
}
