# Markdown Persistence

Use this recipe to store app-owned notes as readable Markdown while editing
them with CascadeEditor. The native iOS bridge uses the default experimental
Markdown profile; use the core analyze/raw-fallback workflow for arbitrary
externally authored files.

## Integration contract

- Treat `MarkdownProfile` as part of the storage format and keep it unchanged
  for a document session.
- Keep the original source and persistence revision until a save succeeds.
- Persist only a successful encode whose warnings contain no
  `MarkdownFidelityImpact.DataLoss` or `Fatal`.
- An aborted load leaves the current editor or preview untouched. A completed
  decode can still carry `DataLoss` warnings.
- Native custom blocks and spans are not automatically part of the default
  Markdown profile. Reject a lossy export instead of silently discarding their
  payloads.
- The Markdown API is experimental. Isolate it behind an app-owned persistence
  adapter so a future profile or syntax migration has one boundary.

## Compose Multiplatform

For app-owned fields that store only Cascade's canonical output, load through
the editor extension and save through the report-bearing export:

```kotlin
@OptIn(ExperimentalCascadeMarkdownApi::class)
fun loadCanonicalMarkdown(
    source: String,
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
): MarkdownDecodeResult =
    stateHolder.loadFromMarkdown(source, textStates, spanStates)

@OptIn(ExperimentalCascadeMarkdownApi::class)
fun encodeForSave(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
): String? {
    val result = stateHolder.toMarkdownWithReport(textStates, spanStates)
    val unsafe = result.warnings.any { warning ->
        warning.impact == MarkdownFidelityImpact.DataLoss ||
            warning.impact == MarkdownFidelityImpact.Fatal
    }
    return result.markdown?.takeUnless { unsafe }
}
```

For externally authored Markdown, call `MarkdownSchema.analyze` before
mounting the native editor model. Open raw source editing when
`report.recommendation` is `RawFallback`; otherwise apply the report's
successful decode result with `applyMarkdownDecodeResult`. Do not decode first
and inspect warnings after replacement when the original source must remain
available character-for-character.

## Native iOS (Swift)

`CascadeEditorController` exposes the default profile through
`loadMarkdown(markdown:)`, `exportMarkdown()`, and
`exportMarkdownWithReport()`. The convenience export discards diagnostics;
persistence code should use the report:

```swift
let load = controller.loadMarkdown(markdown: storedMarkdown)
guard load.success, !load.hasDataLoss else {
    // Keep storedMarkdown; discard/reload this controller before editing.
    return
}

controller.onDocumentChanged = {
    let export = controller.exportMarkdownWithReport()
    guard export.success,
          !export.hasDataLoss,
          let markdown = export.markdown else {
        // Keep the editor open and surface export.warningMessages.
        return
    }
    saveAtomically(markdown)
}
```

Both load and export diagnostics prefix each message with its fidelity impact.
Use `hasDataLoss` for control flow rather than parsing those strings. A
successful `loadMarkdown` is a hard document replacement: it resets focus,
selection, transient editor state, and undo/redo history, then follows the same
mounted/unmounted change-notification contract as `loadJson`.

The native facade deliberately exposes only the default profile and does not
yet expose `MarkdownSchema.analyze`. Use this path for app-owned canonical
Markdown. If users can edit the files in other tools, keep a raw-source fallback
outside Cascade and preflight the source before calling `loadMarkdown`.

For grids, a retained `CascadeDocumentPreviewController` can decode the same
stored bytes:

```swift
let result = previewController.loadMarkdown(markdown: storedMarkdown)
guard result.success else {
    // The last valid preview remains mounted after an aborted decode.
    return
}
```

## Verification

- Round-trip headings, lists, links, underline, and highlight through the exact
  profile used in production.
- Export a native custom span or block and confirm `hasDataLoss` prevents the
  write.
- Feed an over-limit payload to editor and preview controllers and confirm the
  current document/snapshot remains unchanged.
- Run
  `./gradlew :editor-ios-sdk:iosSimulatorArm64Test :editor-ios-sdk:apiCheck`.

Reference implementations and deeper context:

- Core codec contract: `docs/MarkdownSerialization.md`
- Native controller bridge:
  `editor-ios-sdk/src/iosMain/kotlin/io/github/linreal/cascade/ios/controller/CascadeEditorController.kt`
- Native preview bridge:
  `editor-ios-sdk/src/iosMain/kotlin/io/github/linreal/cascade/ios/preview/CascadeDocumentPreviewController.kt`
- Native bridge tests:
  `editor-ios-sdk/src/iosSimulatorArm64Test/kotlin/io/github/linreal/cascade/ios/controller/CascadeEditorControllerTest.kt`
  and
  `editor-ios-sdk/src/iosSimulatorArm64Test/kotlin/io/github/linreal/cascade/ios/preview/CascadeDocumentPreviewControllerTest.kt`
