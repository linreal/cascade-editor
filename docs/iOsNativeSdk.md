# Native iOS SDK Technical Context

## 1. Feature Overview

The Native iOS SDK exposes CascadeEditor to Swift applications without requiring consumers to use the Kotlin Multiplatform editor API, Compose types, or internal state holders directly. It packages the editor as a dynamic `CascadeEditor.xcframework`, provides a `CascadeEditorController` for document and editing operations, and hosts the Compose editor in a UIKit `UIViewController` that can be embedded in SwiftUI. For collection surfaces, `CascadeDocumentPreviewController` hosts the bounded static document preview without creating an editor runtime. The bridge also supports native UIKit/SwiftUI custom editor blocks, native slash commands, localization, complete runtime color palettes, runtime configuration, document import/export, and app-owned formatting chrome. A native Swift sample demonstrates persisted editing, a preview grid whose cards open editable documents, a rich-text comments composer, custom-block integrations, and live family-aware theme switching while sharing the same serialized document format as the existing KMP sample.

## 2. Architecture & Design Decisions

### Module and framework boundary

The feature introduces the `:editor-ios-sdk` Kotlin Multiplatform module with iOS device (`iosArm64`) and Apple Silicon simulator (`iosSimulatorArm64`) targets. It builds a dynamic framework named `CascadeEditor`; the lower-level `:editor` framework was renamed to `CascadeEditorCore` to avoid a framework-name collision. The SDK depends on `:editor` with `implementation`, rather than exporting it, which keeps Compose, Skiko, `EditorStateHolder`, and other core implementation types out of Swift-facing signatures.

Public bridge types use `@ObjCName(..., exact = true)` so generated Objective-C/Swift names remain deliberate. Binary Compatibility Validator snapshots guard the Kotlin public surface of both `:editor` and `:editor-ios-sdk`, and CI now runs the SDK tests, API check, and XCFramework assembly on macOS.

The dynamic framework owns its Compose resources. The editor subtree provides a
`ResourceReader` bound to bundle ID `io.github.linreal.cascade.editor`, avoiding
Compose's first-matching-framework lookup when a host embeds multiple Compose
SDKs. `PrivacyInfo.xcprivacy`, dependency notices, and matching dSYMs are
included in every release slice. Framework version metadata and
`CascadeEditorSdk.version` are generated from the shared Gradle
`VERSION_NAME`.

### Controller facade

`CascadeEditorController` is the primary SDK entry point. It owns one stable set of:

- `EditorStateHolder`
- `BlockTextStates` and `BlockSpanStates`
- `BlockRegistry` seeded with built-in block types
- `SlashCommandRegistry`
- configuration, color-palette, and localization snapshots
- mounted toolbar actions and derived toolbar state

This is a facade pattern: Swift sees curated value objects and controller methods, while the controller translates them into core editor operations. The owned registries are intentionally stable for the controller lifetime, allowing custom blocks and slash commands to be registered before or after the editor is mounted.

### Compose-to-UIKit host

`CascadeEditorController.makeViewController()` creates a `ComposeUIViewController` and mounts `CascadeEditor` with the controller-owned state, runtime holders, and registries. It maps `CascadeEditorConfiguration` to `CascadeEditorConfig`, theme, toolbar, link popup, and slash-command slots. A Swift-supplied color snapshot replaces all core `CascadeEditorColors` slots while retaining the preset typography and dimensions; without a custom snapshot, `isDark` selects the built-in light/dark theme. The host is created non-opaque (`opaque = false`): `CascadeEditor` paints no canvas background, so the native screen background behind the view controller shows through the editor.

The host uses Compose snapshot state and `snapshotFlow` for two bridge streams:

1. formatting, indentation, and link state are mapped into `CascadeToolbarState`;
2. editor state plus authoritative document blocks are observed for `onStateChanged` and `onDocumentChanged` callbacks.

Document observation uses `EditorStateHolder.resolveDocumentBlocks(...)`, which folds live text and spans into snapshot blocks without serializing JSON on every edit. A per-composition mount token prevents an old view controller from clearing or publishing bridge state when UIKit/SwiftUI transitions temporarily overlap two hosts created from the same controller.

### Document preview facade

`CascadeDocumentPreviewController` is the Swift/UIKit entry point for bounded
document summaries. The facade is experimental and its source and binary API
may change before preview mode is stabilized. It owns decoded immutable blocks, a
`CascadeDocumentPreviewConfiguration` snapshot, core localization defaults, and
a dedicated registry seeded with built-in preview renderers. It does not create
`CascadeEditorController`, `EditorStateHolder`, text/span runtime holders,
history, focus, slash-command, drag, or editor mutation infrastructure.

`makeViewController()` creates a transparent, non-scrolling
`ComposeUIViewController` containing `CascadeDocumentPreview`. The native owner
must give that controller bounded width and height. A reusable list or grid cell
should retain one preview controller and its returned view controller for the
cell lifetime, update it through `loadJson(...)` and
`updateConfiguration(...)`, then release both when the cell is dismantled.
Calling `makeViewController()` on every SwiftUI update would remount the Compose
tree and is outside the intended lifecycle.

The configuration's `textScale: Double` defaults to `1.0` and multiplies the
host font scale for every `sp` inside the preview, including custom Compose
preview renderers. It does not scale `dp` geometry or explicit host bounds;
text layout remeasures normally. Invalid native values (non-finite,
non-positive, or non-positive/non-finite after conversion to the core `Float`)
normalize to `1.0`.

The preview facade uses the generic document decoder. A successful JSON load
publishes one new immutable block snapshot; a parse failure returns
`success = false`, reports warning messages, and preserves the currently
displayed snapshot. Unknown block types and opaque custom content remain in the
decoded document and render through the bounded native preview fallback.
Registrations made on `CascadeEditorController` are not shared: native custom
editor renderers never mount in preview cells, and the SDK does not yet expose a
Swift custom preview-provider registration API.

### Native custom-block adapter

`CascadeCustomBlockRegistration` describes a native block's identity, slash-menu metadata, default JSON payload, initial height, and `UIViewController` factory. Registration produces four linked pieces:

- a core `BlockDescriptor` and slash behavior;
- a `NativeCustomBlockType` recognized by the document codec;
- a `NativeCustomBlockRenderer` registered in the core block registry;
- a `CascadeCustomBlockContext` supplied to the native view.

`NativeCustomBlockRenderer` adapts a `UIViewController` into Compose using `UIKitViewController`. Its height is seeded from `estimatedHeight`, retained with `rememberSaveable`, and updated through `CascadeCustomBlockContext.setPreferredHeight(...)`. The context delegates block mutations to `BlockRenderScope`, preserving the editor's read-only policy and structural-history behavior. `onChange` is a pull-based invalidation signal: native views re-read current payload, focus, selection, read-only, and dark-mode values instead of receiving partial deltas.

Native block renderers set `BlockRenderer.supportsDragPreview = false`. The core editor therefore draws a lightweight labeled placeholder during drag instead of creating a second live UIKit controller whose interop view would not follow draw-phase translation.

### Native slash-command adapter

`CascadeSlashCommand` wraps a synchronous Swift handler in the core editor's suspending slash-command contract. The handler receives a short-lived `CascadeSlashCommandContext`, which exposes only anchor-scoped text replacement, block insertion, focus, and menu-close operations. Native commands use `SlashQueryTextPolicy.KeepText`, so the handler owns removal or replacement of the `/query` token.

Command and custom-block registration use last-registration-wins semantics and report collisions through `onInternalError`. The core registries now carry Compose-observable revision counters, so registrations made after mount re-derive built-in items, merged slash registries, and renderer lookups without replacing registry instances.

### Additional core abstractions

The feature promotes or adds the following reusable core APIs:

- `EditorStateHolder.dispatchStructuralAction(...)` is public so SDK and external chrome mutations can create one undoable structural history entry when runtime holders are available.
- `EditorStateHolder.resolveDocumentBlocks(...)` returns authoritative blocks with live runtime text/spans folded in.
- `BlockRenderer.supportsDragPreview` lets platform-view renderers opt out of duplicate live preview composition.
- `builtInBlockSlashCommandId(typeId)` and `BUILTIN_BLOCK_SLASH_COMMAND_ID_PREFIX` expose the stable `builtin.block.<typeId>` identifier scheme for collision detection.

The built-in slash executor also distinguishes text and non-text conversion targets. A blank anchor can be replaced in place by a non-text block; a nonblank anchor keeps its text and receives the new non-text block after it, preventing silent text loss.

## 3. Data Flow

### View creation and state publication

1. Swift creates a `CascadeEditorController`, optionally with initial JSON and configuration.
2. Swift calls `makeViewController()` and caches the returned controller in a `UIViewControllerRepresentable` or UIKit container.
3. The Compose host mounts `CascadeEditor` with controller-owned state, runtime holders, registries, configuration, localization, and callbacks.
4. `rememberCascadeEditorToolbarController(...)` derives formatting, indentation, and link capabilities from current editor state.
5. `snapshotFlow` maps those values to `CascadeToolbarState` and invokes `onToolbarStateChanged` when the value changes.
6. A second `snapshotFlow` compares `EditorState` and `resolveDocumentBlocks(...)` results. State changes invoke `onStateChanged`; content changes invoke both state and document callbacks as applicable.
7. Swift callback handlers re-read pull properties such as `canUndo`, `hasSelection`, and `toolbarState`, then publish them into app-owned observable state.

### User edit to persisted document

1. UIKit events reach the hosted Compose text field or block gesture handlers.
2. Existing editor callbacks dispatch reducer actions or mutate live `BlockTextStates`/`BlockSpanStates` through history-aware integration points.
3. Editor snapshot/runtime state changes and Compose recomposes the affected content.
4. The host's authoritative-block observer detects live text/span or structural changes and invokes `onDocumentChanged`.
5. The Swift app may debounce that signal and call `exportJson()` for persistence.
6. Export synchronously resolves the current document on the main queue and encodes it with `NativeCustomBlockCodec`, preserving registered native custom-block types and payloads.

### Document load

1. Swift passes JSON to `loadJson(...)`.
2. The controller preflights with `DocumentSchema.decodeFromStringWithReport(...)` and the native type codec.
3. A parse failure returns `success = false` with warning strings and leaves the current document and history untouched.
4. A successful decode hard-replaces editor state, clears runtime text/span state and history, and resolves registered custom type IDs to `NativeCustomBlockType`.
5. If mounted, the Compose observer publishes changes; if unmounted, the controller invokes the state/document callbacks directly.

`loadHtml(...)` follows the core default HTML profile, hard-replaces the document, and classifies input-limit or decoder-exception warnings as an unsuccessful load.

### Native preview grid to editor

1. Swift owns a collection of persisted document JSON snapshots and creates one `CascadeDocumentPreviewController` for each visible grid cell.
2. A `UIViewControllerRepresentable` caches the controller returned by `makeViewController()` inside a bounded card; the outer SwiftUI `LazyVGrid` owns scrolling and item identity.
3. Card-level navigation can disable preview links and hit testing so the whole card remains one deliberate tap target.
4. Selecting a card creates a separate `CascadeEditorController`, loads the same JSON, and mounts the full editor screen.
5. The editor exports JSON after edits; the application persists it and republishes the saved snapshot to the grid.
6. The retained preview controller receives the changed JSON through `loadJson(...)`, replacing its immutable snapshot without mounting editor runtime in the grid.

### App-owned toolbar action

1. Swift reads `toolbarState` or receives `onToolbarStateChanged` to determine availability and active/mixed/inactive styles.
2. Swift calls an action such as `toggleBold()`, `indentForward()`, or `applyLink(url:title:)`.
3. The controller forwards to action delegates installed by the mounted `CascadeEditorToolbarController`.
4. Core formatting/link/indentation actions update runtime state, synchronize snapshot content, and capture history according to existing editor rules.
5. Toolbar and document observers publish the new derived state to Swift.

### Native custom-block mutation

1. A registered descriptor decodes or creates a `NativeCustomBlockType` with `BlockContent.Custom` payload data.
2. `NativeCustomBlockRenderer` creates the registered UIKit controller and supplies its block-scoped `CascadeCustomBlockContext`.
3. The native view reads `payloadJson`, selection/focus state, policy flags, and theme state from the context.
4. A context mutation validates the main thread, read-only/capability state, block existence, and JSON-object payload.
5. The context delegates to `BlockRenderScope`; structural mutation updates the document and creates one undo entry when history runtime is bound.
6. Compose observes the changed block. `LaunchedEffect` calls `context.onChange`, and the native view re-reads its context.
7. The view measures its content and reports a preferred height; the renderer clamps and applies the new Compose host height.

### Native slash command

1. Typing `/` opens the merged built-in/native slash registry.
2. Selecting a native item enters the core slash executor with the anchor block and query range.
3. The bridge constructs a temporary `CascadeSlashCommandContext` and synchronously calls the Swift handler.
4. Context operations update anchor text, insert built-in or registered custom blocks, focus the anchor, or close the menu through `SlashCommandEditor`.
5. `done` closes normally, `keepOpen` retains the menu, and `failure(message)` closes and reports the optional message through `onInternalError`.

## 4. Public API Surface

### SDK identity and configuration

- `CascadeEditorSdk.version: String` — currently `"1.0.0"`.
- `CascadeEditorConfiguration` — immutable configuration containing `readOnly`, `toolbarMode`, `slashCommandsEnabled`, `blockSelectionEnabled`, `blockDraggingEnabled`, `blockIndentationEnabled`, `emptyDocumentPlaceholderEnabled`, `isDark`, and `crashPolicy`. The placeholder flag defaults to `false`; when enabled, read-only mode still suppresses the edit-oriented hint.
- `CascadeToolbarMode` — `builtIn` or `none`; `none` disables both the built-in formatting toolbar and its link popup.
- `CascadeCrashPolicy` — `containAndReport` or `rethrow`.

### `CascadeEditorController`

Construction and hosting:

```kotlin
CascadeEditorController()
CascadeEditorController(initialJson: String?)
CascadeEditorController(initialJson: String?, configuration: CascadeEditorConfiguration)
controller.makeViewController(): UIViewController
```

Document APIs:

- `loadJson(json): CascadeDocumentLoadResult`
- `loadHtml(html): CascadeHtmlLoadResult`
- `reset(toJson): CascadeDocumentLoadResult`
- `exportJson(): String`
- `exportHtml(): String`
- `exportPlainText(): String`
- `exportRichText(): CascadeRichTextSnapshot`

Derived state:

- `configuration`, `selectedBlockCount`, `hasSelection`, `toolbarState`, `canUndo`, and `canRedo`.
- `CascadeToolbarState` exposes focus/format availability, tri-state status for six styles, indentation capabilities, link capability, and the existing URL.
- `CascadeStyleState` is `active`, `mixed`, or `inactive`.

Editing commands:

- `focusFirstBlock()`, `clearFocus()`, `clearSelection()`, `deleteSelectedOrFocused()`
- `undo()`, `redo()`
- `toggleBold()`, `toggleItalic()`, `toggleUnderline()`, `toggleStrikeThrough()`, `toggleInlineCode()`, `toggleHighlight(argb)`
- `indentForward()`, `indentBackward()`
- `applyLink(url, title)`, `removeLink()`

`focusFirstBlock()` may be called before or after `makeViewController()`.
Pre-mount focus is retained and enters the normal focus pipeline when the
editor mounts. A text-editable first block then requests the caret and software
keyboard when current editor policy and platform input allow it; non-text or
read-only first blocks receive editor focus without a keyboard guarantee.

Runtime configuration, colors, and localization:

- `updateConfiguration(value)`, `setReadOnly(value)`, `setDarkMode(value)`, `setToolbarMode(value)`, `setSlashCommandsEnabled(value)`
- `setColors(CascadeEditorColors)`, `clearCustomColors()`
- `setLocalization(CascadeEditorLocalization)`
- `CascadeEditorColors` is a mutable, complete 25-slot palette seeded from the built-in light (`init()`) or light/dark (`init(isDark:)`) preset. Every property is a Swift `Int64` containing `0xAARRGGBB`; `placeholderText` controls the empty-document hint.
- `CascadeLocalizedStrings` supplies nullable overrides for built-in UI/accessibility strings, including `emptyDocumentPlaceholder`.
- `CascadeLocalizedBlockStrings` supplies slash-menu name, description, and additive keywords by block type ID.

`setColors` snapshots the whole bag and can be called before or after
`makeViewController()`. Later bag mutations do not affect the editor until
`setColors` is called again. A custom palette remains active when `setDarkMode`
changes because `isDark` is also semantic state exposed to native custom blocks;
apps with named themes should update both values together. `clearCustomColors`
returns color selection to the built-in light/dark presets.

The native sample demonstrates the recommended host integration. It does not
force `preferredColorScheme`; instead it observes SwiftUI's
`@Environment(\.colorScheme)`, resolves the selected theme family's matching
variant, and calls both `setDarkMode` and `setColors` whenever appearance
changes. The SDK remains explicit and host-driven, so apps retain control over
their own theme-family model.

Example Swift runtime theme application:

```swift
let colors = CascadeEditorColors(isDark: theme.isDark)
colors.primary = 0xFF147D64
controller.setDarkMode(value: theme.isDark)
controller.setColors(colors: colors)
```

Callbacks:

- `onDocumentChanged: (() -> Unit)?`
- `onStateChanged: (() -> Unit)?`
- `onInternalError: ((String) -> Unit)?`
- `onOpenLink: ((String) -> Unit)?`
- `onToolbarStateChanged: ((CascadeToolbarState) -> Unit)?`

### Document and rich-text models

- `CascadeDocumentLoadResult(success, warningMessages)`; `CascadeHtmlLoadResult` is a type alias.
- `CascadeEditorDocumentBuilder` fluently adds paragraphs, headings, todos, bullet/numbered lists, quotes, code, dividers, and custom blocks, then emits canonical JSON with `buildJson()`.
- `CascadeRichTextSnapshot` contains document-ordered `CascadeRichTextBlock` values.
- `CascadeRichTextSpan(start, end, kind, argb, url)` exposes supported core span styles through `CascadeSpanKind`.

Example Swift usage:

```swift
let controller = CascadeEditorController()
_ = controller.loadJson(json: storedJson)
let viewController = controller.makeViewController()
controller.onDocumentChanged = { save(controller.exportJson()) }
```

### `CascadeDocumentPreviewController`

Construction and hosting:

```kotlin
CascadeDocumentPreviewController()
CascadeDocumentPreviewController(
    configuration: CascadeDocumentPreviewConfiguration,
)
controller.makeViewController(): UIViewController
```

Presentation and loading:

- `loadJson(json): CascadeDocumentLoadResult` decodes a new immutable preview
  snapshot. Parse failure preserves the previously displayed blocks.
- `configuration` exposes the current presentation snapshot.
- `updateConfiguration(value)` updates all presentation policy at once.
- `setDarkMode(value)` updates only `isDark`.
- `setColors(CascadeEditorColors)` snapshots and applies a complete host
  palette; `clearCustomColors()` resumes the built-in light/dark preset.
- `CascadeDocumentPreviewConfiguration` exposes positive bounded
  `maxBlocks`/`maxLinesPerTextBlock`, `textScale`, `textSelectionEnabled`,
  `linksEnabled`, `isDark`, and `crashPolicy`. Defaults are four blocks, three
  lines, `1.0` text scale, selection off, links enabled, light mode, and
  `containAndReport`. Non-positive limits normalize to `1`; invalid text scales
  (non-finite, non-positive, or non-positive/non-finite after conversion to the
  core `Float`) normalize to `1.0`.

Callbacks:

- `onOpenLink: ((String) -> Unit)?` is the only native preview link-opening
  path. Without it, links remain visual-only.
- `onInternalError: ((String) -> Unit)?` receives main-thread misuse, contained
  renderer failures, and host callback failures. Exceptions thrown by this
  reporter are swallowed at the Swift/Objective-C boundary.

Swift grid integration keeps one controller per live cell:

```swift
let preview = CascadeDocumentPreviewController(
    configuration: CascadeDocumentPreviewConfiguration(
        maxBlocks: 4, maxLinesPerTextBlock: 3,
        textScale: 0.8,
        textSelectionEnabled: false, linksEnabled: false,
        isDark: isDark, crashPolicy: .containAndReport
    )
)
let loadResult = preview.loadJson(json: document.json)
guard loadResult.success else {
    // Surface or recover the malformed stored document.
    return
}
// theme is the host-resolved family and light/dark variant.
preview.setColors(colors: theme.editorColors)
let viewController = preview.makeViewController()
```

The preview and editable controllers accept the same `CascadeEditorColors`
bridge, so a grid card and its destination editor can share one resolved host
palette. Call both `setDarkMode` and `setColors` when a named theme or
appearance changes. Both controllers snapshot the mutable color bag, preserve
the custom palette across `setDarkMode`, and expose `clearCustomColors()` to
return to preset selection.

The sample's `CascadeDocumentPreviewHost` wraps the view controller with
`UIViewControllerRepresentable`. `PreviewGalleryScreen` uses a bounded
`LazyVGrid`; `PreviewDocumentLibrary` seeds and persists document JSON; and
`PreviewDocumentEditorScreen` opens a regular editor, autosaves exported JSON,
flushes pending edits before backgrounding or leaving the screen, and
republishes successful saves so the corresponding preview updates. A failed
Back-button flush keeps the editor open and surfaces the save failure.

### Custom blocks

- `CascadeCustomBlockRegistration(...)` — registers identity, slash metadata/behavior, default JSON-object payload, estimated height, and `(CascadeCustomBlockContext) -> UIViewController` factory.
- `CascadeCustomBlockSlashBehavior` — `insert` or `convertInPlace`.
- `CascadeCustomBlockContext` — live `payloadJson`, focus/selection/read-only/capability/theme properties; merge/replace payload; insert before/after; delete/focus block; report preferred height; receive `onChange` invalidations.
- `CascadeCustomBlockMutationResult` — `success`, `readOnly`, `invalidPayload`, `unknownType`, or `blockUnavailable`.
- `CascadeEditorController.registerBlock(registration)` installs the descriptor, codec recognition, renderer, and slash behavior.

### Slash commands

- `CascadeSlashCommand(id, title, description, keywords, handler)`.
- `CascadeSlashCommandContext` — `replaceQueryText`, `updateAnchorText`, insert before/after anchor, `focusAnchor`, and `closeMenu`.
- `CascadeSlashCommandResult` — shared `done` and `keepOpen` values plus `failure(message)`.
- `CascadeEditorController.registerSlashCommand(command)` installs or replaces a root command.

### New `:editor` public APIs

- `EditorStateHolder.dispatchStructuralAction(action, textStates?, spanStates?)`
- `EditorStateHolder.resolveDocumentBlocks(textStates, spanStates): List<Block>`
- `BlockRenderer.supportsDragPreview: Boolean`
- `BUILTIN_BLOCK_SLASH_COMMAND_ID_PREFIX: String`
- `builtInBlockSlashCommandId(typeId): SlashCommandId`

## 5. Integration Points

- **Core editor state and history:** the controller owns `EditorStateHolder`, `BlockTextStates`, and `BlockSpanStates`; toolbar and custom-block operations reuse core history-aware actions.
- **Compose UI:** `ComposeUIViewController` hosts the existing `CascadeEditor`, and Compose snapshot state drives all bridge callbacks.
- **UIKit and SwiftUI:** both editor and preview facades return `UIViewController`; the sample embeds them with separate `UIViewControllerRepresentable` adapters. Custom editor blocks also return `UIViewController`, allowing `UIHostingController`-backed SwiftUI content.
- **Registry system:** native blocks extend `BlockRegistry`; native commands extend `SlashCommandRegistry`. Observable revisions make runtime registration visible to an already-mounted editor.
- **Serialization:** editable JSON uses `DocumentSchema` plus `NativeCustomBlockCodec`; HTML uses `HtmlProfile.Default`. Both share the editor's live text/span holders. The preview facade uses `DocumentSchema` directly to preserve generic unknown/custom data without creating runtime holders or resolving native editor types.
- **Rich text and toolbar:** the bridge maps `FormattingState`, `IndentationState`, and `LinkState` from `CascadeEditorToolbarController` into Swift-friendly state and actions.
- **Localization and theme:** Swift string overrides resolve into core `CascadeEditorStrings` and `CascadeEditorBlockStrings`. `CascadeEditorColors` snapshots map all 25 ARGB slots into the core palette and recompose live. Without a custom palette, `isDark` selects the built-in colors; it remains exposed to native custom blocks in either mode.
- **Local build:** `scripts/build-xcframework.sh` assembles the dynamic debug XCFramework at `editor-ios-sdk/build/XCFrameworks/debug/CascadeEditor.xcframework`. The Xcode sample links and embeds this local artifact.
- **External distribution:** `scripts/package-ios-sdk.sh` tests and assembles the release framework, validates resources/privacy/version/dSYMs, and emits a one-root ZIP plus SwiftPM checksum and manifest. `release-ios-sdk.yml` publishes immutable source-tag assets and updates `linreal/cascade-editor-ios`.
- **Consumer validation:** `scripts/validate-ios-consumer.sh` stages the publication ZIP in `iosConsumerSmokeTest`, builds a generic-device Release app, executes its UI test on an arm64 simulator, and inspects the embedded framework.
- **Release operations:** `docs/iOsPublication.md` is the publication and recovery runbook.

## 7. Edge Cases & Known Constraints

- All state-mutating controller methods, context operations, and ordinary state getters are main-thread-only. Off-main misuse reports `onInternalError` and returns a stable fallback/no-op. Export methods are the exception: they synchronously hop to the main queue to return authoritative content. Callers must not block the main thread waiting for an off-main export, or they can deadlock.
- Color values use the low 32 bits of each `Int64` as `0xAARRGGBB`. `setColors` is a complete replacement, not a partial merge; seed the bag with `CascadeEditorColors(isDark:)`, change the desired slots, and reapply it for every named-theme switch.
- Toolbar actions, `undo()`, and `redo()` are unavailable before the Compose editor is mounted. `canUndo` and `canRedo` return `false` in that state. Document load/export does not require a mounted view.
- Structural history requires live text/span runtime holders. `dispatchStructuralAction(...)` still applies the action when no holders are bound or passed, but the change is not undoable and is logged.
- JSON parse failure is preflighted and preserves the current document and history. Successful JSON load/reset is a hard replacement and clears history. HTML load follows the core hard-replacement path; fatal warning categories affect `success` but do not provide the same explicit preflight-preservation guarantee.
- Unknown document block types are preserved. Registered native type IDs decode to renderable `NativeCustomBlockType`; custom content still uses the serializer's generic custom-content fallback and can emit a non-fatal unknown-content-kind warning.
- Custom payloads must be JSON objects. Invalid/non-object payloads are rejected by runtime mutation and insertion APIs. The document builder instead records `lastErrorMessage` and emits an empty payload. Supported values are JSON-compatible strings, finite numbers, booleans, nulls, lists, and nested string-keyed objects; unsupported map keys/value types are coerced or omitted.
- Custom-block type IDs cannot collide with built-in IDs, including the `heading_` family. Re-registering a native block ID is accepted, replaces the prior registration, and reports a warning.
- Slash-command IDs use last-registration-wins behavior, including collisions with built-in IDs. Native handlers are synchronous, the context is valid only during the handler call, and the handler owns its `/query` text.
- Custom-block mutations return `readOnly` when either editor read-only state or `BlockRenderScope.canUpdateBlock` denies mutation. `focusBlock()` remains available because it is not a document mutation.
- Custom block height is clamped to `1...10,000` points; NaN/infinity falls back to `120` points. Native views must report height changes to avoid clipping or excess space.
- Custom UIKit renderers opt out of live drag previews and receive a generic placeholder ghost. Renderer factory failures are reported and replaced with an empty `UIViewController`.
- Native callbacks and custom-block `onChange` handlers are exception-contained. Errors in `onInternalError` itself are swallowed to prevent exceptions crossing the Swift/Objective-C boundary.
- `exportPlainText()` omits non-text blocks completely. `exportRichText()` includes every block, representing non-text blocks with empty text/spans; unsupported custom span styles are omitted, and exported ranges are clamped to text length.
- Rich-text offsets are core text offsets and are consumed as UTF-16 offsets by the Swift sample. Consumers combining block runs must shift ranges using UTF-16 length, not Swift grapheme-cluster count.
- The built XCFramework includes arm64 device and arm64 simulator slices only;
  Compose Multiplatform 1.11.1 does not publish the `iosX64` dependencies
  required for an Intel simulator framework.
- The framework is dynamic. Xcode embeds and signs it in the consuming app
  through SwiftPM; consumers must not add a second manual Embed Frameworks
  entry.
- Binary API dumps validate Kotlin declarations but do not validate `@ObjCName` spelling or every Objective-C header-lowering detail. Swift-visible naming changes require inspection of the generated `CascadeEditor.h`.
- `CascadeDocumentPreviewController` operations and configuration access are
  main-thread-only. Guarded off-main calls report `onInternalError` and return a
  stable fallback/no-op.
- A preview view controller is transparent and non-scrolling. Its UIKit/SwiftUI
  owner must supply bounded dimensions, retain it for the owning cell lifetime,
  and let the outer collection own scrolling, reuse, and navigation.
- Preview link activation requires both `linksEnabled` and an explicit
  `onOpenLink` callback. The callback is exception-contained; the SDK does not
  invoke an implicit platform URL opener.
- Native custom editor registrations do not participate in preview rendering.
  Unknown/custom JSON is preserved and shown through the generic bounded
  fallback. There is no Swift custom preview-provider API yet, and native
  UIKit/SwiftUI editor renderers are never mounted by the preview facade.
- The sample demonstrates the lifecycle and persistence flow, but no manual iOS
  memory, scrolling, accessibility, or frame-time evidence is claimed.
- ⚠️ Unclear: iOS native Compose text input is explicitly requested by the common text-field call but currently forced off in the iOS platform implementation due to CMP-10404. The TODO does not define the Compose version or acceptance criteria for enabling it.

## 8. Glossary

- **Authoritative document blocks:** snapshot blocks with current runtime text and span edits folded in by `resolveDocumentBlocks(...)`.
- **Bridge state:** Swift-friendly state and callbacks derived from the internal Compose editor state.
- **Custom block context:** the block-scoped, main-thread handle through which a native view reads current state and requests editor mutations.
- **Hard replacement:** a document load/reset that replaces the editor state, clears runtime holders, and clears undo/redo history.
- **Mount token:** identity object used to ensure only the current Compose host publishes or clears controller bridge state.
- **Native custom block:** a custom document block whose content is rendered by a UIKit/SwiftUI-owned `UIViewController` hosted inside Compose.
- **Native document preview:** a bounded static Compose preview hosted by
  `CascadeDocumentPreviewController`, with decoded immutable blocks and no
  editor runtime.
- **Native type codec:** `NativeCustomBlockCodec`, which maps registered custom type IDs between persisted JSON and renderable native block types.
- **Runtime holders:** `BlockTextStates` and `BlockSpanStates`, which contain live editing values that may be newer than `EditorState` snapshot content.
- **Structural action:** a semantic document mutation captured as one full-checkpoint history entry.
- **Toolbar state:** a Swift-facing projection of formatting, indentation, and link state, including tri-state style activation.
- **XCFramework:** Apple's bundle format containing framework binaries for multiple Apple platform architectures.
