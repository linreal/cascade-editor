# Android: editor jumps after deleting a nested list item

## Environment

- App version: 2.8.0
- Device: Pixel 8
- Android: 16
- Input method: Gboard

## Steps to reproduce

1. Open a document containing a nested bullet list.
2. Place the cursor at the beginning of the second nested item.
3. Press Backspace twice.
4. Type any character.

## Expected

The nested item is removed and focus remains on the previous list item.

## Actual

Focus moves to the first block and the viewport jumps to the top.

> Reproduces only when the document has more than one screen of content.

Relevant log excerpt:

```
focus target missing for block 8f2a
fallback focus selected block 0001
```

See the [screen recording](../attachments/editor-jump.mp4).

