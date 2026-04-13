# VARYNX 2.0 Privacy Policy

**Last Updated:** April 5, 2026

---

## Our Commitment

VARYNX is built on a single, non-negotiable principle: **your data never leaves your devices**. We don't collect it, transmit it, store it remotely, or sell it. Period.

---

## Data Collection

VARYNX collects **no data whatsoever** from your devices to external servers.

| Category | Collected? | Details |
|----------|-----------|---------|
| Personal information | **No** | Never. |
| Device identifiers | **No** | No IMEI, Android ID, or advertising IDs. |
| Usage analytics | **No** | No Firebase Analytics, Mixpanel, or similar. |
| Crash reports | **No** | Crashes are logged locally only. No Crashlytics, Sentry, or external services. |
| Location data | **No** | Not requested, not accessed. |
| Network traffic | **No** | The app makes zero requests to external servers. |
| Advertising data | **No** | No ads, no ad identifiers, no ad SDKs. |
| Mesh traffic | **No** | Mesh communication is strictly device-to-device on your local network. No internet relay. |

---

## On-Device Data

VARYNX stores the following data **locally on your devices only**:

- **Security scan results** — Stored in local files with automatic rotation
- **Audit logs** — Local append-only log with configurable retention
- **Settings and preferences** — Stored in platform-appropriate local storage
- **Device identity** — Cryptographic key pair stored locally for mesh authentication
- **Trust graph** — List of trusted peer devices, stored locally
- **Mesh state** — Vector clocks and peer heartbeat data for sync consistency
- **Threat history** — Recent threat events for intelligence module continuity

All on-device data can be:
- Viewed by you at any time within the app
- Deleted by you at any time through Settings
- Exported by you through explicit user action

---

## Mesh Communication

VARYNX 2.0 devices communicate through an **encrypted local mesh network**:

- All mesh traffic stays on your local network (LAN/BLE)
- Messages are encrypted with **AES-256-GCM** and signed with **Ed25519**
- Key exchange uses **X25519 ECDH** with **HKDF-SHA256** key derivation
- No internet relay, no cloud proxy, no external routing
- Trust is established only through physical proximity pairing (6-digit code exchange)
- Trust is **non-transitive** — you must explicitly approve every device

---

## Permissions

VARYNX requests only the permissions necessary for its security modules:

| Permission | Purpose | Required? |
|-----------|---------|-----------|
| Camera | QR code scanning (on-demand only) | Optional |
| Bluetooth Scan/Connect | BLE proximity scanning and mesh transport | Optional |
| NFC | NFC interaction detection | Optional |
| Wi-Fi State | Network security monitoring | Recommended |
| Nearby Wi-Fi Devices | Mesh device discovery | Recommended |
| Foreground Service | Background Guardian Engine operation | Recommended |
| Post Notifications | Guardian threat alerts | Recommended |
| Battery Optimization | Ensure uninterrupted background protection | Optional |
| Query All Packages | App threat detection and permission analysis | Recommended |

All permissions are requested at runtime with clear explanations. Denying a permission disables only the related module — the rest of the system continues to function.

---

## Third-Party Services

VARYNX uses **no third-party services**:

- No analytics SDKs
- No crash reporting services
- No advertising networks
- No cloud APIs
- No remote databases
- No CDNs or content delivery

The only network activity is **local mesh communication** between your own trusted devices on your own network.

---

## Data Sharing

We do not share your data with anyone because we do not have your data.

---

## Children's Privacy

VARYNX does not knowingly collect data from children under 13, or from anyone of any age. No data is collected.

---

## Changes to This Policy

We will update this policy if our practices change. However, our core commitment — **zero data collection** — is a design constraint, not a policy choice. It is enforced by the architecture of the software itself.

---

## Contact

Questions about this privacy policy:

- **Email**: [varynx.contact@gmail.com](mailto:varynx.contact@gmail.com)
- **GitHub**: [Issues](../../issues)
