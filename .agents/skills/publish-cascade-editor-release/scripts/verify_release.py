#!/usr/bin/env python3
"""Verify CascadeEditor's local release state without reading credentials."""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path


SEMVER_RE = re.compile(r"^(?:v)?(\d+)\.(\d+)\.(\d+)$")
RELEASE_TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
REQUIRED_FILES = (
    "CHANGELOG.md",
    "README.md",
    "gradle.properties",
    "THIRD_PARTY_NOTICES/iOS-SDK-DEPENDENCIES.md",
    "docs/iOsPublication.md",
    "docs/iOsNativeSdk.md",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify preflight, prepared, or committed Cascade release state."
    )
    parser.add_argument("version", help="Release version: MAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH")
    parser.add_argument(
        "--repo-root",
        default=".",
        help="CascadeEditor repository root (default: current directory)",
    )
    parser.add_argument(
        "--phase",
        required=True,
        choices=("preflight", "prepared", "committed"),
    )
    return parser.parse_args()


def run_git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ("git", *args),
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"git {' '.join(args)} failed: {detail}")
    return result.stdout.strip()


def parse_version(raw: str) -> tuple[str, tuple[int, int, int]]:
    match = SEMVER_RE.fullmatch(raw)
    if not match:
        raise ValueError("version must match MAJOR.MINOR.PATCH")
    parts = tuple(int(value) for value in match.groups())
    return ".".join(str(value) for value in parts), parts


def first_h2(markdown: str) -> str | None:
    match = re.search(r"^##\s+(.+?)\s*$", markdown, flags=re.MULTILINE)
    return match.group(1) if match else None


def section(markdown: str, heading: str) -> str | None:
    match = re.search(
        rf"^##\s+{re.escape(heading)}\s*$\n(.*?)(?=^##\s+|\Z)",
        markdown,
        flags=re.MULTILINE | re.DOTALL,
    )
    return match.group(1) if match else None


def merged_release_tags(root: Path) -> list[tuple[tuple[int, int, int], str]]:
    tags: list[tuple[tuple[int, int, int], str]] = []
    output = run_git(root, "tag", "--merged", "HEAD")
    for tag in output.splitlines():
        match = RELEASE_TAG_RE.fullmatch(tag)
        if match:
            tags.append((tuple(int(value) for value in match.groups()), tag))
    return sorted(tags)


def verify_common(
    root: Path,
    version: str,
    version_parts: tuple[int, int, int],
    errors: list[str],
) -> None:
    for relative in REQUIRED_FILES:
        if not (root / relative).is_file():
            errors.append(f"missing required file: {relative}")

    try:
        top = Path(run_git(root, "rev-parse", "--show-toplevel")).resolve()
        if top != root:
            errors.append(f"--repo-root is not the Git top-level: {root}")
    except RuntimeError as exc:
        errors.append(str(exc))
        return

    branch = run_git(root, "branch", "--show-current")
    if branch != "main":
        errors.append(f"release branch must be main, found {branch or 'detached HEAD'}")

    origin = run_git(root, "remote", "get-url", "origin", check=False)
    if not re.search(r"(?:github\.com[:/])linreal/cascade-editor(?:\.git)?$", origin):
        errors.append("origin is not linreal/cascade-editor")

    tags = merged_release_tags(root)
    if tags and version_parts <= tags[-1][0]:
        errors.append(
            f"version {version} must be greater than latest reachable tag {tags[-1][1]}"
        )

    tag = f"v{version}"
    if run_git(root, "rev-parse", "-q", "--verify", f"refs/tags/{tag}", check=False):
        errors.append(f"release tag already exists locally: {tag}")


def verify_preflight(root: Path, errors: list[str]) -> None:
    if run_git(root, "status", "--porcelain"):
        errors.append("preflight requires a clean worktree")

    local_head = run_git(root, "rev-parse", "HEAD")
    origin_main = run_git(root, "rev-parse", "origin/main", check=False)
    if not origin_main:
        errors.append("origin/main is unavailable; fetch origin first")
    elif local_head != origin_main:
        errors.append("local main must exactly equal origin/main at preflight")


def verify_release_files(root: Path, version: str, errors: list[str]) -> None:
    values = re.findall(
        r"^VERSION_NAME=(.+?)\s*$",
        (root / "gradle.properties").read_text(encoding="utf-8"),
        flags=re.MULTILINE,
    )
    if values != [version]:
        errors.append(f"gradle.properties must contain exactly VERSION_NAME={version}")

    changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    expected_heading = f"[{version}] - {dt.date.today().isoformat()}"
    if first_h2(changelog) != expected_heading:
        errors.append(f"first changelog release must be: ## {expected_heading}")
    newest = section(changelog, expected_heading)
    if newest is None:
        errors.append("new changelog section is missing")
    else:
        if not re.search(
            r"^###\s+(Added|Changed|Deprecated|Removed|Fixed|Security)\s*$",
            newest,
            flags=re.MULTILINE,
        ):
            errors.append("new changelog section needs a supported change heading")
        if not re.search(r"^-\s+\S", newest, flags=re.MULTILINE):
            errors.append("new changelog section needs at least one user-facing bullet")

    readme = (root / "README.md").read_text(encoding="utf-8")
    quick_start = section(readme, "Quick Start")
    coordinate = f'implementation("io.github.linreal:cascade-editor:{version}")'
    if quick_start is None:
        errors.append("README.md has no Quick Start section")
    elif quick_start.count(coordinate) != 1:
        errors.append(f"README Quick Start must contain exactly: {coordinate}")

    notices = (
        root / "THIRD_PARTY_NOTICES/iOS-SDK-DEPENDENCIES.md"
    ).read_text(encoding="utf-8")
    notice_pattern = re.compile(
        rf"`CascadeEditor\.xcframework`\s+for version\s+{re.escape(version)}\b",
        flags=re.DOTALL,
    )
    if not notice_pattern.search(notices):
        errors.append("iOS SDK dependency notice does not name the release version")

    publication = (root / "docs/iOsPublication.md").read_text(encoding="utf-8")
    required_publication_fragments = (
        f"For the current publication, the version is `{version}`.",
        f"scripts/package-ios-sdk.sh {version}",
        f"build/ios-release/{version}/CascadeEditor.xcframework.zip",
        f'git tag -a v{version} -m "CascadeEditor {version}"',
        f"git push origin v{version}",
    )
    for fragment in required_publication_fragments:
        if fragment not in publication:
            errors.append(f"iOS publication runbook is missing current example: {fragment}")
    if "git push origin main" not in publication:
        errors.append("iOS publication runbook must push main before the tag")

    native_doc = (root / "docs/iOsNativeSdk.md").read_text(encoding="utf-8")
    current_literals = re.findall(
        r"`CascadeEditorSdk\.version: String`[^\n]*currently\s+`\"([0-9]+\.[0-9]+\.[0-9]+)\"`",
        native_doc,
    )
    if current_literals and current_literals != [version]:
        errors.append(
            "docs/iOsNativeSdk.md has a stale literal CascadeEditorSdk.version"
        )

    diff_check = subprocess.run(
        ("git", "diff", "--check"),
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if diff_check.returncode != 0:
        errors.append(f"git diff --check failed:\n{diff_check.stdout.strip()}")


def verify_prepared(root: Path, errors: list[str]) -> None:
    status = run_git(root, "status", "--porcelain")
    if not status:
        errors.append("prepared phase requires uncommitted release changes")
        return
    changed = {
        line[3:].strip()
        for line in status.splitlines()
        if len(line) >= 4 and not line.startswith("??")
    }
    required_changes = {
        "CHANGELOG.md",
        "README.md",
        "gradle.properties",
        "THIRD_PARTY_NOTICES/iOS-SDK-DEPENDENCIES.md",
        "docs/iOsPublication.md",
    }
    missing = sorted(required_changes - changed)
    if missing:
        errors.append(f"required release files are not changed: {', '.join(missing)}")

    local_head = run_git(root, "rev-parse", "HEAD")
    origin_main = run_git(root, "rev-parse", "origin/main", check=False)
    if not origin_main:
        errors.append("origin/main is unavailable; fetch origin first")
    elif local_head != origin_main:
        errors.append("prepared changes must still be based exactly on origin/main")


def verify_committed(root: Path, version: str, errors: list[str]) -> None:
    if run_git(root, "status", "--porcelain"):
        errors.append("committed phase requires a clean worktree")

    expected = f"release v{version} preparation"
    subject = run_git(root, "log", "-1", "--pretty=%s")
    if subject != expected:
        errors.append(f"release commit subject must be exactly: {expected}")

    head = run_git(root, "rev-parse", "HEAD")
    parent = run_git(root, "rev-parse", "HEAD^", check=False)
    origin_main = run_git(root, "rev-parse", "origin/main", check=False)
    if origin_main not in (head, parent):
        errors.append(
            "origin/main must equal the release commit or its first parent before tagging"
        )


def main() -> int:
    args = parse_args()
    try:
        version, version_parts = parse_version(args.version)
    except ValueError as exc:
        print(f"ERROR: {exc}")
        return 2

    root = Path(args.repo_root).expanduser().resolve()
    errors: list[str] = []

    try:
        verify_common(root, version, version_parts, errors)
        if args.phase == "preflight":
            verify_preflight(root, errors)
        else:
            verify_release_files(root, version, errors)
            if args.phase == "prepared":
                verify_prepared(root, errors)
            else:
                verify_committed(root, version, errors)
    except (OSError, RuntimeError) as exc:
        errors.append(str(exc))

    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"Release verification failed with {len(errors)} error(s).")
        return 1

    print(f"Release verification passed for {version} ({args.phase}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
