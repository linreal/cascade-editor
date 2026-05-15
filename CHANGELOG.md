# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added `CascadeEditorConfig(readOnly = ...)` and public `LocalCascadeEditorConfig` for UI-scoped read-only editor behavior.
- Added read-only enforcement for built-in editor interaction surfaces: text input, formatting, indentation, link editing, slash commands, todo toggles, block selection, drag/reorder, empty-space focus, and structural keyboard edits are disabled while scrolling, text selection/copy, and existing link opening remain available.


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
