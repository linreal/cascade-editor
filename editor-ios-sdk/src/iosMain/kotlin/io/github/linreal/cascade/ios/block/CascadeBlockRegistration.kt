@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.block

import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * How a registered custom block behaves when chosen from the slash menu.
 */
@ObjCName("CascadeCustomBlockSlashBehavior", exact = true)
public enum class CascadeCustomBlockSlashBehavior {
    /** Insert a new block of this type below the anchor block. */
    insert,

    /** Convert the anchor block in place into this type. */
    @ObjCName(name = "convertInPlace", swiftName = "convertInPlace")
    convertInPlace,
}

/**
 * Swift-facing description of a native custom block type.
 *
 * A registration is turned into a block descriptor (for the slash menu), a
 * renderable type, a serialization codec entry, and a renderer that hosts the
 * native view returned by [rendererFactory] inside the editor.
 *
 * @property typeId Unique id matching the `typeId` used in persisted documents
 *   and [CascadeEditorDocumentBuilder.customBlock][io.github.linreal.cascade.ios.model.CascadeEditorDocumentBuilder.customBlock].
 * @property displayName Human-readable name shown in the slash menu.
 * @property description Short description shown in the slash menu.
 * @property keywords Extra search terms for the slash menu.
 * @property slashBehavior Insert-below vs convert-in-place semantics.
 * @property defaultPayloadJson JSON object used as the block's payload when it is
 *   created from the slash menu. Must be a JSON object; a non-object or malformed
 *   value falls back to an empty payload.
 * @property estimatedHeight Initial host height in points, applied before the
 *   native view reports its own height via
 *   [CascadeCustomBlockContext.setPreferredHeight]. Clamped to a sane range.
 * @property rendererFactory Builds the native view controller that renders a
 *   block instance. Called once per rendered block; the supplied
 *   [CascadeCustomBlockContext] stays valid for that block's lifetime.
 */
@ObjCName("CascadeCustomBlockRegistration", exact = true)
public class CascadeCustomBlockRegistration public constructor(
    public val typeId: String,
    public val displayName: String,
    public val description: String,
    public val keywords: List<String> = emptyList(),
    public val slashBehavior: CascadeCustomBlockSlashBehavior = CascadeCustomBlockSlashBehavior.insert,
    public val defaultPayloadJson: String = "{}",
    public val estimatedHeight: Double = DEFAULT_BLOCK_HEIGHT,
    public val rendererFactory: (CascadeCustomBlockContext) -> UIViewController,
)
