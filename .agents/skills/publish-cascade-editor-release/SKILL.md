---
name: publish-cascade-editor-release
description: Prepare, validate, tag, publish, and verify a CascadeEditor release from an explicit SemVer version. Use when asked to prepare or publish a Cascade Editor release, update release-facing versions and the curated changelog, run Kotlin/Compose and native iOS gates, commit release preparation, push the immutable source tag, publish all editor variants to Maven Central, monitor the iOS SwiftPM release workflow, or recover a failed release without moving published tags or assets.
---

# Publish Cascade Editor Release

Run the CascadeEditor release as a guarded, evidence-backed operation. Treat the
source tag, GitHub Release assets, Maven coordinates, and Swift package tag as
immutable once published.

## Input and scope

1. Require one version from the user. Accept `MAJOR.MINOR.PATCH` or
   `vMAJOR.MINOR.PATCH`, strip the optional `v`, and reject pre-release/build
   suffixes.
2. Derive `TAG=v$VERSION`.
3. Interpret “prepare” as local validation plus the release-preparation commit.
   Do not push, tag, or publish.
4. Interpret “publish” or “release” as authorization to execute the complete
   workflow, including pushing `main`, pushing the tag, and publishing to Maven
   Central. If the request is ambiguous, stop before the first remote mutation.
5. Preserve unrelated work. Never stage `local.properties`, credentials,
   generated build output, or unreviewed files.

## 1. Establish a safe baseline

1. Read `CLAUDE.local.md`, `ARCHITECTURE.md`, and
   `docs/iOsPublication.md` before acting.
2. Run `git status --short --branch`, inspect `origin`, and fetch `main` plus
   tags from `origin`.
3. Require:
   - the `linreal/cascade-editor` repository;
   - branch `main`;
   - a clean worktree;
   - local `main` exactly equal to `origin/main`;
   - no local or remote `$TAG`;
   - no GitHub Release for `$TAG`;
   - `$VERSION` strictly greater than the newest reachable release tag.
4. Run the bundled verifier:

   ```bash
   python3 <skill-directory>/scripts/verify_release.py \
     "$VERSION" --repo-root . --phase preflight
   ```

5. Confirm Java 17, Xcode command-line tools, Swift, GitHub CLI authentication,
   and an available iPhone simulator.
6. Verify publication readiness without printing secrets:
   - run `./gradlew :editor:checkSigningConfiguration`;
   - confirm the repository has the
     `CASCADE_EDITOR_IOS_RELEASE_TOKEN` Actions secret by name only;
   - confirm `linreal/cascade-editor-ios` exists with a `main` branch;
   - confirm the target Maven version is not already public.

Do not open or print `local.properties`. Let Gradle report missing signing or
Maven configuration by key name.

## 2. Build the user-facing release inventory

1. Resolve the last release tag from the fetched, reachable
   `vMAJOR.MINOR.PATCH` tags.
2. Inspect all work since it:

   ```bash
   git log --first-parent --reverse "$LAST_TAG"..HEAD
   git diff --stat "$LAST_TAG"..HEAD
   git diff --name-status "$LAST_TAG"..HEAD
   ```

3. Read the relevant implementation, public API dumps, documentation, samples,
   and tests for candidate changes. Do not derive the changelog from commit
   subjects alone.
4. Curate only user-material changes under the existing headings `Added`,
   `Changed`, `Deprecated`, `Removed`, `Fixed`, and `Security` as applicable.
   Prioritize new capabilities, behavior changes, compatibility, migrations,
   important reliability fixes, and dependency/platform changes that affect
   consumers.
5. Exclude CI-only changes, release plumbing, refactors with no consumer
   effect, typo fixes, minor test maintenance, and implementation trivia.
6. Call out breaking changes and required migrations explicitly. Do not claim a
   feature unless current source and public API evidence support it.

## 3. Update release-facing files

Apply these changes together:

1. Add the newest `CHANGELOG.md` section directly below the title:
   `## [$VERSION] - YYYY-MM-DD`. Use the local release date and concise,
   user-facing bullets.
2. Set the single `VERSION_NAME` in `gradle.properties` to `$VERSION`.
3. Update only the dependency coordinate in README’s `Quick Start` section to:
   `implementation("io.github.linreal:cascade-editor:$VERSION")`.
4. Update the distributed SDK version in
   `THIRD_PARTY_NOTICES/iOS-SDK-DEPENDENCIES.md`. Re-audit dependency versions
   only when build dependencies changed; do not confuse a dependency version
   such as kotlinx.coroutines with the CascadeEditor release version.
5. Update every current-publication example in `docs/iOsPublication.md`:
   the current version sentence, packaging command, validation artifact path,
   annotated tag command, and tag push command. Preserve historical immutable
   tag examples.
6. Update `CascadeEditorSdk.version` in `docs/iOsNativeSdk.md` if the document
   still states a literal current value.
7. Search for stale consumer-facing current-version assertions across tracked
   documentation and templates. Review matches individually; never perform a
   repository-wide numeric replacement.

Run the prepared-state verifier and inspect the diff:

```bash
python3 <skill-directory>/scripts/verify_release.py \
  "$VERSION" --repo-root . --phase prepared
git diff --check
git diff -- CHANGELOG.md README.md gradle.properties \
  THIRD_PARTY_NOTICES/iOS-SDK-DEPENDENCIES.md \
  docs/iOsPublication.md docs/iOsNativeSdk.md
```

## 4. Run release gates

Run the same consumer-relevant gates used by CI:

```bash
./gradlew \
  :editor:assembleDebug \
  :editor:allTests \
  :editor:apiCheck \
  :sample:allTests \
  :editor:checkSigningConfiguration
```

Then execute the native iOS runbook exactly for `$VERSION`:

```bash
scripts/package-ios-sdk.sh "$VERSION"
scripts/validate-ios-consumer.sh \
  "build/ios-release/$VERSION/CascadeEditor.xcframework.zip"
```

If `apiCheck` fails because an intentional public declaration changed, run the
appropriate `apiDump` task, inspect every generated API change, include only
intentional updates, and rerun all affected gates. Never refresh API dumps just
to silence an unexplained failure.

Require every command to exit successfully. Do not weaken, skip, or parallelize
the serialized native packaging steps. Keep the user informed during long
native builds.

## 5. Commit the preparation

1. Re-run the prepared-state verifier after all gates.
2. Review `git status`, the complete diff, generated API changes if any, and
   `git diff --check`.
3. Stage only reviewed release files.
4. Commit with the exact message:

   ```text
   release v$VERSION preparation
   ```

5. Run:

   ```bash
   python3 <skill-directory>/scripts/verify_release.py \
     "$VERSION" --repo-root . --phase committed
   ```

Stop here for a preparation-only request.

## 6. Push the immutable source release

1. Fetch `origin/main` and tags again.
2. Require `origin/main` to equal the release commit’s first parent. If `main`
   moved, stop, reconcile, and rerun the affected release gates.
3. Create the annotated tag locally:

   ```bash
   git tag -a "$TAG" -m "CascadeEditor $VERSION"
   ```

4. Push in the runbook order:

   ```bash
   git push origin main
   git push origin "$TAG"
   ```

5. Verify both remote `main` and `$TAG` resolve to the release commit. Never
   force-push, move, delete, or recreate a published release tag.

Pushing the tag starts `.github/workflows/release-ios-sdk.yml`.

## 7. Publish Maven artifacts

From the tagged release commit, run the user-required publication task:

```bash
./gradlew :editor:publishAllPublicationsToMavenCentralRepository
```

Require a zero exit status. The editor publication config enables automatic
Maven Central release; do not run a second release task speculatively. If the
command fails after uploading anything, inspect the Maven Central deployment
state before retrying so an immutable version is not duplicated or abandoned.

## 8. Verify both publication channels

1. Find the tag-triggered `Release iOS SDK` GitHub Actions run and monitor it to
   a successful conclusion. Use `gh run watch <run-id> --exit-status` when
   available.
2. Verify the GitHub Release for `$TAG` contains the XCFramework ZIP and its
   SHA-256 file.
3. Verify `linreal/cascade-editor-ios` has the matching unprefixed `$VERSION`
   tag and that its manifest resolves the public binary URL and checksum.
4. Poll Maven Central with a bounded wait until
   `io/github/linreal/cascade-editor/$VERSION/cascade-editor-$VERSION.pom` is
   public. Report “publication accepted; propagation pending” if the bounded
   wait expires—do not claim full completion early.
5. Confirm the local worktree is clean and still on the release commit.

Declare the operation complete only when the Maven publication command
succeeded and the iOS workflow succeeded. Distinguish successful publication
from downstream repository propagation in the debrief.

## Failure and recovery

- Before creating `$TAG`, fix the issue, rerun affected gates, amend with a new
  preparation commit if needed, and review again.
- After pushing `$TAG`, follow `docs/iOsPublication.md`. Do not move the tag or
  overwrite GitHub Release assets.
- If the iOS workflow fails before creating its GitHub Release, fix
  workflow-only problems on `main` and dispatch the existing source tag as the
  runbook permits.
- If immutable iOS assets already exist, recover from the recorded workflow
  evidence and checksum; never rebuild or clobber them.
- If a published binary is defective, prepare a new patch version.
- If Maven publication partially fails, inspect the Central deployment before
  retrying. Never reuse the version for different bytes.

## Debrief

Report:

- previous tag, new version, release commit, and source tag;
- curated changelog themes and every release-facing file changed;
- tests, API checks, iOS packaging/consumer validation, and signing check;
- Maven publication command result and public-coordinate status;
- iOS workflow, GitHub Release assets, and Swift package tag status;
- any pending propagation or manual recovery action.
