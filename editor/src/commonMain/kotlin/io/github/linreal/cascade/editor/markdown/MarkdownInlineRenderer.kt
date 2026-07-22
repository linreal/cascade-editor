package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import kotlin.coroutines.cancellation.CancellationException

/**
 * Verified inline span encoder.
 *
 * A sweep-line walker over span boundaries — never per-boundary full-span
 * filtering — emits canonical markers with close/reopen for
 * partial overlaps, then **re-parses its own output with the decode-side
 * the JetBrains-backed inline AST adapter**. Output is returned only when it decodes back to
 * exactly the spans that were encoded; on mismatch the weakest span is dropped
 * with [MarkdownEncodeWarning.AmbiguousEmphasis] (or
 * [MarkdownEncodeWarning.DroppedSpanOverlap] for code/link residue) and the
 * segment re-emits — never silently different spans.
 *
 * `InlineCode` overlap classification:
 * - an outer style fully containing a code range nests normally;
 * - disjoint styles are trivial;
 * - a style wholly inside code content is unrepresentable → dropped with
 *   `DroppedSpanOverlap`;
 * - a partial crossing splits the code range at the crossing boundary
 *   (close/reopen); the adjacent code pieces re-merge under decode-side span
 *   normalization, and the verification pass proves it.
 * - code content containing a line ending cannot round-trip (CommonMark
 *   normalizes it to a space) → the code span drops with `DroppedSpanOverlap`.
 */
internal class MarkdownInlineRenderer(
    private val profile: MarkdownProfile,
    private val limits: MarkdownCodecLimits,
    private val warningSink: (MarkdownEncodeWarning) -> Unit,
) {

    /**
     * Render one text block's content as physical output lines. Hard-break
     * markers (when [hardBreakMarker] is non-null) are appended to every
     * non-final line so prefix encoders transform complete lines.
     */
    fun renderLines(
        blockId: BlockId?,
        text: String,
        spans: List<TextSpan>,
        hardBreakMarker: String?,
    ): List<String> {
        // Span-free text enters the same emit/verify loop: plain text has
        // value-based failure modes the escaper cannot fix (trailing spaces,
        // trailing spaces before a hard break, >0 leading spaces) and those
        // must warn instead of silently re-decoding differently — otherwise a
        // clean encodeWithReport would falsify the default support claim.
        val normalized = SpanAlgorithms.normalize(spans, text.length)
        val working = classify(normalized, text, blockId)
        // Whether the bare text (no spans, no markers) survives a decode.
        // Computed lazily on the first mismatch: it separates value-based text
        // exclusions (unfixable — warn once) from marker-induced text damage
        // (fixable by dropping the ambiguous span).
        var plainProbe: Boolean? = null
        fun plainTextRoundTrips(): Boolean = plainProbe ?: run {
            val plainLines = attachBreakMarkers(
                escapeText(text, atLineStart = true),
                hardBreakMarker,
            )
            val ok = verify(plainLines, text, emptyList()) == VerifyOutcome.Match
            plainProbe = ok
            ok
        }

        while (true) {
            val parts = emitParts(text, working)
            switchJuxtaposedItalics(parts, text)
            val emitted = assemble(parts)
            val lines = attachBreakMarkers(emitted, hardBreakMarker)

            if (!needsVerification(working)) return lines
            val outcome = verify(lines, text, working)
            if (outcome == VerifyOutcome.Match) return lines
            if (outcome == VerifyOutcome.TextMismatch && !plainTextRoundTrips()) {
                // The visible text itself does not round-trip (a value-based
                // exclusion); dropping spans cannot fix it,
                // so warn once and return instead of spending
                // AmbiguousEmphasis warnings on innocent spans.
                warningSink(
                    MarkdownEncodeWarning.DroppedSpanOverlap(
                        blockId = blockId,
                        textRange = null,
                        reason = "emitted inline text failed encode-side verification",
                    ),
                )
                return lines
            }
            // Span mismatch, or a text mismatch induced by the emitted
            // markers themselves (e.g. an unpairable leftover delimiter):
            // both are resolved by dropping the weakest span and re-emitting.

            val victim = chooseVictim(working)
            if (victim == null) {
                // Defensive: a span mismatch with nothing left to drop.
                for (span in working) {
                    warningSink(
                        MarkdownEncodeWarning.DroppedSpanOverlap(
                            blockId = blockId,
                            textRange = MarkdownTextRange(span.start, span.end),
                            reason = "span failed encode-side verification",
                        ),
                    )
                }
                if (working.isEmpty()) return lines
                working.clear()
                continue
            }
            working.remove(victim)
            if (victim.strategy == Strategy.Delimiter) {
                warningSink(
                    MarkdownEncodeWarning.AmbiguousEmphasis(
                        blockId = blockId,
                        textRange = MarkdownTextRange(victim.start, victim.end),
                    ),
                )
            } else {
                warningSink(
                    MarkdownEncodeWarning.DroppedSpanOverlap(
                        blockId = blockId,
                        textRange = MarkdownTextRange(victim.start, victim.end),
                        reason = "span failed encode-side verification",
                    ),
                )
            }
        }
    }

    // Classification

    private enum class Strategy { Delimiter, Code, LinkNative, MarkPair }

    private class RenderSpan(
        val start: Int,
        val end: Int,
        val style: SpanStyle,
        val strategy: Strategy,
        var marker: String? = null,
        var pair: MarkdownMarkPair? = null,
    ) {
        val openPriority: Int
            get() = when (strategy) {
                Strategy.LinkNative -> 0
                Strategy.MarkPair -> 1
                Strategy.Delimiter -> 2
                Strategy.Code -> 3
            }
    }

    private fun classify(
        spans: List<TextSpan>,
        text: String,
        blockId: BlockId?,
    ): MutableList<RenderSpan> {
        val codeRanges = ArrayList<TextSpan>()
        val out = ArrayList<RenderSpan>(spans.size)

        for (span in spans) {
            if (span.style == SpanStyle.InlineCode) {
                val covered = text.substring(span.start, span.end)
                if (covered.indexOf('\n') >= 0) {
                    // CommonMark normalizes line endings inside code spans to a
                    // space, so the code marks drop while the text is kept.
                    warningSink(
                        MarkdownEncodeWarning.DroppedSpanOverlap(
                            blockId = blockId,
                            textRange = MarkdownTextRange(span.start, span.end),
                            reason = "inline-code content contains a line ending, " +
                                "which cannot round-trip as a Markdown code span",
                        ),
                    )
                    continue
                }
                codeRanges.add(span)
                out.add(RenderSpan(span.start, span.end, span.style, Strategy.Code))
            }
        }

        for (span in spans) {
            val style = span.style
            if (style == SpanStyle.InlineCode) continue

            // A style wholly inside code content is unrepresentable; containing
            // and disjoint layouts are fine; partial crossings split the code
            // range at emission time.
            val insideCode = codeRanges.any { code ->
                span.start >= code.start && span.end <= code.end &&
                    !(span.start <= code.start && span.end >= code.end)
            }
            if (insideCode) {
                warningSink(
                    MarkdownEncodeWarning.DroppedSpanOverlap(
                        blockId = blockId,
                        textRange = MarkdownTextRange(span.start, span.end),
                        reason = "span lies inside inline-code content, where no " +
                            "Markdown markup can apply",
                    ),
                )
                continue
            }

            if (style is SpanStyle.Link) {
                out.add(
                    RenderSpan(span.start, span.end, style, Strategy.LinkNative).also {
                        it.pair = DefaultMarkdownSpanEncoders.linkMarkPair(style.url)
                    },
                )
                continue
            }

            val explicit = profile.spanEncoderFor(style)
            if (explicit != null) {
                val pair = encodePairSafely(explicit, style, blockId)
                if (pair != null) {
                    out.add(
                        RenderSpan(span.start, span.end, style, Strategy.MarkPair)
                            .also { it.pair = pair },
                    )
                    continue
                }
            }

            val marker = DefaultMarkdownSpanEncoders.canonicalMarkerFor(profile, style)
            if (marker != null) {
                out.add(
                    RenderSpan(span.start, span.end, style, Strategy.Delimiter)
                        .also { it.marker = marker },
                )
                continue
            }

            // No encoding: text kept, marks dropped through the span fallback.
            warningSink(
                MarkdownEncodeWarning.UnsupportedSpan(
                    typeId = (style as? SpanStyle.Custom)?.typeId,
                    reason = "no Markdown encoding is registered for this span style",
                    blockId = blockId,
                    textRange = MarkdownTextRange(span.start, span.end),
                ),
            )
            val fallback = profile.encoderSpanFallback ?: continue
            val pair = encodePairSafely(fallback, style, blockId) ?: continue
            if (pair.open.isNotEmpty() || pair.close.isNotEmpty()) {
                out.add(
                    RenderSpan(span.start, span.end, style, Strategy.MarkPair)
                        .also { it.pair = pair },
                )
            }
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodePairSafely(
        encoder: MarkdownSpanEncoder<*>,
        style: SpanStyle,
        blockId: BlockId?,
    ): MarkdownMarkPair? = try {
        (encoder as MarkdownSpanEncoder<SpanStyle>).encode(style)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        warningSink(
            MarkdownEncodeWarning.EncoderException(
                typeId = (style as? SpanStyle.Custom)?.typeId,
                message = e.message ?: (e::class.simpleName ?: "exception"),
                blockId = blockId,
            ),
        )
        null
    }

    // Emission (sweep line over span boundaries)

    private sealed interface Part {
        class Text(val raw: String) : Part
        class Marker(val span: RenderSpan, val open: Boolean) : Part
        class Code(val content: String) : Part
    }

    private fun emitParts(text: String, working: List<RenderSpan>): MutableList<Part> {
        val parts = ArrayList<Part>()
        if (working.isEmpty()) {
            if (text.isNotEmpty()) parts.add(Part.Text(text))
            return parts
        }

        val stackSpans = working.filter { it.strategy != Strategy.Code }
        val codeSpans = working.filter { it.strategy == Strategy.Code }.sortedBy { it.start }

        val boundaryList = ArrayList<Int>(working.size * 2 + 2)
        boundaryList.add(0)
        boundaryList.add(text.length)
        for (span in working) {
            boundaryList.add(span.start)
            boundaryList.add(span.end)
        }
        val boundaries = boundaryList.distinct().sorted().toIntArray()

        val opensAt = HashMap<Int, MutableList<RenderSpan>>()
        val hasCloseAt = HashMap<Int, Boolean>()
        for (span in stackSpans) {
            opensAt.getOrPut(span.start) { mutableListOf() }.add(span)
            hasCloseAt[span.end] = true
        }
        for (list in opensAt.values) {
            // Longest span first; at equal extents links open outermost so the
            // canonical form is `[**text**](target)`.
            list.sortWith(compareByDescending<RenderSpan> { it.end }.thenBy { it.openPriority })
        }

        val stack = ArrayList<RenderSpan>()
        var codeIndex = 0

        for (index in boundaries.indices) {
            val at = boundaries[index]

            if (hasCloseAt[at] == true) {
                var lowest = -1
                for (stackIndex in stack.indices) {
                    if (stack[stackIndex].end == at) {
                        lowest = stackIndex
                        break
                    }
                }
                if (lowest >= 0) {
                    val reopen = ArrayList<RenderSpan>()
                    for (stackIndex in stack.indices.reversed()) {
                        if (stackIndex < lowest) break
                        val span = stack.removeAt(stackIndex)
                        parts.add(Part.Marker(span, open = false))
                        if (span.end != at) reopen.add(span)
                    }
                    reopen.sortWith(compareByDescending { it.end })
                    for (span in reopen) {
                        stack.add(span)
                        parts.add(Part.Marker(span, open = true))
                    }
                }
            }

            opensAt[at]?.forEach { span ->
                stack.add(span)
                parts.add(Part.Marker(span, open = true))
            }

            if (index + 1 >= boundaries.size) break
            val next = boundaries[index + 1]
            if (next <= at) continue

            while (codeIndex < codeSpans.size && codeSpans[codeIndex].end <= at) codeIndex++
            val code = codeSpans.getOrNull(codeIndex)
            if (code != null && at >= code.start && next <= code.end) {
                parts.add(Part.Code(text.substring(at, next)))
            } else {
                parts.add(Part.Text(text.substring(at, next)))
            }
        }
        return parts
    }

    /**
     * The `*` → `_` auto-switch: when two markers of
     * the same character from different spans end up juxtaposed, the span with
     * an alternate-character marker (canonically the italic `_`) switches —
     * unless one of its boundaries is intraword, where `_` cannot flank and the
     * ambiguity risk is accepted (verification decides).
     */
    private fun switchJuxtaposedItalics(parts: List<Part>, text: String) {
        for (index in 0 until parts.size - 1) {
            val a = parts[index] as? Part.Marker ?: continue
            val b = parts[index + 1] as? Part.Marker ?: continue
            val markerA = a.span.marker ?: continue
            val markerB = b.span.marker ?: continue
            if (markerA[0] != markerB[0] || a.span === b.span) continue

            val candidates = listOf(a.span, b.span)
                .filter { it.strategy == Strategy.Delimiter }
                .sortedBy { it.marker?.length ?: Int.MAX_VALUE }
            for (candidate in candidates) {
                val current = candidate.marker ?: continue
                if (hasIntrawordBoundary(candidate, text)) continue
                val alternate = DefaultMarkdownSpanEncoders.alternateMarkerFor(
                    profile = profile,
                    style = candidate.style,
                    excludeChar = current[0],
                ) ?: continue
                candidate.marker = alternate
                break
            }
        }
    }

    private fun hasIntrawordBoundary(span: RenderSpan, text: String): Boolean {
        fun alnum(index: Int): Boolean =
            index in text.indices && text[index].isLetterOrDigit()
        val openIntraword = alnum(span.start - 1) && alnum(span.start)
        val closeIntraword = alnum(span.end - 1) && alnum(span.end)
        return openIntraword || closeIntraword
    }

    private fun assemble(parts: List<Part>): String {
        val sb = StringBuilder()
        var atLineStart = true
        for (part in parts) {
            when (part) {
                is Part.Text -> {
                    val escaped = escapeText(part.raw, atLineStart)
                    sb.append(escaped)
                    if (part.raw.isNotEmpty()) atLineStart = part.raw.last() == '\n'
                }

                is Part.Marker -> {
                    sb.append(markerText(part))
                    atLineStart = false
                }

                is Part.Code -> {
                    val delimiters = Markdown.codeSpanDelimiters(part.content)
                    sb.append(delimiters.open).append(part.content).append(delimiters.close)
                    atLineStart = false
                }
            }
        }
        return sb.toString()
    }

    private fun markerText(part: Part.Marker): String {
        val span = part.span
        span.marker?.let { return it }
        val pair = span.pair ?: return ""
        return if (part.open) pair.open else pair.close
    }

    private fun escapeText(text: String, atLineStart: Boolean): String {
        val escaped = Markdown.escapeInline(
            text,
            if (atLineStart) MarkdownEscapeContext.LineStart else MarkdownEscapeContext.MidLine,
        )
        return escaped
    }

    private fun attachBreakMarkers(emitted: String, hardBreakMarker: String?): List<String> {
        val lines = emitted.split('\n')
        if (hardBreakMarker == null || lines.size == 1) return lines
        return lines.mapIndexed { index, line ->
            if (index < lines.lastIndex) line + hardBreakMarker else line
        }
    }

    // Encode-side verification

    private enum class VerifyOutcome { Match, TextMismatch, SpanMismatch }

    // A MarkPair span whose emission decodes back through the Markdown parser
    // (the built-in HTML `<u>` / `<mark>` islands, when the bridge is active) is
    // verified like any other span — the reparse rebuilds Underline/Highlight,
    // so mixed HTML/Markdown islands are proven by reparse.
    // Verification is disabled only when an *opaque* consumer MarkPair span is
    // present (its emission may not decode back; the consumer owns correctness).
    private fun needsVerification(working: List<RenderSpan>): Boolean =
        working.none { it.strategy == Strategy.MarkPair && !isBridgeVerifiableMarkPair(it) }

    private fun isBridgeVerifiableMarkPair(span: RenderSpan): Boolean =
        profile.htmlInMarkdown is HtmlInMarkdown.Bridge &&
            (span.style is SpanStyle.Underline || span.style is SpanStyle.Highlight)

    private fun verify(lines: List<String>, text: String, working: List<RenderSpan>): VerifyOutcome {
        // Mimic the decode path: the block phase trims leading whitespace from
        // paragraph continuation lines before the inline phase sees the leaf.
        val input = lines.joinToString("\n") { line -> line.trimStart(' ', '\t') }
        val parseInput = MarkdownParseInput.of(input)
        val root = parseMarkdownTree(parseInput.text)
        val container = root.children.firstOrNull {
            it.type == org.intellij.markdown.MarkdownElementTypes.PARAGRAPH
        } ?: root
        val state = MarkdownParseState(limits)
        val parsed = MarkdownAstInlineDecoder(
            parseInput,
            profile,
            limits,
            state,
            MarkdownDefinitionTable(),
            emptySet(),
        ).decode(container)
        if (state.isAborted || parsed.escalationKind != null) {
            return VerifyOutcome.TextMismatch
        }
        if (parsed.text != text) return VerifyOutcome.TextMismatch

        val expected = working.map { span ->
            val style = if (span.strategy == Strategy.Delimiter) {
                val marker = span.marker
                (marker?.let { DefaultMarkdownSpanEncoders.decodedStyleFor(profile, it) })
                    ?: span.style
            } else {
                span.style
            }
            TextSpan(span.start, span.end, style)
        }
        return if (parsed.spans == SpanAlgorithms.normalize(expected, text.length)) {
            VerifyOutcome.Match
        } else {
            VerifyOutcome.SpanMismatch
        }
    }

    /**
     * The weakest span to drop after a verification mismatch: the shortest
     * delimiter-emitted span (later start breaks ties), then code spans, then
     * links, then a bridge/consumer MarkPair span **last** — so one bad
     * MarkPair (e.g. an out-of-range Highlight) is dropped itself rather than
     * destroying every other span with misleading `AmbiguousEmphasis`
     * warnings. Every drop emits a warning at the call site.
     */
    private fun chooseVictim(working: List<RenderSpan>): RenderSpan? {
        val delimiter = working
            .filter { it.strategy == Strategy.Delimiter }
            .minWithOrNull(compareBy<RenderSpan> { it.end - it.start }.thenByDescending { it.start })
        if (delimiter != null) return delimiter
        val code = working.lastOrNull { it.strategy == Strategy.Code }
        if (code != null) return code
        val link = working.lastOrNull { it.strategy == Strategy.LinkNative }
        if (link != null) return link
        return working.lastOrNull { it.strategy == Strategy.MarkPair }
    }
}
