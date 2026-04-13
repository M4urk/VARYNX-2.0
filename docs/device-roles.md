# VARYNX 2.0 — Device Roles

## Overview

Every device in the VARYNX mesh is assigned a role that determines its capabilities, responsibilities, and resource allocation. Roles are set at identity generation time and encoded in the device's Ed25519 identity certificate.

Eight device classes across four tiers:
- **Controller tier**: CONTROLLER (trust authority, mesh controller)
- **Full guardian tier**: GUARDIAN (full scans, reflex, identity)
- **Hub tier**: HUB_HOME (LAN anchor), HUB_WEAR (watch aggregator)
- **Node tier**: NODE_LINUX (headless server), NODE_POCKET (on-person), NODE_SATELLITE (edge/offline), GUARDIAN_MICRO (constrained)

## Role Definitions

### CONTROLLER
**Platforms**: Windows Desktop

| Property | Value |
|----------|-------|
| Priority | 5 (Highest) |
| Engines | Full OS-level + mesh controller |
| Sync | LAN + BLE |
| Storage | Full trust graph authority, threat log |
| Power | Battery-aware |

**Responsibilities**:
- Trust graph authority — owns mesh policy
- Device dashboard (topology, roles, threats, health)
- Hub for pairing new devices (generates 6-digit codes)
- Engine diagnostics and mesh visualization
- Scan orchestration across mesh

---

### GUARDIAN
**Platforms**: Android phones, Android tablets

| Property | Value |
|----------|-------|
| Priority | 3 |
| Engines | Full mobile (scam, clipboard, BLE skimmer, NFC, network, app behavior, device state, permissions, install, runtime, overlay, notification, USB, sensor, app tamper) |
| Sync | LAN + BLE + NFC |
| Storage | Local threat log |
| Power | Battery-optimized (foreground service) |

**Responsibilities**:
- Primary personal protection device
- 15 protection modules running in four-domain organism loop
- Full reflex engine and identity engine
- Full mesh participant
- Forwards watch state into the mesh

---

### HUB_HOME
**Platforms**: Linux home servers, Raspberry Pi, NUC, NAS

| Property | Value |
|----------|-------|
| Priority | 4 |
| Engines | Full OS-level (process, network, USB, file integrity, startup) |
| Sync | LAN primary |
| Storage | Full threat log, trust graph, device registry |
| Power | Always-on assumed |

**Responsibilities**:
- Always-on LAN mesh anchor
- Device discovery and onboarding
- Local intelligence and cross-device threat correlation
- Persistent storage and log aggregation

---

### HUB_WEAR
**Platforms**: Desktop/server (JVM daemon)

| Property | Value |
|----------|-------|
| Priority | 2 |
| Engines | Micro guardian engine |
| Sync | LAN (TCP/UDP multicast) |
| Storage | Wear device state aggregation |
| Power | Always-on (daemon) |

**Responsibilities**:
- Wear-specific hub node running on desktop/server
- Aggregates Wear OS watch state
- Participates in mesh as a satellite node
- NOT a watch app — it's a desktop daemon

---

### NODE_LINUX
**Platforms**: Linux servers, headless VPS

| Property | Value |
|----------|-------|
| Priority | 3 |
| Engines | Full headless guardian (process, network, file integrity, USB) |
| Sync | LAN |
| Storage | Full threat log |
| Power | Always-on (server) |

**Responsibilities**:
- Headless server-grade monitoring
- Same engine as desktop (no UI)
- Full mesh participant

---

### NODE_POCKET
**Platforms**: Raspberry Pi Zero, USB compute sticks

| Property | Value |
|----------|-------|
| Priority | 1 |
| Engines | BLE/NFC scanning, proximity |
| Sync | LAN + BLE |
| Storage | Threat buffer (5-min decay) |
| Power | Battery-aware, duty-cycled |

**Responsibilities**:
- On-person micro guardian
- Passive BLE environment scanner
- Skimmer/swarm detection
- Proximity-based alerts

---

### NODE_SATELLITE
**Platforms**: Remote/edge hardware

| Property | Value |
|----------|-------|
| Priority | 1 |
| Engines | Micro guardian with offline buffer |
| Sync | LAN (intermittent) |
| Storage | Deep offline event buffer (1000 cap) |
| Power | Low-power, adaptive cycle |

**Responsibilities**:
- Offline-first operation
- Mesh repeater
- Autonomous threat escalation
- Burst sync on reconnection

---

### GUARDIAN_MICRO
**Platforms**: Wear OS smartwatches (actual watch APK)

| Property | Value |
|----------|-------|
| Priority | 1 |
| Engines | Minimal (alert display, sensor relay) |
| Sync | Data Layer to paired phone only |
| Storage | Alert buffer (50 max) |
| Power | Ultra-low, duty-cycled |

**Responsibilities**:
- Runs on the watch hardware
- Auto-mode only micro guardian
- Send sensor signals to paired phone
- Does NOT participate in LAN mesh directly

---

## Ecosystem Architecture

```
Wear OS Watch (GUARDIAN_MICRO)
        ↓ Data Layer
Android Phone (GUARDIAN)
        ↓ Mesh
Windows EXE (CONTROLLER)
Linux Daemon (NODE_LINUX)
Home Hub (HUB_HOME)
Pocket Node (NODE_POCKET)
Satellite Node (NODE_SATELLITE)
WearOS JVM Daemon (HUB_WEAR)
```

## Role Hierarchy

```
CONTROLLER (trust authority, weight 5)
  └── HUB_HOME (LAN anchor, weight 4)
        └── GUARDIAN (full mobile, weight 3)
        └── NODE_LINUX (headless server, weight 3)
              └── HUB_WEAR (wear aggregator, weight 2)
                    └── NODE_POCKET (on-person, weight 1)
                    └── NODE_SATELLITE (edge, weight 1)
                    └── GUARDIAN_MICRO (watch, weight 1)
```

## Role Assignment

Roles are assigned at `DeviceKeyStore.generate(displayName, role)` and cannot be changed without re-keying. The role is encoded in the `DeviceIdentity` and broadcast in mesh heartbeats.

```kotlin
enum class DeviceRole(val label: String) {
    CONTROLLER("Controller"),
    GUARDIAN("Guardian"),
    GUARDIAN_MICRO("Micro Guardian"),
    HUB_HOME("Home Hub"),
    HUB_WEAR("Wear Hub"),
    NODE_SATELLITE("Satellite"),
    NODE_POCKET("Pocket Node"),
    NODE_LINUX("Linux Node")
}
```
