#!/usr/bin/env bash
#
# Builds and verifies the release XCFramework, then creates the immutable ZIP
# and SwiftPM repository contents used for one published version.
#
# Usage:
#   scripts/package-ios-sdk.sh
#   scripts/package-ios-sdk.sh 1.8.1
#
# Output:
#   build/ios-release/<version>/CascadeEditor.xcframework.zip
#   build/ios-release/<version>/CascadeEditor.xcframework.zip.sha256
#   build/ios-release/<version>/swift-package/
#   iosConsumerSmokeTest/LocalPackage/Artifacts/CascadeEditor.xcframework
set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly CONFIGURED_VERSION="$(sed -n 's/^VERSION_NAME=//p' "$REPO_ROOT/gradle.properties")"
readonly VERSION="${1:-$CONFIGURED_VERSION}"
readonly BUILD_ROOT="$REPO_ROOT/build/ios-release/$VERSION"
readonly SOURCE_XCFRAMEWORK="$REPO_ROOT/editor-ios-sdk/build/XCFrameworks/release/CascadeEditor.xcframework"
readonly STAGED_XCFRAMEWORK="$BUILD_ROOT/CascadeEditor.xcframework"
readonly ARCHIVE="$BUILD_ROOT/CascadeEditor.xcframework.zip"
readonly PACKAGE_ROOT="$BUILD_ROOT/swift-package"
readonly LOCAL_CONSUMER_ARTIFACTS="$REPO_ROOT/iosConsumerSmokeTest/LocalPackage/Artifacts"
readonly LOCAL_CONSUMER_XCFRAMEWORK="$LOCAL_CONSUMER_ARTIFACTS/CascadeEditor.xcframework"
readonly EXPECTED_BUNDLE_ID="io.github.linreal.cascade.editor"

fail() {
    echo "error: $*" >&2
    exit 1
}

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
    fail "version must be a SemVer release such as 1.8.1"
[[ "$VERSION" == "$CONFIGURED_VERSION" ]] ||
    fail "requested version $VERSION does not match VERSION_NAME=$CONFIGURED_VERSION"

# Kotlin/Native release LTO is memory-intensive. Configuration cache permits
# independent link tasks to overlap, which can exhaust the 7 GB standard macOS
# CI runner. Keep test linking and release linking in separate invocations and
# serialize native workers; do not remove this constraint without a cold CI run.
"$REPO_ROOT/gradlew" -p "$REPO_ROOT" --max-workers=1 \
    :editor-ios-sdk:allTests \
    :editor-ios-sdk:apiCheck

"$REPO_ROOT/gradlew" -p "$REPO_ROOT" --max-workers=1 \
    :editor-ios-sdk:assembleCascadeEditorReleaseXCFramework

rm -rf "$BUILD_ROOT"
mkdir -p "$BUILD_ROOT" "$PACKAGE_ROOT"
ditto "$SOURCE_XCFRAMEWORK" "$STAGED_XCFRAMEWORK"

readonly SLICES=(
    "ios-arm64"
    "ios-arm64-simulator"
)
readonly REQUIRED_RESOURCE_FILES=(
    "ic_hide_keyboard.xml"
    "ic_link.xml"
    "ic_format_indent_increase.xml"
    "ic_format_indent_decrease.xml"
)

for slice in "${SLICES[@]}"; do
    framework="$STAGED_XCFRAMEWORK/$slice/CascadeEditor.framework"
    binary="$framework/CascadeEditor"
    info_plist="$framework/Info.plist"
    privacy_manifest="$framework/PrivacyInfo.xcprivacy"
    dsym_binary="$STAGED_XCFRAMEWORK/$slice/dSYMs/CascadeEditor.framework.dSYM/Contents/Resources/DWARF/CascadeEditor"

    [[ -f "$binary" ]] || fail "missing $slice framework binary"
    [[ -f "$framework/Headers/CascadeEditor.h" ]] || fail "missing $slice public header"
    [[ -f "$framework/Modules/module.modulemap" ]] || fail "missing $slice module map"
    [[ -f "$privacy_manifest" ]] || fail "missing $slice privacy manifest"
    [[ -f "$dsym_binary" ]] || fail "missing $slice dSYM"

    file "$binary" | grep -q "dynamically linked shared library" ||
        fail "$slice is not a dynamic framework"

    # Kotlin/Native keeps a large local symbol table in the linked framework
    # even though the matching dSYM already owns the debug information. Strip
    # only the staged publication copy so Gradle outputs and dSYMs stay intact.
    # The exported-symbol digest guards the Swift/Objective-C ABI boundary.
    binary_size_before_strip="$(stat -f '%z' "$binary")"
    exported_symbols_before_strip="$(
        nm -gjU "$binary" | LC_ALL=C sort | shasum -a 256 | awk '{print $1}'
    )"
    xcrun strip -x "$binary"
    binary_size_after_strip="$(stat -f '%z' "$binary")"
    exported_symbols_after_strip="$(
        nm -gjU "$binary" | LC_ALL=C sort | shasum -a 256 | awk '{print $1}'
    )"
    [[ "$exported_symbols_before_strip" == "$exported_symbols_after_strip" ]] ||
        fail "$slice exported symbols changed during stripping"
    (( binary_size_after_strip <= binary_size_before_strip )) ||
        fail "$slice framework grew during stripping"
    echo "$slice framework binary: $binary_size_before_strip -> $binary_size_after_strip bytes after local-symbol stripping"

    bundle_id="$(plutil -extract CFBundleIdentifier raw "$info_plist")"
    short_version="$(plutil -extract CFBundleShortVersionString raw "$info_plist")"
    build_version="$(plutil -extract CFBundleVersion raw "$info_plist")"
    minimum_os="$(plutil -extract MinimumOSVersion raw "$info_plist")"
    [[ "$bundle_id" == "$EXPECTED_BUNDLE_ID" ]] ||
        fail "$slice bundle id is $bundle_id"
    [[ "$short_version" == "$VERSION" ]] ||
        fail "$slice short version is $short_version"
    [[ "$build_version" == "$VERSION" ]] ||
        fail "$slice build version is $build_version"
    [[ "$minimum_os" == "16.0" ]] ||
        fail "$slice minimum iOS version is $minimum_os"

    plutil -lint "$privacy_manifest" >/dev/null
    plutil -extract NSPrivacyTracking raw "$privacy_manifest" | grep -qx "false" ||
        fail "$slice privacy manifest unexpectedly enables tracking"
    privacy_contents="$(plutil -p "$privacy_manifest")"
    for privacy_value in \
        NSPrivacyAccessedAPICategoryFileTimestamp \
        0A2A.1 \
        NSPrivacyAccessedAPICategorySystemBootTime \
        35F9.1; do
        grep -q "$privacy_value" <<< "$privacy_contents" ||
            fail "$slice privacy manifest is missing $privacy_value"
    done

    resources_root="$framework/composeResources/cascadeeditor.editor.generated.resources/drawable"
    for resource_file in "${REQUIRED_RESOURCE_FILES[@]}"; do
        [[ -f "$resources_root/$resource_file" ]] ||
            fail "$slice is missing Compose resource $resource_file"
    done

    binary_uuid="$(dwarfdump --uuid "$binary" | awk '{print $2}')"
    dsym_uuid="$(dwarfdump --uuid "$dsym_binary" | awk '{print $2}')"
    [[ -n "$binary_uuid" && "$binary_uuid" == "$dsym_uuid" ]] ||
        fail "$slice dSYM UUID does not match its binary"

    licenses="$framework/Licenses"
    mkdir -p "$licenses/THIRD_PARTY_NOTICES"
    ditto "$REPO_ROOT/LICENSE" "$licenses/LICENSE"
    ditto "$REPO_ROOT/THIRD_PARTY_NOTICES" "$licenses/THIRD_PARTY_NOTICES"
done

COPYFILE_DISABLE=1 ditto -c -k --norsrc --keepParent "$STAGED_XCFRAMEWORK" "$ARCHIVE"
unzip -t "$ARCHIVE" >/dev/null

archive_roots="$(unzip -Z1 "$ARCHIVE" | awk -F/ 'NF { print $1 }' | sort -u)"
[[ "$archive_roots" == "CascadeEditor.xcframework" ]] ||
    fail "archive must contain exactly one CascadeEditor.xcframework root"

checksum="$(swift package compute-checksum "$ARCHIVE")"
archive_sha="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
[[ "$checksum" == "$archive_sha" ]] ||
    fail "SwiftPM and shasum checksums disagree"
printf '%s  %s\n' "$archive_sha" "$(basename "$ARCHIVE")" > "$ARCHIVE.sha256"

sed \
    -e "s/__VERSION__/$VERSION/g" \
    -e "s/__CHECKSUM__/$checksum/g" \
    "$REPO_ROOT/distribution/swift-package/Package.swift.template" \
    > "$PACKAGE_ROOT/Package.swift"
sed \
    -e "s/__VERSION__/$VERSION/g" \
    "$REPO_ROOT/distribution/swift-package/README.md.template" \
    > "$PACKAGE_ROOT/README.md"
ditto "$REPO_ROOT/LICENSE" "$PACKAGE_ROOT/LICENSE"
ditto "$REPO_ROOT/THIRD_PARTY_NOTICES" "$PACKAGE_ROOT/THIRD_PARTY_NOTICES"

(cd "$PACKAGE_ROOT" && swift package dump-package >/dev/null)

# Keep the checked-out smoke-test project directly buildable in Xcode. This
# ignored copy comes from the final publication ZIP, not Gradle's framework
# directory, so local manual testing exercises the same packaged artifact.
rm -rf "$LOCAL_CONSUMER_XCFRAMEWORK"
mkdir -p "$LOCAL_CONSUMER_ARTIFACTS"
unzip -q "$ARCHIVE" -d "$LOCAL_CONSUMER_ARTIFACTS"
[[ -d "$LOCAL_CONSUMER_XCFRAMEWORK" ]] ||
    fail "archive did not install the local consumer XCFramework"

echo "XCFramework archive: $ARCHIVE"
echo "SHA-256: $archive_sha"
echo "Swift package files: $PACKAGE_ROOT"
echo "Xcode smoke-test artifact: $LOCAL_CONSUMER_XCFRAMEWORK"
