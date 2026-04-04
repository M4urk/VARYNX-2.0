# VARYNX 2.0 — Device Roles

## Overview

Every device in the VARYNX mesh is assigned a role that determines its capabilities, responsibilities, and resource allocation. Roles are set at identity generation time and encoded in the device's Ed25519 identity certificate.

## Role Definitions

### SENTINEL
**Platforms**: Linux home servers, Raspberry Pi, always-on hardware

| Property | Value |
|----------|-------|
| Priority | Highest in mesh |
| Engines | Full OS-level (process, network, USB, file integrity, startup) |
| Sync | LAN primary, BLE optional |
| Storage | Full threat log, trust graph authority |
| Power | Always-on assumed |

**Responsibilities**:
- Anchor node for mesh trust graph
- Persistent threat event storage and cross-device log aggregation
- Policy distribution to other mesh members
- Intelligence pack hosting and validation

---

### CONTROLLER
**Platforms**: Windows Desktop, Linux Desktop (laptops)

| Property | Value |
|----------|-------|
| Priority | High |
| Engines | Full OS-level (same as Sentinel) |
| Sync | LAN + BLE |
| Storage | Local threat log, trust graph replica |
| Power | Battery-aware (power-save mode when unplugged) |

**Responsibilities**:
- Full guardian loop with UI (dashboard, events, pairing, settings)
- Hub for pairing new devices (generates 6-digit codes)
- Engine diagnostics and mesh visualization
- Can promote to Sentinel when no dedicated Sentinel exists

---

### GUARDIAN
**Platforms**: Android phones, Android tablets

| Property | Value |
|----------|-------|
| Priority | Medium |
| Engines | Mobile-specific (scam, clipboard, BLE skimmer, NFC, network, app behavior, device state, permissions, install, runtime, overlay, notification, USB, sensor, app tamper) |
| Sync | LAN + BLE + NFC |
| Storage | Local threat log |
| Power | Battery-optimized (foreground service) |

**Responsibilities**:
- Primary personal protection device
- 15 protection modules running in four-domain organism loop
- Proximity-aware (BLE distance bracketing)
- Paired with NOTIFIER (watch) for alert relay

---

### NOTIFIER
**Platforms**: Wear OS smartwatches

| Property | Value |
|----------|-------|
| Priority | Low |
| Engines | Minimal (guardian-lite: alert display, sensor relay) |
| Sync | BLE only (paired to GUARDIAN phone) |
| Storage | Alert buffer (50 max) |
| Power | Ultra-low, duty-cycled |

**Responsibilities**:
- Display threat alerts with severity-aware haptics
- Relay sensor anomalies (heart rate, accelerometer, gyroscope)
- DND-mode aware alert filtering
- Complication data for watch face integration

---

### POCKET (Pocket Node)
**Platforms**: Raspberry Pi Zero, dedicated pocket hardware

| Property | Value |
|----------|-------|
| Priority | Low |
| Engines | Headless (proximity, BLE scanning, isolation detection) |
| Sync | BLE primary |
| Storage | Threat buffer (5-min decay) |
| Power | Battery-aware, duty-cycled BLE scanning |

**Responsibilities**:
- Passive BLE environment scanner
- Skimmer/swarm detection
- Mesh isolation monitoring
- Proximity-based alerts for unknown or threatening devices

---

## Role Hierarchy

```
SENTINEL (authority)
  └── CONTROLLER (full engines + UI)
        └── GUARDIAN (mobile protection)
              └── NOTIFIER (alert relay)
              └── POCKET (passive scanning)
```

## Trust Relationships

| Initiator | Target | Trust Flow |
|-----------|--------|------------|
| SENTINEL | Any | Distributes policy, hosts intelligence packs |
| CONTROLLER | GUARDIAN | Pairing hub, mesh visualization |
| GUARDIAN | NOTIFIER | Alert relay, state sync (one-way) |
| GUARDIAN | POCKET | Receives BLE scan results |
| Any | Any | Mutual authentication via 3-step handshake |

## Role Assignment

Roles are assigned at `DeviceKeyStore.generate(displayName, role)` and cannot be changed without re-keying. The role is encoded in the `DeviceIdentity` and broadcast in mesh heartbeats.

```kotlin
enum class DeviceRole {
    SENTINEL,    // Always-on anchor node
    CONTROLLER,  // Desktop with full UI
    GUARDIAN,    // Mobile primary device
    NOTIFIER,    // Watch alert relay
    POCKET       // Passive pocket scanner
}
```
