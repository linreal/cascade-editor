package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.richtext.buildPreviewAnnotatedString
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi

/**
 * Static renderer for built-in text blocks other than todos.
 *
 * It deliberately owns no text field, focus, input, pointer, or animation state. Shared
 * content-slot chrome keeps its settled geometry aligned with [TextBlockRenderer].
 */
@OptIn(ExperimentalCascadePreviewApi::class)
internal class PreviewTextBlockRenderer : BlockPreviewRenderer<BlockType> {

    @Composable
    override fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    ) {
        val content = block.content as? BlockContent.Text ?: return
        val theme = LocalCascadeTheme.current
        val targetStyle = remember(block.type, theme.typography) {
            resolveTextBlockStyle(block.type, theme.typography)
        }.copy(color = theme.colors.text)

        when (block.type) {
            is BlockType.Code -> {
                CodeBlockVisual(modifier = modifier) {
                    PreviewBlockText(
                        content = content,
                        textStyle = targetStyle,
                        modifier = Modifier.fillMaxWidth(),
                        scope = scope,
                        supportsSpans = false,
                    )
                }
            }

            is BlockType.Quote -> {
                QuoteBlockVisual(modifier = modifier) {
                    PreviewBlockText(
                        content = content,
                        textStyle = targetStyle,
                        modifier = Modifier.fillMaxWidth(),
                        scope = scope,
                        supportsSpans = block.type.supportsSpans,
                    )
                }
            }

            is BlockType.BulletList, is BlockType.NumberedList -> {
                ListPrefixRowVisual(
                    block = block,
                    textStyle = targetStyle,
                    modifier = modifier.withStaticBlockIndentation(block),
                ) { contentModifier ->
                    PreviewBlockText(
                        content = content,
                        textStyle = targetStyle,
                        modifier = contentModifier,
                        scope = scope,
                        supportsSpans = block.type.supportsSpans,
                    )
                }
            }

            else -> {
                PreviewBlockText(
                    content = content,
                    textStyle = targetStyle,
                    modifier = modifier.withStaticBlockIndentation(block),
                    scope = scope,
                    supportsSpans = block.type.supportsSpans,
                )
            }
        }
    }
}

/**
 * Shared static text leaf for built-in preview renderers.
 *
 * Span conversion is cached by all visual inputs and bypassed for block types, such as
 * code, that opt out of rich-text spans.
 */
@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
internal fun PreviewBlockText(
    content: BlockContent.Text,
    textStyle: TextStyle,
    modifier: Modifier,
    scope: BlockPreviewScope,
    supportsSpans: Boolean,
    baseDecoration: TextDecoration? = null,
) {
    val colors = LocalCascadeTheme.current.colors
    val renderSpans = if (supportsSpans) content.spans else emptyList()
    val openLink = remember(scope) {
        { target: String -> scope.openLink(target) }
    }
    val linkHandler = openLink.takeIf { scope.canOpenLinks }
    val annotatedText = remember(
        content.text,
        renderSpans,
        colors.inlineCodeBackground,
        colors.highlight,
        colors.linkText,
        baseDecoration,
        linkHandler,
    ) {
        buildPreviewAnnotatedString(
            text = content.text,
            spans = renderSpans,
            inlineCodeBackground = colors.inlineCodeBackground,
            highlightBackground = colors.highlight,
            linkText = colors.linkText,
            baseDecoration = baseDecoration,
            onOpenLink = linkHandler,
        )
    }

    BasicText(
        text = annotatedText,
        modifier = modifier,
        style = textStyle,
        maxLines = scope.config.maxLinesPerTextBlock ?: Int.MAX_VALUE,
        overflow = scope.config.textOverflow,
    )
}
