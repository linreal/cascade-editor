# Imported entity and escaping checks

Research &amp; Development owns build &#35;42. The imported source also contains
the numeric character reference &#x2713; and an unknown named entity &nosuch; that
must remain literal text.

## Literal syntax in prose

- The heading marker is written as \# when it is not a heading.
- The checklist token is written as \[x\] when it is only an example.
- A Windows path such as `C:\Users\demo` stays inside inline code.
- Comparison text like `a < b` remains code rather than starting HTML.

