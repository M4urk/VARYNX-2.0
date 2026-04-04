# Changelog

All notable changes to VARYNX 2.0 are documented here.
This file contains public-safe information only. No internal implementation details are included.

---

## [2.0.0-beta] — 2026-04-04

### Added
- **Security Scan** — full device security audit across all active protection modules
- **Skimmer Detection** — Bluetooth skimmer scanning with pattern matching and RSSI analysis
- **QR / Scam Scanner** — QR code content analysis with URL risk scoring and scam heuristics
- **Desktop scanner tabs** — Security Scan, Skimmer Scan, and QR Scan available in the desktop Control Center
- **Android scanner screens** — dedicated screens for Security Scan, Skimmer Scan, and QR Scan on mobile
- **Desktop Settings redesign** — tabbed layout (General / Modules / About) with clean, polished UI
- **SECURITY.md** — public security disclosure policy
- **CHANGELOG.md** — public version history

### Changed
- Module count increased to 78 registered modules across 7 categories
- Protection module count increased to 17
- Desktop Control Center now has 13 navigation tabs
- Settings screen uses Armoury Crate-inspired tab layout with expandable sections
- Test assertions updated to reflect new module counts
- Stability certificate updated to public-safe format (393 tests, 0 failures, 12 modules)

### Fixed
- Desktop WebView error resilience — uncaught JS exceptions no longer crash tabs
- Desktop CSS animation fix for screen transitions
- Android WindowInsets padding applied consistently across all 8 screens
- Mesh TCP port configuration respected by composite transport
- Pairing session deadlock resolved
- Service loop stability improvements
- All compiler warnings eliminated

---

## [2.0.0-alpha] — 2026-03-15

### Added
- **Multi-platform mesh networking** — encrypted peer-to-peer sync across Android, Windows, Linux, WearOS, and embedded nodes
- **Guardian Organism** — four-domain detection cycle (Core, Engine, Reflex, Identity)
- **76 detection modules** across 7 categories (Protection, Reflex, Engine, Intelligence, Identity, Mesh, Platform)
- **Device roles** — Sentinel, Controller, Guardian, Notifier with role-aware capabilities
- **Desktop Control Center** — JavaFX WebView dashboard with WebSocket IPC
- **Android Guardian app** — Compose UI with 8 screens (Dashboard, Identity, Devices, Mesh, Topology, Roles, Threats, Settings)
- **Mesh protocol** — Ed25519 + X25519 key exchange, AES-256-GCM encrypted envelopes, 6-digit pairing
- **Trust graph** — non-transitive, local-first device trust with signed mutations
- **Vector clock sync** — conflict-free state merging across mesh peers
- **Consensus engine** — multi-node threat consensus with leader election and mesh lockdown
- **Delta compression** — bandwidth-efficient sync for constrained transports
- **Device role hierarchy** — tiered roles with capability restrictions
- **CI pipeline** — GitHub Actions build workflow
- **LICENSE** — proprietary All Rights Reserved license
- **README** — public project overview

### Platforms
- Android (phone / tablet) — Guardian
- Windows Desktop — Controller
- Linux (server / laptop) — Sentinel / Controller
- WearOS (watch) — Notifier
- Home Hub (Raspberry Pi) — Sentinel
- Pocket Node (portable) — Guardian
- Satellite Node (remote) — Guardian
