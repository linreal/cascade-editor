# Document Save/Load Serialization — Technical Context

**Branch:** `T-011-save-restore`
**Commits:** 8 (from `T-011 tasks decomposition` through `T-011 review fixes`)
**Files changed:** 34 (+3699 / -196 lines)

---

## 1. Feature Overview

CascadeEditor stores documents as an in-memory `List<Block>`, but has no way to persist or restore them. This feature adds a versioned JSON serialization layer that converts the live block list to JSON and back, with policy-driven handling of IDs, unknown block types, and malformed data.

The implementation solves three problems:

1. **Save** — Snapshot the current document, merging live runtime text/spans (from on-screen blocks) with snapshot content (from off-screen blocks) into a single canonical JSON string.
2. **Load** — Deserialize JSON back into blocks, clear stale runtime state, and set the editor to the loaded content.
3. **Forward compatibility** — Unknown block types encountered during decode are preserved as opaque `UnknownBlockType` objects and rendered with a placeholder UI, preventing silent data loss on round-trip.

---

## 2. Architecture & Design Decisions

### 2.1 New Types Introduced

| File | Type | Role |
|---|---|---|
| `DocumentSchema.kt` | `object DocumentSchema` | Stateless encode/decode entry point (mirrors `RichTextSchema` pattern) |
| `UnknownBlockType.kt` | `data class UnknownBlockType` | Opaque container for unrecognized block types; stores raw JSON string |
| `UnknownBlockRenderer.kt` | `internal object UnknownBlockRenderer` | Compose placeholder for unknown blocks |
| `BlockTypeCodec.kt` | `interface BlockTypeCodec` | Consumer hook for custom type encode/decode |
| `BlockContentCodec.kt` | `interface BlockContentCodec` | Consumer hook for custom content encode/decode |
| `DocumentDecodeWarning.kt` | `sealed class DocumentDecodeWarning` | Non-fatal decode issue hierarchy (7 subclasses) |
| `DocumentDecodeResult.kt` | `data class DocumentDecodeResult` | Decode output: blocks + warnings |
| `DocumentSerializationExt.kt` | Extension functions | `EditorStateHolder.toJson()` and `.loadFromJson()` |
| `BlockIdMode.kt` | `enum class BlockIdMode` | `Preserve` or `Regenerate` IDs on decode |
| `DuplicateIdMode.kt` | `enum class DuplicateIdMode` | `RegenerateLaterDuplicates` or `FailFast` |
| `CustomDataMode.kt` | `enum class CustomDataMode` | `Strict` or `LenientSkipUnsupported` for encode |
| `DocumentEncodeOptions.kt` | `data class DocumentEncodeOptions` | Encode configuration (custom data mode) |
| `DocumentDecodeOptions.kt` | `data class DocumentDecodeOptions` | Decode configuration (ID mode, duplicate mode) |

### 2.2 Patterns

- **Stateless singleton codec** — `DocumentSchema` is an `object` with no mutable state. All configuration flows through options parameters. This matches the existing `RichTextSchema` pattern.
- **Sealed class warnings** — `DocumentDecodeWarning` is a sealed class (not sealed interface) to allow future subclasses while recommending `else` branches for consumers.
- **Codec chain with fallthrough** — Both type and content encoding follow the same 3-step resolution: built-in handler -> consumer codec -> fallback. `null` return means "I don't handle this."
- **Raw JSON preservation** — `UnknownBlockType` stores the original type JSON as a `String` (not `JsonObject`) to keep the core model independent of `kotlinx.serialization.json` types. The serialization layer parses/stringifies at the boundary.
- **Runtime-first snapshot** — `toJson()` checks for live `TextFieldState`/span entries before falling back to snapshot `BlockContent.Text`. This ensures on-screen edits are captured without dropping off-screen data.

### 2.3 Key Design Choices

1. **Why `String` for `rawTypeJson` instead of `JsonObject`?**
   Keeps `UnknownBlockType` in the `core` package without pulling in serialization dependencies. The serialization layer owns the parse/stringify conversion.

2. **Why preserve unknown blocks instead of skipping them?**
   Prevents silent data loss on round-trip. A document saved with blocks from a newer editor version and re-saved by an older version retains those blocks.

3. **Why expose `textStates`/`spanStates` as `CascadeEditor` parameters?**
   Runtime text/span holders were previously `remember`-ed inside the composable, making them inaccessible for save/load. Hoisting them as optional parameters (with defaults) preserves existing ergonomics while enabling external access.

---

## 3. Data Flow

### 3.1 Save (Encode)

```
EditorStateHolder.toJson(textStates, spanStates)
  |
  +-- For each Block in state.blocks:
  |     |
  |     +-- If content is Text:
  |     |     +-- Check textStates.get(blockId) for live text
  |     |     +-- Check spanStates.get(blockId) for live spans
  |     |     +-- Use runtime values if present, otherwise snapshot
  |     |
  |     +-- If content is not Text: pass through unchanged
  |
  +-- DocumentSchema.encodeToString(resolvedBlocks)
        |
        +-- Build envelope: { "version": 1, "blocks": [...] }
        |
        +-- For each block:
              +-- encodeBlockType(): built-in -> codec -> UnknownBlockType raw -> fallback
              +-- encodeBlockContent(): built-in -> codec -> Custom fallback
                    +-- Text content delegates to RichTextSchema.encode()
```

### 3.2 Load (Decode)

```
EditorStateHolder.loadFromJson(jsonString, textStates, spanStates)
  |
  +-- DocumentSchema.decodeFromStringWithReport(jsonString)
  |     |
  |     +-- Validate version (<= CURRENT_VERSION)
  |     +-- For each block JSON entry:
  |     |     +-- Validate type object + typeId (skip if malformed)
  |     |     +-- decodeBlockType(): built-in -> codec -> UnknownBlockType
  |     |     +-- decodeBlockContent(): built-in kinds -> codec -> Custom fallback
  |     |     +-- resolveBlockId(): Regenerate mode / Preserve with dedup
  |     |     +-- Assemble Block(id, type, content)
  |     |
  |     +-- renumberNumberedLists(blocks)
  |     +-- Return DocumentDecodeResult(blocks, warnings)
  |
  +-- textStates.clear()     // Purge stale runtime text
  +-- spanStates.clear()     // Purge stale runtime spans
  +-- setState(EditorState.withBlocks(result.blocks))
  +-- Return result (so caller can inspect warnings)
```

**Important ordering detail:** During decode, type and content are validated *before* the block ID is resolved. This prevents skipped (malformed) blocks from polluting the `seenIds` set, which would cause false duplicate-ID warnings on later valid blocks sharing the same ID.

---

## 4. Public API Surface

### 4.1 DocumentSchema (core encode/decode)

```kotlin
// Package: io.github.linreal.cascade.editor.serialization

public object DocumentSchema {
    public const val CURRENT_VERSION: Int = 1

    // Encode
    public fun encode(blocks, options?, typeCodec?, contentCodec?): JsonObject
    public fun encodeToString(blocks, options?, typeCodec?, contentCodec?): String

    // Decode (warnings discarded)
    public fun decode(json, options?, typeCodec?, contentCodec?): List<Block>
    public fun decodeFromString(jsonString, options?, typeCodec?, contentCodec?): List<Block>

    // Decode with report
    public fun decodeWithReport(json, options?, typeCodec?, contentCodec?): DocumentDecodeResult
    public fun decodeFromStringWithReport(jsonString, options?, typeCodec?, contentCodec?): DocumentDecodeResult
}
```

### 4.2 Editor Integration Extensions

```kotlin
// Snapshot live editor to JSON string
public fun EditorStateHolder.toJson(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    options: DocumentEncodeOptions = DocumentEncodeOptions(),
    typeCodec: BlockTypeCodec? = null,
    contentCodec: BlockContentCodec? = null,
): String

// Load JSON into editor, clearing runtime state
public fun EditorStateHolder.loadFromJson(
    jsonString: String,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    options: DocumentDecodeOptions = DocumentDecodeOptions(),
    typeCodec: BlockTypeCodec? = null,
    contentCodec: BlockContentCodec? = null,
): DocumentDecodeResult
```

### 4.3 Codec Interfaces

```kotlin
public interface BlockTypeCodec {
    public fun encodeType(type: BlockType): JsonObject?      // null = fall through
    public fun decodeType(typeId: String, json: JsonObject): BlockType?
}

public interface BlockContentCodec {
    public fun encodeContent(content: BlockContent): JsonObject?
    public fun decodeContent(kind: String, json: JsonObject): BlockContent?
}
```

### 4.4 CascadeEditor Composable (modified signature)

```kotlin
@Composable
public fun CascadeEditor(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates = remember { BlockTextStates() },   // NEW
    spanStates: BlockSpanStates = remember { BlockSpanStates() },   // NEW
    registry: BlockRegistry = remember { createEditorRegistry() },
    // ... rest unchanged
)
```

### 4.5 BlockRegistry (new methods)

```kotlin
public fun getRenderer(blockType: BlockType): BlockRenderer<*>?  // NEW overload
public fun setUnknownBlockRenderer(renderer: BlockRenderer<*>)   // NEW
```

---

## 5. Integration Points

### 5.1 Existing Serialization Layer

`DocumentSchema` delegates text content encoding to the existing `RichTextSchema`. The document envelope wraps a `kind: "text"` discriminator around `RichTextSchema`'s output, preserving its inner `version` field.

### 5.2 Editor Composable

`CascadeEditor.kt` was modified to accept `textStates` and `spanStates` as optional parameters (defaulting to `remember { ... }`). All internal usages were updated to use these hoisted instances. This is a **non-breaking API change** — existing call sites that don't pass these parameters retain identical behavior.

### 5.3 Block Registry

`BlockRegistry` gained a `getRenderer(BlockType)` overload that falls back to the unknown-block renderer for `UnknownBlockType` instances. `createEditorRegistry()` now calls `setUnknownBlockRenderer(UnknownBlockRenderer)`.

### 5.4 Core Model

`UnknownBlockType` implements `CustomBlockType` (existing interface), with `supportsText = false` and `isConvertible = false`. It lives in the `core` package alongside other block type definitions.

### 5.5 Renamed Local Variables (TextBlockField)

`TextBlockField.kt` renames `blockTextStates` -> `textStates` and `blockSpanStates` -> `spanStates` for consistency with the hoisted parameter names. The corresponding `SpanMaintenanceTextObserver` constructor parameter names were also updated.

---

## 7. Edge Cases & Known Constraints

### ID Handling

- **Empty or missing IDs** in Preserve mode are regenerated with a `MissingIdRegenerated` warning.
- **Duplicate IDs** default to regenerating later occurrences. `FailFast` mode throws immediately.
- **Skipped (malformed) blocks never register their ID** in the seen-set, preventing false duplicate warnings on later valid blocks with the same ID.
- IDs are validated only as non-empty strings; UUID format is not enforced.

### Version Guard

- Missing `version` field defaults to `1`.
- `version > CURRENT_VERSION` throws `IllegalArgumentException` — there is no graceful degradation for future major versions.

### Content Fallbacks

- Missing `content` object defaults to `BlockContent.Empty`.
- Missing `kind` in content object defaults to `BlockContent.Empty`.
- Non-string `kind` values (e.g., numeric `42`) are coerced to their string representation and treated as unknown content kinds.
- Image content missing `uri` causes the entire block to be skipped (not just the content).

### Custom Data Map Encoding

- Only primitive types (`String`, `Int`, `Long`, `Float`, `Double`, `Boolean`, `null`), `List`, and `Map<String, *>` are supported.
- `Map` with non-`String` keys: throws in `Strict` mode, drops the entry in `Lenient` mode.
- Numeric type width is not preserved on round-trip (`Int` may become `Long` after decode, because `JsonPrimitive.longOrNull` is checked before `intOrNull`).

### Runtime State Priority

- `toJson()` checks for runtime entry *existence* (`get(blockId) != null`) before reading. This avoids calling `getSpans()` on absent entries, which would return empty lists and incorrectly erase off-screen spans.
- `loadFromJson()` clears *all* runtime state (`textStates.clear()`, `spanStates.clear()`) before setting new blocks. This prevents stale `TextFieldState` entries from overriding loaded content when block IDs are preserved.

### Unknown Block Rendering

- `UnknownBlockRenderer` displays hardcoded English text: `"Unsupported block type: {typeId}"`. Localization is deferred.
- Unknown blocks participate in drag-and-drop (reorderable) but are non-editable and non-focusable.

### Image Blocks

- Image block type and content round-trip correctly at the data layer.
- No built-in `ImageRenderer` exists yet — image blocks are data-safe but not user-visible until the renderer lands.

---

## 8. Glossary

| Term | Definition |
|---|---|
| **Block** | The fundamental content unit in CascadeEditor. Has an `id`, `type`, and `content`. |
| **BlockType** | Sealed interface describing the semantic type of a block (Paragraph, Heading, Todo, etc.). |
| **BlockContent** | Sealed interface for block payload: `Text` (with spans), `Image`, `Empty`, or `Custom`. |
| **BlockId** | Value class wrapping a `String` identifier for a block. |
| **TextSpan** | A range (`start`, `end`) plus a `SpanStyle` applied to text content. |
| **UnknownBlockType** | A `CustomBlockType` subclass that holds raw JSON for an unrecognized type, enabling lossless round-trip. |
| **DocumentSchema** | Stateless object handling JSON encode/decode of `List<Block>` with versioning. |
| **RichTextSchema** | Existing stateless object handling JSON encode/decode of `BlockContent.Text` with spans. |
| **BlockTextStates** | Runtime holder mapping `BlockId` to Compose `TextFieldState` for on-screen blocks. |
| **BlockSpanStates** | Runtime holder mapping `BlockId` to live span state for on-screen blocks. |
| **Codec** | Consumer-provided `BlockTypeCodec` or `BlockContentCodec` that hooks into encode/decode for custom types. |
| **DocumentDecodeWarning** | Sealed class hierarchy representing non-fatal decode issues (unknown types, duplicate IDs, malformed blocks, etc.). |
| **Snapshot content** | The `BlockContent` stored in the `Block` data class — may be stale if the block is currently being edited on-screen. |
| **Runtime content** | The live `TextFieldState` text and span state maintained by Compose — authoritative for on-screen blocks. |
| **Round-trip guarantee** | A document with unknown blocks, loaded and re-saved without modification, preserves all unknown block data. Semantic equivalence, not byte-for-byte. |
