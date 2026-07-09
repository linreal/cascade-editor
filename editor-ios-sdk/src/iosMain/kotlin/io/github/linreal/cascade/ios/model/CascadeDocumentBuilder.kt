@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.model

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@ObjCName("CascadeEditorDocumentBuilder", exact = true)
public class CascadeEditorDocumentBuilder {
    private val blocks: MutableList<Block> = mutableListOf()

    public var lastErrorMessage: String? = null
        private set

    public fun clearLastError(): CascadeEditorDocumentBuilder = apply {
        lastErrorMessage = null
    }

    public fun paragraph(text: String): CascadeEditorDocumentBuilder = apply {
        blocks += Block.paragraph(text)
    }

    public fun heading(level: Int, text: String): CascadeEditorDocumentBuilder = apply {
        val safeLevel = level.coerceIn(1, 6)
        if (safeLevel != level) {
            lastErrorMessage = "Heading level must be between 1 and 6"
        }
        blocks += Block.heading(safeLevel, text)
    }

    public fun todo(text: String, checked: Boolean): CascadeEditorDocumentBuilder = apply {
        blocks += Block.todo(text, checked)
    }

    public fun bulletList(text: String): CascadeEditorDocumentBuilder = apply {
        blocks += Block.bulletList(text)
    }

    public fun numberedList(text: String, number: Int): CascadeEditorDocumentBuilder = apply {
        val safeNumber = number.coerceAtLeast(1)
        if (safeNumber != number) {
            lastErrorMessage = "Numbered list number must be >= 1"
        }
        blocks += Block.numberedList(text, safeNumber)
    }

    public fun quote(text: String): CascadeEditorDocumentBuilder = apply {
        blocks += Block(
            id = BlockId.generate(),
            type = BlockType.Quote,
            content = BlockContent.Text(text),
        )
    }

    public fun code(text: String): CascadeEditorDocumentBuilder = apply {
        blocks += Block(
            id = BlockId.generate(),
            type = BlockType.Code,
            content = BlockContent.Text(text),
        )
    }

    public fun divider(): CascadeEditorDocumentBuilder = apply {
        blocks += Block.divider()
    }

    public fun customBlock(typeId: String, payloadJson: String): CascadeEditorDocumentBuilder = apply {
        val payload = parseJsonObjectPayloadSafely(payloadJson)
        if (payload.errorMessage != null) {
            lastErrorMessage = payload.errorMessage
        }
        blocks += Block(
            id = BlockId.generate(),
            type = NativeDocumentBlockType(typeId),
            content = BlockContent.Custom(typeId, payload.data),
        )
    }

    public fun buildJson(): String = DocumentSchema.encodeToString(blocks)
}

internal data class NativeDocumentBlockType(
    override val typeId: String,
) : CustomBlockType {
    override val displayName: String = typeId
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}
