# Markdown Serialization

Technical context for the experimental Markdown import/export feature in
`:editor`. Public APIs live in `io.github.linreal.cascade.editor.markdown` and
require `ExperimentalCascadeMarkdownApi` opt-in.

## 1. Scope

The Markdown codec converts between a Markdown `String` and CascadeEditor's
existing block/span model. It also supports direct import/export from
`EditorStateHolder` and can analyze whether a source is safe for native editing
or should stay in a raw-text fallback.

The codec targets bounded task, memo, and note fields. It is a canonical
whole-document codec, not a byte-preserving or minimal-diff file editor.

The default GFM-oriented behavior includes:

- paragraphs, ATX/setext headings, thematic breaks, fenced/indented code;
- blockquotes, bullet/ordered/task lists;
- emphasis, strong emphasis, strikethrough, inline code;
- inline/reference links, autolinks, entities, soft/hard breaks;
- policy-driven HTML bridging;
- opaque preservation or warning-based degradation for front matter, math,
  footnotes, tables, images, metadata, and unrepresentable containers.

## 2. Parser dependency decision

Decode grammar recognition is delegated to `org.jetbrains:markdown:0.7.7`.
Cascade uses its GFM AST internally; JetBrains types do not cross the public
API. The dependency is Apache-2.0 licensed and publishes the KMP variants used
by this project.

This boundary replaces the former hand-written block parser, inline parser,
delimiter stack, source-backed parser node tree, recognizer registry, and
public decode-syntax extension contracts.

### Ownership boundary

| Concern | Owner |
|---|---|
| CommonMark/GFM tokenization, block structure, inline precedence, emphasis matching | JetBrains Markdown |
| BOM and CRLF/CR normalization with original UTF-16 range mapping | Cascade |
| AST-to-`Block`/`TextSpan` mapping | Cascade |
| HardBreak mode and editor outline normalization | Cascade |
| Unsupported-syntax preserve/degrade behavior | Cascade |
| HTML bridge and sanitization boundary | Cascade |
| Fidelity warnings, source ranges, resource limits, no-partial-result rules | Cascade |
| Canonical encoding and encoder extensions | Cascade |
| Executable profile support claims and analyze/native-edit gate | Cascade |


## 3. Decode architecture

`MarkdownDecodeEngine` runs this pipeline:

1. Reject an input over `MarkdownCodecLimits.maxInputChars`.
2. Preflight the bounded emphasis-delimiter-run limit.
3. `MarkdownParseInput` performs one source scan: it retains the original text,
   removes a leading BOM from the parser projection, normalizes CRLF/lone CR to
   LF, builds the original-offset boundary map, and indexes source locations.
4. `parseMarkdownTree` invokes an assertion-free `MarkdownParser` with
   `CascadeMarkdownFlavour`.
5. An iterative definition/depth prepass collects link-reference definitions,
   applies first-definition-wins, and enforces nesting/definition limits.
6. `MarkdownAstDecoder` lowers the AST directly to editor blocks. Inline AST
   children lower to visible text plus normalized `TextSpan`s.
7. Unsupported syntax either warns and degrades or escalates its owning root
   block to an exact `md.preserved`/`md.preservedHtml` slice.
8. `normalizeIndentationOutline` then `renumberNumberedLists` establish editor
   invariants.
9. A successful result contains the complete block list. Any fatal limit or
   engine failure returns an aborted result with no partial payload.

`CascadeMarkdownFlavour` starts from GFM but omits JetBrains' dollar-math
sequential parser. That parser recognizes currency, spaced delimiters, and
other sources outside Cascade's documented math subset. Cascade performs a
small conservative math recognition pass while code spans and images remain
opaque.

The adapter contains two narrow JetBrains 0.7.7 compatibility shims covered by
tests:

- angle-form link destinations can arrive as an `AUTOLINK` child rather than
  `LINK_DESTINATION`;
- an outer-link AST around `[a [b](u2)](u1)` is corrected to CommonMark's
  inner-link-wins result.

These are local AST-shape adapters, not a competing Markdown parser.

### Source preservation

`MarkdownParseInput` owns both views required by decode: the unchanged original
string used for exact slices and locations, and the normalized parser
projection with its normalized-boundary-to-original-boundary map. CRLF, lone
CR, Unicode, and original spelling therefore survive opaque preservation
character-exactly without a second source scan.

Named entity replacement code points come from JetBrains Markdown's generated
table. Cascade retains the documented v1 name subset, exact unknown-name
diagnostics, and its own numeric-code-point handling because JetBrains 0.7.7's
converter narrows numeric values through a single UTF-16 `Char`.

## 4. Encode architecture

Encoding remains Cascade-owned:

1. Resolve live editor text/spans over the stored snapshot.
2. Dispatch a registered block-group encoder, block encoder, or fallback.
3. `MarkdownInlineRenderer` normalizes spans, performs a sweep-line render,
   escapes visible text, and chooses canonical markers.
4. Reparse the candidate through the same JetBrains-backed inline adapter.
5. If decoded text/spans differ, drop the weakest unrepresentable residue and
   emit a data-loss warning instead of claiming a false round trip.
6. Join units with CommonMark or HardBreak separators, add the canonical final
   newline, apply LF/CRLF output policy, and enforce output/warning limits.

The supported encoder contracts are:

- `MarkdownBlockEncoder<T>`;
- `MarkdownSpanEncoder<T>`;
- `MarkdownBlockGroupEncoder`;
- `MarkdownEncodeContext`;
- `MarkdownEmit.Raw`, `Verbatim`, and `Skip`;
- `MarkdownMarkPair`.

Custom encoders register by built-in class or custom `typeId`. Registration
never widens `MarkdownProfileSupportSet`; a profile that adds encoders must
install an explicit executable support claim.

## 5. Profiles and policies

`MarkdownProfile` is immutable and safe to reuse. It configures encoding,
policies, and support claims—not decode grammar.

The default document claim is executable: it encodes and decodes the candidate
and compares block type, content, attributes, and spans while ignoring generated
block IDs. During `analyze`, the existing canonical encode is reused and only
the verification decode is added. Custom support predicates remain public
narrowing hooks, but cannot widen native-edit safety past a failed round trip.

Public composition includes:

- `withMarkdownBlockEncoder<T>`;
- `withMarkdownSpanEncoder<T>`;
- `withCustomMarkdownBlockEncoder`;
- `withCustomMarkdownSpanEncoder`;
- `withMarkdownBlockGroupEncoder` / `withoutMarkdownBlockGroupEncoder`;
- block/span encoder fallbacks;
- `withHtmlInMarkdown`, `withoutHtmlBridge`;
- `withUnsupportedSyntax`;
- `withNewlineSemantics`, `withSoftBreak`, `withHardBreakEncode`;
- `withEntityDecode`;
- `withSupportSet` and `supportSet`.

| Policy | Options |
|---|---|
| `HtmlInMarkdown` | `Bridge(HtmlProfile)`, `Preserve`, `WarnAndStrip`, `Strip` |
| `UnsupportedSyntax` | `Preserve`, `WarnAndDegrade` |
| `NewlineSemantics` | `CommonMark`, `HardBreak` |
| `SoftBreak` | `Space`, `LineBreak` |
| `HardBreakEncode` | `Backslash`, `TwoSpaces` |
| `EntityDecode` | `Standard`, `None` |

Bold, italic, and strikethrough use the fixed CommonMark/GFM marker dialect:
canonical `**`, `*`, `~~`, with `__` and `_` available internally when encode
verification needs a non-conflicting alternate.

### Unsupported syntax

`UnsupportedSyntax.Preserve` creates an `UnknownBlockType`:

- `md.preserved` with `kind` and `rawMarkdown`;
- `md.preservedHtml` for opaque block HTML.

Preservation is safe for storage and re-emission but not natively editable, so
its fidelity impact is `OpaquePreservation`.

`WarnAndDegrade` keeps representable visible content and reports `DataLoss` for
structure or metadata that cannot be represented.

### HTML

The default `HtmlInMarkdown.Bridge(HtmlProfile.Default)` routes HTML fragments
through the existing HTML codec. Text leaves inside HTML are reparsed as
Markdown, so `<u>**bold**</u>` retains both styles. Underline and Highlight
encode through `<u>` and `<mark>` while the bridge is active.

`withoutHtmlBridge()` strips HTML on decode, removes HTML-emitting span
encoders and HTML preservation paths, and installs a strict support set.

This codec is not an HTML sanitizer. Hosts must sanitize at their render or
trust boundary.

## 6. Public API

### Entry points

| API | Result |
|---|---|
| `MarkdownSchema.decode` | `List<Block>?`; null only on abort |
| `MarkdownSchema.decodeWithReport` | status, nullable blocks, warnings, source locator |
| `MarkdownSchema.encode` | `String?`; null only on abort |
| `MarkdownSchema.encodeWithReport` | status, nullable Markdown, warnings |
| `MarkdownSchema.analyze` | `MarkdownFidelityReport` with Native/RawFallback recommendation |

Successful empty input/output stays non-null. Callers must use status/null to
distinguish an abort from an empty document.

### Editor integration

| Extension | Behavior |
|---|---|
| `EditorStateHolder.toMarkdown(...)` | resolve live state and encode |
| `toMarkdownWithReport(...)` | same, retaining diagnostics |
| `loadFromMarkdown(...)` | decode and hard-replace only on success |
| `applyMarkdownDecodeResult(...)` | apply a precomputed successful result on the UI thread |

An aborted import leaves the editor untouched. A successful import resets
focus, selection, transient runtime state, and undo/redo history like other
whole-document load paths.

### Limits

| Default limit | Value |
|---|---:|
| Input UTF-16 code units | 4,000,000 |
| Output UTF-16 code units | 16,000,000 |
| Block nesting depth | 32 |
| Produced blocks | 50,000 |
| Spans per block | 1,000 |
| Total spans | 50,000 |
| Reference definitions | 5,000 |
| Delimiter runs | 10,000 |
| Collected warnings | 1,000 |

Limit exhaustion is fatal and returns no partial payload.

### Diagnostics

Every warning has a `MarkdownFidelityImpact`:

- `Informational`;
- `Canonicalization`;
- `OpaquePreservation`;
- `DataLoss`;
- `Fatal`.

Decode warnings use half-open original-source UTF-16 ranges. Encode warnings
use a block id and/or visible-text range. Consumers should gate by `impact` and
include an `else` when matching concrete warning subclasses.

## 7. Host save contract

For externally authored Markdown:

1. Keep the original source and persistence revision.
2. Analyze without writing.
3. Use native editing only when the recommendation is `Native`; otherwise show
   a raw-text editor.
4. On explicit save, encode with a report.
5. Persist only a successful payload with no `DataLoss`/`Fatal` impact and an
   unchanged source revision.

```kotlin
val report = MarkdownSchema.analyze(source, profile)
if (report.recommendation == MarkdownEditModeRecommendation.Native) {
    val decoded = report.decodeResult
    editor.applyMarkdownDecodeResult(decoded, textStates, spanStates)
} else {
    openRawEditor(source)
}
```

Use the same profile for analyze, decode, editing, and encode. A profile is part
of the storage contract; it must not change mid-session.

For app-owned fields that only store this codec's canonical output, direct
decode is reasonable, though report-based saving remains useful for defensive
checks and telemetry.

## 8. Testing and performance

The retained test strategy validates Cascade-owned behavior rather than
retesting the dependency's complete grammar implementation:

- focused projection tests for BOM/CRLF/CR mapping, source locations, and exact preservation;
- decode/lowering tests for product block/span semantics and warnings;
- 1,000 generated in-support CommonMark round trips;
- 300 generated HardBreak round trips;
- selected CommonMark/GFM differential fixtures;
- HTML bridge, strict profile, fidelity, editor integration, and preservation;
- hostile/fuzz inputs and every resource limit;
- encode escaping and reparse verification;
- desktop 64 KiB performance guards.

The post-simplification local desktop guard reports 21 ms decode, 19 ms encode,
and 47 ms analyze for the generated 64 KiB fixture. Values are
machine-dependent; enforced thresholds remain 250/250/750 ms. Decode is still
synchronous, so hosts should run large decode/analyze operations off the UI
thread.


## 9. Extending behavior

Encoder customization remains supported. Arbitrary decode-grammar injection is
not. For a new Markdown construct:

1. Prefer opaque preservation if native editing is not required.
2. If it is a broadly useful editor feature, add an internal AST adapter with a
   native block/span model, warnings, limits, source mapping, and tests.
3. If upstream grammar support is required, evaluate a narrowly scoped internal
   flavour extension; do not expose upstream AST types publicly.
4. Update the executable support set only after round-trip behavior is proven.

The removed sample table/footnote profiles existed only to prove the retired
decode-extension API and were not wired into the application. Default table and
footnote preservation behavior remains covered.

## 10. Build and licensing

The dependency is declared in `gradle/libs.versions.toml` and consumed from the
editor `commonMain` source set. Version changes must run desktop, Android/KMP
compilation, API validation, round-trip, differential, fuzz, and performance
tests because AST shapes are an internal integration contract.

JetBrains Markdown is Apache License 2.0. Its license and notices must be
included by downstream packaging as required by the consuming platform.
