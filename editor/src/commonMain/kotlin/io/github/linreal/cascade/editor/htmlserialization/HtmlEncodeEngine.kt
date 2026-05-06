package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType

internal object HtmlEncodeEngine {

    internal fun encodeWithReport(blocks: List<Block>, profile: HtmlProfile): HtmlEncodeResult {
        val warnings = mutableListOf<HtmlEncodeWarning>()
        val context = HtmlEncodeContextImpl(profile = profile, warnings = warnings)
        val html = try {
            Runner(profile = profile, context = context, warnings = warnings).encode(blocks)
        } catch (throwable: Throwable) {
            warnings += encoderExceptionWarning(typeId = null, throwable = throwable)
            ""
        }
        return HtmlEncodeResult(html = html, warnings = warnings)
    }

    private class Runner(
        private val profile: HtmlProfile,
        private val context: HtmlEncodeContextImpl,
        private val warnings: MutableList<HtmlEncodeWarning>,
    ) {

        fun encode(blocks: List<Block>): String = buildString {
            var index = 0
            while (index < blocks.size) {
                val block = blocks[index]
                val groupMatch = findGroupMatch(block)
                if (groupMatch == null) {
                    append(encodeSingleBlock(block))
                    index++
                } else {
                    val run = collectRun(blocks, startIndex = index, groupMatch = groupMatch)
                    append(encodeGroupOrFallback(groupMatch.encoder, run))
                    index += run.size
                }
            }
        }

        private fun findGroupMatch(block: Block): GroupMatch? {
            for (named in profile.blockGroupEncoders) {
                val key = groupKeyOrNull(named.encoder, block)
                if (key != null) return GroupMatch(encoder = named.encoder, key = key)
            }
            return null
        }

        private fun collectRun(
            blocks: List<Block>,
            startIndex: Int,
            groupMatch: GroupMatch,
        ): List<Block> {
            val run = mutableListOf(blocks[startIndex])
            var index = startIndex + 1
            while (index < blocks.size) {
                val nextBlock = blocks[index]
                val nextKey = groupKeyOrNull(groupMatch.encoder, nextBlock)
                if (nextKey != groupMatch.key) break
                run += nextBlock
                index++
            }
            return run
        }

        private fun encodeGroupOrFallback(
            encoder: BlockGroupEncoder,
            blocks: List<Block>,
        ): String {
            val emit = try {
                encoder.encodeGroup(context, blocks)
            } catch (throwable: Throwable) {
                warnings += encoderExceptionWarning(blocks.firstOrNull()?.warningTypeId(), throwable)
                HtmlEmit.Skip
            }

            return when (emit) {
                is HtmlEmit.Raw -> emit.html
                HtmlEmit.Skip -> blocks.joinToString(separator = "") { encodeSingleBlockWithFallbackOnly(it) }
            }
        }

        private fun encodeSingleBlock(block: Block): String {
            val primary = profile.blockEncoderFor(block)
            if (primary != null) {
                val emit = try {
                    primary.encodeUnchecked(context, block, block.content)
                } catch (throwable: Throwable) {
                    warnings += encoderExceptionWarning(block.warningTypeId(), throwable)
                    HtmlEmit.Skip
                }
                if (emit != HtmlEmit.Skip) return emit.toHtml()
            }
            return encodeBlockFallback(block)
        }

        private fun encodeSingleBlockWithFallbackOnly(block: Block): String = encodeBlockFallback(block)

        private fun encodeBlockFallback(block: Block): String {
            val fallback = profile.encoderBlockFallback ?: return ""
            val emit = try {
                fallback.encode(context, block, block.content)
            } catch (throwable: Throwable) {
                warnings += encoderExceptionWarning(block.warningTypeId(), throwable)
                HtmlEmit.Skip
            }
            return if (emit == HtmlEmit.Skip) "" else emit.toHtml()
        }

        private fun groupKeyOrNull(encoder: BlockGroupEncoder, block: Block): Any? =
            try {
                encoder.groupKey(block)
            } catch (throwable: Throwable) {
                warnings += encoderExceptionWarning(block.warningTypeId(), throwable)
                null
            }

        private data class GroupMatch(
            val encoder: BlockGroupEncoder,
            val key: Any,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun BlockEncoder<*>.encodeUnchecked(
    ctx: HtmlEncodeContext,
    block: Block,
    content: BlockContent,
): HtmlEmit =
    (this as BlockEncoder<BlockType>).encode(ctx, block, content)

private fun HtmlEmit.toHtml(): String = when (this) {
    is HtmlEmit.Raw -> html
    HtmlEmit.Skip -> ""
}

private fun Block.warningTypeId(): String {
    val type = this.type
    if (type is CustomBlockType) return type.typeId
    val content = this.content
    if (content is BlockContent.Custom) return content.typeId
    return type.typeId
}
