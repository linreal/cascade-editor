# Document Preview

`CascadeDocumentPreview` is an experimental, stateless renderer for bounded
document summaries in cards, feeds, search results, and grids. It renders an
immutable `List<Block>` with static Compose content and no editor state holder,
text fields, focus, history, toolbar, drag system, or internal scrolling.

Opt in while the API is experimental:

```kotlin
@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
fun NotePreview(blocks: List<Block>, registry: BlockRegistry) {
    CascadeDocumentPreview(
        blocks = blocks,
        registry = registry,
        config = CascadeDocumentPreviewConfig.GridCard,
    )
}
```

## Preview or read-only editor?

The two surfaces solve different problems.

| | `CascadeDocumentPreview` | `CascadeEditor(readOnly = true)` |
|---|---|---|
| Input | Immutable `List<Block>` | `EditorStateHolder` and editor runtime state |
| Text | Static `BasicText` | Read-only editor text fields |
| Scrolling | None; the host owns scrolling | Editor-owned document scrolling |
| Selection | Off by default; optional Compose selection | Reader-oriented selection/copy |
| Focus and IME | No editor text focus or IME; links keep normal accessibility focus | Editor focus infrastructure remains available |
| Transition to editing | Mount the editor separately | Can change editor configuration in place |
| Intended use | Bounded summaries and card content | Full read-only document viewing |

Neither surface is an authorization boundary. Enforce document permissions in
application state, persistence, and synchronization code.

## Lists and grids

Create one registry outside the item lambda and reuse it for every preview:

```kotlin
@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
fun NoteGrid(
    notes: List<NotePreviewModel>,
    onOpenLink: (String) -> Unit,
) {
    val previewRegistry = remember { createEditorRegistry() }
    val previewTheme = remember { CascadeEditorTheme.light() }
    val previewStrings = remember { CascadeEditorStrings.default() }
    val previewBlockStrings = remember { CascadeEditorBlockStrings.default() }

    LazyVerticalGrid(columns = GridCells.Adaptive(240.dp)) {
        items(notes, key = { it.id }) { note ->
            CascadeDocumentPreview(
                blocks = note.blocks,
                registry = previewRegistry,
                theme = previewTheme,
                strings = previewStrings,
                blockStrings = previewBlockStrings,
                config = CascadeDocumentPreviewConfig.GridCard.copy(
                    textScale = 0.8f,
                ),
                modifier = Modifier.fillMaxWidth(),
                onOpenLink = onOpenLink,
            )
        }
    }
}
```

The preview uses a non-scrollable `Column`; the surrounding list or grid owns
vertical scrolling and card sizing. Hoist the registry, theme, and localization
objects once for a large collection, as above.

Configure a registry on the UI thread before sharing it. A single
`BlockRegistry` may hold both editor and preview renderers, but they remain
separate channels.

## Swift and UIKit

The Native iOS SDK exposes the same bounded preview through
`CascadeDocumentPreviewController`. This native facade is also experimental;
its source and binary API may change before preview mode is stabilized. It
accepts document JSON through `loadJson`, owns only decoded immutable blocks
plus preview presentation state, and returns a transparent, non-scrolling
`UIViewController` from `makeViewController()`. It does not create a
`CascadeEditorController`, editor state holder, runtime text/span state,
history, focus, slash commands, drag behavior, or editor mutation callbacks.

`CascadeDocumentPreviewConfiguration` provides Swift-friendly bounded policy:
`maxBlocks`, `maxLinesPerTextBlock`, `textScale`, `textSelectionEnabled`,
`linksEnabled`, `isDark`, and `crashPolicy`. The defaults match the grid-card
policy: four blocks, three lines, `1.0` text scale, selection disabled, links
enabled, light mode, and `containAndReport`. Native limits are non-null; values
below `1` are normalized to `1` before reaching the core preview. Invalid text
scales (non-finite, non-positive, or non-positive/non-finite after conversion
to the core `Float`) normalize to `1.0`.

Create and retain one controller and one returned view controller for each live
UIKit or SwiftUI cell. Supply a bounded frame, update the existing controller
with `loadJson(...)`, `updateConfiguration(...)`, `setDarkMode(...)`, or
`setColors(...)`, and release it when the cell leaves the collection. The outer
collection owns scrolling, stable item identity, reuse, and navigation.

```swift
let controller = CascadeDocumentPreviewController(
    configuration: CascadeDocumentPreviewConfiguration(
        maxBlocks: 4, maxLinesPerTextBlock: 3,
        textScale: 0.8,
        textSelectionEnabled: false, linksEnabled: false,
        isDark: isDark, crashPolicy: .containAndReport
    )
)
let loadResult = controller.loadJson(json: document.json)
guard loadResult.success else {
    // Surface or recover the malformed stored document.
    return
}
// theme is the host-resolved family and light/dark variant.
controller.setColors(colors: theme.editorColors)
let previewViewController = controller.makeViewController()
```

A successful `loadJson(json:)` replaces the immutable preview snapshot and
returns any non-fatal decode warnings. A parse failure returns `success = false`
and preserves the preview already on screen. `onInternalError` receives
contained preview errors and main-thread misuse. `onOpenLink` is the only link
opening seam; without that callback, links are visual-only.

`setColors(colors:)` uses the same complete `CascadeEditorColors` bridge as the
editable controller and snapshots values immediately. Later mutations to the
bag do not affect the mounted preview until `setColors` is called again. The
custom palette survives `setDarkMode`; call both methods when the host changes
a named theme or light/dark appearance. `clearCustomColors()` returns to the
built-in preset selected by `isDark`.

The native sample is an integration reference rather than a second editor
implementation:

- `CascadeDocumentPreviewHost` embeds the controller with
  `UIViewControllerRepresentable`;
- `PreviewGalleryScreen` supplies the `LazyVGrid`, bounded card frames, stable
  models, and card-level navigation;
- `PreviewDocumentLibrary` copies bundled JSON fixtures into app-owned storage;
- selecting a card mounts a separate `CascadeEditorController` in
  `PreviewDocumentEditorScreen`;
- debounced editor exports update the library, pending edits are synchronously
  flushed before backgrounding or leaving, and a failed Back-button flush keeps
  the editor open;
- each successful save updates the library, then the retained card controller
  reloads that JSON and refreshes its static preview.

The sample disables preview links and hit testing inside each card so the outer
button owns the complete tap target. A host that wants interactive links can
enable them and install `onOpenLink`, but must define how link taps arbitrate
with card navigation.

Native custom editor registrations are intentionally isolated from preview
mode. The facade preserves unknown block types and opaque custom content during
JSON decode, then uses the bounded generic preview fallback. It never mounts
native UIKit/SwiftUI editor renderers. There is no Swift custom
preview-provider registration API yet.

## Configuration

`CascadeDocumentPreviewConfig.Default` is the same instance as `GridCard`:

- at most 4 blocks;
- at most 3 lines per text block;
- `1f` text scale;
- ellipsis overflow;
- text selection disabled;
- built-in link annotations and scope link delegation enabled;
- `CrashPolicy.ContainAndReport`.

Both limits accept only positive values. A `null` limit means that dimension is
unbounded. `textScale` must be finite and greater than zero. Prefer the named
preset when removing both bounds:

```kotlin
CascadeDocumentPreview(
    blocks = smallStaticDocument,
    config = CascadeDocumentPreviewConfig.Unbounded,
)
```

`Unbounded` is still non-scrollable and composes every supplied block eagerly.
It is intended only for known-small static documents, not list or grid cells.
Use `GridCard.copy(...)` to tune one policy while preserving the others:

```kotlin
val searchResultConfig = CascadeDocumentPreviewConfig.GridCard.copy(
    maxBlocks = 2,
    maxLinesPerTextBlock = 2,
    textScale = 0.8f,
    textSelectionEnabled = false,
)
```

`textScale: Float = 1f` multiplies the host font scale for every `sp` rendered
inside the preview, including text from custom preview renderers. It does not
scale `dp`-based padding, indentation, spacing, or explicit host-imposed card
bounds. Text remeasures normally, so wrapping and intrinsic preview height can
change. A value near `0.8f` is a practical starting point for two-column grids
while preserving the typographic hierarchy.

Limits are presentation-only. Preview never truncates the stored model,
renumbers lists, normalizes indentation, or appends the editor's trailing empty
paragraph.

## Immutable input and projection

Supply immutable block snapshots. Keep decoding and projection outside preview
composition:

- decode JSON, HTML, or Markdown in a repository, view model, or other data
  boundary;
- cache the resulting `List<Block>` in the screen's state model;
- pass stable document and item identities to the lazy container;
- do not parse persisted content inside a grid item lambda.

If the source is a mounted editor, project live text and spans with
`EditorStateHolder.resolveDocumentBlocks(textStates, spanStates)` at a
controlled synchronization boundary, then publish that result as preview
state. `CascadeDocumentPreview` intentionally does not observe
`BlockTextStates` or `BlockSpanStates`.

The component retains the immutable input list rather than copying the full
document. It selects only the configured prefix for bounded rendering. Custom
scope lookup builds its block-ID index lazily; duplicate IDs resolve to the
first source-order block.

## Built-in rendering

`createEditorRegistry()` includes dedicated preview renderers for every built-in
block type:

- paragraphs and headings use the matching Cascade typography;
- bullet and numbered lists preserve static indentation and prefix layout;
- quotes share the editor's quote chrome and italic body treatment;
- code blocks preserve line breaks, use monospace/code chrome, and deliberately
  ignore rich-text spans;
- todos render a static checked or unchecked indicator; checked text retains
  its base strikethrough;
- dividers reuse the editor's visible divider geometry.

Text blocks use `BasicText` and a static `AnnotatedString`. Supported rich-text
styles share the editor's visual span mapping, and the preview introduces no
editor sentinel character. Built-in preview rendering creates no editable text
semantics, editor text-focus target, pointer-input editor behavior, or
animation state. Enabled link annotations retain their normal accessibility
and keyboard focus semantics without focusing text or opening the IME.

## Links and selection

Stored link spans receive the normal link color and underline. When
`linksEnabled` is true and the host supplies `onOpenLink`, built-in preview text
also installs Compose clickable link annotations. Activating a valid link trims
surrounding whitespace, rejects a blank target, and invokes the host callback
exactly once. Preview never delegates to `LocalUriHandler` or another implicit
platform opener.

When `linksEnabled` is false, built-in text retains link styling but installs no
link click semantics, and `BlockPreviewScope.openLink(target)` becomes a no-op.
Omitting `onOpenLink` also leaves built-in links visual-only. The link text does
not install a dead clickable overlay, so a surrounding card can continue to own
ordinary navigation.

The same policy-aware `BlockPreviewScope.openLink(target)` seam is available to
custom preview renderers. It applies the stored-target validation above before
delegating to the host. A custom renderer must attach its own deliberate link
interaction and account for any surrounding card click behavior.

Selection is disabled by default so a card can own normal pointer interaction.
Setting `textSelectionEnabled = true` wraps the preview in a Compose
`SelectionContainer`; it does not introduce a text field, focus, document
mutation, or an editor sentinel. Selection handles, cross-block selection, and
clipboard behavior can vary by platform and should be verified on every
supported target.

Line limits are visual measurement limits. Ellipsized text may still expose its
full source text through semantics; do not treat visual ellipsis as data
redaction.

## Custom preview renderers

Editor renderers are never invoked automatically in preview mode. Register a
separate stateless renderer for each custom type that needs a tailored summary:

```kotlin
@OptIn(ExperimentalCascadePreviewApi::class)
public object CalloutPreviewRenderer : BlockPreviewRenderer<CalloutBlock> {
    @Composable
    override fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    ) {
        val text = (block.content as? BlockContent.Text)?.text.orEmpty()
        BasicText(
            text = text,
            modifier = modifier,
            maxLines = scope.config.maxLinesPerTextBlock ?: Int.MAX_VALUE,
            overflow = scope.config.textOverflow,
        )
    }
}

@OptIn(ExperimentalCascadePreviewApi::class)
fun createPreviewRegistry(): BlockRegistry {
    return createEditorRegistry().apply {
        registerPreviewRenderer(CalloutBlock.typeId, CalloutPreviewRenderer)
    }
}
```

`BlockPreviewScope` exposes only the original ordered blocks, current preview
configuration, block lookup, `canOpenLinks`, and policy-aware link opening.
Custom renderers should install clickable link semantics only when
`canOpenLinks` is true. The scope has no editor actions, mutation callbacks,
editor focus, history, text/span state, or mutation surface.

Custom renderer text expressed in `sp` automatically follows
`scope.config.textScale` through the preview-local font scale; do not multiply
font sizes by the value again. `dp` tokens are not scaled directly, though text
remeasurement may affect the surrounding layout.

For media or platform-view custom blocks, prefer a cached thumbnail or bounded
placeholder. A preview renderer should not mount another editor or heavy
controller; the application owns media loading, lifecycle, and caching.

## Missing-renderer fallback

When no preview renderer is registered, the registry's preview fallback is
used. The built-in fallback:

- preserves nonblank plain-text content as an excerpt of at most three lines;
- shows a localized unsupported-block label for empty or opaque custom data;
- never inspects arbitrary custom payloads;
- never invokes an editor `BlockRenderer` or `ScopedBlockRenderer`.

Registering only an editor renderer therefore does not change preview behavior.
Use `setUnknownBlockPreviewRenderer(...)` to replace the fallback for a
product-specific registry.

## Blocks without text content

`DocumentSchema` decodes a block whose JSON omits `content` as
`BlockContent.Empty`, with no warning and regardless of block type, so a
text block or todo can reach preview rendering without `BlockContent.Text`.

The built-in text and todo preview renderers present those blocks as an empty
line — a todo keeps its checkbox — rather than rendering nothing. A block that
consumes one of the `maxBlocks` slots always occupies that slot, so a bounded
card cannot silently shrink. Custom preview renderers should apply the same
rule; returning early on a content type mismatch leaves an invisible gap.

## Accessibility

Built-in preview text is static and non-editable. Todos expose checkbox role and
checked state without a click or toggle action. Unsupported blocks expose a
localized label, and the preview root exposes no vertical-scroll action.

Custom renderers own their semantics. Give thumbnails and placeholders useful
labels, expose state without fake disabled controls, and keep interactive
elements explicit. Verify selection, clipboard, and screen-reader behavior on
Android, iOS, desktop, and wasm before promising identical cross-platform
behavior.

## Crash containment

The default `CrashPolicy.ContainAndReport` applies a per-block containment
modifier around preview measurement, placement, and drawing. A contained
failure is reported with preview/block context through `onInternalError` at
most once per mounted renderer; later layout/draw passes skip that failed
renderer instead of repeatedly throwing. Reporter failures are also contained.
`CrashPolicy.Rethrow` is useful in tests and debug builds.

Compose does not support a reliable `try/catch` boundary around a composable
invocation. A custom renderer that throws during composition can still fail the
host composition and remains a trusted-code boundary. The current containment
guarantee is specifically for measurement, placement, and drawing; the
missing-renderer fallback does not turn arbitrary composition exceptions into
safe UI.

## Current limitations

- The API is experimental and may change before stabilization.
- Preview is not a full-document reader, scroll container, permission boundary,
  or semantic summary generator.
- `Unbounded` composes eagerly and is unsuitable for unknown-size grid content.
- Link navigation requires an explicit `onOpenLink` callback; preview has no
  implicit platform URI opener.
- Cross-platform selection and clipboard details require host QA.
- Custom blocks need dedicated preview renderers for product-specific fidelity.
- Remote images, embeds, media lifecycle, and caching are application concerns.
- The Swift/UIKit facade currently has no native custom preview-provider API;
  custom editor renderers never mount and unknown/custom blocks use the generic
  bounded fallback.

The architecture removes editor runtime systems from preview composition, but
this document makes no frame-time, allocation, memory, scrolling, or
performance-multiplier claim for Compose or the native iOS facade. Publish such
claims only with repeatable release benchmarks, representative documents, and
recorded device/target details.
