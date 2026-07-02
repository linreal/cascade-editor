package io.github.linreal.cascade.screens.comments

import androidx.compose.runtime.mutableStateListOf
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Runtime state for the comments sample.
 *
 * Owns the composer's editor runtime ([editorState] / [textStates] / [spanStates])
 * plus the in-memory feed.
 */
internal class CommentsScreenModel {
    val textStates = BlockTextStates()
    val spanStates = BlockSpanStates()
    val editorState = EditorStateHolder()

    val comments = mutableStateListOf<Comment>().apply { addAll(seedComments()) }

    private var nextId: Long = (comments.maxOfOrNull { it.id } ?: 0L) + 1L

    init {
        resetComposer()
    }

    /**
     * Flattens the composer's current document into a single [Comment], or returns
     * `null` when the composer is empty/blank. Block texts are joined with newlines
     * and each block's spans are shifted into the combined coordinate space.
     */
    fun buildOwnComment(): Comment? {
        val builder = StringBuilder()
        val spans = mutableListOf<TextSpan>()
        editorState.state.blocks.forEachIndexed { index, block ->
            if (index > 0) builder.append('\n')
            val base = builder.length
            val blockText = textStates.getVisibleText(block.id)
                ?: (block.content as? BlockContent.Text)?.text
                ?: ""
            builder.append(blockText)
            spanStates.getSpans(block.id).forEach { span ->
                spans.add(span.copy(start = span.start + base, end = span.end + base))
            }
        }
        val text = builder.toString()
        if (text.isBlank()) return null
        return Comment(
            id = nextId++,
            authorName = SelfAuthor.NAME,
            initials = SelfAuthor.INITIALS,
            avatarColor = SelfAuthor.color,
            timestamp = "Now",
            isOwn = true,
            text = text,
            spans = spans,
        )
    }

    fun addComment(comment: Comment) {
        comments.add(comment)
    }

    /** Returns the composer to a single empty paragraph and clears editor history. */
    fun resetComposer() {
        editorState.loadFromJson(EMPTY_DOCUMENT_JSON, textStates, spanStates)
    }

    companion object {
        private val EMPTY_DOCUMENT_JSON: String =
            DocumentSchema.encodeToString(listOf(Block.paragraph()))
    }
}
