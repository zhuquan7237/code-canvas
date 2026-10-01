#!/usr/bin/env python3
"""Generate docs/update/latest.json from a real, already-built and signed APK.

Never invents a hash, size or URL: every field comes from the file on disk plus the
command line. Run this *after* the release asset is uploaded, then commit the manifest,
so the app's updater only ever sees a version that genuinely exists.

Usage:
  python scripts/release.py --apk app/build/outputs/apk/release/app-release.apk \\
      --version-name 0.1.2 --version-code 3 --tag v0.1.2 \\
      --asset-name app-release.apk --notes "修复...(见 RELEASE-v0.1.2.md)"
"""
import argparse
import hashlib
import json
import pathlib
import sys

REPO = "zhuquan7237/code-canvas"
MAX_APK_BYTES = 8 * 1024 * 1024


def sha256_of(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_notes(notes_arg: str, notes_file: str | None):
    """Notes may be given inline or as a path. A path-shaped value that does not exist is an
    error: silently shipping the path as the user-visible release note is how a dialog ends up
    showing 'C:\\...\\notes.md'."""
    def read(path: str):
        p = pathlib.Path(path)
        if p.is_file():
            text = p.read_text(encoding="utf-8").strip()
            if not text:
                print(f"notes file is empty: {p}", file=sys.stderr)
                return None
            print(f"notes read from {p} ({len(text)} chars)")
            return text
        return None

    if not notes_arg and not notes_file:
        print("pass --notes (text or path) or --notes-file", file=sys.stderr)
        return None

    if notes_file:
        text = read(notes_file)
        if text is None:
            print(f"notes file not found or empty: {notes_file}", file=sys.stderr)
        return text

    from_file = read(notes_arg)
    if from_file is not None:
        return from_file
    looks_like_path = any(sep in notes_arg for sep in ("/", "\\")) or \
        notes_arg.strip().lower().endswith((".md", ".txt"))
    if looks_like_path:
        print(f"--notes looks like a path but no such file: {notes_arg}\n"
              f"Pass the text itself, or create the file first.", file=sys.stderr)
        return None
    return notes_arg.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--tag", required=True, help="git tag, e.g. v0.1.2")
    parser.add_argument("--asset-name", default="app-release.apk")
    parser.add_argument("--notes", default=None,
                        help="release notes text, or a path to a file containing them")
    parser.add_argument("--notes-file", default=None,
                        help="read the release notes from this file (same as passing its path to --notes)")
    parser.add_argument("--apk-url", default=None,
                        help="primary download address; defaults to the GitHub release asset")
    parser.add_argument("--fallback-apk-url", default=None,
                        help="optional mirror tried when the primary address fails")
    parser.add_argument("--out", default="docs/update/latest.json")
    parser.add_argument("--also-out", default=None,
                        help="write the same manifest to a second path (e.g. the copy for the mirror host)")
    args = parser.parse_args()

    notes = resolve_notes(args.notes, args.notes_file)
    if notes is None:
        return 5

    apk = pathlib.Path(args.apk)
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2
    size = apk.stat().st_size
    if size > MAX_APK_BYTES:
        print(f"APK is {size} bytes, above the {MAX_APK_BYTES} byte updater cap", file=sys.stderr)
        return 3
    if args.version_code <= 0:
        print("versionCode must be positive", file=sys.stderr)
        return 4

    manifest = {
        "schemaVersion": 1,
        "versionName": args.version_name,
        "versionCode": args.version_code,
        "apkUrl": args.apk_url or f"https://github.com/{REPO}/releases/download/{args.tag}/{args.asset_name}",
        "sha256": sha256_of(apk),
        "size": size,
        "notes": notes,
    }
    if args.fallback_apk_url:
        manifest["fallbackApkUrl"] = args.fallback_apk_url

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.also_out:
        mirror = pathlib.Path(args.also_out)
        mirror.parent.mkdir(parents=True, exist_ok=True)
        mirror.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {mirror} ({mirror.stat().st_size} bytes)")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(f"\nwrote {out} ({out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
