#!/usr/bin/env bash
#
# Validates a publication ZIP from the point of view of a clean external app.
# The fixture intentionally has no direct link to this repository's Gradle
# outputs; the supplied archive is its only SDK input.
set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ARCHIVE="${1:-}"
readonly FIXTURE="$REPO_ROOT/iosConsumerSmokeTest"
readonly OUTPUT_ROOT="$REPO_ROOT/build/ios-consumer-smoke"
readonly PROJECT_NAME="CascadeEditorConsumerSmoke"
readonly SCHEME="CascadeEditorConsumerSmoke"
readonly APP_BUNDLE_ID="io.github.linreal.CascadeEditorConsumerSmoke"

fail() {
    echo "error: $*" >&2
    exit 1
}

inspect_app_bundle() {
    local app="$1"
    local label="$2"
    local framework="$app/Frameworks/CascadeEditor.framework"
    local framework_binary="$framework/CascadeEditor"

    [[ -d "$app" ]] || fail "$label app was not produced"
    [[ -f "$framework_binary" ]] || fail "$label app does not embed the dynamic framework"
    [[ -f "$framework/PrivacyInfo.xcprivacy" ]] || fail "$label app is missing the privacy manifest"
    [[ -d "$framework/Licenses/THIRD_PARTY_NOTICES" ]] ||
        fail "$label app is missing dependency notices"
    [[ ! -d "$app/compose-resources" ]] ||
        fail "$label app unexpectedly depends on app-root Compose resources"

    for resource_file in \
        ic_hide_keyboard.xml \
        ic_link.xml \
        ic_format_indent_increase.xml \
        ic_format_indent_decrease.xml; do
        find "$framework/composeResources" -name "$resource_file" -type f | grep -q . ||
            fail "$label app framework is missing $resource_file"
    done

    file "$framework_binary" | grep -q "dynamically linked shared library" ||
        fail "$label app's CascadeEditor binary is not dynamic"
    otool -L "$app/$PROJECT_NAME" |
        grep -q "@rpath/CascadeEditor.framework/CascadeEditor" ||
        fail "$label app executable is not linked through @rpath"
}

[[ -n "$ARCHIVE" ]] || fail "usage: scripts/validate-ios-consumer.sh <CascadeEditor.xcframework.zip>"
[[ -f "$ARCHIVE" ]] || fail "archive does not exist: $ARCHIVE"

work_root="$(mktemp -d "${TMPDIR:-/tmp}/cascade-ios-consumer.XXXXXX")"
trap 'rm -rf "$work_root"' EXIT

rm -rf "$OUTPUT_ROOT"
mkdir -p "$OUTPUT_ROOT"
ditto "$FIXTURE" "$work_root/$PROJECT_NAME"
mkdir -p "$work_root/$PROJECT_NAME/LocalPackage/Artifacts"
# package-ios-sdk.sh installs an ignored copy for convenient manual Xcode use.
# Remove it from the temporary fixture so this test still has exactly one SDK
# input: the archive passed on the command line.
rm -rf "$work_root/$PROJECT_NAME/LocalPackage/Artifacts/CascadeEditor.xcframework"
unzip -q "$ARCHIVE" -d "$work_root/$PROJECT_NAME/LocalPackage/Artifacts"

readonly PROJECT_ROOT="$work_root/$PROJECT_NAME"
readonly PROJECT="$PROJECT_ROOT/$PROJECT_NAME.xcodeproj"
readonly DERIVED_DATA="$OUTPUT_ROOT/DerivedData"
readonly RESULT_BUNDLE="$OUTPUT_ROOT/CascadeEditorConsumerSmoke.xcresult"

[[ -d "$PROJECT_ROOT/LocalPackage/Artifacts/CascadeEditor.xcframework" ]] ||
    fail "publication ZIP did not expand to one CascadeEditor.xcframework"
if grep -R -E -q "editor-ios-sdk|gradlew|Copy Compose Resources" "$PROJECT"; then
    fail "clean consumer project contains a repository-local SDK shortcut"
fi

xcodebuild \
    -quiet \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -resolvePackageDependencies

xcodebuild \
    -quiet \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -derivedDataPath "$DERIVED_DATA" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    build

readonly DEVICE_APP="$DERIVED_DATA/Build/Products/Release-iphoneos/$PROJECT_NAME.app"
inspect_app_bundle "$DEVICE_APP" "generic-device"

simulator_udid="${CASCADE_SIMULATOR_UDID:-}"
if [[ -z "$simulator_udid" ]]; then
    simulator_udid="$(
        xcrun simctl list devices available |
            awk -F '[()]' '/iPhone/ { print $2; exit }'
    )"
fi
[[ -n "$simulator_udid" ]] || fail "no available iPhone simulator found"
xcrun simctl boot "$simulator_udid" 2>/dev/null || true
xcrun simctl bootstatus "$simulator_udid" -b

xcodebuild \
    -quiet \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration Release \
    -destination "id=$simulator_udid" \
    -derivedDataPath "$DERIVED_DATA" \
    -resultBundlePath "$RESULT_BUNDLE" \
    test

readonly SIMULATOR_APP="$DERIVED_DATA/Build/Products/Release-iphonesimulator/$PROJECT_NAME.app"
inspect_app_bundle "$SIMULATOR_APP" "simulator"
codesign --verify --deep --strict "$SIMULATOR_APP"

xcrun xcresulttool get test-results summary --path "$RESULT_BUNDLE" > "$OUTPUT_ROOT/test-summary.txt"
mkdir -p "$OUTPUT_ROOT/attachments"
xcrun xcresulttool export attachments \
    --path "$RESULT_BUNDLE" \
    --output-path "$OUTPUT_ROOT/attachments" >/dev/null

echo "Clean iOS consumer validation passed."
echo "Test result: $RESULT_BUNDLE"
echo "Summary: $OUTPUT_ROOT/test-summary.txt"
echo "Screenshots: $OUTPUT_ROOT/attachments"
