# VARYNX 2.0 — Threat Model

## 1. System Overview

VARYNX is a decentralized, offline-first, multi-device security mesh. It operates without cloud services, relying on local trust graphs, encrypted peer-to-peer sync, and deterministic guardian logic.

## 2. Trust Boundaries

| Boundary | Description |
|----------|-------------|
| **Device ↔ Device** | Mesh transport (LAN UDP/TCP, BLE, NFC). All payloads encrypted (AES-256-GCM) and signed (Ed25519). |
| **OS ↔ Guardian** | Guardian engines read system state via OS APIs (/proc, sysfs, ProcessHandle). Write access limited to local config. |
| **User ↔ Guardian** | CLI/UI interfaces. No authentication required (local-only daemon). Policy changes require signed config. |
| **Guardian ↔ Intelligence** | Intelligence packs are offline bundles. Validated against Ed25519 signatures before loading. No remote fetch. |

## 3. Assets

| Asset | Sensitivity | Location |
|-------|------------|----------|
| Device private keys (Ed25519, X25519) | CRITICAL | In-memory only, zeroed on shutdown |
| Trust graph | HIGH | Local storage, signed mutations |
| Guardian state | MEDIUM | Volatile memory |
| Threat event log | MEDIUM | Append-only ring buffer (500 entries) |
| Intelligence packs | LOW | Local filesystem, signature-verified |
| Policy config | HIGH | Signed, versioned, namespace-scoped |

## 4. Adversary Model

### A1: Local Userland Attacker
- **Capabilities**: Run arbitrary processes, modify user files, intercept network traffic
- **Mitigations**: Protected guardian process, signed configs, integrity-checked logs, process monitoring engines

### A2: Network Attacker
- **Capabilities**: Passive sniffing, active MITM, replay attacks, rogue device injection
- **Mitigations**: AES-256-GCM encryption, Ed25519 signatures, mutual authentication handshake (3-step), anti-replay nonces, HKDF-derived session keys

### A3: Rogue Mesh Peer
- **Capabilities**: Joined mesh with stolen/compromised credentials, can send malformed envelopes
- **Mitigations**: Trust graph with per-peer trust levels, signed envelopes, envelope size limits, vector clock validation

### A4: Physical Attacker
- **Capabilities**: USB device injection (BadUSB), NFC relay attacks, BLE sniffing
- **Mitigations**: USB rapid-connect detection, NFC anti-replay (2s window), BLE skimmer MAC prefix matching, proximity distance bracketing

## 5. Threat Matrix

| ID | Threat | Target | Severity | Mitigation |
|----|--------|--------|----------|------------|
| T1 | Process injection | Guardian daemon | CRITICAL | Self-protection, watchdog, signed binary |
| T2 | Trust graph poisoning | Mesh trust | HIGH | Signed mutations, vector clock consistency |
| T3 | Replay attack on sync | Mesh transport | HIGH | Nonce + timestamp, anti-replay window |
| T4 | Fork-bomb / resource exhaustion | Host OS | MEDIUM | Process engine spawn-rate detection |
| T5 | Rogue network interface | Host network | MEDIUM | Interface add/remove monitoring |
| T6 | BadUSB device injection | Host USB | HIGH | Rapid-connect storm detection, trusted device set |
| T7 | Config tampering | Policy engine | HIGH | Ed25519-signed configs with version enforcement |
| T8 | Log deletion/modification | Audit trail | MEDIUM | Append-only ring buffer, cross-device sync |
| T9 | Intelligence pack spoofing | Detection rules | HIGH | Ed25519 pack signatures, schema validation |
| T10 | BLE skimmer | Bluetooth stack | MEDIUM | MAC prefix matching, suspicious UUID detection |

## 6. Security Invariants

1. **Guardian process cannot be silently disabled** — watchdog and cross-device monitoring
2. **All mesh payloads are encrypted and signed** — no plaintext transport
3. **Policy changes require cryptographic signatures** — no unsigned config mutations
4. **Intelligence packs validated before loading** — schema, expiry, signature checks
5. **Private keys never leave memory** — zeroed on shutdown, no disk persistence
6. **Trust requires mutual authentication** — 3-step handshake with HKDF session derivation
7. **Logs are append-only** — ring buffer with sequence integrity

## 7. Residual Risks

| Risk | Severity | Notes |
|------|----------|-------|
| Kernel-level rootkits | HIGH | Beyond userland guardian scope |
| Firmware compromise | HIGH | Hardware trust anchor not available |
| Side-channel attacks | MEDIUM | Memory timing, cache probing |
| Physical device theft | MEDIUM | Keys in memory are volatile; at-rest not protected |
| Supply chain compromise | LOW | Build system integrity assumed |
