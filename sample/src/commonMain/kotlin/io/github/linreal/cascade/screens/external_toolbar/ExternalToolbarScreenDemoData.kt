package io.github.linreal.cascade.screens.external_toolbar

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.profiles.CustomHtmlProfile
import io.github.linreal.cascade.profiles.CustomHtmlSamples

/**
 * Span styles observed by the external toolbar.
 */
internal val ExternalToolbarTrackedStyles = listOf(
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode,
)

/**
 * Plain host-form field used to make the editor look embedded in a real screen.
 *
 * The sample keeps these fields separate from editor content so the surrounding
 * app model is visibly independent from CascadeEditor state.
 */
internal data class ExternalToolbarFormField(
    val label: String,
    val value: String,
)

internal val ExternalToolbarScreenLeadingFields = listOf(
    ExternalToolbarFormField(
        label = "Date",
        value = "May 17, 2026",
    ),
)

internal val ExternalToolbarScreenTrailingFields = listOf(
    ExternalToolbarFormField(
        label = "Completion status",
        value = "20%",
    ),
    ExternalToolbarFormField(
        label = "Email",
        value = "sample@example.com",
    ),
    ExternalToolbarFormField(
        label = "Data field",
        value = "Customer profile",
    ),
)

/**
 * Initial editor document for the sample, decoded from the Custom HTML dialect
 * so the external toolbar demo exercises profile-backed HTML content.
 */
internal fun buildExternalToolbarDemoBlocks(): List<Block> =
    HtmlSchema.decode(HtmlExampleData, CustomHtmlProfile.Profile)


private val HtmlExampleData: String = """
        An example of an <strong>editable</strong> custom field
        Style text using floating toolbar
        Slash Command panel is disabled using <code>SlashCommandSlot.None</code>
      """.trimIndent()
