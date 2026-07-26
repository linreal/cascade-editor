# Clean iOS consumer smoke test

This app validates CascadeEditor exactly as an external iOS application consumes
it: through a local Swift package whose only target is the packaged binary
XCFramework. The Xcode project has no Gradle phase, source-project reference,
resource-copy phase, or direct framework link.

Prepare the publication artifact from the repository root:

```bash
scripts/package-ios-sdk.sh
```

That command also unpacks the final publication ZIP into the ignored
`LocalPackage/Artifacts/CascadeEditor.xcframework` path. You can then open
`CascadeEditorConsumerSmoke.xcodeproj` in Xcode and build or run the
`CascadeEditorConsumerSmoke` scheme normally. A fresh checkout intentionally
does not contain this large generated binary, so packaging is required once
before opening the project.

Run the isolated publication harness with:

```bash
scripts/validate-ios-consumer.sh \
  build/ios-release/1.8.1/CascadeEditor.xcframework.zip
```

The harness creates a temporary clean copy, removes any locally prepared
framework, inserts only the supplied publication ZIP into
`LocalPackage/Artifacts`, builds a Release app for a generic iOS device, runs
the UI test on an arm64 simulator, and inspects the final app bundle.

Do not add the XCFramework to this directory in Git. The smoke test must always
consume the ZIP produced by `scripts/package-ios-sdk.sh`.
