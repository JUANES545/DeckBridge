# DeckBridge

**DeckBridge** turns your Android phone into the control surface for a **hardware-style macro deck**—while **host agents** on your Windows or macOS machine receive actions over the **LAN** and drive the desktop for you.

Pair once with a short **onboarding** flow, link a computer via **QR / discovery**, and use the **live mirror** to see pad highlights, knobs, and host context at a glance. The app is built for a **two-device story**: phone as deck, PC or Mac as execution host.

<p align="center">
  <a href="https://github.com/JUANES545/DeckBridge/releases/latest">
    <img src="https://img.shields.io/github/v/release/JUANES545/DeckBridge?color=3DDC84&label=Download%20latest%20APK&logo=android&logoColor=white&style=for-the-badge" alt="Download latest DeckBridge APK from GitHub Releases" />
  </a>
  &nbsp;
  <a href="https://github.com/JUANES545/DeckBridge/releases/latest">
    <img src="https://img.shields.io/github/release-date/JUANES545/DeckBridge?label=Released&logo=github&style=for-the-badge" alt="Latest release date" />
  </a>
</p>

> **Stable build:** always grab the APK from **[Releases → Latest](https://github.com/JUANES545/DeckBridge/releases/latest)**. Version and release notes live next to the download.

---

## Host agents (ready flow)

DeckBridge is designed around **first-party host agents** on the same network:

| Capability | What you get |
|------------|----------------|
| **Discovery** | UDP broadcast on **8766** to find agents on Wi‑Fi |
| **Control plane** | HTTP to the agent (default **8765**) for health, pairing, and action delivery |
| **Pairing** | Guided **PC connection** flow with **QR** payload handoff so the phone trusts the right host |
| **Trust & recovery** | Settings let you refresh endpoints, re-run discovery, and open **“add another computer”** when you change machines |

Agents run **outside** this repository (Windows / macOS installers or binaries maintained separately). This repo ships **only the Android app** (`:app`).

```mermaid
flowchart TB
  subgraph Phone["📱 DeckBridge (Android)"]
    UI[Dashboard + mirror]
    DISC[LAN discovery :8766]
    HTTP[HTTP client :8765]
    QR[QR pairing]
    UI --> DISC
    UI --> HTTP
    UI --> QR
  end
  subgraph Host["💻 Host agent (Windows / macOS)"]
    AG[HTTP agent]
    DESK[Desktop automation]
    AG --> DESK
  end
  DISC -.->|same Wi‑Fi| AG
  HTTP -->|actions + health| AG
  QR -.->|trust payload| AG
```

---

## Experience walkthrough

Suggested reading order: **first run → home → settings**.  
*Figures below are **tone placeholders** (same aspect ratio as a phone). Replace with real device screenshots anytime: `bash scripts/capture-readme-screenshots.sh` (requires `adb` + a running emulator or USB device with the app installed).*

### 1 · Onboarding

Guided first run; **Skip** sits top-right if you already use DeckBridge on another install.

<p align="center"><img src="docs/readme/screenshots/01-onboarding.png" width="280" alt="DeckBridge onboarding screen"/></p>

### 2 · After onboarding

Connection gate or hand-off before you land on the main deck—this is where **PC / agent pairing** starts when you choose it.

<p align="center"><img src="docs/readme/screenshots/02-after-onboarding.png" width="280" alt="DeckBridge post-onboarding or connection gate"/></p>

### 3 · Dashboard

**Hardware mirror**, deck grid, and host platform context—where you spend most of your time.

<p align="center"><img src="docs/readme/screenshots/03-dashboard.png" width="280" alt="DeckBridge dashboard with mirror"/></p>

### 4 · Settings

**Agents & delivery**: discovery, LAN endpoint, health checks, **add another computer**, calibration entry, and advanced toggles.

<p align="center"><img src="docs/readme/screenshots/04-settings.png" width="280" alt="DeckBridge settings"/></p>

```mermaid
flowchart LR
  A[Install APK] --> B[Onboarding]
  B --> C{Link PC now?}
  C -->|QR / discovery| D[Trusted host agent]
  C -->|Later| E[Dashboard]
  D --> E
  E --> F[Settings / add host / health]
```

---

## USB gadget HID (optional, advanced)

Some builds can probe **USB gadget HID** nodes (`hidg*`) for **direct keyboard injection** when the kernel exposes writable devices. That path is **fragile and device-specific**—it is aimed at **privileged / root-capable** setups and is **not** required for the normal **LAN agent** experience. Most users should rely on **Wi‑Fi + host agent** only.

---

## Build from source

```bash
./gradlew :app:assembleDebug
```

Release signing uses `keystore.properties` + `keystore/` (see `keystore.properties.example`); those files stay **out of git**.

```bash
./gradlew :app:assembleRelease
```

---

## Changelog & releases

Human-readable history: [`CHANGELOG.md`](CHANGELOG.md).  
GitHub Releases attach a **signed APK** for each stable tag (see **Download latest APK** above).
