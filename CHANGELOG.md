# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
