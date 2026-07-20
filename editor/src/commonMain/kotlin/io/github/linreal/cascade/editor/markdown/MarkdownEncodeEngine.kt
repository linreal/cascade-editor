package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import kotlin.coroutines.cancellation.CancellationException

/** Encode-side line-ending option. */
@ExperimentalCascadeMarkdownApi
public enum class MarkdownLineEnding {
    /** Unix `\n` line endings. Default. */
    Lf,

    /** Windows `\r\n` line endings. */
    CrLf,
}

/**
 * Canonical whole-document Markdown writer.
 *
 * The engine — never the encoders — owns block separation: every encoder emit
 * is one canonical unit, units are joined with a blank line in CommonMark mode
 * (a single newline under [NewlineSemantics.HardBreak]), and non-empty output
 * ends with exactly one final newline. [MarkdownEmit.Raw] declares content
 * only; [MarkdownEmit.Verbatim] is the distinct path for preserved source
 * whose slice is never escaped or transformed — with one documented boundary
 * canonicalization: trailing newlines of a document-final verbatim unit fold
 * into the single final newline (built-in preserved slices are terminator-free,
 * so only consumer emissions ending in `\n` can observe this).
 *
 * Group encoders run first (the `listOutline` run claim mirrors the HTML
 * engine); a throwing or [MarkdownEmit.Skip]-returning encoder degrades
 * through the registered fallback with a warning — consumer exceptions never
 * cross the codec boundary.
 *
 * Inline span emission for text blocks routes through
 * [MarkdownEncodeContextImpl.encodeInlineLines], which is the
 * seam where the verified inline renderer (sweep-line walker over span
 * boundaries, not per-boundary span filtering) plugs in.
 */
internal object MarkdownEncodeEngine {

    fun encodeWithReport(
        blocks: List<Block>,
        profile: MarkdownProfile,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
        lineEnding: MarkdownLineEnding = MarkdownLineEnding.Lf,
    ): MarkdownEncodeResult {
        val state = EncodeState(limits)
        val context = MarkdownEncodeContextImpl(
            profile = profile,
            limits = limits,
            warningSink = state::warn,
        )
        val hardBreak = profile.newlineSemantics == NewlineSemantics.HardBreak
        val separator = if (hardBreak) "\n" else "\n\n"
        val runner = Runner(profile, context, state, unitSeparator = separator)

        val units = ArrayList<String>()
        var runningLength = 0
        var index = 0
        while (index < blocks.size && !state.isAborted) {
            val block = blocks[index]
            val groupMatch = runner.findGroupMatch(block)
            // `null` means "nothing to emit" (an encoder Skip that reached an
            // empty fallback); `""` means an intentional empty unit (a HardBreak
            // empty paragraph). Only the latter becomes a blank line.
            val unit: String?
            if (groupMatch == null) {
                unit = runner.encodeSingleBlock(block)
                index++
            } else {
                val run = runner.collectRun(blocks, startIndex = index, groupMatch = groupMatch)
                unit = runner.encodeGroupOrFallback(groupMatch.encoder, run)
                index += run.size
            }
            if (unit == null) continue
            // In HardBreak mode empty units are real: an empty paragraph owns
            // one blank line, so N empties between two non-empty units yield
            // exactly N+1 newlines and trailing empties preserve trailing
            // blanks. In CommonMark mode a bare empty unit is
            // still dropped.
            if (unit.isEmpty() && !hardBreak) continue
            units += unit
            runningLength += unit.length + separator.length
            if (runningLength > limits.maxOutputChars) {
                state.abort(MarkdownEncodeWarning.OutputLimitExceeded(limit = limits.maxOutputChars))
            }
        }
        if (state.isAborted) return MarkdownEncodeResult.aborted(state.warnings.toList())

        val body = units.joinToString(separator)
        var output = when {
            units.isEmpty() -> ""
            // HardBreak: the join already carries the blank-line structure;
            // append exactly one final newline (the last line's terminator)
            // without trimming, so trailing empty paragraphs survive.
            hardBreak -> body + "\n"
            else -> body.trimEnd('\n') + "\n"
        }
        if (lineEnding == MarkdownLineEnding.CrLf) output = output.toCrLf()
        if (output.length > limits.maxOutputChars) {
            state.abort(MarkdownEncodeWarning.OutputLimitExceeded(limit = limits.maxOutputChars))
            return MarkdownEncodeResult.aborted(state.warnings.toList())
        }
        return MarkdownEncodeResult.success(
            markdown = output,
            warnings = state.warnings.toList(),
        )
    }

    /** Bounded warning collection + abort latch, mirroring [MarkdownParseState]. */
    private class EncodeState(private val limits: MarkdownCodecLimits) {

        private val warningList = ArrayList<MarkdownEncodeWarning>()
        val warnings: List<MarkdownEncodeWarning> get() = warningList

        private var fatal: MarkdownEncodeWarning? = null
        val isAborted: Boolean get() = fatal != null

        fun warn(warning: MarkdownEncodeWarning) {
            if (isAborted) return
            if (warningList.size >= limits.maxWarnings) {
                abort(
                    MarkdownEncodeWarning.LimitExceeded(
                        kind = MarkdownCodecLimitKind.Warnings,
                        limit = limits.maxWarnings,
                    ),
                )
                return
            }
            warningList.add(warning)
        }

        fun abort(warning: MarkdownEncodeWarning) {
            if (fatal != null) return
            fatal = warning
            warningList.add(warning)
        }
    }

    private class Runner(
        private val profile: MarkdownProfile,
        private val context: MarkdownEncodeContextImpl,
        private val state: EncodeState,
        private val unitSeparator: String,
    ) {

        fun findGroupMatch(block: Block): GroupMatch? {
            for (named in profile.blockGroupEncoders) {
                val key = groupKeyOrNull(named.encoder, block)
                if (key != null) return GroupMatch(encoder = named.encoder, key = key)
            }
            return null
        }

        fun collectRun(blocks: List<Block>, startIndex: Int, groupMatch: GroupMatch): List<Block> {
            val run = mutableListOf(blocks[startIndex])
            var index = startIndex + 1
            while (index < blocks.size) {
                val nextKey = groupKeyOrNull(groupMatch.encoder, blocks[index])
                if (nextKey != groupMatch.key) break
                run += blocks[index]
                index++
            }
            return run
        }

        // Returns null when there is nothing to emit (Skip that reached an empty
        // fallback); "" is a distinct intentional empty unit.
        fun encodeGroupOrFallback(encoder: MarkdownBlockGroupEncoder, blocks: List<Block>): String? {
            val emit = try {
                encoder.encodeGroup(context, blocks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.warn(encoderExceptionWarning(blocks.firstOrNull(), e))
                MarkdownEmit.Skip
            }
            return when (emit) {
                is MarkdownEmit.Raw -> emit.markdown.trim('\n')
                is MarkdownEmit.Verbatim -> emit.markdown
                MarkdownEmit.Skip -> {
                    val parts = blocks.mapNotNull { encodeBlockFallback(it) }
                    if (parts.isEmpty()) null else parts.joinToString(unitSeparator)
                }
            }
        }

        fun encodeSingleBlock(block: Block): String? {
            val primary = profile.blockEncoderFor(block)
            if (primary != null) {
                val emit = try {
                    primary.encodeUnchecked(context, block, block.content)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.warn(encoderExceptionWarning(block, e))
                    MarkdownEmit.Skip
                }
                when (emit) {
                    // Content-only contract: defensively drop boundary blank
                    // lines an encoder should not have included.
                    is MarkdownEmit.Raw -> return emit.markdown.trim('\n')
                    is MarkdownEmit.Verbatim -> return emit.markdown
                    MarkdownEmit.Skip -> Unit
                }
            }
            return encodeBlockFallback(block)
        }

        private fun encodeBlockFallback(block: Block): String? {
            val fallback = profile.encoderBlockFallback ?: return null
            val emit = try {
                fallback.encode(context, block, block.content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.warn(encoderExceptionWarning(block, e))
                MarkdownEmit.Skip
            }
            return when (emit) {
                is MarkdownEmit.Raw -> emit.markdown.trim('\n')
                is MarkdownEmit.Verbatim -> emit.markdown
                MarkdownEmit.Skip -> null
            }
        }

        private fun groupKeyOrNull(encoder: MarkdownBlockGroupEncoder, block: Block): Any? =
            try {
                encoder.groupKey(block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.warn(encoderExceptionWarning(block, e))
                null
            }

        private fun encoderExceptionWarning(
            block: Block?,
            exception: Exception,
        ): MarkdownEncodeWarning = MarkdownEncodeWarning.EncoderException(
            typeId = block?.warningTypeId(),
            message = exception.message ?: (exception::class.simpleName ?: "exception"),
            blockId = block?.id,
        )
    }

    private data class GroupMatch(
        val encoder: MarkdownBlockGroupEncoder,
        val key: Any,
    )
}

@Suppress("UNCHECKED_CAST")
private fun MarkdownBlockEncoder<*>.encodeUnchecked(
    ctx: MarkdownEncodeContext,
    block: Block,
    content: BlockContent,
): MarkdownEmit = (this as MarkdownBlockEncoder<BlockType>).encode(ctx, block, content)

private fun Block.warningTypeId(): String {
    val type = this.type
    if (type is CustomBlockType) return type.typeId
    val content = this.content
    if (content is BlockContent.Custom) return content.typeId
    return type.typeId
}

private fun String.toCrLf(): String {
    if (indexOf('\n') < 0) return this
    val out = StringBuilder(length + 16)
    var previous = ' '
    for (ch in this) {
        // A verbatim slice may already contain CRLF pairs; never double the CR.
        if (ch == '\n' && previous != '\r') out.append('\r')
        out.append(ch)
        previous = ch
    }
    return out.toString()
}
