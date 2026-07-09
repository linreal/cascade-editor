@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.controller

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@ObjCName("CascadeDocumentLoadResult", exact = true)
public data class CascadeDocumentLoadResult(
    public val success: Boolean,
    public val warningMessages: List<String>,
)

public typealias CascadeHtmlLoadResult = CascadeDocumentLoadResult
