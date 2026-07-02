package io.github.linreal.cascade.screens.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle as ComposeSpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan

/**
 * A single comment in the demo feed.
 *
 * Comments are stored as plain text plus a list of [TextSpan]s (the same public
 * rich-text model the editor produces). The feed renders them with
 * [rememberCommentAnnotatedString] rather than embedding an editor per comment.
 */
@Immutable
internal data class Comment(
    val id: Long,
    val authorName: String,
    val initials: String,
    val avatarColor: Color,
    val timestamp: String,
    val isOwn: Boolean,
    val text: String,
    val spans: List<TextSpan>,
)

/**
 * Small builder that accumulates plain and styled text segments and computes the
 * resulting [TextSpan] offsets, so seed data can be authored without manual index
 * arithmetic.
 */
internal class CommentTextBuilder {
    private val sb = StringBuilder()
    private val spans = mutableListOf<TextSpan>()

    fun plain(text: String): CommentTextBuilder {
        sb.append(text)
        return this
    }

    fun styled(text: String, vararg styles: SpanStyle): CommentTextBuilder {
        val start = sb.length
        sb.append(text)
        val end = sb.length
        styles.forEach { style -> spans.add(TextSpan(start, end, style)) }
        return this
    }

    fun text(): String = sb.toString()

    fun spans(): List<TextSpan> = spans.toList()
}

private inline fun buildComment(
    id: Long,
    authorName: String,
    initials: String,
    avatarColor: Color,
    timestamp: String,
    isOwn: Boolean,
    block: CommentTextBuilder.() -> Unit,
): Comment {
    val builder = CommentTextBuilder().apply(block)
    return Comment(
        id = id,
        authorName = authorName,
        initials = initials,
        avatarColor = avatarColor,
        timestamp = timestamp,
        isOwn = isOwn,
        text = builder.text(),
        spans = builder.spans(),
    )
}

private val AvatarMaya = Color(0xFF6C3DE8)
private val AvatarTheo = Color(0xFFE2B23A)
private val AvatarJordan = Color(0xFF34C77B)
private val AvatarYou = Color(0xFF160B2E)

/** Author identity used for comments sent from the composer. */
internal object SelfAuthor {
    const val NAME: String = "You"
    const val INITIALS: String = "You"
    val color: Color = AvatarYou
}

/** Seed feed mirroring the imported design. */
internal fun seedComments(): List<Comment> = listOf(
    buildComment(1, "Maya Reyes", "MR", AvatarMaya, "9:32 AM", isOwn = false) {
        plain("The new ")
        styled("onboarding flow", SpanStyle.Bold)
        plain(" feels so much smoother. Nice work shipping it this sprint.")
    },
    buildComment(2, "Theo Kane", "TK", AvatarTheo, "9:41 AM", isOwn = false) {
        plain("One thing — the ")
        styled("empty state", SpanStyle.Italic)
        plain(" on step 2 still says ")
        styled("TODO", SpanStyle.InlineCode)
        plain(". Can we fill that in before the demo?")
    },
    buildComment(3, "Jordan Park", "JP", AvatarJordan, "9:48 AM", isOwn = false) {
        plain("Good catch. I'll have copy ready by ")
        styled("this afternoon", SpanStyle.Underline)
        plain(" and ping you to review.")
    },
    buildComment(4, SelfAuthor.NAME, SelfAuthor.INITIALS, SelfAuthor.color, "9:50 AM", isOwn = true) {
        plain("Perfect. I'll hold the build until the ")
        styled("copy lands", SpanStyle.Bold)
        plain(" — flag me if anything blocks you.")
    },
    buildComment(5, "Theo Kane", "TK", AvatarTheo, "9:53 AM", isOwn = false) {
        plain("Nothing blocking — ")
        styled("thanks both", SpanStyle.Italic)
        plain(". Drafting now.")
    },
)

/**
 * Maps the public [SpanStyle] runs of a comment onto a Compose [AnnotatedString].
 *
 * This is the realistic consumer-side counterpart to the editor's internal span
 * mapper: an app reads `(text, spans)` out of the editor and renders it however
 * it likes — here, as a styled chat bubble.
 */
@Composable
internal fun rememberCommentAnnotatedString(
    text: String,
    spans: List<TextSpan>,
    inlineCodeBackground: Color,
    linkColor: Color,
): AnnotatedString = remember(text, spans, inlineCodeBackground, linkColor) {
    buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start == end) return@forEach
            val style = when (val s = span.style) {
                SpanStyle.Bold -> ComposeSpanStyle(fontWeight = FontWeight.Bold)
                SpanStyle.Italic -> ComposeSpanStyle(fontStyle = FontStyle.Italic)
                SpanStyle.Underline -> ComposeSpanStyle(textDecoration = TextDecoration.Underline)
                SpanStyle.StrikeThrough -> ComposeSpanStyle(textDecoration = TextDecoration.LineThrough)
                SpanStyle.InlineCode -> ComposeSpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = inlineCodeBackground,
                )
                is SpanStyle.Highlight -> ComposeSpanStyle(background = Color(s.colorArgb))
                is SpanStyle.Link -> ComposeSpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                )
                is SpanStyle.Custom -> null
            }
            if (style != null) addStyle(style, start, end)
        }
    }
}
