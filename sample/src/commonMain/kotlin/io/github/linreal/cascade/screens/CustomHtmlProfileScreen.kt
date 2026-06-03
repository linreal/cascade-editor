package io.github.linreal.cascade.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_back
import cascadeeditor.sample.generated.resources.ic_dark_mode
import cascadeeditor.sample.generated.resources.ic_light_mode
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.loadFromHtml
import io.github.linreal.cascade.editor.htmlserialization.toHtml
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.profiles.CustomHtmlProfile
import io.github.linreal.cascade.profiles.CustomHtmlSamples
import io.github.linreal.cascade.ui.PageScaffold
import org.jetbrains.compose.resources.painterResource

@Composable
fun CustomHtmlProfileScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()
    val textStates = remember { BlockTextStates() }
    val spanStates = remember { BlockSpanStates() }
    val initialBlocks = remember {
        HtmlSchema.decode(CustomHtmlSamples.DialectHtml, CustomHtmlProfile.Profile)
    }
    val editorState = rememberEditorState(initialBlocks)

    var sourceHtml by remember { mutableStateOf(CustomHtmlSamples.DialectHtml) }
    var exportedHtml by remember { mutableStateOf(CustomHtmlSamples.CanonicalHtml) }
    var status by remember { mutableStateOf(compareHtml(sourceHtml, exportedHtml)) }
    var decodeWarnings by remember { mutableStateOf<List<HtmlDecodeWarning>>(emptyList()) }
    var warningStatus by remember { mutableStateOf("Import to inspect decode warnings") }

    fun importHtml() {
        val result = editorState.loadFromHtml(
            html = sourceHtml,
            textStates = textStates,
            spanStates = spanStates,
            profile = CustomHtmlProfile.Profile,
        )
        decodeWarnings = result.warnings
        warningStatus = if (result.warnings.isEmpty()) {
            "No decode warnings"
        } else {
            "${result.warnings.size} decode warning(s)"
        }
        status = "Imported, warnings: ${result.warnings.size}"
    }

    fun exportHtml() {
        exportedHtml = editorState.toHtml(
            textStates = textStates,
            spanStates = spanStates,
            profile = CustomHtmlProfile.Profile,
        )
        status = compareHtml(sourceHtml, exportedHtml)
    }

    PageScaffold(maxContentWidth = 1040.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Custom HTML Profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.size(40.dp),
            ) {
                Image(
                    painter = painterResource(
                        if (isDark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode
                    ),
                    contentDescription = if (isDark) "Switch to light" else "Switch to dark",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val html = CustomHtmlSamples.DialectHtml
                        sourceHtml = html
                        decodeWarnings = emptyList()
                        warningStatus = "Source changed; import to refresh warnings"
                        status = compareHtml(html, exportedHtml)
                    },
                ) {
                    Text("Dialect")
                }

                Button(onClick = { importHtml() }) {
                    Text("Import")
                }
                Button(
                    onClick = {
                        importHtml()
                        exportHtml()
                    },
                ) {
                    Text("Round Trip")
                }
                Button(onClick = { exportHtml() }) {
                    Text("Export")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Strict match compares the two HTML fields byte-for-byte.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DecodeWarningsPanel(
                status = warningStatus,
                warnings = decodeWarnings,
            )

            HtmlTextArea(
                label = "Source HTML",
                value = sourceHtml,
                onValueChange = {
                    sourceHtml = it
                    decodeWarnings = emptyList()
                    warningStatus = "Source changed; import to refresh warnings"
                    status = compareHtml(it, exportedHtml)
                },
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

            HtmlTextArea(
                label = "Exported HTML",
                value = exportedHtml,
                onValueChange = {},
                readOnly = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DecodeWarningsPanel(
    status: String,
    warnings: List<HtmlDecodeWarning>,
) {
    val borderColor = if (warnings.isEmpty()) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    }
    val titleColor = if (warnings.isEmpty()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Decode warnings",
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (warnings.isEmpty()) {
                Text(
                    text = "Warnings from the latest import will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                warnings.take(6).forEachIndexed { index, warning ->
                    Text(
                        text = "${index + 1}. ${warning.describe()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (warnings.size > 6) {
                    Text(
                        text = "+${warnings.size - 6} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlTextArea(
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
        maxLines = 12,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun compareHtml(sourceHtml: String, exportedHtml: String): String {
    if (sourceHtml == exportedHtml) return "Byte match"

    val normalizedSource = normalizeCustomHtml(sourceHtml)
    val normalizedExport = normalizeCustomHtml(exportedHtml)
    return if (normalizedSource == normalizedExport) {
        "Canonical match"
    } else {
        "Different"
    }
}

private fun normalizeCustomHtml(html: String): String =
    HtmlSchema.encode(
        HtmlSchema.decode(html, CustomHtmlProfile.Profile),
        CustomHtmlProfile.Profile,
    )

private fun HtmlDecodeWarning.describe(): String = when (this) {
    is HtmlDecodeWarning.UnknownTag ->
        "Unknown tag <$tag> at offset $charOffset"
    is HtmlDecodeWarning.UnknownAttribute ->
        "Unknown attribute $attr on <$tag> at offset $charOffset"
    is HtmlDecodeWarning.StrayClosingTag ->
        "Stray closing tag </$tag> at offset $charOffset"
    is HtmlDecodeWarning.MismatchedNesting ->
        "Mismatched nesting: expected </$expected>, found </$found> at offset $charOffset"
    is HtmlDecodeWarning.UnclosedTag ->
        "Unclosed tag <$tag> at offset $charOffset"
    is HtmlDecodeWarning.InvalidAttribute ->
        "Invalid $attr on <$tag> at offset $charOffset: $reason ($value)"
    is HtmlDecodeWarning.BlockInInlineContext ->
        "Block result from <$tag> inside inline content at offset $charOffset"
    is HtmlDecodeWarning.DroppedContent ->
        "Dropped content at offset $charOffset: $reason"
    is HtmlDecodeWarning.DroppedAttribute ->
        "Dropped $attr on <$tag> at offset $charOffset: $reason"
    is HtmlDecodeWarning.DecoderException ->
        "Decoder exception${tag?.let { " in <$it>" }.orEmpty()} at offset $charOffset: $message"
    is HtmlDecodeWarning.InputLimitExceeded ->
        "Decoder exception: document length is $actual, while max is $limit "

}
