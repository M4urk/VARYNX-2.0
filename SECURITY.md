# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 2.0.x | Yes (current) |
| 1.x | Maintenance only |

## Reporting a Vulnerability

If you discover a security vulnerability in VARYNX, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

### How to Report

1. Email the maintainer directly with a description of the vulnerability.
2. Include steps to reproduce the issue, if possible.
3. Provide your assessment of severity (Critical / High / Medium / Low).

### What to Expect

- Acknowledgement within 48 hours.
- An initial assessment within 7 days.
- A fix or mitigation plan within 30 days for confirmed vulnerabilities.
- Credit in the release notes (unless you prefer to remain anonymous).

### Scope

The following are in scope for security reports:

- Mesh transport encryption or authentication bypasses
- Trust graph manipulation or poisoning
- Replay attacks against the sync protocol
- Local privilege escalation via the guardian service
- IPC authentication bypasses
- Data leakage of private keys or trust state

### Out of Scope

- Vulnerabilities requiring physical access to an unlocked device
- Denial-of-service attacks against the local daemon
- Issues in third-party dependencies (report upstream instead)
- Feature requests or non-security bugs (use GitHub Issues)

## Security Design Principles

VARYNX is built on the following security foundations:

- **Zero cloud** — all data stays on your devices, no outbound connections
- **Zero telemetry** — no usage tracking, analytics, or data collection
- **Encrypt-then-Sign** — all mesh payloads use AES-256-GCM encryption and Ed25519 signatures
- **Non-transitive trust** — every device must be explicitly approved by the user
- **Replay protection** — nonce and timestamp validation on all mesh messages
- **Local-only IPC** — desktop service binds to localhost with token authentication
- **Fail-closed** — the system defaults to a secure state on errors

## Repository Exposure Controls

To prevent unauthorized reproduction and accidental disclosure:

- **Core/engine/security code stays private** in an internal repository.
- **Public repository is shell/docs only** (UI shell, public-safe docs, brand assets, non-sensitive scaffolding).
- **No signing material** (keystores, certificates, provisioning files) may be tracked.
- **No personal/internal paths** (for example local user profile paths) may be tracked.
- **No auth tokens/secrets** may be embedded in code, scripts, logs, or docs.

## Public Release Gate

Any push intended for public visibility must pass all checks:

1. Public safety scan passes.
2. Signing material scan passes.
3. Personal path scan passes.
4. Private-core path exclusions are respected for public mirror exports.

Automation for this gate lives in:

- `.github/workflows/public-safety.yml`
- `tools/security/public_safety_scan.py`
- `tools/security/PUBLIC_REPO_SPLIT_POLICY.md`

## Disclosure Policy

We follow coordinated disclosure. Please allow reasonable time for a fix before any public disclosure.
