# Markdown Round-Trip Example Corpus

These files are manual fixtures for `MarkdownFieldScreen`. Copy a file into the
**Source Markdown** field, select the profile settings shown below, and run
**Analyze** or **Round Trip**.

The corpus deliberately mixes documents that are safe for native editing with
documents that should enter raw fallback. A canonical match is acceptable when
the codec normalizes equivalent syntax; byte equality is not required unless a
case explicitly tests preservation.

## Default CommonMark profile

| File | Scenario | Expected recommendation | Primary coverage |
|---|---|---|---|
| `01-personal-weekly-note.md` | Personal/project note | Native editor | Headings, emphasis, strike, links, bullets, quote |
| `02-engineering-task-description.md` | Implementation task | Native editor | Task lists, nested bullets, inline/fenced code |
| `03-bug-report.md` | Reproducible defect | Native editor | Ordered steps, quote, code block, relative link |
| `04-meeting-notes.md` | Team meeting record | Native editor | Sections, decisions, tasks, attendees, links |
| `05-release-checklist.md` | Mobile release runbook | Native editor | Task lists, numbered rollout, thematic break |
| `07-customer-support-handoff.md` | Support escalation | Native editor | Structured prose, quote, bullets, mail link |
| `08-product-requirements.md` | Lightweight PRD | Native editor | Heading hierarchy and nested list outline |
| `09-reference-links-research.md` | Research note | Native editor; source rewritten | Reference links canonicalized to inline links |
| `10-html-formatting-note.md` | Note with inline HTML styles | Native editor with Bridge | `<u>`, `<mark>`, and nested Markdown emphasis |
| `16-entities-and-escaping.md` | Imported technical prose | Native editor; informational warning | Numeric, supported, and unknown entities |

## HardBreak profile

Select **Newlines → Hard break** before testing:

| File | Scenario | Expected recommendation | Primary coverage |
|---|---|---|---|
| `06-hard-break-daily-log.md` | Chat/memo-style daily log | Native editor | Literal line breaks and explicit blank paragraphs |

## Preservation and raw-fallback cases

Use **Unsupported syntax → Preserve** unless testing degradation deliberately.

| File | Scenario | Expected recommendation | Primary coverage |
|---|---|---|---|
| `11-pipe-table-dashboard.md` | Metrics dashboard | Raw fallback | GFM pipe table preservation |
| `12-code-language-design-note.md` | Technical design note | Raw fallback | Fenced-code info string preservation |
| `13-front-matter-and-image.md` | File-oriented knowledge note | Raw fallback | YAML front matter and block image |
| `14-footnotes-and-math.md` | Research/academic note | Raw fallback | Footnote reference/definition and math blocks |
| `15-block-html-embed.md` | Imported CMS fragment | Policy-dependent; normally Raw fallback | Block HTML under Bridge/Preserve/Strip/Strict |

## Suggested test passes

1. Run every native case with the default controls and confirm `Native editor`.
2. Run the hard-break log in CommonMark, then HardBreak, and compare block shape.
3. Run each unsupported case with Preserve, then `Warn + degrade`, and compare
   diagnostics and output.
4. Run the HTML cases through Bridge, Preserve, Warn + strip, Strip, and Strict.
5. Switch output from LF to CRLF and confirm semantic/canonical equality even
   when byte equality changes.

