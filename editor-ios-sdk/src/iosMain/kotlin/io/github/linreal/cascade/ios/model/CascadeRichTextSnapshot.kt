@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@ObjCName("CascadeSpanKind", exact = true)
public enum class CascadeSpanKind {
    bold,
    italic,
    underline,
    @ObjCName(name = "strikeThrough", swiftName = "strikeThrough")
    strikeThrough,
    @ObjCName(name = "inlineCode", swiftName = "inlineCode")
    inlineCode,
    highlight,
    link,
}

@ObjCName("CascadeRichTextSpan", exact = true)
public data class CascadeRichTextSpan(
    public val start: Int,
    public val end: Int,
    public val kind: CascadeSpanKind,
    public val argb: Long = 0L,
    public val url: String? = null,
)

@ObjCName("CascadeRichTextBlock", exact = true)
public data class CascadeRichTextBlock(
    public val blockId: String,
    public val typeId: String,
    public val text: String,
    public val spans: List<CascadeRichTextSpan>,
)

@ObjCName("CascadeRichTextSnapshot", exact = true)
public data class CascadeRichTextSnapshot(
    public val blocks: List<CascadeRichTextBlock>,
)
