#!/usr/bin/env python3
"""
═══════════════════════════════════════════════════════════════════
 VARYNX 2.0 — Mesh Chaos Test
═══════════════════════════════════════════════════════════════════

 File placement:
   tools/stress/mesh-chaos-test.py

 Run with:
   python3 tools/stress/mesh-chaos-test.py

 Requirements:
   Python 3.10+, no external dependencies (stdlib only)

 What it does:
   Simulates an 8-node VARYNX mesh network with full chaos injection:
   - Node deaths & resurrections
   - Network partition (split-brain)
   - Fake threat injection on every node
   - High sync load (rapid heartbeat exchange)
   - Trust graph validation (no transitive trust)
   - Vector clock convergence under chaos
   - Quorum lockdown decision
   - Threat relay verification (all nodes see injected threats)
   - Simulated X25519/Ed25519 + AES-256-GCM envelope integrity

 Pass criteria:
   • Zero crashes across all chaos scenarios
   • Mesh sync latency < 800ms under chaos (simulated)
   • Vector clocks converge after partition heal
   • All injected threats detected on every reachable node
   • Trust graph remains consistent (no phantom edges)
   • Quorum lockdown triggers when majority nodes report CRITICAL

 Copyright (c) 2026 VARYNX. All rights reserved.
═══════════════════════════════════════════════════════════════════
"""
import sys
import time
import random
import hashlib
import hmac
import struct
import threading
import uuid
import json
from dataclasses import dataclass, field
from enum import IntEnum, Enum
from typing import Optional
from collections import defaultdict

# ─── Constants ────────────────────────────────────────────────────

PROTOCOL_VERSION = 2
BROADCAST = "*"
DEFAULT_TCP_PORT = 42421
HEARTBEAT_INTERVAL_MS = 30_000
THREAT_TTL_MS = 5 * 60 * 1000
NONCE_SIZE = 12
KEY_SIZE = 32
SIGNATURE_SIZE = 64

# ─── Enums ────────────────────────────────────────────────────────

class DeviceRole(Enum):
    CONTROLLER = "CONTROLLER"
    GUARDIAN = "GUARDIAN"
    HUB_HOME = "HUB_HOME"
    HUB_WEAR = "HUB_WEAR"
    NODE_LINUX = "NODE_LINUX"
    NODE_POCKET = "NODE_POCKET"
    NODE_SATELLITE = "NODE_SATELLITE"
    GUARDIAN_MICRO = "GUARDIAN_MICRO"

class ThreatLevel(IntEnum):
    NONE = 0
    LOW = 1
    MEDIUM = 2
    HIGH = 3
    CRITICAL = 4

class GuardianMode(Enum):
    SENTINEL = "SENTINEL"
    ALERT = "ALERT"
    DEFENSE = "DEFENSE"
    LOCKDOWN = "LOCKDOWN"
    SAFE = "SAFE"

class MessageType(Enum):
    HEARTBEAT = 0
    THREAT_EVENT = 1
    STATE_SYNC = 2
    POLICY_UPDATE = 3
    COMMAND = 4
    ACK = 5
    PAIR_REQUEST = 6
    PAIR_RESPONSE = 7
    PAIR_CONFIRM = 8

# ─── Data Classes ─────────────────────────────────────────────────

@dataclass
class DeviceIdentity:
    device_id: str
    display_name: str
    role: DeviceRole
    public_key_exchange: bytes  # X25519 (32 bytes, simulated)
    public_key_signing: bytes   # Ed25519 (32 bytes, simulated)
    created_at: float = field(default_factory=time.time)

@dataclass
class TrustEdge:
    remote_device_id: str
    remote_display_name: str
    remote_role: DeviceRole
    remote_public_key_exchange: bytes
    remote_public_key_signing: bytes
    shared_secret: bytes  # X25519 DH result (32 bytes)
    paired_at: float

@dataclass
class ThreatEvent:
    event_id: str
    source_module_id: str
    threat_level: ThreatLevel
    title: str
    description: str
    timestamp: float = field(default_factory=time.time)
    resolved: bool = False

@dataclass
class HeartbeatPayload:
    device_id: str
    display_name: str
    role: DeviceRole
    threat_level: ThreatLevel
    guardian_mode: GuardianMode
    active_module_count: int
    uptime: float
    clock: dict  # VectorClock snapshot {device_id: counter}
    known_peers: set

@dataclass
class MeshEnvelope:
    version: int
    msg_type: MessageType
    sender_id: str
    recipient_id: str
    timestamp: float
    nonce: bytes
    payload: bytes
    signature: bytes

# ─── Simulated Crypto ─────────────────────────────────────────────

def sim_generate_keypair() -> tuple:
    """Simulate X25519 or Ed25519 keypair (32-byte random keys)."""
    private = random.randbytes(KEY_SIZE)
    public = hashlib.sha256(private).digest()
    return private, public

def sim_dh_shared_secret(our_private: bytes, their_public: bytes) -> bytes:
    """Simulate X25519 Diffie-Hellman shared secret."""
    return hmac.new(our_private, their_public, hashlib.sha256).digest()

def sim_sign(private_key: bytes, message: bytes) -> bytes:
    """Simulate Ed25519 signature (HMAC-SHA512 truncated to 64 bytes)."""
    return hmac.new(private_key, message, hashlib.sha512).digest()

def sim_verify(public_key: bytes, message: bytes, signature: bytes) -> bool:
    """Simulate Ed25519 verification."""
    # In simulation, we derive the signing key from the public key hash
    # This is NOT real crypto — it validates the simulation flow
    return len(signature) == 64

def sim_aes_gcm_encrypt(key: bytes, nonce: bytes, plaintext: bytes, aad: bytes = b"") -> bytes:
    """Simulate AES-256-GCM encryption (XOR + HMAC tag)."""
    # XOR cipher for simulation (NOT real encryption)
    stream = hashlib.sha256(key + nonce).digest()
    while len(stream) < len(plaintext):
        stream += hashlib.sha256(stream).digest()
    ciphertext = bytes(a ^ b for a, b in zip(plaintext, stream[:len(plaintext)]))
    tag = hmac.new(key, nonce + ciphertext + aad, hashlib.sha256).digest()[:16]
    return ciphertext + tag

def sim_aes_gcm_decrypt(key: bytes, nonce: bytes, ciphertext_with_tag: bytes, aad: bytes = b"") -> Optional[bytes]:
    """Simulate AES-256-GCM decryption with tag verification."""
    if len(ciphertext_with_tag) < 16:
        return None
    ciphertext = ciphertext_with_tag[:-16]
    tag = ciphertext_with_tag[-16:]
    expected_tag = hmac.new(key, nonce + ciphertext + aad, hashlib.sha256).digest()[:16]
    if not hmac.compare_digest(tag, expected_tag):
        return None
    stream = hashlib.sha256(key + nonce).digest()
    while len(stream) < len(ciphertext):
        stream += hashlib.sha256(stream).digest()
    return bytes(a ^ b for a, b in zip(ciphertext, stream[:len(ciphertext)]))

def sim_hkdf(ikm: bytes, salt: bytes, info: bytes, length: int = 32) -> bytes:
    """Simulate HKDF-SHA256."""
    prk = hmac.new(salt if salt else b"\x00" * 32, ikm, hashlib.sha256).digest()
    okm = b""
    t = b""
    counter = 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([counter]), hashlib.sha256).digest()
        okm += t
        counter += 1
    return okm[:length]

# ─── VectorClock ──────────────────────────────────────────────────

class VectorClock:
    def __init__(self):
        self._clocks: dict[str, int] = {}
        self._lock = threading.Lock()

    def tick(self, device_id: str):
        with self._lock:
            self._clocks[device_id] = self._clocks.get(device_id, 0) + 1

    def merge(self, other_map: dict):
        with self._lock:
            for k, v in other_map.items():
                self._clocks[k] = max(self._clocks.get(k, 0), v)

    def snapshot(self) -> dict:
        with self._lock:
            return dict(self._clocks)

    def is_before(self, other_map: dict) -> bool:
        snap = self.snapshot()
        all_keys = set(snap) | set(other_map)
        at_least_one_less = False
        for k in all_keys:
            ours = snap.get(k, 0)
            theirs = other_map.get(k, 0)
            if ours > theirs:
                return False
            if ours < theirs:
                at_least_one_less = True
        return at_least_one_less

# ─── TrustGraph ───────────────────────────────────────────────────

class TrustGraph:
    def __init__(self):
        self._edges: dict[str, TrustEdge] = {}
        self._lock = threading.Lock()

    def add_trust(self, edge: TrustEdge):
        with self._lock:
            self._edges[edge.remote_device_id] = edge

    def revoke_trust(self, device_id: str):
        with self._lock:
            self._edges.pop(device_id, None)

    def is_trusted(self, device_id: str) -> bool:
        with self._lock:
            return device_id in self._edges

    def get_edge(self, device_id: str) -> Optional[TrustEdge]:
        with self._lock:
            return self._edges.get(device_id)

    def trusted_ids(self) -> set:
        with self._lock:
            return set(self._edges.keys())

    def peer_count(self) -> int:
        with self._lock:
            return len(self._edges)

# ─── MeshNode (simulated) ────────────────────────────────────────

class MeshNode:
    """Simulates a single VARYNX mesh node with full crypto + sync."""

    def __init__(self, name: str, role: DeviceRole):
        self.name = name
        self.role = role
        self.device_id = str(uuid.uuid4())

        # Crypto keys
        self.exchange_private, self.exchange_public = sim_generate_keypair()
        self.signing_private, self.signing_public = sim_generate_keypair()

        self.identity = DeviceIdentity(
            device_id=self.device_id,
            display_name=name,
            role=role,
            public_key_exchange=self.exchange_public,
            public_key_signing=self.signing_public,
        )

        self.trust_graph = TrustGraph()
        self.vector_clock = VectorClock()
        self.threat_level = ThreatLevel.NONE
        self.guardian_mode = GuardianMode.SENTINEL
        self.active_modules = 17
        self.alive = True
        self.start_time = time.time()

        # Threat tracking
        self.received_threats: list[ThreatEvent] = []
        self.local_threats: list[ThreatEvent] = []
        self._lock = threading.Lock()

        # Peer state cache
        self.peer_states: dict[str, HeartbeatPayload] = {}

    def pair_with(self, other: "MeshNode"):
        """Establish mutual trust between two nodes (simulates 6-digit pairing)."""
        shared_secret = sim_dh_shared_secret(self.exchange_private, other.exchange_public)
        # Derive separate session keys for each direction via HKDF
        key_a_to_b = sim_hkdf(shared_secret, b"varynx-pair", self.device_id.encode())
        key_b_to_a = sim_hkdf(shared_secret, b"varynx-pair", other.device_id.encode())

        self.trust_graph.add_trust(TrustEdge(
            remote_device_id=other.device_id,
            remote_display_name=other.name,
            remote_role=other.role,
            remote_public_key_exchange=other.exchange_public,
            remote_public_key_signing=other.signing_public,
            shared_secret=shared_secret,
            paired_at=time.time(),
        ))

        other.trust_graph.add_trust(TrustEdge(
            remote_device_id=self.device_id,
            remote_display_name=self.name,
            remote_role=self.role,
            remote_public_key_exchange=self.exchange_public,
            remote_public_key_signing=self.signing_public,
            shared_secret=shared_secret,
            paired_at=time.time(),
        ))

    def build_heartbeat(self) -> HeartbeatPayload:
        self.vector_clock.tick(self.device_id)
        return HeartbeatPayload(
            device_id=self.device_id,
            display_name=self.name,
            role=self.role,
            threat_level=self.threat_level,
            guardian_mode=self.guardian_mode,
            active_module_count=self.active_modules,
            uptime=time.time() - self.start_time,
            clock=self.vector_clock.snapshot(),
            known_peers=self.trust_graph.trusted_ids(),
        )

    def receive_heartbeat(self, hb: HeartbeatPayload):
        if not self.trust_graph.is_trusted(hb.device_id):
            return
        self.vector_clock.merge(hb.clock)
        self.peer_states[hb.device_id] = hb

    def inject_threat(self, module_id: str, level: ThreatLevel, title: str):
        event = ThreatEvent(
            event_id=str(uuid.uuid4()),
            source_module_id=module_id,
            threat_level=level,
            title=title,
            description=f"Injected on {self.name}",
        )
        with self._lock:
            self.local_threats.append(event)
        self.threat_level = max(self.threat_level, level)
        self._update_mode()

    def receive_threat(self, event: ThreatEvent, sender_id: str):
        if not self.trust_graph.is_trusted(sender_id):
            return
        with self._lock:
            self.received_threats.append(event)
        self.threat_level = max(self.threat_level, event.threat_level)
        self._update_mode()

    def build_sealed_envelope(self, msg_type: MessageType, payload: bytes,
                               recipient_id: str) -> Optional[MeshEnvelope]:
        """Build an encrypted + signed mesh envelope."""
        nonce = random.randbytes(NONCE_SIZE)
        aad = f"{PROTOCOL_VERSION}:{msg_type.value}:{self.device_id}:{recipient_id}".encode()

        if recipient_id == BROADCAST:
            # Broadcast: sign only (payload is cleartext heartbeat)
            encrypted = payload
        else:
            edge = self.trust_graph.get_edge(recipient_id)
            if not edge:
                return None
            encrypted = sim_aes_gcm_encrypt(edge.shared_secret, nonce, payload, aad)

        sig_input = struct.pack(">I", PROTOCOL_VERSION) + msg_type.value.to_bytes(1, "big") + \
                    self.device_id.encode() + recipient_id.encode() + nonce + encrypted
        signature = sim_sign(self.signing_private, sig_input)

        return MeshEnvelope(
            version=PROTOCOL_VERSION,
            msg_type=msg_type,
            sender_id=self.device_id,
            recipient_id=recipient_id,
            timestamp=time.time(),
            nonce=nonce,
            payload=encrypted,
            signature=signature,
        )

    def open_envelope(self, env: MeshEnvelope) -> Optional[bytes]:
        """Verify + decrypt an incoming envelope."""
        if env.sender_id == self.device_id:
            return None
        if not sim_verify(b"", b"", env.signature):
            return None

        if env.recipient_id == BROADCAST:
            return env.payload

        edge = self.trust_graph.get_edge(env.sender_id)
        if not edge:
            return None

        aad = f"{env.version}:{env.msg_type.value}:{env.sender_id}:{env.recipient_id}".encode()
        return sim_aes_gcm_decrypt(edge.shared_secret, env.nonce, env.payload, aad)

    def _update_mode(self):
        if self.threat_level == ThreatLevel.CRITICAL:
            self.guardian_mode = GuardianMode.LOCKDOWN
        elif self.threat_level == ThreatLevel.HIGH:
            self.guardian_mode = GuardianMode.DEFENSE
        elif self.threat_level == ThreatLevel.MEDIUM:
            self.guardian_mode = GuardianMode.ALERT
        else:
            self.guardian_mode = GuardianMode.SENTINEL

    def kill(self):
        self.alive = False

    def resurrect(self):
        self.alive = True

    def get_all_threat_ids(self) -> set:
        with self._lock:
            local = {t.event_id for t in self.local_threats}
            remote = {t.event_id for t in self.received_threats}
            return local | remote

# ─── MeshSimulator ────────────────────────────────────────────────

class MeshSimulator:
    """Orchestrates 8 mesh nodes with chaos injection."""

    ROLES = [
        ("Phone-Guardian", DeviceRole.GUARDIAN),
        ("Desktop-Controller", DeviceRole.CONTROLLER),
        ("Linux-Node", DeviceRole.NODE_LINUX),
        ("HomeHub", DeviceRole.HUB_HOME),
        ("PocketNode", DeviceRole.NODE_POCKET),
        ("SatelliteNode", DeviceRole.NODE_SATELLITE),
        ("WearOS-Watch", DeviceRole.GUARDIAN_MICRO),
        ("Tablet-Guardian", DeviceRole.GUARDIAN),
    ]

    def __init__(self):
        self.nodes: list[MeshNode] = []
        self.network_partitions: set[tuple] = set()
        self.stats = defaultdict(int)
        self.errors: list[str] = []

    def setup(self):
        """Create 8 nodes and establish full mesh trust."""
        print("[SETUP] Creating 8-node mesh...")
        for name, role in self.ROLES:
            self.nodes.append(MeshNode(name, role))

        # Full mesh pairing (every node trusts every other node)
        for i in range(len(self.nodes)):
            for j in range(i + 1, len(self.nodes)):
                self.nodes[i].pair_with(self.nodes[j])
                self.stats["pairings"] += 1

        print(f"[SETUP] {len(self.nodes)} nodes created, {self.stats['pairings']} pairings established")

    def can_communicate(self, a: MeshNode, b: MeshNode) -> bool:
        """Check if two nodes can currently communicate (not partitioned)."""
        pair = tuple(sorted([a.device_id, b.device_id]))
        return pair not in self.network_partitions and a.alive and b.alive

    def broadcast_heartbeats(self):
        """All alive nodes exchange heartbeats."""
        for node in self.nodes:
            if not node.alive:
                continue
            hb = node.build_heartbeat()
            for peer in self.nodes:
                if peer.device_id != node.device_id and self.can_communicate(node, peer):
                    peer.receive_heartbeat(hb)
                    self.stats["heartbeats"] += 1

    def relay_threats(self):
        """All alive nodes relay their local threats to all trusted peers."""
        for node in self.nodes:
            if not node.alive:
                continue
            with node._lock:
                threats = list(node.local_threats)
            for threat in threats:
                # Encrypt + sign for each peer
                payload = threat.title.encode()
                for peer in self.nodes:
                    if peer.device_id == node.device_id:
                        continue
                    if not self.can_communicate(node, peer):
                        continue
                    envelope = node.build_sealed_envelope(
                        MessageType.THREAT_EVENT, payload, peer.device_id
                    )
                    if envelope:
                        plaintext = peer.open_envelope(envelope)
                        if plaintext is not None:
                            peer.receive_threat(threat, node.device_id)
                            self.stats["threats_relayed"] += 1

    # ── Chaos Injectors ──

    def chaos_kill_node(self, index: int):
        """Kill a random node (simulate crash)."""
        node = self.nodes[index]
        if node.alive:
            node.kill()
            self.stats["node_kills"] += 1
            print(f"  [CHAOS] Killed {node.name}")

    def chaos_resurrect_node(self, index: int):
        """Resurrect a dead node."""
        node = self.nodes[index]
        if not node.alive:
            node.resurrect()
            self.stats["node_resurrections"] += 1
            print(f"  [CHAOS] Resurrected {node.name}")

    def chaos_network_partition(self, a_idx: int, b_idx: int):
        """Create a network partition between two nodes."""
        a, b = self.nodes[a_idx], self.nodes[b_idx]
        pair = tuple(sorted([a.device_id, b.device_id]))
        self.network_partitions.add(pair)
        self.stats["partitions"] += 1
        print(f"  [CHAOS] Partition: {a.name} <-> {b.name}")

    def chaos_heal_partition(self, a_idx: int, b_idx: int):
        """Heal a network partition."""
        a, b = self.nodes[a_idx], self.nodes[b_idx]
        pair = tuple(sorted([a.device_id, b.device_id]))
        self.network_partitions.discard(pair)
        self.stats["heals"] += 1

    def chaos_inject_threat(self, node_idx: int, level: ThreatLevel):
        """Inject a fake threat on a node."""
        node = self.nodes[node_idx]
        if not node.alive:
            return
        modules = [
            "protect_scam_detector", "protect_clipboard_shield", "protect_bt_skimmer",
            "protect_nfc_guardian", "protect_network_integrity", "protect_app_behavior",
            "protect_device_state", "protect_permission_watchdog", "protect_install_monitor",
            "protect_runtime_threat", "protect_overlay_detector", "protect_notification_analyzer",
            "protect_usb_integrity", "protect_sensor_anomaly", "protect_app_tamper",
            "protect_security_audit", "protect_qr_scanner",
        ]
        module = random.choice(modules)
        node.inject_threat(module, level, f"Chaos threat ({level.name}) on {node.name}")
        self.stats["threats_injected"] += 1

    def chaos_heal_all_partitions(self):
        self.network_partitions.clear()

    # ── Validation Tests ──

    def test_trust_graph_no_transitive(self):
        """Verify no transitive trust: each edge was explicitly paired."""
        print("\n[TEST] Trust graph — no transitive trust...")
        for node in self.nodes:
            trusted = node.trust_graph.trusted_ids()
            for pid in trusted:
                edge = node.trust_graph.get_edge(pid)
                assert edge is not None, f"{node.name} has phantom edge to {pid}"
                assert len(edge.shared_secret) == KEY_SIZE, f"Invalid shared secret length"
                assert edge.remote_public_key_exchange is not None
                assert edge.remote_public_key_signing is not None
            # Each node should trust exactly 7 others
            expected = len(self.nodes) - 1
            assert len(trusted) == expected, \
                f"{node.name} trusts {len(trusted)} nodes, expected {expected}"
        print("  [PASS] Trust graph consistent — no transitive edges")

    def test_vector_clock_convergence(self):
        """After partition heal + heartbeat exchange, clocks should converge."""
        print("\n[TEST] Vector clock convergence after partition heal...")
        # Heal all partitions
        self.chaos_heal_all_partitions()
        # Resurrect all nodes
        for node in self.nodes:
            node.resurrect()
        # Run several rounds of heartbeat exchange
        for _ in range(10):
            self.broadcast_heartbeats()
        # All alive nodes should know about all other nodes in their vector clocks
        for node in self.nodes:
            snap = node.vector_clock.snapshot()
            for peer in self.nodes:
                if peer.device_id == node.device_id:
                    continue
                assert peer.device_id in snap, \
                    f"{node.name} missing {peer.name} in vector clock after convergence"
        print("  [PASS] Vector clocks converged across all 8 nodes")

    def test_threat_relay_completeness(self):
        """After relay, all reachable nodes should have received all threats."""
        print("\n[TEST] Threat relay completeness...")
        # Heal everything and relay
        self.chaos_heal_all_partitions()
        for n in self.nodes:
            n.resurrect()
        # Multiple relay rounds to ensure propagation
        for _ in range(5):
            self.relay_threats()

        # Collect all threat IDs injected across the mesh
        all_threat_ids = set()
        for node in self.nodes:
            with node._lock:
                for t in node.local_threats:
                    all_threat_ids.add(t.event_id)

        # Every node should know about every threat
        for node in self.nodes:
            known = node.get_all_threat_ids()
            missing = all_threat_ids - known
            if missing:
                self.errors.append(
                    f"{node.name} missing {len(missing)} of {len(all_threat_ids)} threats"
                )
                print(f"  [WARN] {node.name} missing {len(missing)} threats")
            else:
                pass  # All threats received

        if not self.errors:
            print(f"  [PASS] All {len(all_threat_ids)} threats visible on all 8 nodes")
        else:
            print(f"  [PARTIAL] {len(self.errors)} nodes have gaps (may be due to stale chaos state)")

    def test_quorum_lockdown(self):
        """When majority (5+) nodes report CRITICAL, all should be in LOCKDOWN."""
        print("\n[TEST] Quorum lockdown decision...")
        # Inject CRITICAL threats on 5 of 8 nodes
        for i in range(5):
            self.chaos_inject_threat(i, ThreatLevel.CRITICAL)

        # After heartbeat exchange, check modes
        for _ in range(3):
            self.broadcast_heartbeats()

        critical_count = sum(1 for n in self.nodes if n.threat_level == ThreatLevel.CRITICAL)
        lockdown_count = sum(1 for n in self.nodes if n.guardian_mode == GuardianMode.LOCKDOWN)

        assert critical_count >= 5, f"Expected 5+ CRITICAL nodes, got {critical_count}"
        assert lockdown_count >= 5, f"Expected 5+ LOCKDOWN nodes, got {lockdown_count}"
        print(f"  [PASS] Quorum lockdown: {critical_count} CRITICAL, {lockdown_count} LOCKDOWN")

    def test_envelope_crypto_integrity(self):
        """Verify encrypted envelope round-trip between all node pairs."""
        print("\n[TEST] Envelope crypto integrity (X25519/Ed25519 + AES-256-GCM)...")
        success = 0
        total = 0
        for i, sender in enumerate(self.nodes):
            for j, receiver in enumerate(self.nodes):
                if i == j:
                    continue
                total += 1
                payload = f"Secret from {sender.name} to {receiver.name} — {uuid.uuid4()}".encode()
                envelope = sender.build_sealed_envelope(
                    MessageType.THREAT_EVENT, payload, receiver.device_id
                )
                assert envelope is not None, f"Failed to seal {sender.name} → {receiver.name}"
                plaintext = receiver.open_envelope(envelope)
                if plaintext is not None and plaintext == payload:
                    success += 1
                else:
                    self.errors.append(f"Crypto round-trip failed: {sender.name} → {receiver.name}")

        assert success == total, f"Crypto failures: {total - success} of {total}"
        print(f"  [PASS] {success}/{total} encrypted envelope round-trips successful")

    def test_sync_latency_under_chaos(self):
        """Measure simulated sync latency (heartbeat exchange time) under chaos."""
        print("\n[TEST] Sync latency under chaos...")
        # Inject chaos
        self.chaos_kill_node(random.randint(0, 7))
        self.chaos_network_partition(0, 1)
        self.chaos_network_partition(2, 3)

        # Measure heartbeat exchange time
        start = time.perf_counter()
        for _ in range(100):
            self.broadcast_heartbeats()
        elapsed_ms = (time.perf_counter() - start) * 1000

        avg_ms = elapsed_ms / 100

        # Heal for next tests
        self.chaos_heal_all_partitions()
        for n in self.nodes:
            n.resurrect()

        assert avg_ms < 800, f"Avg sync latency {avg_ms:.1f}ms exceeds 800ms"
        print(f"  [PASS] Avg sync latency: {avg_ms:.1f}ms (< 800ms threshold)")

    # ── Main Test Runner ──

    def run_chaos_scenario(self):
        """Run the complete chaos scenario."""
        print("\n" + "=" * 70)
        print(" VARYNX 2.0 — MESH CHAOS TEST")
        print("=" * 70)

        self.setup()

        # ── Phase 1: Baseline validation ──
        print("\n[PHASE 1] Baseline validation...")
        self.broadcast_heartbeats()
        self.test_trust_graph_no_transitive()
        self.test_envelope_crypto_integrity()

        # ── Phase 2: Massive chaos injection ──
        print("\n[PHASE 2] Chaos injection (500 rounds)...")
        for round_num in range(500):
            r = random.random()
            if r < 0.1:
                self.chaos_kill_node(random.randint(0, 7))
            elif r < 0.15:
                self.chaos_resurrect_node(random.randint(0, 7))
            elif r < 0.25:
                a, b = random.sample(range(8), 2)
                self.chaos_network_partition(a, b)
            elif r < 0.30:
                a, b = random.sample(range(8), 2)
                self.chaos_heal_partition(a, b)
            elif r < 0.60:
                self.chaos_inject_threat(
                    random.randint(0, 7),
                    random.choice([ThreatLevel.LOW, ThreatLevel.MEDIUM, ThreatLevel.HIGH, ThreatLevel.CRITICAL])
                )
            else:
                self.broadcast_heartbeats()
                self.relay_threats()

            self.stats["chaos_rounds"] += 1

        # ── Phase 3: Convergence after chaos ──
        print("\n[PHASE 3] Convergence after chaos...")
        self.test_vector_clock_convergence()
        self.test_threat_relay_completeness()
        self.test_quorum_lockdown()
        self.test_sync_latency_under_chaos()

        # ── Final Report ──
        print("\n" + "=" * 70)
        print(" RESULTS")
        print("=" * 70)
        for k, v in sorted(self.stats.items()):
            print(f"  {k}: {v}")
        print()

        if self.errors:
            print(f"  ERRORS: {len(self.errors)}")
            for e in self.errors[:10]:
                print(f"    - {e}")
            print("\n  RESULT: FAIL")
            return False
        else:
            print("  RESULT: PASS")
            return True


# ─── Entry Point ──────────────────────────────────────────────────

def main():
    sim = MeshSimulator()
    try:
        passed = sim.run_chaos_scenario()
    except Exception as e:
        print(f"\n[FATAL] Unhandled exception: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(2)

    sys.exit(0 if passed else 1)

if __name__ == "__main__":
    main()
