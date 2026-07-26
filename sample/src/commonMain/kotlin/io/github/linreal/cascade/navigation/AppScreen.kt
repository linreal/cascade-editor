package io.github.linreal.cascade.navigation

sealed class AppScreen {
    data object Landing : AppScreen()
    data object EditorDemo : AppScreen()
    data object CustomBlocks : AppScreen()
    data object CustomToolbar : AppScreen()
    data object ExternalToolbar : AppScreen()
    data object CustomHtmlProfile : AppScreen()
    data object Comments : AppScreen()
    data object MarkdownField : AppScreen()
    data object PreviewGallery : AppScreen()

    /** Editable destination reached from a preview card, addressed by document ID. */
    data class PreviewDocumentEditor(val documentId: String) : AppScreen()

    internal val saveKey: String
        get() = when (this) {
            Landing -> "landing"
            EditorDemo -> "editor_demo"
            CustomBlocks -> "custom_blocks"
            CustomToolbar -> "custom_toolbar"
            ExternalToolbar -> "external_toolbar"
            CustomHtmlProfile -> "custom_html_profile"
            Comments -> "comments"
            MarkdownField -> "markdown_field"
            PreviewGallery -> "preview_gallery"
            is PreviewDocumentEditor -> "$PREVIEW_DOCUMENT_EDITOR_PREFIX$documentId"
        }

    companion object {
        private const val PREVIEW_DOCUMENT_EDITOR_PREFIX = "preview_document:"

        internal fun fromSaveKey(saveKey: String): AppScreen {
            if (saveKey.startsWith(PREVIEW_DOCUMENT_EDITOR_PREFIX)) {
                val documentId = saveKey.removePrefix(PREVIEW_DOCUMENT_EDITOR_PREFIX)
                // A blank ID cannot address a document; fall back to the grid.
                return if (documentId.isEmpty()) {
                    PreviewGallery
                } else {
                    PreviewDocumentEditor(documentId)
                }
            }
            return when (saveKey) {
                Landing.saveKey -> Landing
                EditorDemo.saveKey -> EditorDemo
                CustomBlocks.saveKey -> CustomBlocks
                CustomToolbar.saveKey -> CustomToolbar
                ExternalToolbar.saveKey -> ExternalToolbar
                CustomHtmlProfile.saveKey -> CustomHtmlProfile
                Comments.saveKey -> Comments
                MarkdownField.saveKey -> MarkdownField
                PreviewGallery.saveKey -> PreviewGallery
                else -> Landing
            }
        }
    }
}
