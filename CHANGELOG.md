# Changelog

## [1.8.0] - 2026-07-23

### Added

- Added an experimental, profile-driven Markdown codec with `MarkdownSchema` and `EditorStateHolder` import/export helpers, CommonMark/GFM-oriented block and inline support, policy-driven HTML bridging, structured warnings, bounded resource limits, source preservation for unsupported syntax, and fidelity analysis that recommends native editing or a raw-text fallback
- Added the Swift-facing `:editor-ios-sdk` static framework with a controller and UIKit host, JSON/HTML document APIs, toolbar state and actions, localization, change callbacks, native custom blocks, and native slash commands; also added an XCFramework build script and a native Swift sample
- Added `CascadeEditorConfig(blockIndentationEnabled = ...)` to disable built-in indentation commands while keeping drag-and-drop at the payload's original indentation
- Added public integration seams for external editor chrome and platform bridges: `EditorStateHolder.dispatchStructuralAction(...)`, `EditorStateHolder.resolveDocumentBlocks(...)`, `BlockRenderer.supportsDragPreview`, and stable built-in slash-command ID helpers
- Added comments-composer and Markdown round-trip examples to the sample app

### Changed

- Lowered the Android minimum SDK from 28 to 26
- Updated Compose Multiplatform from 1.11.0 to 1.11.1

### Fixed

- Fixed block renderers and slash commands registered after the editor is mounted not becoming available until an unrelated state change
- Fixed built-in slash conversion to non-text custom blocks so blank anchors are replaced safely and nonblank anchor text is never discarded
- Fixed JSON, HTML, and Markdown link persistence rewriting relative, fragment, `mailto:`, `tel:`, and custom-scheme targets as HTTPS URLs
- Fixed drag previews for platform-view renderers by allowing them to opt out of duplicate live composition and use a lightweight placeholder

## [1.7.0] - 2026-06-28

### Added

- Added `ScopedBlockRenderer<T>` and the public `BlockRenderScope` seam for interactive custom blocks: scoped renderers receive a live, capability-gated scope to inspect editor state and apply block mutations (`updateBlock`, `replaceBlock`, `insertBlockBefore`/`insertBlockAfter`, `deleteBlock`, `focusBlock`), each producing a single structural undo/redo entry
- Added read-only/policy capability flags on `BlockRenderScope` (`readOnly`, `canUpdateBlock`, `canEditBlockStructure`, `canSelectBlocks`, `canDragBlocks`) that are re-checked at call time so mutations are safe no-ops when disabled or when the target block is missing

### Changed

- Redesigned the default `RichTextToolbar` as a floating pill: a fixed `/` slash circle, scrollable formatting and indent/outdent/link controls, the iOS hide-keyboard button pinned right, and accent-tinted active states
- Redesigned the sample app landing page, editor chrome, and theming

## [1.6.0] - 2026-06-07

### Added

- Added crash containment for built-in editor surfaces: per-block measure/draw failures are caught at a containment boundary so a failing block degrades to a safe fallback instead of crashing the host
- Added `CrashPolicy` (`Rethrow` / `ContainAndReport`), `CascadeError`, and the `CascadeErrorReporter` host hook for routing contained failures into application crash reporting
- Added `CascadeEditorConfig(crashPolicy = ..., onInternalError = ...)` to control containment behavior; defaults to `ContainAndReport` for release and supports `Rethrow` for tests and debug builds
- Added always-contain-and-warn guarantees for serialization entry points: `loadFromJson()` / `loadFromHtml()` no longer throw on malformed input, returning `DocumentDecodeWarning.DocumentParseFailed` / `HtmlDecodeWarning.InputLimitExceeded` instead

### Changed

- Updated dependencies: Compose Multiplatform 1.11.0, Kotlin 2.3.21

### Fixed

- Fixed scroll-to-unfocused-block behavior on the Android target

## [1.5.0] - 2026-05-17

### Added

- Added `CascadeEditorConfig(readOnly = ...)` and public `LocalCascadeEditorConfig` for UI-scoped read-only editor behavior.
- Added read-only enforcement for built-in editor interaction surfaces: text input, formatting, indentation, link editing, slash commands, todo toggles, block selection, drag/reorder, empty-space focus, and structural keyboard edits are disabled while scrolling, text selection/copy, and existing link opening remain available.
- Added support for editor-independent external toolbars.
- Added support for disabling the Slash Command Palette. 

## [1.4.0] - 2026-05-06

### Added

- Added experimental HTML import/export support with profile-driven decode/encode APIs, default block/span mappings, parser policies, warnings, and `EditorStateHolder.toHtml()` / `loadFromHtml()` helpers
- Added link spans with URL validation, serialization, toolbar actions, popup editing, link opening hooks, and custom chrome access through link state/actions
- Added code block support with plain-text editing semantics, dedicated rendering, serialization, slash-command integration, and span-formatting guards


## [1.3.0] - 2026-04-28

### Changed

- Expanded supported block indentation from `0..3` to `0..5`
- Added free indentation: supported blocks can now start at any indentation level, including the first block and blocks after unsupported boundaries
- Drag-and-drop now supports free target indentation while still moving child blocks with their parent payload
- Numbered-list prefixes now follow numbered-list ancestry with a `1.` → `a.` → `i.` cycle instead of absolute indentation depth
- Updated indentation serialization, documentation, and regression coverage for the new outline rules

## [1.2.0] - 2026-04-26

### Added
- Flat-outline block indentation for paragraphs, todos, bullet lists, and numbered lists
- Public indentation state/actions for custom editor chrome, plus default toolbar indent/outdent controls
- Depth-aware drag-and-drop, nested numbered-list formatting, and indentation-aware Enter/Backspace editing behavior
- Editor now supports Desktop target as well

### Changed

- Document serialization now preserves supported indentation attributes and normalizes invalid outlines on decode
- Undo/redo history now captures indentation and drag reindentation as semantic structural transactions
- Sample landing page layout now adapts better to widescreen displays

## [1.1.0] - 2026-04-09

### Added

- Undo/redo support with a hybrid linear history model: compact one-block entries for typing and eligible formatting edits, plus full-document checkpoints for structural edits
- Public history API on `EditorStateHolder`: `canUndo`, `canRedo`, `undo()`, and `redo()`
- Built-in history keyboard shortcuts: `Cmd/Ctrl+Z` for undo and `Shift+Cmd/Ctrl+Z` for redo
- History-aware replay that restores focused block selection/caret and pending formatting styles
- Demo toolbar controls for undo and redo in the sample editor screen

### Changed

- Built-in editor flows such as split/merge, drag reorder, slash commands, list auto-detection, todo toggles, and selected-range formatting now participate in undo/redo history
- Hard document replacement paths (`setState(...)`, `loadFromJson(...)`) now clear undo/redo history

## [1.0.0] - 2026-04-02

### Added

- Block-based editing with paragraphs, headings (H1-H6), todo lists, bullet lists, numbered lists, quotes, and dividers
- Rich text formatting: bold, italic, underline, strikethrough, inline code, highlight, and custom span styles
- Full span preservation across split, merge, and block conversion operations
- Drag and drop block reordering with auto-scroll, drop indicator, and drag preview
- Block selection: single, multi-select, and range selection with visual feedback
- Slash command system with searchable popup, keyboard navigation, submenu support, and custom command registration
- Custom block type support via `CustomBlockType` and `BlockRenderer` interfaces
- Document serialization with `toJson()` / `loadFromJson()`, versioned schemas, and codec hooks for custom types
- Theming API with `CascadeEditorTheme`, configurable colors and typography, light/dark presets
- Localization support with `CascadeEditorStrings` and `CascadeEditorBlockStrings` for full UI string customization
- List auto-detection: typing `- ` or `1. ` converts a paragraph into the corresponding list type
- Smart list continuation on Enter and exit-to-paragraph on empty Enter
- Keyboard shortcuts for formatting (Cmd+B/I/U on macOS, Ctrl+B/I/U on other platforms)
- iOS keyboard dismiss button in the formatting toolbar
- Kotlin Multiplatform support targeting Android and iOS with native Compose rendering
