# Native iOS SDK publication

This is the operator runbook for publishing `CascadeEditor.xcframework` through
Swift Package Manager. The release artifact is built from the source tag,
validated in a clean iOS app, uploaded once to the matching GitHub Release, and
referenced by checksum from a small public manifest repository.

## Distribution layout

Two repositories are used:

- `linreal/cascade-editor` owns source code, source tag `vMAJOR.MINOR.PATCH`,
  release workflow, and immutable `CascadeEditor.xcframework.zip` asset.
- `linreal/cascade-editor-ios` owns the consumer-facing `Package.swift` and
  matching `MAJOR.MINOR.PATCH` Swift package tag.

Keeping the manifest in a separate repository avoids a checksum cycle: the
source tag is fixed before its binary is built, while the package manifest can
be committed only after the final ZIP checksum exists.

The ZIP contains exactly one `CascadeEditor.xcframework` root. Each dynamic
framework slice contains Compose resources, `PrivacyInfo.xcprivacy`, the MIT
license, dependency notices, and a matching dSYM. Supported slices are arm64
iOS devices and arm64 Apple Silicon simulators; minimum deployment is iOS 16.0.

## One-time GitHub setup

1. Create the public repository `linreal/cascade-editor-ios` with an initial
   README commit so a `main` branch exists. Do not put the binary in Git.
2. Create a GitHub App installation token or fine-grained personal access token
   scoped only to `linreal/cascade-editor-ios`, with **Contents: Read and
   write**. A GitHub App is preferred for team-operated releases.
3. Add it to `linreal/cascade-editor` as the Actions repository secret
   `CASCADE_EDITOR_IOS_RELEASE_TOKEN`.
4. Protect both repositories' release tags from force-push/deletion. Configure
   the package repository's branch rules so the release credential can update
   `main`.
5. Keep GitHub Release assets immutable. Never enable an automation path that
   uploads with `--clobber`.

The source workflow pins third-party Actions to reviewed commit SHAs and uses
the Apple Silicon `macos-26` image.

## Prepare a release

Use a new patch version. Existing tags and assets are immutable; do not move
`v1.8.0`. For the current publication, the version is `1.8.1`.

1. Set `VERSION_NAME` in `gradle.properties`.
2. Update `CHANGELOG.md`, README installation examples, and the version in
   `THIRD_PARTY_NOTICES/iOS-SDK-DEPENDENCIES.md`.
3. Run the local release gate:

   ```bash
   scripts/package-ios-sdk.sh 1.8.1
   scripts/validate-ios-consumer.sh \
     build/ios-release/1.8.1/CascadeEditor.xcframework.zip
   ```

   Packaging also installs an ignored copy of that final ZIP under
   `iosConsumerSmokeTest/LocalPackage/Artifacts`, allowing the checked-out
   consumer project to be opened and run directly in Xcode. The validation
   script removes this convenience copy from its temporary workspace before
   injecting the supplied archive.

4. Review the committed public API dumps. If an intentional Swift-facing
   declaration changed, run `./gradlew :editor-ios-sdk:apiDump`, inspect the
   generated `CascadeEditor.h`, and commit the API change.
5. Commit the release preparation to `main`, then create and push the exact
   source tag:

   ```bash
   git tag -a v1.8.1 -m "CascadeEditor 1.8.1"
   git push origin main
   git push origin v1.8.1
   ```

Pushing the tag starts `.github/workflows/release-ios-sdk.yml`. The manual
workflow-dispatch input is only for publishing an already-existing source tag;
it is not a substitute for a tag.

## Automated gates

Publication stops unless all of these pass:

- Kotlin iOS SDK tests and binary API compatibility;
- release links for arm64 device and arm64 simulator;
- dynamic Mach-O type and `@rpath` identity;
- bundle ID, semantic version, and iOS 16.0 metadata;
- Compose toolbar icon resources in both slices;
- privacy manifest presence and syntax;
- matching binary/dSYM UUIDs;
- one-root ZIP shape and matching SwiftPM/SHA-256 checksums;
- generic iOS device Release build without signing;
- clean SwiftPM consumer UI test on an iOS simulator;
- final app inspection for embedded dynamic framework, framework-owned
  resources, privacy metadata, notices, and `@rpath` linkage;
- public binary-target URL and checksum resolution after upload.

The clean consumer fixture contains no Gradle build phase, source framework
reference, or resource-copy phase. Its only SDK input is the generated ZIP.
The UI test waits for `CascadeEditorSdk.version`, verifies document export, and
checks toolbar accessibility nodes whose icons are loaded from Compose
resources. Its result bundle and screenshot are retained as workflow evidence.

## Publication sequence

The workflow performs these state changes only after all local gates pass:

1. Create the matching GitHub Release and upload the ZIP and `.sha256` file.
2. Resolve the generated package against the public asset URL and checksum.
3. Copy the generated manifest, README, license, and notices into
   `linreal/cascade-editor-ios`.
4. Commit to that repository's `main` branch and create its
   `MAJOR.MINOR.PATCH` tag.

The source tag is the root of trust. The GitHub Release asset and Swift package
tag must always describe the same version.

## Failure and recovery

- Before the GitHub Release step, fix the problem and rerun the workflow for
  the same source tag.
- After the GitHub Release exists, do not rebuild, delete, replace, or overwrite
  the asset. Download the workflow evidence artifact and finish the package
  repository update from its generated `swift-package/` directory, preserving
  the recorded checksum.
- If a published binary is defective, publish a new patch version. Do not
  mutate the old tag, ZIP, checksum, release, or package tag.
- If package-repository publication fails after `main` was pushed but before
  its version tag was pushed, verify that commit contains the expected checksum
  and tag that exact commit.

## Consumer verification

After publication, create a new empty UIKit or SwiftUI app and add:

```text
https://github.com/linreal/cascade-editor-ios
```

Select the new version and add the `CascadeEditor` product to the application
target. There must be no manual framework embed or resource-copy phase. Build
both a simulator destination and a generic iOS device before announcing the
release.
