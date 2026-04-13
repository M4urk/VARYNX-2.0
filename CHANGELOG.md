# Changelog

All notable changes to VARYNX 2.0 are documented here.
This file contains public-safe information only. No internal implementation details are included.

---

## [2.0.0] — 2026-04-11

### Added
- **Desktop UI restructuring** — five top-level tabs (Overview, Devices, Activity, Mesh, Settings) replacing the previous twelve-tab layout
- **Per-node detail views** — click any node in the Devices tab to see Health & Modules, Events, Mesh telemetry, and Actions for that device
- **Node IPC commands** — ListNodes, GetNodeStatus, TriggerNodeScan, ResetNode, TrustNode, UntrustNode, RenameNode, GetMeshSnapshot, GetNodeMeshStatus
- **Node data models** — NodeId, NodeType, NodeSummary, NodeStatusSnapshot, MeshTelemetry, NodeModuleStatus, NodeEvent
- **Device type sub-tabs** — auto-generated filter tabs per node type (Desktop, Android, Linux, Pocket, Satellite, Home Hub)
- **Hello Organism launcher** — runnable JVM host for the guardian cycle via `:tools:helloOrganism`
- **Module activation matrix** — role-aware platform/module scoping for Android, Desktop, Linux, WearOS, HomeHub, Pocket, and Satellite nodes
- **Lightweight mesh pairing + desktop IPC skeletons** — repo-aligned helpers for pairing flow, status snapshots, and controller actions

### Changed
- **Desktop Control Center** consolidated from 12 tabs to 5 top-level tabs with embedded sub-views
- **Settings simplified** — General and About only, removed verbose descriptions
- **Release profile name** standardized to `VARYNX 2.0 v1`
- **Desktop telemetry polish** now surfaces mesh health, sync health, quorum state, scoring profile, and clearer event guidance
- **Release docs** updated for the stable `2.0.0` line

### Fixed
- **Role policy enforcement** hardened across controller, guardian, Linux, pocket, satellite, wear, and home-hub roles
- **Release audit coverage** verified across core tests and all current platform targets

### Verification
- **474 tests — 0 failures** across 40 test suites
- **Build verification passed** for Android app, WearOS, Windows service, Windows desktop, Linux, HomeHub, Pocket, Satellite, and tools

---

## [2.0.0-beta.2] — 2026-04-05

### Added
- **Manual trust management** — revoke trust from individual devices on Android (MeshScreen) and Desktop (IPC)
- **Trusted devices UI** — online/offline trusted peer sections in Android MeshScreen with revoke confirmation
- **PRIVACY.md** — comprehensive zero-collection privacy policy
- **CONTRIBUTING.md** — contribution guidelines and security reporting
- **CODE_OF_CONDUCT.md** — community standards and enforcement policy

### Fixed
- **Trust persistence** — trust graph now saved immediately on pairing across all 7 platforms (was only saved on shutdown — crash lost all pairings)
- **Linux identity regeneration** — Linux daemon no longer creates a new device identity on every restart (was calling `DeviceKeyStore.generate()` instead of `persistence.loadOrCreateKeyStore()`)
- **ProGuard rules** — comprehensive R8 keep rules for kotlinx-serialization, Ktor, ML Kit, CameraX, crypto, mesh protocol, coroutines, Compose, and DataStore (was empty — release APK would crash)
- **CoreDomain thread safety** — all module list access now synchronized with `withLock` to prevent race conditions between service and UI threads
- **IPC auth token leak** — service no longer prints authentication token to stdout on startup
- **POLICY_UPDATE handling** — mesh policy update messages now properly logged as threat events instead of silently ignored

### Changed
- README.md rewritten — comprehensive documentation matching V1 quality with badges, architecture diagram, module tables, test suite summary, and Founder Story
- Test count increased to 413 across 31 test suites (0 failures)

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
- Desktop Control Center now has 12 navigation tabs (QR Scan removed — camera-only feature)
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
