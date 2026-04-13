# VARYNX PRIME — MASTER BLUEPRINT + HUB MESH SERVICE SPEC

**File:** `VARYNXPRIME_MASTER_BLUEPRINT.md`  
**Date:** April 12, 2026  
**Status:** Foundation Release  

---

## 0. Overview

**Scope:**  
Single, founder‑grade master file covering:

- Prime feature map  
- Prime OS architecture  
- Launch messaging  
- Differentiation matrix  
- Roadmap (Phase 1–3)
- Android engines (Battery Enforcement, Logs)  
- Desktop modules (Action Center, Integrity Monitor)  
- Prime Hub integration  
- Prime Desktop Node v2  
- **Prime Hub Mesh Service — full endpoint‑level spec with payloads + examples**
- **Prime Hub Identity Service — token issuance, validation, rotation**
- **Prime Hub Pairing Service — local trust establishment**

Drop‑in as the root blueprint for Prime + Hub + Nodes.

---

## 1. Prime Feature Map

```text
VARYNX PRIME — FEATURE MAP

CORE PRINCIPLES
- 100% offline
- 100% deterministic
- 0 telemetry
- 0 analytics
- 0 cloud dependencies
- Unified organism architecture
- System-level guardian engine
- Local-only mesh intelligence
- Hardware-backed identity

SYSTEM-LEVEL FEATURES
- Privileged binder services:
    - IVarynxGuardianService
    - IVarynxReflexService
    - IVarynxIdentityService
    - IVarynxMeshService
- Kernel-adjacent reflex engine
- Verified boot + organism attestation
- SELinux custom domains for organism modules
- System call anomaly detection
- Permission reflex rules
- Overlay guardian (anti-phishing, anti-spoofing)
- OS-level network enforcement
- Local-only mesh communication
- Local identity engine (TEE-backed)
- Local pairing engine
- Local threat intelligence engine
- Local behavior scoring + heuristics
- Local anomaly detection
- Local state machine for threat response

ANDROID PRIME FEATURES
- Prime Dashboard
- Prime Panels
- Prime Permission Flow
- Prime Reflex Log
- Prime Identity Console
- Prime Mesh Console
- Prime System Integrity Report
- Prime 24‑Hour Log History (exportable)
- Prime Battery Enforcement (Unrestricted auto-apply)

DESKTOP PRIME FEATURES
- Prime Desktop Guardian
- Prime Mesh Node
- Prime Identity Node
- Prime Reflex Alerts
- Prime Integrity Monitor
- Prime Action Center (actionable UI)
- Prime Export Console

DEVELOPER-FACING FEATURES
- Prime Build Pipeline
- Weekly AOSP patch merging
- Kernel hardening configs
- Verified boot signing keys
- Prime module loader
- Prime Hub integration layer
```

---

## 2. Prime OS Architecture

```text
VARYNX PRIME — SYSTEM ARCHITECTURE

[ HARDWARE ]
    • CPU
    • Memory
    • Secure Element (TEE)
    • Hardware Keystore

[ KERNEL LAYER ]
    • Kernel Hardening
    • Memory Tagging
    • Syscall Filters
    • Varynx Reflex Hooks (kernel-adjacent)

[ SYSTEM SERVICES LAYER ]
    • IVarynxGuardianService
    • IVarynxReflexService
    • IVarynxIdentityService
    • IVarynxMeshService
    • Prime Network Enforcement
    • Prime Permission Engine
    • Prime Battery Enforcement Engine

[ ORGANISM LAYER ]
    • Reflex Engine
    • Identity Engine
    • Mesh Engine
    • Pairing Engine
    • Threat Engine
    • State Machine Engine
    • Log Engine (24‑hour rolling buffer)
    • Export Engine

[ PRIME OS LAYER ]
    • Prime Dashboard
    • Prime Panels
    • Prime Permission Flow
    • Prime Overlay Guardian
    • Prime System Integrity Monitor
    • Prime Log History (24h)
    • Prime Battery Optimization Controller

[ USER SPACE ]
    • Apps
    • Services
    • UI
```

---

## 3. Prime Launch Message

```text
VARYNX PRIME — LAUNCH MESSAGE

Introducing VARYNX Prime — the first offline, deterministic security OS.

No cloud.
No telemetry.
No analytics.
No tracking.
No compromises.

VARYNX Prime is a system-level guardian built on a unified organism architecture.
Every reflex, every decision, every protection happens locally — on your device —
with zero data ever leaving it.

This is not another security app.
This is not another Android fork.
This is a new category: an offline security organism OS.

Built by a single founder.
Engineered with precision.
Designed for people who refuse to be profiled, tracked, or analyzed.

Where has this been?
Right here.
VARYNX Prime.
```

---

## 4. Prime Differentiation Matrix

```text
VARYNX PRIME — DIFFERENTIATION MATRIX

CAPABILITY                     | BIG COMPANIES | GRAPHENEOS | VARYNX PRIME
------------------------------|---------------|------------|--------------
Offline Threat Engine         | No            | Partial    | Yes (Full)
Unified Organism Architecture | No            | No         | Yes
Kernel-Adjacent Reflex        | No            | No         | Yes
Local Mesh Network            | No            | No         | Yes
Local Identity Engine         | No            | No         | Yes
Local Pairing Engine          | No            | No         | Yes
Zero Telemetry                | Impossible    | Yes        | Yes
Prime Hub Integration         | No            | No         | Yes
Cross-Device Organism Sync    | No            | No         | Yes
24h Log History (Local)       | No            | No         | Yes
Battery Enforcement Engine    | No            | No         | Yes
```

---

## 5. Prime Roadmap (Phase 1–3)

```text
PHASE 1 — PRIME FOUNDATION
- Prime OS architecture
- Guardian engine deepening
- Reflex engine v2
- Identity engine v2
- Mesh engine v2
- Battery Enforcement Engine
- 24h Log History Engine
- Prime Dashboard v1
- Prime Desktop Node v1
- Prime Hub integration layer

PHASE 2 — PRIME ORGANISM
- Full organism state machine
- Cross-device reflex sync
- Desktop Action Center
- Desktop Integrity Monitor
- Prime Panels v2
- Prime Permission Flow v2
- Prime Overlay Guardian v2
- Prime Export Console
- Prime OTA pipeline

PHASE 3 — PRIME SYSTEM MODE
- AOSP fork integration
- Kernel reflex hooks expansion
- SELinux domain hardening
- Verified boot chain integration
- Full OS-grade release
- Multi-device organism mesh
```

---

## 6. Android Engines

### 6.1 Battery Enforcement Engine

**Location:** `prime/android/BatteryEnforcementEngine.kt`

```kotlin
package prime.android.power

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri

interface BatteryEnforcementLogger {
    fun log(message: String)
}

class BatteryEnforcementEngine(
    private val context: Context,
    private val logger: BatteryEnforcementLogger
) {

    fun isUnrestricted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val result = pm.isIgnoringBatteryOptimizations(context.packageName)
        logger.log("BatteryEnforcement: isUnrestricted=$result")
        return result
    }

    fun requestUnrestricted() {
        if (isUnrestricted()) {
            logger.log("BatteryEnforcement: already unrestricted, no request needed")
            return
        }

        logger.log("BatteryEnforcement: launching ignore battery optimizations intent")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun onUserToggleChanged(enabled: Boolean) {
        logger.log("BatteryEnforcement: toggle changed -> $enabled")
        if (enabled) {
            requestUnrestricted()
        } else {
            logger.log("BatteryEnforcement: user disabled enforcement (no OS-level re-restriction possible)")
        }
    }
}
```

---

### 6.2 Log History Engine (24h + export)

**Location:** `prime/android/LogHistoryEngine.kt`

```kotlin
package prime.android.logs

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogCategory {
    REFLEX, IDENTITY, MESH, SYSTEM, INTEGRITY
}

data class PrimeLogEntry(
    val timestamp: Long,
    val category: LogCategory,
    val message: String
)

class LogHistoryEngine(
    private val maxWindowMillis: Long = 24L * 60L * 60L * 1000L
) {

    private val buffer = ConcurrentLinkedQueue<PrimeLogEntry>()

    fun add(category: LogCategory, message: String) {
        val entry = PrimeLogEntry(
            timestamp = System.currentTimeMillis(),
            category = category,
            message = message
        )
        buffer.add(entry)
        trim()
    }

    fun list(
        categories: Set<LogCategory>? = null
    ): List<PrimeLogEntry> {
        val now = System.currentTimeMillis()
        return buffer.filter {
            now - it.timestamp <= maxWindowMillis &&
                (categories == null || it.category in categories)
        }.sortedBy { it.timestamp }
    }

    fun clear() {
        buffer.clear()
    }

    fun exportToFile(target: File): File {
        val lines = list(null).joinToString(separator = "\n") { entry ->
            "${entry.timestamp},${entry.category},${entry.message.replace("\n", " ")}"
        }
        target.writeText(lines)
        return target
    }

    private fun trim() {
        val now = System.currentTimeMillis()
        while (true) {
            val head = buffer.peek() ?: break
            if (now - head.timestamp > maxWindowMillis) {
                buffer.poll()
            } else {
                break
            }
        }
    }
}
```

---

## 7. Desktop Modules

### 7.1 Prime Action Center

**Location:** `prime/desktop/action-center/ActionCenter.ts`

```typescript
export type ActionSeverity = 'info' | 'warning' | 'critical';

export interface ActionItem {
    id: string;
    timestamp: number;
    severity: ActionSeverity;
    title: string;
    description: string;
    actions: Array<{
        id: string;
        label: string;
    }>;
}

export interface ActionCenterBackend {
    listOpenIssues(): Promise<ActionItem[]>;
    performAction(issueId: string, actionId: string): Promise<void>;
}

export class ActionCenterController {
    constructor(private backend: ActionCenterBackend) {}

    async getIssues(): Promise<ActionItem[]> {
        return this.backend.listOpenIssues();
    }

    async handleAction(issueId: string, actionId: string): Promise<void> {
        await this.backend.performAction(issueId, actionId);
    }
}
```

---

### 7.2 Prime Integrity Monitor

**Location:** `prime/desktop/integrity/IntegrityMonitor.ts`

```typescript
export type IntegrityState = 'OK' | 'DEGRADED' | 'AT_RISK';

export interface IntegritySnapshot {
    state: IntegrityState;
    lastReflexTimestamp: number | null;
    lastMeshSyncTimestamp: number | null;
    identityStatus: 'BOUND' | 'UNBOUND' | 'ERROR';
    meshStatus: 'CONNECTED' | 'DISCONNECTED' | 'DEGRADED';
}

export interface IntegrityBackend {
    getSnapshot(): Promise<IntegritySnapshot>;
    subscribe(callback: (snapshot: IntegritySnapshot) => void): () => void;
}

export class IntegrityMonitorController {
    private unsubscribe: (() => void) | null = null;

    constructor(private backend: IntegrityBackend) {}

    async current(): Promise<IntegritySnapshot> {
        return this.backend.getSnapshot();
    }

    startListening(onUpdate: (snapshot: IntegritySnapshot) => void) {
        this.stopListening();
        this.unsubscribe = this.backend.subscribe(onUpdate);
    }

    stopListening() {
        if (this.unsubscribe) {
            this.unsubscribe();
            this.unsubscribe = null;
        }
    }
}
```

---

## 8. Prime Hub — Role & Overview

```text
PRIME HUB — ROLE
- Local coordinator for:
    - Identity (token issuance, validation, rotation)
    - Mesh (node registration, heartbeat, event broadcast)
    - Pairing (trust establishment between nodes)
    - Reflex routing (distribute threat events)
    - Log aggregation (collect 24h logs from all nodes)

SERVICES
- Mesh Service (node lifecycle, heartbeat, broadcasts)
- Identity Service (token management)
- Pairing Service (local trust)
- Log Service (aggregation)

PROPERTIES
- Local-only (Unix socket or localhost TCP)
- Encrypted (all traffic)
- Authenticated (identity tokens required)
- Deterministic message formats (JSON, one message per line)
```

---

## 9. Prime Hub Mesh Service (Full Endpoint Spec)

**Transport:**  
- Local TCP or Unix domain socket  
- JSON messages, one per line  
- All messages include `type` and `nodeId`

### 9.1 Message Envelope

```json
{
  "type": "string",
  "nodeId": "string",
  "payload": { }
}
```

### 9.2 Mesh Service Endpoints

1. **REGISTER_NODE** — Node registers with Hub
2. **HEARTBEAT** — Node alive check
3. **REFLEX_EVENT_BROADCAST** — Threat event fan-out
4. **INTEGRITY_EVENT_BROADCAST** — System state change fan-out
5. **MESH_STATUS_QUERY** — Query mesh topology

#### 9.2.1 REGISTER_NODE

**Direction:** Node → Hub  
**type:** `"REGISTER_NODE"`

**Request:**

```json
{
  "type": "REGISTER_NODE",
  "nodeId": "desktop-01",
  "payload": {
    "nodeType": "DESKTOP",
    "platform": "WINDOWS",
    "version": "2.0.0",
    "identityToken": "PRIME_ID_TOKEN_BASE64"
  }
}
```

**Response (Hub → Node):**

```json
{
  "type": "REGISTER_NODE_ACK",
  "nodeId": "desktop-01",
  "payload": {
    "accepted": true,
    "meshId": "mesh-001",
    "heartbeatIntervalMs": 15000
  }
}
```

#### 9.2.2 HEARTBEAT

**Direction:** Node → Hub  
**type:** `"HEARTBEAT"`

**Request:**

```json
{
  "type": "HEARTBEAT",
  "nodeId": "desktop-01",
  "payload": {
    "timestamp": 1712850000000,
    "state": "OK"
  }
}
```

**Response (Hub → Node, optional):**

```json
{
  "type": "HEARTBEAT_ACK",
  "nodeId": "desktop-01",
  "payload": {
    "meshState": "STABLE"
  }
}
```

#### 9.2.3 REFLEX_EVENT_BROADCAST

**Direction:** Hub → Node (fan‑out)  
**type:** `"REFLEX_EVENT"`

**Payload:**

```json
{
  "type": "REFLEX_EVENT",
  "nodeId": "hub",
  "payload": {
    "eventId": "reflex-123",
    "sourceNodeId": "android-01",
    "timestamp": 1712850005000,
    "severity": "CRITICAL",
    "category": "NETWORK",
    "message": "Suspicious outbound connection blocked",
    "details": {
      "destinationIp": "203.0.113.10",
      "destinationPort": 443
    }
  }
}
```

Nodes receiving this:
- Desktop Node → surfaces in Action Center  
- Android Node → logs to 24h history (if relevant)  

#### 9.2.4 INTEGRITY_EVENT_BROADCAST

**Direction:** Hub → Node (fan‑out)  
**type:** `"INTEGRITY_EVENT"`

**Payload:**

```json
{
  "type": "INTEGRITY_EVENT",
  "nodeId": "hub",
  "payload": {
    "eventId": "integrity-456",
    "sourceNodeId": "desktop-01",
    "timestamp": 1712850010000,
    "state": "DEGRADED",
    "reason": "Mesh heartbeat delayed",
    "metrics": {
      "lastHeartbeatMsAgo": 32000
    }
  }
}
```

#### 9.2.5 MESH_STATUS_QUERY

**Direction:** Node → Hub  
**type:** `"MESH_STATUS_QUERY"`

**Request:**

```json
{
  "type": "MESH_STATUS_QUERY",
  "nodeId": "desktop-01",
  "payload": {}
}
```

**Response:**

```json
{
  "type": "MESH_STATUS_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "meshId": "mesh-001",
    "nodes": [
      {
        "nodeId": "android-01",
        "nodeType": "ANDROID",
        "state": "OK",
        "lastHeartbeat": 1712850008000
      },
      {
        "nodeId": "desktop-01",
        "nodeType": "DESKTOP",
        "state": "DEGRADED",
        "lastHeartbeat": 1712850009000
      }
    ]
  }
}
```

---

## 10. Prime Hub Identity Service (Full Endpoint Spec)

### 10.1 Role

Identity Service is the local authority for:

- Issuing Prime identity tokens
- Validating tokens presented by nodes
- Rotating tokens deterministically
- Reporting identity binding status per node
- No cloud, no external CA, no exportable secrets

### 10.2 Identity Token Format

```json
{
  "version": 1,
  "subject": "android-01",
  "issuedAt": 1712850000000,
  "expiresAt": 1712936400000,
  "deviceClass": "ANDROID",
  "capabilities": [
    "MESH_MEMBER",
    "REFLEX_EMITTER",
    "INTEGRITY_REPORTER"
  ],
  "signature": "BASE64_SIGNATURE"
}
```

Signature is computed over all other fields using a local, hardware‑backed key.  
Token is non‑exportable in raw key form; only the signed blob is shared.

### 10.3 Identity Service Endpoints

1. **ISSUE_IDENTITY_TOKEN**
2. **VALIDATE_IDENTITY_TOKEN**
3. **ROTATE_IDENTITY_TOKEN**
4. **GET_IDENTITY_STATUS**

#### 10.3.1 ISSUE_IDENTITY_TOKEN

**Direction:** Node → Hub  
**type:** `"ISSUE_IDENTITY_TOKEN"`

**Request:**

```json
{
  "type": "ISSUE_IDENTITY_TOKEN",
  "nodeId": "android-01",
  "payload": {
    "nodeType": "ANDROID",
    "platform": "ANDROID_14",
    "requestedCapabilities": [
      "MESH_MEMBER",
      "REFLEX_EMITTER",
      "INTEGRITY_REPORTER"
    ]
  }
}
```

**Response (granted):**

```json
{
  "type": "ISSUE_IDENTITY_TOKEN_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "granted": true,
    "identityToken": {
      "version": 1,
      "subject": "android-01",
      "issuedAt": 1712850000000,
      "expiresAt": 1712936400000,
      "deviceClass": "ANDROID",
      "capabilities": [
        "MESH_MEMBER",
        "REFLEX_EMITTER",
        "INTEGRITY_REPORTER"
      ],
      "signature": "BASE64_SIGNATURE"
    }
  }
}
```

**Response (denied):**

```json
{
  "type": "ISSUE_IDENTITY_TOKEN_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "granted": false,
    "reason": "UNAUTHORIZED_NODE_TYPE"
  }
}
```

#### 10.3.2 VALIDATE_IDENTITY_TOKEN

**Direction:** Node → Hub  
**type:** `"VALIDATE_IDENTITY_TOKEN"`

**Request:**

```json
{
  "type": "VALIDATE_IDENTITY_TOKEN",
  "nodeId": "desktop-01",
  "payload": {
    "identityToken": {
      "version": 1,
      "subject": "desktop-01",
      "issuedAt": 1712850000000,
      "expiresAt": 1712936400000,
      "deviceClass": "DESKTOP",
      "capabilities": [
        "MESH_MEMBER",
        "REFLEX_CONSUMER",
        "INTEGRITY_REPORTER"
      ],
      "signature": "BASE64_SIGNATURE"
    }
  }
}
```

**Response (valid):**

```json
{
  "type": "VALIDATE_IDENTITY_TOKEN_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "valid": true,
    "reason": null
  }
}
```

**Response (invalid):**

```json
{
  "type": "VALIDATE_IDENTITY_TOKEN_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "valid": false,
    "reason": "EXPIRED"
  }
}
```

#### 10.3.3 ROTATE_IDENTITY_TOKEN

**Direction:** Node → Hub  
**type:** `"ROTATE_IDENTITY_TOKEN"`

Used when token is near expiry or capabilities change.

**Request:**

```json
{
  "type": "ROTATE_IDENTITY_TOKEN",
  "nodeId": "android-01",
  "payload": {
    "currentToken": {
      "version": 1,
      "subject": "android-01",
      "issuedAt": 1712850000000,
      "expiresAt": 1712936400000,
      "deviceClass": "ANDROID",
      "capabilities": [
        "MESH_MEMBER",
        "REFLEX_EMITTER",
        "INTEGRITY_REPORTER"
      ],
      "signature": "BASE64_SIGNATURE"
    },
    "requestedCapabilities": [
      "MESH_MEMBER",
      "REFLEX_EMITTER",
      "INTEGRITY_REPORTER"
    ]
  }
}
```

**Response (success):**

```json
{
  "type": "ROTATE_IDENTITY_TOKEN_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "rotated": true,
    "identityToken": {
      "version": 1,
      "subject": "android-01",
      "issuedAt": 1712936401000,
      "expiresAt": 1713022800000,
      "deviceClass": "ANDROID",
      "capabilities": [
        "MESH_MEMBER",
        "REFLEX_EMITTER",
        "INTEGRITY_REPORTER"
      ],
      "signature": "BASE64_SIGNATURE_NEW"
    }
  }
}
```

**Response (denied):**

```json
{
  "type": "ROTATE_IDENTITY_TOKEN_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "rotated": false,
    "reason": "INVALID_CURRENT_TOKEN"
  }
}
```

#### 10.3.4 GET_IDENTITY_STATUS

**Direction:** Node → Hub  
**type:** `"GET_IDENTITY_STATUS"`

**Request:**

```json
{
  "type": "GET_IDENTITY_STATUS",
  "nodeId": "desktop-01",
  "payload": {}
}
```

**Response:**

```json
{
  "type": "GET_IDENTITY_STATUS_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "bound": true,
    "subject": "desktop-01",
    "deviceClass": "DESKTOP",
    "capabilities": [
      "MESH_MEMBER",
      "REFLEX_CONSUMER",
      "INTEGRITY_REPORTER"
    ],
    "expiresAt": 1712936400000
  }
}
```

---

## 11. Prime Hub Pairing Service (Full Endpoint Spec)

### 11.1 Role

Pairing Service is the local authority for:

- Establishing trust between nodes (Android, Desktop, Linux)
- Performing local key exchange
- Recording local attestation results
- Listing and revoking pairings
- No cloud, no remote servers, no exportable long‑term secrets

### 11.2 Pairing Model

```json
{
  "pairingId": "string",
  "nodeA": "string",
  "nodeB": "string",
  "createdAt": 1712850000000,
  "state": "ACTIVE",
  "attestation": {
    "nodeAResult": "OK",
    "nodeBResult": "OK"
  }
}
```

States: `"PENDING"` | `"ACTIVE"` | `"REVOKED"`

### 11.3 Pairing Service Endpoints

1. **INITIATE_PAIRING**
2. **CONFIRM_PAIRING**
3. **LIST_PAIRED_NODES**
4. **REVOKE_PAIRING**
5. **PAIRING_STATUS**

#### 11.3.1 INITIATE_PAIRING

**Direction:** Node → Hub  
**type:** `"INITIATE_PAIRING"`

**Request:**

```json
{
  "type": "INITIATE_PAIRING",
  "nodeId": "android-01",
  "payload": {
    "targetNodeId": "desktop-01",
    "channel": "LOCAL_MESH",
    "attestationData": {
      "androidBuildFingerprint": "fingerprint-string",
      "primeVersion": "1.0.0"
    }
  }
}
```

**Response (accepted):**

```json
{
  "type": "INITIATE_PAIRING_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "accepted": true,
    "pairingId": "pair-123",
    "state": "PENDING"
  }
}
```

**Response (rejected):**

```json
{
  "type": "INITIATE_PAIRING_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "accepted": false,
    "reason": "TARGET_NOT_AVAILABLE"
  }
}
```

#### 11.3.2 CONFIRM_PAIRING

**Direction:** Target node → Hub  
**type:** `"CONFIRM_PAIRING"`

**Request:**

```json
{
  "type": "CONFIRM_PAIRING",
  "nodeId": "desktop-01",
  "payload": {
    "pairingId": "pair-123",
    "accept": true,
    "attestationData": {
      "os": "WINDOWS",
      "primeDesktopVersion": "2.0.0"
    }
  }
}
```

**Response (accepted):**

```json
{
  "type": "CONFIRM_PAIRING_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "pairingId": "pair-123",
    "state": "ACTIVE"
  }
}
```

**Response (rejected):**

```json
{
  "type": "CONFIRM_PAIRING_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "pairingId": "pair-123",
    "state": "REVOKED",
    "reason": "USER_REJECTED"
  }
}
```

#### 11.3.3 LIST_PAIRED_NODES

**Direction:** Node → Hub  
**type:** `"LIST_PAIRED_NODES"`

**Request:**

```json
{
  "type": "LIST_PAIRED_NODES",
  "nodeId": "android-01",
  "payload": {}
}
```

**Response:**

```json
{
  "type": "LIST_PAIRED_NODES_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "pairings": [
      {
        "pairingId": "pair-123",
        "nodeA": "android-01",
        "nodeB": "desktop-01",
        "state": "ACTIVE",
        "createdAt": 1712850000000
      }
    ]
  }
}
```

#### 11.3.4 REVOKE_PAIRING

**Direction:** Node → Hub  
**type:** `"REVOKE_PAIRING"`

Either node in a pairing can revoke it.

**Request:**

```json
{
  "type": "REVOKE_PAIRING",
  "nodeId": "android-01",
  "payload": {
    "pairingId": "pair-123",
    "reason": "USER_REQUEST"
  }
}
```

**Response:**

```json
{
  "type": "REVOKE_PAIRING_RESPONSE",
  "nodeId": "android-01",
  "payload": {
    "pairingId": "pair-123",
    "state": "REVOKED"
  }
}
```

#### 11.3.5 PAIRING_STATUS

**Direction:** Node → Hub  
**type:** `"PAIRING_STATUS"`

**Request:**

```json
{
  "type": "PAIRING_STATUS",
  "nodeId": "desktop-01",
  "payload": {
    "pairingId": "pair-123"
  }
}
```

**Response:**

```json
{
  "type": "PAIRING_STATUS_RESPONSE",
  "nodeId": "desktop-01",
  "payload": {
    "pairingId": "pair-123",
    "state": "ACTIVE",
    "nodeA": "android-01",
    "nodeB": "desktop-01",
    "createdAt": 1712850000000
  }
}
```

---

## 12. Prime Desktop Node v2 — Mesh Integration

```text
COMPONENTS (MESH-SPECIFIC)
- MeshClient:
    - Connects to Hub socket
    - Sends REGISTER_NODE on startup
    - Sends HEARTBEAT at configured interval
    - Listens for REFLEX_EVENT and INTEGRITY_EVENT
    - Supports MESH_STATUS_QUERY on demand

DATA FLOW
- Startup:
    - Load identity token
    - Connect to Hub
    - Send REGISTER_NODE
    - Receive REGISTER_NODE_ACK
    - Start HEARTBEAT loop
- Reflex:
    - Receive REFLEX_EVENT
    - Forward to Action Center
- Integrity:
    - Receive INTEGRITY_EVENT
    - Forward to Integrity Monitor
- Status:
    - On user request, send MESH_STATUS_QUERY
    - Display mesh topology in UI (optional)
```

---

## 13. Repo Layout (Unified Prime)

```text
/varynx-prime
    /docs
        VARYNXPRIME_MASTER_BLUEPRINT.md
        MESH_PROTOCOL.md
        IDENTITY_TOKEN_FORMAT.md
        PAIRING_PROTOCOL.md
        PHASE_1_ROADMAP.md

    /android
        /app
            /src
                /main
                    /kotlin/com/varynx/prime
                        BatteryEnforcementEngine.kt
                        LogHistoryEngine.kt
                        PrimeDashboard.kt
                        PrimePanels.kt
                        PrimePermissionFlow.kt
                        PrimeOverlayGuardian.kt
        build.gradle.kts

    /desktop
        /action-center
            ActionCenter.ts
            ActionCenterUI.tsx
        /integrity
            IntegrityMonitor.ts
            IntegrityMonitorUI.tsx
        /mesh
            MeshClient.ts
        build.gradle.kts

    /hub
        /mesh-service
            MeshServer.ts
            MessageTypes.ts
        /identity-service
            IdentityService.ts
        /pairing-service
            PairingService.ts
        /log-service
            LogService.ts
        build.gradle.kts

    build.gradle.kts
    settings.gradle.kts
    README.md
    LICENSE
```

---

## 14. Integration Points

**Android (Prime):**
- On startup → request identity token from Hub
- Log all reflex events via Hub
- Display paired devices in Mesh Console
- Export 24h logs

**Desktop Node v2:**
- Connect to Hub on startup
- Register as DESKTOP platform
- Listen for reflex/integrity broadcasts
- Surface actions in Action Center
- Show mesh status in Integrity Monitor

**Hub:**
- Serve all three services (Mesh, Identity, Pairing)
- Route events and status updates
- Maintain local, encrypted state

---

**End of Master Blueprint**  
**Next Steps:** Implement repos on GitHub, begin Phase 1 development.
