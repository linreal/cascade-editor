package io.github.linreal.cascade.profiles

object CustomHtmlSamples {
    val DialectHtml: String = """
        Hello world!
        <ul><li>List
        </li><li>
        </li></ul><ol><li>Numeric
        </li><li>
        </li><li>
        </li></ol><strong>bold</strong>
        <em>italic</em>
        <s>strike</s>
        <strong><em><s>all</s></em></strong>
        <a rel="nofollow noreferrer noopener" target="_blank" href="https://www.google.com">Link</a>
        <code>Code</code>
        Normal text
        <pre>Code block
        Code Block
        </pre>
        <ul><li class="ql-indent-2">content</li></ul>""".trimIndent()

    val CanonicalHtml: String = """
        Hello world!
        <ul><li>List
        </li><li>
        </li></ul><ol><li>Numeric
        </li><li>
        </li><li>
        </li></ol><strong>bold</strong>
        <em>italic</em>
        <s>strike</s>
        <strong><em><s>all</s></em></strong>
        <a rel="nofollow noreferrer noopener" target="_blank" href="https://www.google.com">Link</a>
        <code>Code</code>
        Normal text
        <pre>Code block
        Code Block
        </pre>
        <ul><li class="ql-indent-2">content
        </li></ul>""".trimIndent()
}
