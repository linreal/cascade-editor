# Preview Mode

Use this recipe for a grid or feed of static document summaries where selecting
a card opens the same document in a full editor.

## Integration contract

- Store one canonical document JSON payload per stable document ID.
- Decode outside item composition and keep the last valid snapshot when a reload
  fails.
- Let the outer grid own scrolling, sizing, taps, and navigation.
- Use bounded previews for collections; do not use a read-only editor as a card.
- After editing, persist first and publish the new snapshot only after the write
  succeeds.
- Preview mode is experimental. Keep its use behind a small host adapter.

## Compose Multiplatform

1. Decode repository JSON into immutable `List<Block>` values before rendering.
   Keep both `json` and `blocks` in the screen model so the editor and preview
   share one persistence format.

2. Hoist the registry, theme, and localization objects once, then render cards
   with stable keys:

```kotlin
@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
fun PreviewGrid(documents: List<PreviewDocument>, onEdit: (String) -> Unit) {
    val registry = remember { createEditorRegistry() }
    val theme = remember { CascadeEditorTheme.light() }
    val strings = remember { CascadeEditorStrings.default() }
    val blockStrings = remember { CascadeEditorBlockStrings.default() }

    LazyVerticalGrid(columns = GridCells.Adaptive(260.dp)) {
        items(documents, key = { it.id }) { document ->
            Box(Modifier.clickable { onEdit(document.id) }) {
                CascadeDocumentPreview(
                    blocks = document.blocks,
                    registry = registry,
                    theme = theme,
                    strings = strings,
                    blockStrings = blockStrings,
                    config = CascadeDocumentPreviewConfig.GridCard.copy(
                        textScale = 0.8f,
                        linksEnabled = false,
                    ),
                    onOpenLink = null,
                )
            }
        }
    }
}
```

3. Navigate by document ID. Create editor state only on the destination screen,
   load that document's JSON, and save the editor export back through the
   repository. When the repository publishes new `blocks`, the keyed card
   refreshes without replacing the grid. Replace only the edited entry so the
   other `blocks` lists keep their identity and their cards do not recompose.

   Register a `BlockTypeCodec` for every custom block type the grid renders.
   Without one, a custom block decodes as `UnknownBlockType` after the first
   save and silently drops to the generic fallback.

If links must work inside a card, enable `linksEnabled`, provide `onOpenLink`,
and explicitly arbitrate link taps against card navigation. Register custom
preview renderers with `registerPreviewRenderer`; editor renderers are a
separate channel.

The multiplatform setting is `textScale: Float = 1f`. For compact two-column
cards, start with `0.8f`. It multiplies the host font scale for every `sp`
inside the preview, including custom renderers. It does not scale `dp` spacing
or explicit host bounds; text layout remeasures normally.

## Native iOS (Swift/SwiftUI)

1. Keep seeded and user-edited JSON in an app-owned `ObservableObject` store.
   Publish a document update only after its atomic file write succeeds.

2. Give each live card model one retained preview controller. Load JSON
   explicitly so malformed storage is observable:

```swift
let configuration = CascadeDocumentPreviewConfiguration(
    maxBlocks: 4,
    maxLinesPerTextBlock: 3,
    textScale: 0.8,
    textSelectionEnabled: false,
    linksEnabled: false,
    isDark: isDark,
    crashPolicy: .containAndReport
)
let controller = CascadeDocumentPreviewController(
    configuration: configuration
)
let result = controller.loadJson(json: document.json)
guard result.success else {
    // Surface or recover the malformed stored document.
    return
}
```

3. Wrap `controller.makeViewController()` in
   `UIViewControllerRepresentable`. Create it only in
   `makeUIViewController`; update the retained controller from the card model
   instead of remounting Compose. Give the host a bounded frame and disable its
   hit testing when the outer card owns taps.

4. Navigate by stable document ID to a separate `CascadeEditorController`.
   Debounce ordinary exports, synchronously flush pending edits when the scene
   leaves `.active` and before Back, and keep the editor open if persistence
   fails. A successful store publication should call `loadJson` on the retained
   card controller.

Keep `onOpenLink` nil when the card owns navigation. Native custom editor views
do not cross into preview mode; unknown/custom JSON uses the bounded generic
fallback. The Swift/native setting is `textScale: Double = 1.0`. Invalid values
(non-finite, non-positive, or non-positive/non-finite after conversion to the
core `Float`) normalize to `1.0`.

Reference implementations:

- Multiplatform: `sample/src/commonMain/kotlin/io/github/linreal/cascade/screens/PreviewGalleryScreen.kt`
  and `sample/src/commonMain/kotlin/io/github/linreal/cascade/screens/preview/`
- Native iOS: `iosNativeSample/iosNativeSample/Screens/PreviewGalleryScreen.swift`
  and `iosNativeSample/iosNativeSample/Screens/Preview/`
- Full behavior and extension contract: `docs/DocumentPreview.md`
