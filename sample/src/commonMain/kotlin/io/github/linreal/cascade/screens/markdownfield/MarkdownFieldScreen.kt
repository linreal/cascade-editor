@file:OptIn(io.github.linreal.cascade.editor.markdown.ExperimentalCascadeMarkdownApi::class)

package io.github.linreal.cascade.screens.markdownfield

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.markdown.EntityDecode
import io.github.linreal.cascade.editor.markdown.HardBreakEncode
import io.github.linreal.cascade.editor.markdown.HtmlInMarkdown
import io.github.linreal.cascade.editor.markdown.MarkdownDecodeResult
import io.github.linreal.cascade.editor.markdown.MarkdownDecodeWarning
import io.github.linreal.cascade.editor.markdown.MarkdownEditModeRecommendation
import io.github.linreal.cascade.editor.markdown.MarkdownEncodeResult
import io.github.linreal.cascade.editor.markdown.MarkdownEncodeWarning
import io.github.linreal.cascade.editor.markdown.MarkdownFidelityImpact
import io.github.linreal.cascade.editor.markdown.MarkdownFidelityReport
import io.github.linreal.cascade.editor.markdown.MarkdownLineEnding
import io.github.linreal.cascade.editor.markdown.MarkdownProfile
import io.github.linreal.cascade.editor.markdown.MarkdownSchema
import io.github.linreal.cascade.editor.markdown.MarkdownSourceLocator
import io.github.linreal.cascade.editor.markdown.NewlineSemantics
import io.github.linreal.cascade.editor.markdown.SoftBreak
import io.github.linreal.cascade.editor.markdown.UnsupportedSyntax
import io.github.linreal.cascade.editor.markdown.applyMarkdownDecodeResult
import io.github.linreal.cascade.editor.markdown.loadFromMarkdown
import io.github.linreal.cascade.editor.markdown.toMarkdownWithReport
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.screens.TitledEditorTopBar
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.ui.PageScaffold

private const val COMMONMARK_SAMPLE = "# Markdown round trip\n\n" +
    "Edit **bold**, *italic*, ~~strike~~, `code`, and a [relative link](../guide.md).\n\n" +
    "- First item\n- Second item\n\n> Quotes and <u>HTML-backed underline</u> are supported.\n"

private const val HARD_BREAK_SAMPLE = "Daily update\n" +
    "First visible line\n" +
    "Second visible line\n\n" +
    "Blank lines become explicit empty paragraphs.\n"

const val PRESERVED_SAMPLE = "# Unsupported syntax\n\n" +
    "The table below is preserved by the default profile.\n\n" +
    "| item | usd |\n| --- | ---: |\n| infra | 42 |\n"

private const val HTML_SAMPLE = "# HTML policies\n\n" +
    "<u>Underline with **nested bold**</u> and " +
    "<mark data-cascade-highlight=\"FFFFD54F\">highlight</mark>.\n\n" +
    "<section><p>Block HTML passes through the fragment bridge.</p></section>\n"

private enum class MarkdownSample(val label: String) {
    CommonMark("CommonMark sample"),
    HardBreak("Hard-break sample"),
    Unsupported("Unsupported sample"),
    Html("HTML sample"),
}

private enum class NewlineMode(val label: String) {
    CommonMark("CommonMark"),
    HardBreak("Hard break"),
}

private enum class SoftBreakMode(val label: String) {
    Space("Space"),
    LineBreak("Literal newline"),
}

private enum class HardBreakStyle(val label: String) {
    Backslash("Backslash"),
    TwoSpaces("Two spaces"),
}

private enum class UnsupportedMode(val label: String) {
    Preserve("Preserve"),
    Degrade("Warn + degrade"),
}

private enum class MarkdownHtmlMode(val label: String) {
    Bridge("Bridge"),
    Preserve("Preserve"),
    WarnAndStrip("Warn + strip"),
    Strip("Strip"),
    Strict("Strict no-HTML"),
}

private enum class EntityMode(val label: String) {
    Decode("Decode"),
    Literal("Keep literal"),
}

/**
 * Interactive Markdown codec playground, mirroring [io.github.linreal.cascade.screens.CustomHtmlProfileScreen].
 *
 * The source field, editor document, and exported field are deliberately
 * separate. Analyze inspects source without mutation; Import replaces the
 * editor; Export serializes the editor; Round Trip performs Import then Export.
 * Every public Markdown policy can be selected so behavior and fidelity
 * warnings are visible instead of hidden behind the host-contract sample.
 */
@Composable
fun MarkdownFieldScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    initialSource: String = COMMONMARK_SAMPLE,
) {
    val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
    val textStates = remember { BlockTextStates() }
    val spanStates = remember { BlockSpanStates() }

    val initialReport = remember(initialSource) {
        MarkdownSchema.analyze(initialSource, MarkdownProfile.Default)
    }
    val editorState = rememberEditorState(initialReport.decodeResult.blocks.orEmpty())

    var sourceMarkdown by remember(initialSource) { mutableStateOf(initialSource) }
    var exportedMarkdown by remember(initialSource) {
        mutableStateOf(initialReport.encodeResult?.markdown.orEmpty())
    }
    var newlineMode by remember { mutableStateOf(NewlineMode.CommonMark) }
    var softBreakMode by remember { mutableStateOf(SoftBreakMode.Space) }
    var hardBreakStyle by remember { mutableStateOf(HardBreakStyle.Backslash) }
    var unsupportedMode by remember { mutableStateOf(UnsupportedMode.Preserve) }
    var htmlMode by remember { mutableStateOf(MarkdownHtmlMode.Bridge) }
    var entityMode by remember { mutableStateOf(EntityMode.Decode) }
    var lineEnding by remember { mutableStateOf(MarkdownLineEnding.Lf) }

    var lastAnalysis by remember { mutableStateOf<MarkdownFidelityReport?>(initialReport) }
    var lastDecode by remember { mutableStateOf<MarkdownDecodeResult?>(initialReport.decodeResult) }
    var lastEncode by remember { mutableStateOf<MarkdownEncodeResult?>(initialReport.encodeResult) }
    var status by remember {
        mutableStateOf(
            compareMarkdown(
                source = initialSource,
                exported = initialReport.encodeResult?.markdown.orEmpty(),
                profile = MarkdownProfile.Default,
                lineEnding = MarkdownLineEnding.Lf,
            ),
        )
    }

    val profile = remember(
        newlineMode,
        softBreakMode,
        hardBreakStyle,
        unsupportedMode,
        htmlMode,
        entityMode,
    ) {
        buildMarkdownProfile(
            newlineMode = newlineMode,
            softBreakMode = softBreakMode,
            hardBreakStyle = hardBreakStyle,
            unsupportedMode = unsupportedMode,
            htmlMode = htmlMode,
            entityMode = entityMode,
        )
    }

    fun invalidateResults(message: String) {
        lastAnalysis = null
        lastDecode = null
        lastEncode = null
        status = message
    }

    fun selectSample(sample: MarkdownSample) {
        sourceMarkdown = when (sample) {
            MarkdownSample.CommonMark -> COMMONMARK_SAMPLE
            MarkdownSample.HardBreak -> HARD_BREAK_SAMPLE
            MarkdownSample.Unsupported -> PRESERVED_SAMPLE
            MarkdownSample.Html -> HTML_SAMPLE
        }
        when (sample) {
            MarkdownSample.CommonMark -> {
                newlineMode = NewlineMode.CommonMark
                unsupportedMode = UnsupportedMode.Preserve
                htmlMode = MarkdownHtmlMode.Bridge
            }
            MarkdownSample.HardBreak -> newlineMode = NewlineMode.HardBreak
            MarkdownSample.Unsupported -> unsupportedMode = UnsupportedMode.Preserve
            MarkdownSample.Html -> htmlMode = MarkdownHtmlMode.Bridge
        }
        invalidateResults("Sample loaded — run Analyze or Round Trip")
    }

    fun analyzeSource() {
        val report = MarkdownSchema.analyze(sourceMarkdown, profile)
        lastAnalysis = report
        lastDecode = report.decodeResult
        lastEncode = report.encodeResult
        status = buildString {
            append("Analysis: ")
            append(report.recommendedMode.displayName)
            if (report.preservedBlockCount > 0) {
                append(" · ${report.preservedBlockCount} preserved")
            }
        }
    }

    fun importSource() {
        val result = editorState.loadFromMarkdown(
            markdown = sourceMarkdown,
            textStates = textStates,
            spanStates = spanStates,
            profile = profile,
        )
        lastAnalysis = null
        lastDecode = result
        lastEncode = null
        status = if (result.isSuccess) {
            "Imported ${result.blocks.orEmpty().size} block(s)"
        } else {
            "Import aborted — editor left unchanged"
        }
    }

    fun exportEditor() {
        val result = editorState.toMarkdownWithReport(
            textStates = textStates,
            spanStates = spanStates,
            profile = profile,
            lineEnding = lineEnding,
        )
        lastEncode = result
        result.markdown?.let { exportedMarkdown = it }
        status = if (result.isSuccess) {
            compareMarkdown(sourceMarkdown, exportedMarkdown, profile, lineEnding)
        } else {
            "Export aborted — previous output retained"
        }
    }

    fun roundTrip() {
        val report = MarkdownSchema.analyze(sourceMarkdown, profile)
        lastAnalysis = report
        lastDecode = report.decodeResult
        if (!report.decodeResult.isSuccess) {
            lastEncode = report.encodeResult
            status = "Round trip aborted during decode — editor left unchanged"
            return
        }

        editorState.applyMarkdownDecodeResult(report.decodeResult, textStates, spanStates)
        val encode = editorState.toMarkdownWithReport(
            textStates = textStates,
            spanStates = spanStates,
            profile = profile,
            lineEnding = lineEnding,
        )
        lastEncode = encode
        encode.markdown?.let { exportedMarkdown = it }
        status = if (encode.isSuccess) {
            compareMarkdown(sourceMarkdown, exportedMarkdown, profile, lineEnding)
        } else {
            "Round trip aborted during encode"
        }
    }

    val profileSummary = profileSummary(
        newlineMode = newlineMode,
        softBreakMode = softBreakMode,
        hardBreakStyle = hardBreakStyle,
        unsupportedMode = unsupportedMode,
        htmlMode = htmlMode,
        entityMode = entityMode,
        lineEnding = lineEnding,
    )

    PageScaffold(maxContentWidth = 1040.dp) {
        TitledEditorTopBar(
            title = "Markdown round trip",
            isDark = isDark,
            onBack = onBack,
            onToggleTheme = onToggleTheme,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Analyze inspects source. Import loads the editor. Export reads the editor. " +
                    "Round Trip imports and immediately exports with the selected policies.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SampleSelector(onSelect = ::selectSample)

            MarkdownModePanel(
                newlineMode = newlineMode,
                onNewlineMode = {
                    newlineMode = it
                    invalidateResults("Newline mode changed — run Analyze or Round Trip")
                },
                softBreakMode = softBreakMode,
                onSoftBreakMode = {
                    softBreakMode = it
                    invalidateResults("Soft-break policy changed — run Analyze or Round Trip")
                },
                hardBreakStyle = hardBreakStyle,
                onHardBreakStyle = {
                    hardBreakStyle = it
                    invalidateResults("Hard-break encoding changed — run Analyze or Round Trip")
                },
                unsupportedMode = unsupportedMode,
                onUnsupportedMode = {
                    unsupportedMode = it
                    invalidateResults("Unsupported-syntax policy changed — run Analyze or Round Trip")
                },
                htmlMode = htmlMode,
                onHtmlMode = {
                    htmlMode = it
                    invalidateResults("HTML policy changed — run Analyze or Round Trip")
                },
                entityMode = entityMode,
                onEntityMode = {
                    entityMode = it
                    invalidateResults("Entity policy changed — run Analyze or Round Trip")
                },
                lineEnding = lineEnding,
                onLineEnding = {
                    lineEnding = it
                    invalidateResults("Output line ending changed — run Export or Round Trip")
                },
            )

            ActionRow(
                onAnalyze = ::analyzeSource,
                onImport = ::importSource,
                onRoundTrip = ::roundTrip,
                onExport = ::exportEditor,
            )

            RoundTripSummaryPanel(
                status = status,
                profileSummary = profileSummary,
                report = lastAnalysis,
            )

            DiagnosticsPanel(
                title = "Decode diagnostics",
                emptyMessage = "Analyze, Import, or Round Trip to inspect source diagnostics.",
                lines = lastDecode?.warningLines().orEmpty(),
                hasLoss = lastDecode?.warnings?.hasLossOrFatal() == true,
            )

            DiagnosticsPanel(
                title = "Encode diagnostics",
                emptyMessage = "Analyze, Export, or Round Trip to inspect output diagnostics.",
                lines = lastEncode?.warningLines().orEmpty(),
                hasLoss = lastEncode?.warnings?.hasLossOrFatal() == true,
            )

            MarkdownTextArea(
                label = "Source Markdown",
                value = sourceMarkdown,
                onValueChange = {
                    sourceMarkdown = it
                    invalidateResults("Source changed — run Analyze or Round Trip")
                },
            )

            Text(
                text = "CascadeEditor document",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
            ) {
                CascadeEditor(
                    stateHolder = editorState,
                    textStates = textStates,
                    spanStates = spanStates,
                    theme = editorTheme,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MarkdownTextArea(
                label = "Exported Markdown",
                value = exportedMarkdown,
                onValueChange = {},
                readOnly = true,
            )

            Text(
                text = "Byte match compares the fields exactly. Canonical match means both fields " +
                    "decode and re-encode to the same canonical document under the selected profile.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SampleSelector(onSelect: (MarkdownSample) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Source presets",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkdownSample.entries.forEach { sample ->
                OutlinedButton(onClick = { onSelect(sample) }) {
                    Text(sample.label)
                }
            }
        }
    }
}

@Composable
private fun MarkdownModePanel(
    newlineMode: NewlineMode,
    onNewlineMode: (NewlineMode) -> Unit,
    softBreakMode: SoftBreakMode,
    onSoftBreakMode: (SoftBreakMode) -> Unit,
    hardBreakStyle: HardBreakStyle,
    onHardBreakStyle: (HardBreakStyle) -> Unit,
    unsupportedMode: UnsupportedMode,
    onUnsupportedMode: (UnsupportedMode) -> Unit,
    htmlMode: MarkdownHtmlMode,
    onHtmlMode: (MarkdownHtmlMode) -> Unit,
    entityMode: EntityMode,
    onEntityMode: (EntityMode) -> Unit,
    lineEnding: MarkdownLineEnding,
    onLineEnding: (MarkdownLineEnding) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Codec modes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ModeSelector(
                label = "Newlines",
                selected = newlineMode,
                options = NewlineMode.entries,
                optionLabel = NewlineMode::label,
                onSelected = onNewlineMode,
            )
            ModeSelector(
                label = "Soft break (CommonMark only)",
                selected = softBreakMode,
                options = SoftBreakMode.entries,
                optionLabel = SoftBreakMode::label,
                onSelected = onSoftBreakMode,
            )
            ModeSelector(
                label = "Hard-break marker (CommonMark only)",
                selected = hardBreakStyle,
                options = HardBreakStyle.entries,
                optionLabel = HardBreakStyle::label,
                onSelected = onHardBreakStyle,
            )
            ModeSelector(
                label = "Unsupported syntax",
                selected = unsupportedMode,
                options = UnsupportedMode.entries,
                optionLabel = UnsupportedMode::label,
                onSelected = onUnsupportedMode,
            )
            ModeSelector(
                label = "HTML in Markdown",
                selected = htmlMode,
                options = MarkdownHtmlMode.entries,
                optionLabel = MarkdownHtmlMode::label,
                onSelected = onHtmlMode,
            )
            ModeSelector(
                label = "Entities",
                selected = entityMode,
                options = EntityMode.entries,
                optionLabel = EntityMode::label,
                onSelected = onEntityMode,
            )
            ModeSelector(
                label = "Output line ending",
                selected = lineEnding,
                options = MarkdownLineEnding.entries,
                optionLabel = { if (it == MarkdownLineEnding.Lf) "LF" else "CRLF" },
                onSelected = onLineEnding,
            )
        }
    }
}

@Composable
private fun <T> ModeSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                if (option == selected) {
                    Button(onClick = { onSelected(option) }) {
                        Text(optionLabel(option))
                    }
                } else {
                    OutlinedButton(onClick = { onSelected(option) }) {
                        Text(optionLabel(option))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onAnalyze: () -> Unit,
    onImport: () -> Unit,
    onRoundTrip: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onAnalyze) { Text("Analyze") }
        Button(onClick = onImport) { Text("Import") }
        Button(onClick = onRoundTrip) { Text("Round Trip") }
        Button(onClick = onExport) { Text("Export") }
    }
}

@Composable
private fun RoundTripSummaryPanel(
    status: String,
    profileSummary: String,
    report: MarkdownFidelityReport?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = profileSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report == null) {
                Text(
                    text = "Run Analyze or Round Trip to refresh the fidelity recommendation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Recommended editor: ${report.recommendedMode.displayName} · " +
                        "native safe: ${report.nativeEditingSafe.yesNo()} · " +
                        "rewrites source: ${report.wouldRewriteSource.yesNoOrUnknown()} · " +
                        "preserved blocks: ${report.preservedBlockCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (report.nativeEditingSafe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    title: String,
    emptyMessage: String,
    lines: List<String>,
    hasLoss: Boolean,
) {
    val borderColor = if (hasLoss) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (hasLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (lines.isEmpty()) "None" else "${lines.size} warning(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lines.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                lines.take(8).forEachIndexed { index, line ->
                    Text(
                        text = "${index + 1}. $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (lines.size > 8) {
                    Text(
                        text = "+${lines.size - 8} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTextArea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        label = { Text(label) },
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        minLines = 8,
        maxLines = 14,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun buildMarkdownProfile(
    newlineMode: NewlineMode,
    softBreakMode: SoftBreakMode,
    hardBreakStyle: HardBreakStyle,
    unsupportedMode: UnsupportedMode,
    htmlMode: MarkdownHtmlMode,
    entityMode: EntityMode,
): MarkdownProfile {
    var profile = MarkdownProfile.Default
        .withNewlineSemantics(
            when (newlineMode) {
                NewlineMode.CommonMark -> NewlineSemantics.CommonMark
                NewlineMode.HardBreak -> NewlineSemantics.HardBreak
            },
        )
        .withSoftBreak(
            when (softBreakMode) {
                SoftBreakMode.Space -> SoftBreak.Space
                SoftBreakMode.LineBreak -> SoftBreak.LineBreak
            },
        )
        .withHardBreakEncode(
            when (hardBreakStyle) {
                HardBreakStyle.Backslash -> HardBreakEncode.Backslash
                HardBreakStyle.TwoSpaces -> HardBreakEncode.TwoSpaces
            },
        )
        .withUnsupportedSyntax(
            when (unsupportedMode) {
                UnsupportedMode.Preserve -> UnsupportedSyntax.Preserve
                UnsupportedMode.Degrade -> UnsupportedSyntax.WarnAndDegrade
            },
        )
        .withEntityDecode(
            when (entityMode) {
                EntityMode.Decode -> EntityDecode.Standard
                EntityMode.Literal -> EntityDecode.None
            },
        )

    profile = when (htmlMode) {
        MarkdownHtmlMode.Bridge -> profile
        MarkdownHtmlMode.Preserve -> profile.withHtmlInMarkdown(HtmlInMarkdown.Preserve)
        MarkdownHtmlMode.WarnAndStrip -> profile.withHtmlInMarkdown(HtmlInMarkdown.WarnAndStrip)
        MarkdownHtmlMode.Strip -> profile.withHtmlInMarkdown(HtmlInMarkdown.Strip)
        MarkdownHtmlMode.Strict -> profile.withoutHtmlBridge()
    }
    return profile
}

private fun compareMarkdown(
    source: String,
    exported: String,
    profile: MarkdownProfile,
    lineEnding: MarkdownLineEnding,
): String {
    if (source == exported) return "Byte match"
    val normalizedSource = normalizeMarkdown(source, profile, lineEnding)
    val normalizedExport = normalizeMarkdown(exported, profile, lineEnding)
    return when {
        normalizedSource == null || normalizedExport == null -> "Cannot compare — decode aborted"
        normalizedSource == normalizedExport -> "Canonical match"
        else -> "Different"
    }
}

private fun normalizeMarkdown(
    source: String,
    profile: MarkdownProfile,
    lineEnding: MarkdownLineEnding,
): String? {
    val blocks = MarkdownSchema.decode(source, profile) ?: return null
    return MarkdownSchema.encode(blocks, profile, lineEnding = lineEnding)
}

private fun profileSummary(
    newlineMode: NewlineMode,
    softBreakMode: SoftBreakMode,
    hardBreakStyle: HardBreakStyle,
    unsupportedMode: UnsupportedMode,
    htmlMode: MarkdownHtmlMode,
    entityMode: EntityMode,
    lineEnding: MarkdownLineEnding,
): String = buildString {
    append(newlineMode.label)
    if (newlineMode == NewlineMode.CommonMark) {
        append(" · soft→${softBreakMode.label.lowercase()}")
        append(" · hard→${hardBreakStyle.label.lowercase()}")
    }
    append(" · unsupported→${unsupportedMode.label.lowercase()}")
    append(" · HTML→${htmlMode.label.lowercase()}")
    append(" · entities→${entityMode.label.lowercase()}")
    append(" · ${if (lineEnding == MarkdownLineEnding.Lf) "LF" else "CRLF"}")
}

private fun MarkdownDecodeResult.warningLines(): List<String> =
    warnings.map { it.describe(sourceLocator) }

private fun MarkdownEncodeResult.warningLines(): List<String> = warnings.map { it.describe() }

private fun List<io.github.linreal.cascade.editor.markdown.MarkdownWarning>.hasLossOrFatal(): Boolean =
    any { it.impact == MarkdownFidelityImpact.DataLoss || it.impact == MarkdownFidelityImpact.Fatal }

private fun MarkdownDecodeWarning.describe(locator: MarkdownSourceLocator): String {
    val location = locator.locate(range)
    val detail = when (this) {
        is MarkdownDecodeWarning.UnsupportedSyntax -> "$construct: $detail"
        is MarkdownDecodeWarning.PreservedSyntax -> "Preserved $kind as an opaque block"
        is MarkdownDecodeWarning.HtmlBridged -> "HTML bridged into editor content"
        is MarkdownDecodeWarning.HtmlStripped -> "HTML${tag?.let { " <$it>" }.orEmpty()} stripped"
        is MarkdownDecodeWarning.DroppedAttribute -> "$construct.$attr dropped: $reason"
        is MarkdownDecodeWarning.DuplicateLinkDefinition -> "Duplicate link definition [$label] ignored"
        is MarkdownDecodeWarning.UnsupportedEntity -> "Entity &$name; kept literal"
        is MarkdownDecodeWarning.InputLimitExceeded -> "Input length $actual exceeds $limit"
        is MarkdownDecodeWarning.LimitExceeded -> "$kind limit $limit exceeded"
        is MarkdownDecodeWarning.EngineFailure -> "Engine failure: $message"
    }
    return "$detail · ${impact.name} · ${location.line}:${location.column}"
}

private fun MarkdownEncodeWarning.describe(): String {
    val detail = when (this) {
        is MarkdownEncodeWarning.AmbiguousEmphasis -> "Ambiguous emphasis; weaker formatting dropped"
        is MarkdownEncodeWarning.DroppedSpanOverlap -> "Span overlap dropped: $reason"
        is MarkdownEncodeWarning.DroppedAttribute -> "$attr dropped: $reason"
        is MarkdownEncodeWarning.UnsupportedBlock -> "Unsupported block ${typeId.orEmpty()}: $reason"
        is MarkdownEncodeWarning.UnsupportedSpan -> "Unsupported span ${typeId.orEmpty()}: $reason"
        is MarkdownEncodeWarning.EncoderException ->
            "Encoder${typeId?.let { " $it" }.orEmpty()} failed: $message"
        is MarkdownEncodeWarning.OutputLimitExceeded -> "Output exceeds $limit characters"
        is MarkdownEncodeWarning.LimitExceeded -> "$kind limit $limit exceeded"
        is MarkdownEncodeWarning.EngineFailure -> "Engine failure: $message"
    }
    val block = blockId?.let { " · block $it" }.orEmpty()
    val range = textRange?.let { " · text ${it.start}..${it.endExclusive}" }.orEmpty()
    return "$detail · ${impact.name}$block$range"
}

private val MarkdownEditModeRecommendation.displayName: String
    get() = when (this) {
        MarkdownEditModeRecommendation.Native -> "Native editor"
        MarkdownEditModeRecommendation.RawFallback -> "Raw fallback"
    }

private fun Boolean.yesNo(): String = if (this) "yes" else "no"

private fun Boolean?.yesNoOrUnknown(): String = when (this) {
    true -> "yes"
    false -> "no"
    null -> "unknown"
}
