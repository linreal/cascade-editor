package io.github.linreal.cascade.editor.markdown

/**
 * Hostile plain-text fixtures: strings that read as ordinary content but are
 * built entirely of Markdown syntax triggers and reused by the round-trip
 * property tests — any text drawn from
 * this list must survive `decode(encode(paragraph(text)))` unchanged once the
 * codec pipeline exists.
 */
internal object MarkdownHostileTextFixtures {

    val strings: List<String> = listOf(
        // Line-start block syntax.
        "# not a heading",
        "## also not a heading",
        "> not a quote",
        "- not a bullet",
        "+ not a bullet either",
        "* not a bullet",
        "1. not an ordered item",
        "12) also not an ordered item",
        "--- not a break, but this line is:",
        "---",
        "***",
        "___",
        "===",
        "~~~ not a fence",
        "``` not a fence",
        // Emphasis and code delimiters.
        "*emphasis* and **strong** and _under_ and __dunder__",
        "a*b*c and foo_bar_baz",
        "***triple*** ~~struck~~ ~single~",
        "`code span` and ``wide `span``",
        "5 * 3 = 15 and 4 _ 2",
        // Links, images, autolinks, HTML, entities.
        "[text](url) and [ref][label] and ![img](u)",
        "<https://autolink.example> and <u>tag</u>",
        "&amp; &nbsp; &#35; &unknown;",
        // Escapes.
        """backslash \ and \* literal""",
        // Multi-line combinations: every continuation line re-arms line-start
        // syntax.
        "first\n# second line heading",
        "quote line\n> continuation",
        "para\n- list continuation\n1. ordered continuation",
        "setext bait\n===\nand\n---",
        // Recognizer-shaped content: pipe tables, math blocks/spans, front
        // matter. Must re-decode as paragraph text, never as md.preserved.
        "| a | b |\n| - | - |\n| 1 | 2 |",
        "col a | col b",
        "$$\nE = mc^2\n$$",
        "inline \$x^2\$ math and \$5 prices",
        "---\ntitle: not front matter\n---",
        // Block markers hiding behind 1-3 leading spaces (still block syntax
        // per CommonMark) and carriage-return line endings.
        " # spaced heading bait",
        "   - spaced bullet bait",
        "  1. spaced ordered bait",
        "cr line\r# heading after cr\r\n- bullet after crlf",
        // Mixed pathological soup.
        "#*`_~[]()<>&\\!|=+-.",
        "  leading spaces and trailing spaces  ",
    )
}
