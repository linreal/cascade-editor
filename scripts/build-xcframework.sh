#!/bin/sh
# Builds the Debug CascadeEditor.xcframework consumed by iosNativeSample and any
# local Swift host. Debug is the local-development variant; the Release variant
# (assembleCascadeEditorReleaseXCFramework) is reserved for a future external
# publish and is intentionally not part of this flow.
#
# Output (canonical local consumption path, referenced by the Xcode project):
#   editor-ios-sdk/build/XCFrameworks/debug/CascadeEditor.xcframework
#
# Contains ios-arm64 and ios-arm64-simulator slices. Safe to re-run; Gradle
# skips up-to-date work.
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$REPO_ROOT/gradlew" -p "$REPO_ROOT" :editor-ios-sdk:assembleCascadeEditorDebugXCFramework "$@"

echo "XCFramework: $REPO_ROOT/editor-ios-sdk/build/XCFrameworks/debug/CascadeEditor.xcframework"
