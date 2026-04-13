#!/usr/bin/env python3
"""
Public safety scanner for VARYNX public repository gate.

Fails if tracked content contains:
- obvious credentials/private keys
- machine-specific personal paths
- signing material tracked in git
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

FORBIDDEN_CONTENT_PATTERNS = [
    r"BEGIN [A-Z ]*PRIVATE KEY",
    r"ghp_[A-Za-z0-9]{20,}",
    r"AIza[0-9A-Za-z\-_]{20,}",
    r"xox[baprs]-",
    r"C:\\Users\\",
    r"/Users/",
    r"AppData\\",
]

FORBIDDEN_FILE_PATTERNS = [
    r"\.jks$",
    r"\.keystore$",
    r"\.p12$",
    r"\.pfx$",
    r"\.pem$",
    r"\.der$",
    r"\.mobileprovision$",
]

EXCLUDE_PREFIXES = (
    "build/",
    ".gradle/",
)

SELF_PATH = "tools/security/public_safety_scan.py"


def run_git(*args: str) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr)
        raise SystemExit(proc.returncode)
    return proc.stdout


def tracked_files() -> list[str]:
    out = run_git("ls-files")
    files: list[str] = []
    for line in out.splitlines():
        if not line:
            continue
        if line.startswith(EXCLUDE_PREFIXES):
            continue
        files.append(line)
    return files


def scan_files(files: list[str]) -> list[str]:
    violations: list[str] = []

    compiled_content = [re.compile(p) for p in FORBIDDEN_CONTENT_PATTERNS]
    compiled_file = [re.compile(p) for p in FORBIDDEN_FILE_PATTERNS]

    for rel in files:
        if any(p.search(rel) for p in compiled_file):
            violations.append(f"forbidden tracked file type: {rel}")

        # Skip scanning this scanner source text for its own regex marker literals.
        if rel == SELF_PATH:
            continue

        path = ROOT / rel
        if not path.is_file():
            continue

        try:
            content = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue

        for pat in compiled_content:
            if pat.search(content):
                violations.append(f"forbidden content pattern '{pat.pattern}' in {rel}")

    return violations


def main() -> int:
    files = tracked_files()
    violations = scan_files(files)

    if violations:
        print("PUBLIC SAFETY SCAN: FAILED")
        for v in violations:
            print(f" - {v}")
        return 1

    print("PUBLIC SAFETY SCAN: PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
