# HTML Import / Export — Feature Context

## 1. Feature Overview

CascadeEditor's domain model is a `List<Block>` (paragraphs, headings, lists, code, quote, divider, todo, custom) with rich-text spans. Many integration targets exchange content as HTML strings, but each target uses a different *dialect* — Quill-flavored, GitHub-style code, Notion-style todos. There was no existing way to round-trip a document through such an HTML payload.

This feature ships a profile-driven HTML codec under a new `htmlserialization/` package, parallel to the existing JSON `serialization/` layer. A single configuration object — `HtmlProfile` — drives both directions through a stateless `HtmlSchema` entry point. The library ships an opinionated `HtmlProfile.Default` (HTML5-ish canonical mappings) plus public extension points so a consumer can express its dialect with overrides instead of forking the parser. A reference dialect profile (`sample/.../CustomHtmlProfile.kt`) demonstrates the pattern end-to-end without leaking dialect code into `:editor`.

The codec is common-only Kotlin Multiplatform: no `org.jsoup`, no browser DOM, no platform parser. Decode and encode never throw on user input or consumer-supplied callback failure; everything surfaces as a structured warning.

Out of scope (intentionally): full HTML5 spec compliance, browser-equivalent error recovery, a generic cross-format `DocumentImporter`/`DocumentExporter`, and any built-in vendor-specific profile inside `:editor`.

## 2. Architecture & Design Decisions

### 2.1 Module placement

```
editor/src/commonMain/kotlin/io/github/linreal/cascade/editor/
├── serialization/          existing JSON layer
└── htmlserialization/      new HTML layer
sample/src/commonMain/kotlin/io/github/linreal/cascade/profiles/
└── CustomHtmlProfile.kt    reference dialect profile
```

### 2.2 New types

Public surface (`htmlserialization/`):

- All public declarations are marked `@ExperimentalCascadeHtmlApi` with warning-level opt-in while the codec stabilizes.
- `HtmlSchema` — stateless object, four entry points (`HtmlSchema.kt:16`).
- `HtmlProfile` + `HtmlProfile.Default` — immutable configuration bundle (`HtmlProfile.kt:38`).
- `HtmlProfileSupportSet` — predicate-based round-trip claim (`HtmlProfileSupportSet.kt:29`).
- Codec contracts: `HtmlNodeView`, `TagDecoder`, `TagDecodeContext`, `TagDecodeResult`, `InlineFragment`, `BlockEncoder<T>`, `SpanEncoder<T>`, `BlockGroupEncoder`, `HtmlEncodeContext`, `HtmlEmit`, `HtmlTagPair` (`HtmlCodecContracts.kt`).
- Policies: `BlockSeparator`, `InlineRoot`, `EntityDecode`, `UnknownTagPolicy` (`HtmlPolicies.kt`).
- Reports: `HtmlDecodeResult`, `HtmlEncodeResult`, `HtmlDecodeWarning`, `HtmlEncodeWarning` (`HtmlResults.kt`, `HtmlWarnings.kt`).
- Helpers: `Html.escapeText` / `Html.escapeAttr` (`HtmlEscaping.kt`); `openTagWithCascadeIndentation` and `CASCADE_INDENT_CLASS_PREFIX` (`DefaultBlockEncoders.kt`, `DefaultListOutlineEncoder.kt`).
- Editor extensions: `EditorStateHolder.toHtml(...)` and `loadFromHtml(...)` (`HtmlSerializationExt.kt`).

Internal surface (same package):

- `HtmlParser` (entry), `HtmlTokenizer`, `HtmlTreeBuilder`, `HtmlNode`, `HtmlToken`, `HtmlPolicyApplier` — parser pipeline.
- `HtmlDecodeEngine`, `TagDecodeContextImpl`, `HtmlNodeViewMapper`, `DefaultTagDecoders`, `PreservedHtmlBlockType` — decode side.
- `HtmlEncodeEngine`, `HtmlEncodeContextImpl`, `DefaultBlockEncoders`, `DefaultSpanEncoders`, `DefaultListOutlineEncoder`, `DefaultEncoderFallbacks` — encode side.

### 2.3 Patterns and rationale

- **Single configuration object (`HtmlProfile`).** Encode and decode mappings are nearly always symmetric (decode `<strong>`→`Bold`, encode `Bold`→`<strong>`). Splitting them would force consumers to keep two parallel structures aligned by hand. `HtmlProfile.kt:38`.
- **Immutability with builder methods.** `HtmlProfile` uses `internal constructor` + private `copyWith(...)` instead of `data class` so the public surface stays narrow and composition (`with*` / `without*`) is side-effect-free. Overrides *replace* — there is no chain dispatch; consumers compose by delegating to `tagDecoderFor` (`HtmlProfile.kt:62-77`).
- **Hand-written parser, common-only.** Roughly 700 LoC across `HtmlTokenizer.kt` + `HtmlTreeBuilder.kt`. The dialect grammar is small (no scripts/styles/DOCTYPE), so KMP-pure parsing is cheaper than dealing with platform fragmentation.
- **Source ranges as UTF-16 half-open offsets.** Every `HtmlNode` / `HtmlNodeView` exposes `sourceStart` and `sourceEndExclusive` so `rawSource.substring(start, end)` returns the verbatim original slice. Used for accurate warning offsets and `UnknownTagPolicy.Preserve` lossless capture.
- **Three encoder kinds (`BlockGroupEncoder`, `BlockEncoder<T>`, `SpanEncoder<T>`).** A flat block stream cannot express HTML's structural relationships — consecutive list items must wrap in one `<ul>` / `<ol>` and indentation must turn into nested HTML. Group encoders own runs; per-block encoders own scalars; span encoders own inline. `BlockGroupEncoder` is a regular `interface` because it has two abstract methods (`groupKey` + `encodeGroup`); the others are `fun interface`s.
- **Default uses ONE outline encoder, not two.** `DefaultListOutlineEncoder` matches both `BulletList` and `NumberedList` together so mixed `Bullet(0) → Numbered(1) → Bullet(0)` outlines can become genuinely nested HTML (`<ul><li>...<ol><li>...</ol></li></ul>`). Splitting into bullet/numbered group encoders would emit sibling `<ul></ul><ol></ol><ul></ul>` and lose the parent/child relationship.
- **Per-decoder whitespace ownership.** No global whitespace-collapse policy. `<p>` / heading / `<blockquote>` trim+collapse; `<li>` trims edges and a single trailing `\n`; `<pre>` preserves internal whitespace and drops a single trailing `\n`; inline tags pass through verbatim. Helpers live on `TagDecodeContext.collectInlineText(...)`.
- **No-throw across the consumer boundary.** `HtmlDecodeEngine` and `HtmlEncodeEngine` wrap every consumer decoder/encoder call in `try/catch`. Failures surface as `HtmlDecodeWarning.DecoderException` / `HtmlEncodeWarning.EncoderException` with structured fallback. Strictness is opt-in via `*WithReport`.
- **Round-trip is scoped, not universal.** `HtmlProfileSupportSet` is an executable predicate (block + span + document level) that constrains the round-trip property to what the profile claims. Generated property tests use `SupportSetBlockGenerator` to draw documents *inside* the support set; out-of-set behavior is documented separately in `HtmlOutOfSupportTest.kt`.
- **Sample profile, not built-in.** The Custom reference profile is in `sample/src/commonMain/...`. To make this possible without bypassing `internal` API, `HtmlProfileSupportSet` got a public constructor and `HtmlProfile.withSupportSet(...)` was added. Both are dialect-neutral.

## 3. Data Flow

### 3.1 Decode (`HtmlSchema.decode(html, profile)`)

`HtmlSchema.kt:32` → `HtmlDecodeEngine.decodeWithReport(html, profile)` (`HtmlDecodeEngine.kt:13`):

1. **Parse.** `HtmlParser.parse(rawSource, profile)` (`HtmlParser.kt:29`):
   1. `HtmlTokenizer.tokenize(rawSource, profile.entityDecode)` produces a token stream (`HtmlToken.OpenTag` / `CloseTag` / `Text`). Comments and markup declarations are skipped during tokenization. Text tokens carry post-entity-decode `text` while their source range still points to the pre-decode slice.
   2. `HtmlTreeBuilder.build(rawSource, tokens)` produces an `HtmlNode` tree. Mismatched nesting is straightened (inner descendants auto-close at the mismatch, ancestor closes at the close tag); duplicate counterpart closes for auto-closed tags are suppressed to avoid noisy warnings. Unclosed tags close at end of input. Each event emits an `HtmlDecodeWarning` (`StrayClosingTag`, `MismatchedNesting`, `UnclosedTag`).
   3. `HtmlPolicyApplier.apply(rawSource, nodes, profile)` rewrites *root-level* shape according to `BlockSeparator` and `InlineRoot`. Element children are left alone — per-decoder whitespace ownership applies inside elements. `BlockSeparator.Newline` splits root text on `\n` with surrounding-space trimming; `N` consecutive newlines produce `N − 1` synthetic empty `<p>` elements. `InlineRoot.WrapInParagraph` wraps contiguous root inline runs in synthetic `<p>` nodes; `InlineRoot.Drop` discards them with `DroppedContent` warnings.
2. **View map.** Internal `HtmlNode` is mapped to public `HtmlNodeView` via `HtmlNodeViewMapper.toView(...)`. The internal type stays free to evolve.
3. **Walk.** `HtmlDecodeEngine.Runner.decodeBlocks(rootNodes, parentTag = null)` iterates root nodes:
   - **Text node** → appended to `pendingInline`.
   - **Element node** → look up `profile.tagDecoderFor(tag)`. If present, call `decoder.decode(ctx, attrs, children)` inside a `try/catch`. If absent, hand off to `UnknownTagPolicy`.
   - Result dispatch: `AsBlock` / `AsBlocks` flush the pending inline run as a paragraph and append; `AsText` accumulates inline text and spans; `Drop` no-ops. In *inline* context, returning `AsBlock` / `AsBlocks` emits `BlockInInlineContext`, flattens any `BlockContent.Text` payload, and drops non-text payloads.
4. **`UnknownTagPolicy`** (`HtmlDecodeEngine.kt:160`): `Strip` and `WarnAndStrip` keep child text; `Preserve` produces a `Block` with `type = PreservedHtmlBlockType` and `BlockContent.Custom(typeId = "html.preserved", data = mapOf("tagName" to <lowercase>, "rawHtml" to rawSlice))` (lossless **block-level** only); inline `Preserve` degrades to `WarnAndStrip` because the inline model cannot carry opaque HTML; `Custom` runs a consumer-supplied `(HtmlNodeView.Element, TagDecodeContext) -> TagDecodeResult` handler.
5. **Normalize.** Final blocks pass through `normalizeIndentationOutline(...)` then `renumberNumberedLists(...)` (the same post-decode step `DocumentSchema` uses). Custom decoders therefore receive normalized output at the public entry point.
6. Returns `HtmlDecodeResult(blocks, warnings)`. `HtmlSchema.decode(...)` discards warnings; `decodeWithReport(...)` exposes them.

### 3.2 Encode (`HtmlSchema.encode(blocks, profile)`)

`HtmlSchema.kt:22` → `HtmlEncodeEngine.encodeWithReport(blocks, profile)` (`HtmlEncodeEngine.kt:10`):

1. **Walker.** `HtmlEncodeEngine.Runner.encode(blocks)` iterates `blocks` in document order. For each `block`:
   1. Ask each registered `BlockGroupEncoder` (in registration order) for `groupKey(block)`. The first non-null key starts a run.
   2. Keep consuming consecutive blocks while the *same* encoder returns the same key (`==`). Then call `encoder.encodeGroup(ctx, run)` once.
   3. If no group matches, look up `profile.blockEncoderFor(block)` — checks `block.type` (custom typeId via `CustomBlockType`), then `BlockContent.Custom.typeId`, then the built-in block-class registry.
   4. On `HtmlEmit.Skip` or thrown exception (caught + reported as `EncoderException`), fall back to `profile.encoderBlockFallback`. If the fallback also throws, emit empty string for that block.
2. **Inline rendering.** `HtmlEncodeContextImpl.encodeInline(block)` (`HtmlEncodeContextImpl.kt:15`):
   1. Reads `block.content as? BlockContent.Text` (returns `""` for `Empty` / `Custom`).
   2. Calls `encodeInlineFragment(text, spans, preserveNewlines = block.type == BlockType.Code)`.
   3. Spans are normalized via `SpanAlgorithms.normalize(...)`, mapped to `HtmlTagPair`s through `profile.spanEncoderFor(...)` (with `encoderSpanFallback` on miss/throw). Boundaries are computed from all span endpoints; the renderer walks segments, closing/reopening tag pairs as the active span stack changes. The active-span order is deterministic: earlier `start`, then wider span (`end DESC`), then original normalized index.
   4. For non-Code blocks, embedded `\n` is emitted as `<br>`. For Code, `\n` stays literal (Code blocks also don't get span tags applied to whitespace boundaries — the default Code encoder uses `encodeTextOnly` to drop spans entirely; see edge cases).
3. Returns `HtmlEncodeResult(html, warnings)`.

### 3.3 Editor integration (`EditorStateHolder.toHtml` / `loadFromHtml`)

`HtmlSerializationExt.kt:16` and `:33`:

- `toHtml(textStates, spanStates, profile)` calls `serialization.resolveCurrentBlocks(holder, textStates, spanStates)` (the same helper JSON export uses), then `HtmlSchema.encode(...)`. Live `BlockTextStates` / `BlockSpanStates` override snapshot `BlockContent.Text` for blocks that have runtime entries; off-screen blocks fall back to snapshot content; spans are stripped on non-spans block types defensively.
- `loadFromHtml(html, textStates, spanStates, profile)` calls `HtmlSchema.decodeWithReport`, clears both runtime holders, then `setState(EditorState.withBlocks(result.blocks))`. Focus, selection, drag/slash state, and undo/redo history reset — same hard-replacement semantics as `loadFromJson`.

## 4. Public API Surface

### 4.1 Entry points

```kotlin
public object HtmlSchema {
    public fun encode(blocks: List<Block>, profile: HtmlProfile): String
    public fun encodeWithReport(blocks: List<Block>, profile: HtmlProfile): HtmlEncodeResult
    public fun decode(html: String, profile: HtmlProfile): List<Block>
    public fun decodeWithReport(html: String, profile: HtmlProfile): HtmlDecodeResult
}

public fun EditorStateHolder.toHtml(
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: HtmlProfile,
): String

public fun EditorStateHolder.loadFromHtml(
    html: String,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    profile: HtmlProfile,
): HtmlDecodeResult
```

### 4.2 `HtmlProfile` builder methods

All `with*` / `without*` methods return a new immutable `HtmlProfile`. Source: `HtmlProfile.kt`.

| Method | Purpose |
|---|---|
| `withTagDecoder(tag, TagDecoder)` / `withoutTagDecoder(tag)` / `tagDecoderFor(tag)` | Per-tag decode dispatch. Tag names are matched case-insensitively. `tagDecoderFor` is the documented escape hatch consumer decoders use to delegate to the previous (e.g. default) decoder. |
| `withBlockEncoder<reified T : BlockType>(BlockEncoder<T>)` | Register encoder keyed on `BlockType` *class* — one slot covers `BlockType.Heading` regardless of level. |
| `withSpanEncoder<reified T : SpanStyle>(SpanEncoder<T>)` | Class-keyed span encoder — one slot covers all `SpanStyle.Highlight` colors / `SpanStyle.Link` URLs. |
| `withBlockGroupEncoder(name, BlockGroupEncoder)` / `withoutBlockGroupEncoder(name)` | Group encoders are kept in **registration order**; re-registering under the same name removes the old slot and appends the new one to the tail. |
| `withCustomBlockEncoder(typeId, BlockEncoder<*>)` | Block encoder keyed by `typeId` — matches `CustomBlockType.typeId` *or* `BlockContent.Custom.typeId`. |
| `withCustomSpanEncoder(typeId, SpanEncoder<SpanStyle.Custom>)` | Span encoder keyed by `SpanStyle.Custom.typeId`. |
| `withParserPolicy(BlockSeparator | InlineRoot | EntityDecode)` | Override one parser policy. |
| `withUnknownTagPolicy(UnknownTagPolicy)` | `Strip`, `WarnAndStrip` (default), `Preserve`, or `Custom(handler)`. |
| `withEncoderBlockFallback(BlockEncoder<BlockType>)` / `withEncoderSpanFallback(SpanEncoder<SpanStyle>)` | Fallback executed when no encoder matches, the registered encoder returns `HtmlEmit.Skip`, or it throws. |
| `withSupportSet(HtmlProfileSupportSet)` | Replace the round-trip claim. Used by dialect profiles whose support differs from `Default`. |

### 4.3 Codec contracts (selected)

```kotlin
public sealed interface TagDecodeResult {
    public data class AsBlock(val block: Block) : TagDecodeResult
    public data class AsBlocks(val blocks: List<Block>) : TagDecodeResult
    public data class AsText(val text: String, val spans: List<TextSpan>) : TagDecodeResult
    public data object Drop : TagDecodeResult
}

public sealed interface HtmlEmit {
    public data class Raw(val html: String) : HtmlEmit
    public data object Skip : HtmlEmit
}

public abstract class TagDecodeContext {
    public abstract val isBlockContext: Boolean
    public abstract val parentTag: String?
    public abstract val rawSource: String
    public abstract val charOffset: Int
    public abstract fun decodeInline(children: List<HtmlNodeView>): InlineFragment
    public abstract fun decodeBlocks(children: List<HtmlNodeView>): List<Block>
    public abstract fun collectInlineText(
        children: List<HtmlNodeView>,
        trimEdges: Boolean = false,
        trimSingleTrailingNewline: Boolean = false,
        collapseInternalSpaces: Boolean = false,
    ): InlineFragment
    public abstract fun rawSliceOf(node: HtmlNodeView): String
    public abstract fun tagDecoderFor(tag: String): TagDecoder?
    public abstract fun warn(warning: HtmlDecodeWarning)
}

public abstract class HtmlEncodeContext {
    public abstract fun encodeInline(block: Block): String
    public abstract fun encodeTextOnly(block: Block): String
    public abstract fun encodeInlineFragment(
        text: String,
        spans: List<TextSpan>,
        preserveNewlines: Boolean = false,
    ): String
    public abstract fun escapeText(s: String): String
    public abstract fun escapeAttr(s: String): String
    public abstract fun warn(warning: HtmlEncodeWarning)
}
```

`HtmlNodeView` is a sealed interface with `Element(tag, attrs, children, sourceStart, sourceEndExclusive)` and `Text(text, sourceStart, sourceEndExclusive)`. Tag and attribute *names* are lowercased; attribute *values* are not.

### 4.4 Helpers

```kotlin
public object Html {
    public fun escapeText(s: String): String   // & < >
    public fun escapeAttr(s: String): String   // & < > " '
}

public const val CASCADE_INDENT_CLASS_PREFIX: String = "cascade-indent-"

public fun openTagWithCascadeIndentation(tagName: String, block: Block): String
```

`escapeText` / `escapeAttr` use a single-pass scan and return the input unchanged when no escapable characters are present, avoiding allocation for ordinary content.

### 4.5 Default profile contents

`HtmlProfile.Default` (`HtmlProfile.kt:269`) registers:

- **Default tag decoders** (`DefaultTagDecoders.All`): `<p>`, `<h1>`–`<h6>`, `<blockquote>`, `<pre>`, `<ul>`, `<ol>`, `<li>`, `<hr>`, `<br>`, `<strong>`/`<b>`, `<em>`/`<i>`, `<u>`, `<s>`/`<strike>`/`<del>`, `<code>`, `<a>`, `<mark>`. Decode accepts synonyms (Postel's law).
- **Default block encoders** (`DefaultBlockEncoders`): `Paragraph` (canonical `<p>`, with `class="cascade-indent-N"` on indented paragraphs), `Heading` (`<hN>`), `Quote` (`<blockquote>`), `Code` (`<pre><code>` via `encodeTextOnly`), `Divider` (`<hr>`).
- **Default span encoders** (`DefaultSpanEncoders`): `Bold`→`<strong>`, `Italic`→`<em>`, `Underline`→`<u>`, `StrikeThrough`→`<s>`, `InlineCode`→`<code>`, `Link`→`<a href="..." rel="noreferrer">`, `Highlight`→`<mark data-cascade-highlight="AARRGGBB">`. Highlight color is an upper-case 8-digit ARGB derived from the low 32 bits of `colorArgb`.
- **One block group encoder** named `listOutline` (`DefaultListOutlineEncoder`) that claims both `BulletList` and `NumberedList` and emits genuinely nested `<ul>` / `<ol>`. It uses `class="cascade-indent-N"` on `<li>` only when the editor's actual depth differs from the depth implied by HTML nesting (e.g., free/skipped depths). Ordinary outlines stay canonical.
- **Encode fallbacks** (`DefaultEncoderFallbacks`): block fallback emits `<p>{ctx.encodeInline(block)}</p>` (preserving spans); span fallback returns empty tag pair (preserves text, drops formatting).
- **Default policies**: `UnknownTagPolicy.WarnAndStrip`, `BlockSeparator.BlockTags`, `InlineRoot.Drop`, `EntityDecode.Standard`.
- **Default support set** (`HtmlProfileSupportSet.Default`): all built-in block types **except `Todo`**; all built-in spans (including parameterized `Highlight` and `Link`); indentation across `0..MAX_INDENTATION_LEVEL` on the subset of supported blocks that the editor's capability matrix allows to indent (`Paragraph`, `BulletList`, `NumberedList`).

There is intentionally no default `Todo` mapping — there is no canonical HTML for it (GitHub uses `<input type="checkbox">`, Notion uses `div + aria`). Forcing one would lock consumers out.

## 4.1 How can users implement their own HTML dialect

Profiles are composed, not subclassed. The standard recipe is to start from `HtmlProfile.Default` and override only what the dialect needs differently. The shipped reference is `sample/src/commonMain/kotlin/io/github/linreal/cascade/profiles/CustomHtmlProfile.kt`; this section walks the same five steps a new dialect would follow.

### Step 1 — Pick parser policies

Most non-HTML5 dialects need `BlockSeparator.Newline` (root `\n` separates inline runs) and `InlineRoot.WrapInParagraph` (root-level inline content wraps in synthetic `<p>` instead of being dropped). `EntityDecode.Standard` is almost always correct. See `HtmlPolicies.kt`.

```kotlin
public val Profile: HtmlProfile = HtmlProfile.Default
    .withParserPolicy(BlockSeparator.Newline)
    .withParserPolicy(InlineRoot.WrapInParagraph)
    .withParserPolicy(EntityDecode.Standard)
```

### Step 2 — Override per-tag decoders

Use `withTagDecoder("li", ...)` to read dialect-specific attributes. The example below reads `class="ql-indent-N"` from each `<li>`, falling back to root depth when the regex does not match a concrete digit (so `class="ql-indent-N"` literal placeholders decode safely).

```kotlin
private val CustomLiDecoder: TagDecoder = TagDecoder { ctx, attrs, children ->
    val inline = ctx.collectInlineText(
        children = children.filterNot { it.isListContainer() },
        trimEdges = true,
        trimSingleTrailingNewline = true,
    )
    val type = if (ctx.parentTag == "ol") {
        BlockType.NumberedList(number = 1)
    } else {
        BlockType.BulletList
    }
    TagDecodeResult.AsBlock(
        Block(
            id = BlockId.generate(),
            type = type,
            content = BlockContent.Text(inline.text, inline.spans),
            attributes = BlockAttributes(indentationLevel = attrs.customIndentationLevel()),
        )
    )
}
```

To extend a built-in mapping rather than replace it, call `ctx.tagDecoderFor("li")` from inside the new decoder and delegate explicitly. Registration is replacement, not chaining — this is the documented escape hatch.

### Step 3 — Override span encoders to emit dialect-canonical inline tags

`SpanEncoder<T>` is a `fun interface`; a lambda is enough.

```kotlin
.withSpanEncoder<SpanStyle.Bold>          { HtmlTagPair("<strong>", "</strong>") }
.withSpanEncoder<SpanStyle.Italic>        { HtmlTagPair("<em>", "</em>") }
.withSpanEncoder<SpanStyle.StrikeThrough> { HtmlTagPair("<s>", "</s>") }
.withSpanEncoder<SpanStyle.InlineCode>    { HtmlTagPair("<code>", "</code>") }
.withSpanEncoder<SpanStyle.Link> { style ->
    HtmlTagPair(
        open = """<a rel="nofollow noreferrer noopener" target="_blank" href="${Html.escapeAttr(style.url)}">""",
        close = "</a>",
    )
}
```

Use `Html.escapeAttr` / `Html.escapeText` — never hand-roll escaping.

### Step 4 — Replace structural group encoders for non-canonical list shapes

`HtmlProfile.Default` ships a single `listOutline` encoder that emits genuinely nested `<ul>` / `<ol>`. Dialects whose data model is *flat* (e.g. `class="ql-indent-N"` per `<li>` with siblings instead of nesting) cannot use it, because flat profiles cannot represent a numbered list nested inside a bullet item — that's a property of the dialect, not a bug.

```kotlin
.withoutBlockGroupEncoder("listOutline")
.withBlockGroupEncoder(
    name = "customBulletList",
    encoder = CustomFlatListEncoder(
        outerTag = "ul",
        groupKeyValue = "customBulletList",
        matches = { type -> type == BlockType.BulletList },
    ),
)
.withBlockGroupEncoder(
    name = "customNumberedList",
    encoder = CustomFlatListEncoder(
        outerTag = "ol",
        groupKeyValue = "customNumberedList",
        matches = { type -> type is BlockType.NumberedList },
    ),
)
```

```kotlin
private class CustomFlatListEncoder(
    private val outerTag: String,
    private val groupKeyValue: String,
    private val matches: (BlockType) -> Boolean,
) : BlockGroupEncoder {

    override fun groupKey(block: Block): Any? =
        if (matches(block.type)) groupKeyValue else null

    override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>): HtmlEmit {
        val items = blocks.joinToString(separator = "") { block ->
            val classAttr = block.attributes.indentationLevel.toCustomClassAttr()
            "<li$classAttr>${ctx.encodeInline(block)}</li>"
        }
        return HtmlEmit.Raw("<$outerTag>$items</$outerTag>")
    }
}
```

Always render list-item content with `ctx.encodeInline(block)` so spans inside list items survive — never inline `block.content.text` directly; spans would be silently dropped.

`BlockGroupEncoder` is a regular `interface` (two abstract methods), not a `fun interface`, so use an object/class declaration. Group encoders are queried in **registration order** when looking up the group key; once a run starts, that encoder owns it until the key changes.

### Step 5 — Drop unsupported attributes with structured warnings + declare your support set

Replace per-block encoders that need to drop an attribute the dialect cannot represent. Emit `HtmlEncodeWarning.DroppedAttribute` so consumers can audit.

```kotlin
private val CustomParagraphEncoder: BlockEncoder<BlockType.Paragraph> = BlockEncoder { ctx, block, _ ->
    if (block.attributes.indentationLevel > BlockAttributes.MIN_INDENTATION_LEVEL) {
        ctx.warn(
            HtmlEncodeWarning.DroppedAttribute(
                typeId = block.type.typeId,
                attr = "indentationLevel",
                reason = "Custom HTML only supports indentation on list items",
            )
        )
    }
    HtmlEmit.Raw("<p>${ctx.encodeInline(block)}</p>")
}
```

Then declare the round-trip claim explicitly so generated tests stay scoped:

```kotlin
private val CustomSupportSet: HtmlProfileSupportSet = HtmlProfileSupportSet(
    supportsBlock = ::isCustomSupportedBlock,
    supportsSpan = ::isCustomSupportedSpan,
)
// ...
.withSupportSet(CustomSupportSet)
```

`HtmlProfileSupportSet.supportsDocument(blocks)` automatically rejects (a) outlines that violate the parent/child indentation invariant and (b) documents whose `NumberedList(number)` values would change under `renumberNumberedLists()`. You only need to define the per-block / per-span predicates.

### Optional steps

- `withCustomBlockEncoder(typeId, ...)` and `withCustomSpanEncoder(typeId, ...)` for `CustomBlockType` / `SpanStyle.Custom` payloads (or for `BlockContent.Custom(typeId = "...", ...)`-shaped content). The custom block encoder is also how a consumer round-trips `UnknownTagPolicy.Preserve` output by registering for `typeId = "html.preserved"` and emitting the `rawHtml` payload — that path is byte-identical to the original input slice.
- `withUnknownTagPolicy(UnknownTagPolicy.Custom(handler))` for dialects that need bespoke handling of unrecognized tags.
- `withEncoderBlockFallback { ctx, block, _ -> ... }` for unsupported block types — e.g. mapping `Heading` to `<strong>` when the dialect has no headings.

### What not to do

- Don't reach into `internal` symbols like `PreservedHtmlBlockType` or the engine — they are not part of the API contract and may change. Use `withCustomBlockEncoder(typeId = "html.preserved", ...)`.
- Don't subclass `HtmlProfile` — it has an `internal` constructor by design. Compose with builder methods.
- Don't put dialect profiles in `:editor`. Keep them in a sample / consumer module so `:editor` stays dialect-neutral, exactly the way `CustomHtmlProfile` does.

## 5. Integration Points

- **JSON serialization layer.** `HtmlSerializationExt.toHtml(...)` reuses the internal `serialization.resolveCurrentBlocks(...)` helper so live `BlockTextStates` / `BlockSpanStates` override snapshot content the same way `toJson` does. No JSON behavior is altered.
- **Editor state holder.** `loadFromHtml` calls `setState(EditorState.withBlocks(result.blocks))`, the same hard-replacement path as `loadFromJson`. Focus, selection, slash, drag, undo/redo all reset.
- **Core normalization helpers.** `HtmlDecodeEngine` runs `core.normalizeIndentationOutline(...)` then `core.renumberNumberedLists(...)` after each decode (`HtmlDecodeEngine.kt:23`). `HtmlProfileSupportSet.supportsDocument` reuses both helpers (plus `core.isValidIndentationOutline`) to reject non-normalized inputs from round-trip claims.
- **Span rendering.** `HtmlEncodeContextImpl.encodeInlineFragment` runs `richtext.SpanAlgorithms.normalize(...)` before emitting tags — same algorithm `RichTextSchema` uses, so encoded output is consistent with persisted JSON.
- **Link normalization.** Default `<a>` decode runs `richtext.LinkUrlPolicy.validate(...)` so persisted URLs match the editor's runtime contract; missing/blank/invalid `href` drops the link span and emits `HtmlDecodeWarning.DroppedAttribute`.
- **`BlockAttributes` visibility change.** `BlockAttributes.MIN_INDENTATION_LEVEL` and `MAX_INDENTATION_LEVEL` were `internal` and are now `public` so dialect profiles outside `:editor` can clamp depth correctly. Diff: `BlockAttributes.kt:25-27`.
- **`ARCHITECTURE.md`.** Quick Reference + Implementation Status + testing tables updated to point at new files.

No existing JSON, span, or editor-state behavior was modified.

## 7. Edge Cases & Known Constraints

**Decode Safety And Source Positions**

- **No-throw boundary.** `HtmlDecodeEngine.decodeWithReport` and `HtmlEncodeEngine.encodeWithReport` are wrapped in `try/catch` at the runner level *and* per-call. A consumer decoder that throws → `HtmlDecodeWarning.DecoderException(tag, message, charOffset)` + node falls back to `Drop`; siblings/ancestors continue. A consumer encoder that throws → `HtmlEncodeWarning.EncoderException(typeId, message)` + treats result as `Skip` → fallback runs. If the fallback also throws, the second exception is recorded and an empty string is emitted. Strictness is opt-in via `*WithReport`.
- **Source ranges are UTF-16 code units, not bytes.** `sourceStart` / `sourceEndExclusive` match `String.substring` semantics. Surrogate-pair characters (emoji, non-BMP) produce two-unit ranges; `rawSource.substring(...)` still slices correctly. Coverage in `HtmlSourceRangeTest.kt`.

**Unknown And Unsupported HTML**

- **`UnknownTagPolicy.Preserve` is lossless ONLY block-level.** Block-level unknown elements become `Block(type = PreservedHtmlBlockType, content = BlockContent.Custom(typeId = "html.preserved", data = mapOf("tagName" to ..., "rawHtml" to rawSlice)))`. Round-trip is byte-identical iff a custom block encoder is registered for `typeId = "html.preserved"` that emits the `rawHtml` payload verbatim. Inline `Preserve` *intentionally* degrades to `WarnAndStrip` — the editor's inline model cannot carry opaque HTML, so empty inline tags like mention tokens cannot survive `Preserve`. Consumers needing inline dialect tags must register an explicit `TagDecoder` plus matching custom span encoder.
- **`<script>` / `<style>` reach the codec phase as ordinary elements.** No registered decoder, so `UnknownTagPolicy` decides. Default profile uses `WarnAndStrip` → tag dropped, child text kept (which, for `<script>`, *is* the script body). Consumers wanting to drop the body too should register a no-op decoder for those tags.
- **Profile builder programmer errors.** The only path that *can* throw is profile construction (e.g., passing `null` through a non-null contract). That happens at composition time, not at codec time, so it never crosses an `encode/decode` boundary.

**Tag-Specific Decode Rules**

- **`<br>` is context-dependent.** Inside a text context decoder, `<br>` returns `AsText("\n", emptyList())` so the surrounding block accumulates a literal newline. At root or in pure block context, `<br>` returns `Drop` and emits `HtmlDecodeWarning.DroppedContent` (`DefaultTagDecoders.kt:183`).
- **`<pre>` / `<pre><code>` strip spans at the decode edge.** Code blocks do not support rich spans by editor model. `decodeCode` uses plain-text collection when `parentTag == "pre"`, so no `InlineCode` span leaks into a `BlockType.Code`. Outer `<pre>` decoder also drops a single trailing `\n` immediately before `</pre>` (matches `<pre>x\ny\n</pre>` → `"x\ny"`).
- **`<a>` href validation.** Missing, blank, or invalid `href` → drop the link span entirely, keep the inner text, emit `DroppedAttribute(tag = "a", attr = "href", ...)`. Bare-domain URLs are normalized via `LinkUrlPolicy` before becoming `SpanStyle.Link`.
- **`<mark>` defaults.** No `data-cascade-highlight` → default yellow `0xFFFF_FF00` (`HtmlProfile.DEFAULT_HIGHLIGHT_COLOR_ARGB`). Malformed value (not 8 hex digits) → default yellow + `InvalidAttribute` warning.

**Normalization And Lists**

- **Numeric list numbering.** Decoded `NumberedList(number)` values are *not* trusted from the input. `HtmlDecodeEngine` runs `renumberNumberedLists(...)` after every decode; numbers are derived from depth-aware position. `HtmlProfileSupportSet.supportsDocument` rejects documents whose numbering would change under that pass, so the round-trip property only operates on already-normalized documents.
- **Indentation outline.** Same story: decoded outlines normalize through `normalizeIndentationOutline(...)`. Profiles do not get to bypass that. Free / skipped depths in the editor model are handled by emitting `class="cascade-indent-N"` on `<li>` *only when* nested HTML alone would round-trip to a different depth (`DefaultListOutlineEncoder.appendCascadeIndentationIfNeeded`). Ordinary nested outlines stay canonical.
- **Mixed nested lists in flat dialects.** A dialect profile (e.g. Custom) using two narrow flat list encoders cannot represent a numbered list nested inside a bullet item. Mixed-type adjacent lists become *siblings* on encode; that's a property of the flat dialect, not a bug. The flat profile's `supportSet` excludes such configurations, and the round-trip property test never generates them.

**Encoder And Policy Behavior**

- **Group encoder ownership.** Once a run starts, the active `BlockGroupEncoder` *owns* the contiguous run; other group encoders are not consulted mid-run even if a later block would qualify (`HtmlEncodeEngine.kt:52-67`). On `HtmlEmit.Skip` from a group encoder, each block in the run goes through the *fallback only* — the per-block primary encoder is not retried. This keeps group ownership explicit.
- **Synthetic-paragraph tag.** `HtmlPolicyApplier` wraps inline-at-root runs (under `InlineRoot.WrapInParagraph`) in synthetic `HtmlNode.Element(tag = "p", attrs = emptyMap(), ...)` with source ranges spanning the wrapped run. Consumers that registered a custom `<p>` decoder will receive these too.

## 8. Glossary

- **`Block`** — Editor's atomic content unit (`type: BlockType`, `content: BlockContent`, `attributes: BlockAttributes`, `id: BlockId`). Defined in `core/Block.kt`.
- **`BlockContent.Text` / `BlockContent.Empty` / `BlockContent.Custom`** — Three content shapes. HTML codec encodes `Text` inline, treats `Empty` as empty, and either dispatches `Custom` to a registered custom encoder by `typeId` or emits empty.
- **`BlockGroupEncoder`** — Encoder for a contiguous run of consecutive blocks sharing a `groupKey`. Used for HTML structures that span multiple editor blocks (lists, indentation outlines).
- **`BlockSeparator`** — Parser policy controlling whether root-level `\n` separates blocks. Values: `Newline`, `BlockTags` (default).
- **`cascade-indent-N`** — Default profile's class marker for free/skipped indentation depths. Emitted on `<li>` only when nested HTML alone would decode to a different depth, and on indented `<p>` for non-list blocks. See `CASCADE_INDENT_CLASS_PREFIX`.
- **`charOffset`** — UTF-16 code-unit offset into the original `rawSource`; carried by every `HtmlDecodeWarning` and exposed on `TagDecodeContext.charOffset`.
- **`EntityDecode`** — Parser policy for HTML entity references. `Standard` decodes `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`, numeric (`&#NN;`, `&#xHH;`), and a small named set (`&nbsp;` and friends). `None` passes them through verbatim.
- **`HtmlEmit`** — Encoder return type. `Raw(html)` emits a complete fragment; `Skip` defers to fallback.
- **`HtmlNode` / `HtmlNodeView`** — Internal AST and its public projection. Both expose `sourceStart` / `sourceEndExclusive` UTF-16 half-open offsets.
- **`HtmlProfile`** — Immutable configuration bundle that drives encode + decode. `HtmlProfile.Default` is the shipped HTML5-ish profile.
- **`HtmlProfileSupportSet`** — Predicate trio (`supportsBlock`, `supportsSpan`, `supportsDocument`) declaring which inputs a profile commits to round-tripping. Lives in `HtmlProfileSupportSet.kt`.
- **`InlineFragment`** — `(text, spans)` pair returned by `TagDecodeContext.decodeInline` / `collectInlineText`. Spans use coordinates relative to `text`.
- **`InlineRoot`** — Parser policy for inline content at document root. `Drop` (default) emits `DroppedContent` warnings; `WrapInParagraph` wraps in synthetic `<p>` elements.
- **`listOutline`** — Name of the default profile's single `BlockGroupEncoder` that handles both bullet and numbered lists in one nested outline. `withoutBlockGroupEncoder("listOutline")` removes it.
- **`PreservedHtmlBlockType`** — Internal `CustomBlockType` (`typeId = "html.preserved"`) used by `UnknownTagPolicy.Preserve` to carry block-level unknown elements. Payload: `BlockContent.Custom(typeId = "html.preserved", data = mapOf("tagName" to ..., "rawHtml" to rawSlice))`.
- **`rawSliceOf(node)` / `rawSource`** — Way for a tag decoder to read the verbatim original input slice (untouched by lowercasing or entity decoding). The substrate for `UnknownTagPolicy.Preserve` and accurate dialect-specific parsing.
- **`SpanStyle` / `TextSpan`** — Inline rich-text styling. `TextSpan(start, end, style)` uses half-open coordinates relative to the surrounding text.
- **`SupportSetBlockGenerator`** — Test-only deterministic generator (`editor/src/commonTest/.../htmlserialization/SupportSetBlockGenerator.kt`) that produces editor-normalized documents inside a target `HtmlProfileSupportSet`. Used for the round-trip property test.
- **`TagDecodeContext`** — Decoder-side context: `decodeInline`, `decodeBlocks`, `collectInlineText`, `rawSliceOf`, `tagDecoderFor`, `warn`, plus `isBlockContext` / `parentTag` / `rawSource` / `charOffset`.
- **`TagDecoder`** — `fun interface` `decode(ctx, attrs, children) -> TagDecodeResult`. The unit of decode dispatch; one per tag.
- **`TagDecodeResult`** — Sealed result type: `AsBlock`, `AsBlocks`, `AsText`, `Drop`. Block-shaped results in inline context are flattened with a `BlockInInlineContext` warning.
- **`UnknownTagPolicy`** — Profile-level switch for tags with no registered decoder. Values: `Strip`, `WarnAndStrip` (default), `Preserve` (block-only lossless), `Custom(handler)`.
