package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.CustomBlockType

internal data object PreservedHtmlBlockType : CustomBlockType {
    override val typeId: String = "html.preserved"
    override val displayName: String = "Preserved HTML"
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}
