# VARYNX Public Publish Checklist

Run this checklist before any push to public `origin/main`.

## Remote Discipline

- Confirm you are on public-safe branch (`main` shell/docs only).
- Confirm private source work is pushed to private remote first.
- Never push private engine/security branches to `origin`.

## Required Safety Gates

- Run: `python tools/security/public_safety_scan.py`
- Confirm CI workflow `.github/workflows/public-safety.yml` is active.
- Confirm local hooks are configured: `.githooks/pre-commit` and `.githooks/pre-push`.

## Forbidden in Public Push

- Mesh internals
- Baseline learning internals
- Vault/token internals
- Reflex logic internals
- Hub internals
- Signing artifacts (`.jks`, `.p12`, `.pem`, etc.)
- Secrets/tokens/keys in any file
- Personal machine paths (e.g. `C:\\Users\\...`)

## Final Verification

- `git diff --name-only origin/main...HEAD` shows only public-safe files.
- `git ls-remote --heads origin` exposes only intended public branches.
- If unsure, abort push and re-run scanner.
