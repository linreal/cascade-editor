# Theming & Localization — Technical Context

## 1. Feature Overview

This feature introduces a **theme + strings** configuration layer that lets consumers control every visual and textual aspect of the editor via three top-level parameters on `CascadeEditor()`. All hardcoded `Color(0x...)` values, font sizes, and UI strings have been replaced with reads from `CompositionLocal`-provided config objects. The editor ships light and dark presets and English defaults, while remaining fully open to custom palettes, typography, and translations. Existing consumers are unaffected — all new parameters have defaults that reproduce the previous behavior exactly.

---

## 2. Architecture & Design Decisions

### New Types (all in `editor/.../theme/`)

| Class | Role |
|-------|------|
| `CascadeEditorColors` | `@Immutable data class` — 21 named color slots covering every hardcoded color in the editor plus rich-text rendering colors. Ships `light()` and `dark()` presets. |
| `CascadeEditorTypography` | `@Immutable data class` — 11 `TextStyle` slots (body, heading1–6, code, slash UI, toolbar). Ships `default()`. Styles carry font properties only — **no color**. |
| `CascadeEditorTheme` | Facade combining `colors` + `typography`. Ships `light()` / `dark()`. |
| `CascadeEditorStrings` | `@Immutable data class` — UI chrome strings: toolbar accessibility labels, link popup labels, back button text, and error-message lambdas. Ships `default()` (English). |
| `BlockLocalizedStrings` | Per-block-type display name, description, and additive search keywords. |
| `CascadeEditorBlockStrings` | `Map<typeId, BlockLocalizedStrings>` with a `forType()` lookup. Ships `default()` (English, mirrors `BlockRegistry`). |
| `LocalCascadeTheme` | `internal staticCompositionLocalOf<CascadeEditorTheme>` |
| `LocalCascadeStrings` | `internal staticCompositionLocalOf<CascadeEditorStrings>` |
| `LocalCascadeBlockStrings` | `internal staticCompositionLocalOf<CascadeEditorBlockStrings>` |

### Key Design Decisions

**Consumer controls light/dark.** The editor never calls `isSystemInDarkTheme()`. The consumer chooses a preset or supplies custom values. This keeps the library UI-framework-agnostic and avoids side effects.

**`staticCompositionLocalOf` over `compositionLocalOf`.** Theme and strings change rarely (typically once at startup). Static locals skip the per-recomposition comparison overhead.

**Color and typography are separated.** `TextStyle` slots carry size/weight/family only. Color is always applied at the use-site from `CascadeEditorColors`. This avoids duplication between light and dark themes and prevents accidental color conflicts.

**Block strings are separate from UI strings.** UI strings (`CascadeEditorStrings`) are read at composition time in UI chrome. Block strings (`CascadeEditorBlockStrings`) feed into the slash command pipeline at `remember`-time and have a different lifecycle.

**Additive keyword merging.** Localized keywords supplement English keywords — they never replace them. This guarantees English search always works regardless of locale: `(descriptor.keywords + localized.keywords).distinct()`.

**SpanMapper refactored to accept color parameters.** `SpanMapper` is an `internal object` used inside `OutputTransformation` (layout phase, not composition). It cannot read `CompositionLocal`s. Colors are captured at composition time in `TextBlockField` and passed as explicit parameters to `toComposeSpanStyle()`, `applyStyles()`, `mapRenderableSpans()`, and `toOutputTransformation()`. Link spans use `CascadeEditorColors.linkText` plus underline; click/open semantics are handled by later link interaction layers.

---

## 3. Data Flow

### Theme Flow

```
Consumer
  └─ CascadeEditor(theme = CascadeEditorTheme.dark())
       └─ CompositionLocalProvider(LocalCascadeTheme provides theme)
            ├─ RichTextToolbar: val colors = LocalCascadeTheme.current.colors
            ├─ TextBlockRenderer: val theme = LocalCascadeTheme.current
            ├─ SlashCommandPopup/Row: reads colors + typography
            ├─ DropIndicator: resolves colors.primary when no override
            └─ TextBlockField: captures colors.linkText, colors.inlineCodeBackground, colors.highlight
                 └─ passes into SpanMapper via OutputTransformation closure
```

### Strings Flow

```
Consumer
  └─ CascadeEditor(strings = myStrings, blockStrings = myBlockStrings)
       ├─ CompositionLocalProvider(LocalCascadeStrings provides strings, ...)
       │    ├─ RichTextToolbar: localizedLabel(spec, strings) for accessibility
       │    ├─ SlashCommandPopup: strings.back for back button
       │    └─ UnknownBlockRenderer: strings.unsupportedBlock(typeId)
       │
       └─ remember(blockStrings) {
              BuiltInSlashCommandFactory.generate(descriptors, blockStrings)
          }
          └─ For each descriptor:
               blockStrings.forType(typeId) → localized title/desc/keywords
               └─ Falls back to descriptor English values if not found
```

### Highlight Color Resolution

```
CascadeEditor
  └─ remember(toolbar, theme.colors.highlight) {
       if toolbar uses Default config:
         extract ARGB from colors.highlight
         replace SpanStyle.Highlight in button list
     }
```

This ensures the toolbar's highlight button color matches the theme without requiring consumers to manually sync the toolbar config with the theme.

### Cursor & Selection Colors

```
CascadeEditor
  └─ remember(colors.cursor, colors.textSelectionBackground) {
       TextSelectionColors(handleColor, backgroundColor)
     }
  └─ CompositionLocalProvider(LocalTextSelectionColors provides ...)
       └─ TextBlockField: SolidColor(colors.cursor) → cursorBrush param
```

---

## 4. Public API Surface

### Entry Point

```kotlin
@Composable
public fun CascadeEditor(
    stateHolder: EditorStateHolder,
    // ...existing params...
    theme: CascadeEditorTheme = CascadeEditorTheme.light(),
    strings: CascadeEditorStrings = CascadeEditorStrings.default(),
    blockStrings: CascadeEditorBlockStrings = CascadeEditorBlockStrings.default(),
    modifier: Modifier = Modifier,
    toolbar: ToolbarSlot = ToolbarSlot.Default(),
    // ...
    config: CascadeEditorConfig = CascadeEditorConfig.Default,
)
```

### Theme Configuration

```kotlin
// Use a preset
CascadeEditor(theme = CascadeEditorTheme.dark())

// Customize specific slots
CascadeEditor(
    theme = CascadeEditorTheme.light().copy(
        colors = CascadeEditorColors.light().copy(primary = Color.Red)
    )
)

// Fully custom
CascadeEditor(
    theme = CascadeEditorTheme(
        colors = CascadeEditorColors(/* all slots */),
        typography = CascadeEditorTypography(/* all slots */),
    )
)
```

### Localization

```kotlin
// Provide French strings
CascadeEditor(
    strings = CascadeEditorStrings(
        back = "\u2039 Retour",
        unsupportedBlock = { "Type de bloc non supporté : $it" },
        bold = "Gras",
        italic = "Italique",
        underline = "Souligné",
        strikethrough = "Barré",
        inlineCode = "Code en ligne",
        highlight = "Surligné",
        slashCommand = "Commande slash",
        hideKeyboard = "Masquer le clavier",
        indentForward = "Augmenter le retrait",
        indentBackward = "Réduire le retrait",
        link = "Lien",
        linkApply = "Appliquer le lien",
        linkCancel = "Annuler",
        linkRemove = "Supprimer le lien",
        linkTitle = "Titre",
        linkUrl = "URL",
        linkValidationError = { "URL invalide : $it" },
    ),
    blockStrings = CascadeEditorBlockStrings(
        blocks = mapOf(
            "paragraph" to BlockLocalizedStrings("Paragraphe", "Texte brut", listOf("texte")),
            // ... other blocks
        )
    )
)
```

### Public Types Summary

| Type | Visibility | Factory Methods |
|------|-----------|----------------|
| `CascadeEditorTheme` | `public` | `light()`, `dark()` |
| `CascadeEditorColors` | `public` | `light()`, `dark()` |
| `CascadeEditorTypography` | `public` | `default()` |
| `CascadeEditorStrings` | `public` | `default()` |
| `CascadeEditorBlockStrings` | `public` | `default()` |
| `BlockLocalizedStrings` | `public` | constructor |

Theme/string `CompositionLocal` instances (`LocalCascadeTheme`, `LocalCascadeStrings`, `LocalCascadeBlockStrings`) are `internal` — consumers configure them via parameters, not locals. `LocalCascadeEditorConfig` is the separate public behavior-config local for custom renderer/chrome content; use it for read-only behavior such as disabling custom controls, not for theme or localization.

---

## 5. Integration Points

### Files Modified (beyond new `theme/` package)

| File | What Changed |
|------|-------------|
| `ui/CascadeEditor.kt` | New `theme`/`strings`/`blockStrings` params, `CompositionLocalProvider` wiring, highlight ARGB resolution, `LocalTextSelectionColors` |
| `ui/RichTextToolbar.kt` | Reads `LocalCascadeTheme` + `LocalCascadeStrings`, passes colors/typography/strings to button renderer |
| `ui/SlashCommandPopup.kt` | Reads `LocalCascadeTheme` + `LocalCascadeStrings` for popup background, divider, back button |
| `ui/SlashCommandRow.kt` | Reads `LocalCascadeTheme` for item title/chevron colors and title typography |
| `ui/DropIndicator.kt` | Resolves `colors.primary` from theme when no color override; removed `DropIndicatorDefaults.Color` |
| `ui/BackspaceAwareTextEdit.kt` | Added `cursorBrush` parameter |
| `ui/renderers/TextBlockRenderer.kt` | Reads typography for heading/body/code styles; applies `colors.text` |
| `ui/renderers/TextBlockField.kt` | Captures `colors.linkText`/`colors.inlineCodeBackground`/`colors.highlight`, passes them to SpanMapper via OutputTransformation |
| `ui/renderers/TodoBlockRenderer.kt` | Reads theme for body typography + text color |
| `ui/renderers/DividerBlockRenderer.kt` | Reads `colors.contentDivider` |
| `ui/renderers/UnknownBlockRenderer.kt` | Reads `colors` + `strings` for background/text/error message |
| `richtext/SpanMapper.kt` | `linkText`, `inlineCodeBackground`, and `highlightBackground` are explicit parameters (no private vals or CompositionLocal reads) |
| `slash/BuiltInSlashCommandFactory.kt` | `generate()` accepts optional `CascadeEditorBlockStrings?` for localized titles/descriptions/keywords |

### Downstream Impact

- **Consumer app (`App.kt`)**: Minimal — only needs updating if consumer wants non-default theme/strings. Default parameters preserve existing behavior.
- **Custom block renderers**: Should read `LocalCascadeTheme.current` for colors/typography instead of hardcoding values.
- **Custom slash commands**: Unaffected — localization only applies to built-in commands via `BlockDescriptor` pipeline.
- **Tests**: `SpanMapper` tests updated to pass explicit color parameters. New test files added for all theme/strings types.

---

## 7. Edge Cases & Known Constraints

**SpanMapper cannot read CompositionLocals.** Because it executes inside `OutputTransformation` (layout phase), colors must be captured at composition time in `TextBlockField` and threaded through as parameters. The `remember` key includes both color values so recomposition fires on theme change.

**Highlight color uses ARGB Long conversion.** `SpanStyle.Highlight` stores color as `Long` (ARGB). The conversion in `CascadeEditor` uses `color.toArgb().toUInt().toLong()` — the `toUInt()` step prevents sign extension that would cause mismatch with the `Highlight.colorArgb` field.

**Default toolbar config sentinel check.** The highlight color injection uses reference identity (`toolbar.config === RichTextToolbarConfig.Default`) to detect the default config. Custom configs pass through untouched. This means copying the default config creates a new identity that bypasses injection.

**String lambda equality.** `CascadeEditorStrings` is a data class, but lambdas don't participate in structural equality. Default lambdas such as `unsupportedBlock` and `linkValidationError` are extracted as singletons to ensure `CascadeEditorStrings.default() == CascadeEditorStrings.default()` holds true.

**Typography excludes color.** `TextStyle` slots in `CascadeEditorTypography` carry size, weight, and font family only. Color is always applied from `CascadeEditorColors` at use-site via `.copy(color = colors.text)`. Providing a `TextStyle` with color set will have that color overridden.

**`UnknownBlockRenderer` font size stays hardcoded.** It renders `14.sp` for error text — intentional, as it's a debug/error display not considered user-facing content typography.

**No RTL support.** Explicitly deferred (V1 decision).

**Toolbar button labels (`B`, `I`, `U`, `S`, `<>`, `H`) are not localized.** They are visual glyphs, not linguistic content. Only accessibility `contentDescription` labels are localized via `CascadeEditorStrings`; the link button uses an icon with localized accessibility text.

---

## 8. Glossary

| Term | Definition |
|------|-----------|
| **Color Slot** | A named `Color` property in `CascadeEditorColors` corresponding to exactly one semantic UI element (e.g., `primary`, `toolbarIcon`). |
| **Typography Slot** | A named `TextStyle` property in `CascadeEditorTypography` defining font properties for a specific text element. |
| **Block Strings** | `CascadeEditorBlockStrings` — localized names/descriptions/keywords for block types, keyed by `typeId`. Used in slash command popup. |
| **UI Strings** | `CascadeEditorStrings` — localized text for editor chrome (toolbar labels, popup text, error messages). |
| **CompositionLocal** | Compose mechanism for implicit data passing down the tree. Theme/strings use `staticCompositionLocalOf` (optimized for rarely-changing values). |
| **Additive Keywords** | Localized keywords are merged with English keywords rather than replacing them, ensuring English search always works. |
| **Sentinel Config** | `RichTextToolbarConfig.Default` — used via reference identity (`===`) to detect unmodified default toolbar config for highlight color injection. |
| **SpanMapper** | Internal object that converts domain `SpanStyle` to Compose `SpanStyle`. Runs in layout phase, so colors must be passed as parameters rather than read from `CompositionLocal`. |
| **Cursor Brush** | `SolidColor` brush for the text cursor, resolved from `colors.cursor` and passed via parameter to `BackspaceAwareTextField`. |
