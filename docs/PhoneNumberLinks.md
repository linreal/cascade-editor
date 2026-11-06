# Automatic Phone Links

`CascadeEditor` can expose likely phone numbers as transient links while a
built-in text block is unfocused:

```kotlin
CascadeEditor(
    stateHolder = stateHolder,
    config = CascadeEditorConfig(autoDetectPhoneNumbers = true),
    onOpenLink = { target ->
        when (target.substringBefore(':').lowercase()) {
            "tel", "http", "https", "mailto" -> uriHandler.openUri(target)
        }
    },
)
```

Android uses `Linkify.PHONE_NUMBERS`; iOS uses `NSDataDetector`. These platform
classifiers intentionally avoid linking every number, but their results can
differ by OS version and locale. Focused blocks keep normal caret and selection
behavior. Explicit document links take precedence over detections.

This restores behavior commonly provided by native iOS text surfaces through
[`UITextView.dataDetectorTypes`](https://developer.apple.com/documentation/uikit/uitextview/datadetectortypes).
The implementation uses
[`NSDataDetector`](https://developer.apple.com/documentation/foundation/nsdatadetector)
on iOS and
[`Linkify.PHONE_NUMBERS`](https://developer.android.com/reference/android/text/util/Linkify)
on Android.

Automatic phone links are presentation-only. They do not change undo history,
document spans, JSON, HTML, or Markdown. Desktop, Web/Wasm, custom renderers,
and code blocks are unchanged.

Static previews opt in separately:

```kotlin
CascadeDocumentPreview(
    blocks = blocks,
    config = CascadeDocumentPreviewConfig.Unbounded.copy(
        autoDetectPhoneNumbers = true,
    ),
    onOpenLink = ::openLink,
)
```

The native iOS SDK exposes the same opt-in through
`CascadeEditorConfiguration.autoDetectPhoneNumbers` and
`CascadeDocumentPreviewConfiguration.autoDetectPhoneNumbers`. Both default to
`false`.

## Host policy

Use `onOpenLink` when the app needs scheme allowlisting, analytics, or explicit
error handling. Opening a `tel:` URI should show the platform dialer; it does
not require direct-call permission. Verify the final behavior on physical iOS
and Android devices.

The iOS Simulator has no Phone app and cannot complete a `tel:` launch. It can
still verify that taps produce the expected target; test the final dialer handoff
on a physical device.
