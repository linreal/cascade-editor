package io.github.linreal.cascade.ios

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("CascadeEditorSdk", exact = true)
public object CascadeEditorSdk {
    public const val version: String = "1.7.0"
}
