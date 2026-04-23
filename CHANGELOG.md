# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

**Language:** Changelog entries and pull request descriptions are written in **English**.

## [1.6.0] - 2026-04-23

### Added

- **Audio Outputs page:** Synthetic last deck page built from `macSlot.audioOutputs`. Displays available macOS output devices as buttons; tapping one sends an `AUDIO_OUTPUT_SELECT` action through the Mac Bridge to switch the system audio output. The page is never persisted — it is rebuilt on every state update from the Mac agent.
- **`AUDIO_OUTPUT_SELECT` action kind:** New `DeckGridActionKind`, `DeckButtonIntent.AudioOutputSelect`, and `ResolvedActionKind.AUDIO_OUTPUT_SELECT`. `LoggingActionDispatcher`, `PlatformActionResolver`, `LanActionJsonFactory`, and `DeckKnobPreset` updated to handle it.
- **`AudioOutputDevice` model:** Carries device UID, display name, and active flag. Included in `PlatformSlotState` and forwarded to `AppState`.
- **Unit test — Keep Keyboard Awake visibility:** `KeepKeyboardAwakeVisibilityTest` documents and guards the unconditional display of the keep-keyboard-awake section in Settings.

### Changed

- **Mac Bridge — Tailscale IP isolation:** `_resolve_base_url` in the Mac agent now saves discovered Tailscale IPs under a dedicated `tailscale_ip` key in `mac_bridge.json`, leaving `android_ip` (LAN IP) untouched. A saved Tailscale IP is tried as a fast-path before live Tailscale discovery. This fixes sessions failing over WiFi after a Tailscale session had overwritten the LAN IP.

### Fixed

- **Keep Keyboard Awake always visible:** The section in `SettingsScreen` was gated on `PhysicalKeyboardConnectionState.CONNECTED`, causing it to disappear whenever the BT keyboard was not connected at the time of opening Settings. Gate removed — section is now always shown.
- **`triggerDeckButton` race condition:** When a button tap arrived within one animation frame of a swipe, `activeDeckPageIndex` had not yet updated and the button lookup returned nothing. Added a fallback that searches all pages when the button is not found in the active page.

## [1.5.0] - 2026-04-22

### Added

- **PAGE_NAV action kind:** New `DeckGridActionKind.PAGE_NAV` with `DeckButtonIntent.PageNav.Next` / `PageNav.Prev` intents. Grid buttons and knob bindings can now navigate between deck pages. Codec, validator, editor catalog, and preset factory updated to support it.
- **Haptic feedback on page change:** A subtle tick (`HapticFeedbackType.TextHandleMove`) fires each time the deck settles on a new page.

### Changed

- **Page transitions — AnimatedContent:** Replaced `HorizontalPager` with `AnimatedContent` + `detectHorizontalDragGestures`. Programmatic page changes (knob, PAGE_NAV button, dot tap) use a 80 ms crossfade; manual swipes use a 220 ms directional slide.
- **Optimistic page advance:** `advancePage()` in `DeckBridgeRepositoryImpl` now updates `_appState` immediately before launching the async DataStore write, eliminating the visible freeze on low-end devices when switching pages via knob.

### Fixed

- `DeckKnobEditValidator`: knob action synthetic label changed from blank to `"_"` so the reused `DeckGridEditValidator` path no longer rejects valid knob bindings with a false label-empty error.

## [1.4.0] - 2026-04-21

### Added

- **Mac Bridge — inverted architecture:** Android runs a lightweight HTTP server (port 8767); the Mac agent connects outbound to it, bypassing GlobalProtect and CrowdStrike which block all inbound TCP on corporate Macs. Transport auto-selection on each reconnect: ADB forward → saved IP → Tailscale peer → UDP broadcast (port 8766).
- **In-app update flow:** `AppUpdateManager` handles APK download, verification, and silent installation via the Accessibility Service. `UpdateBanner` surfaces update availability in the dashboard.
- **Platform slot management:** Separate credential slots for Windows (`lan_win_*`) and macOS (`lan_mac_*`) hosts in DataStore. QR pairing payload supports `os=mac` to route into the correct slot automatically. `PlatformSlotState` model tracks slot sync status.
- **Screen always-on:** `FLAG_KEEP_SCREEN_ON` set in `MainActivity.onCreate` — display stays active while the app is in the foreground.
- **Foreground service:** `DeckBridgeService` keeps LAN and Mac Bridge connections alive when the app moves to the background.
- **Dashboard effects:** `DashboardAurora` and `DashboardParticles` animated background components; `AnimatedBackgroundTheme` model.
- **LAN circuit breaker:** `LanCircuitBreaker` prevents repeated failed connection attempts.
- **Accessibility service:** `DeckBridgeAccessibilityService` registered for update installation flow.
- **Unit tests:** `HostDeliveryChannelTest`, `MacBridgeServerTest`, `ConnectionBannerLogicTest`.

### Changed

- **Onboarding:** `OnboardingFlow` restructured with revised copy and step layout.
- **Settings:** `SettingsScreen` reorganized; Mac Bridge radio button shown only for macOS platform slot.
- **Calibration:** `CalibrationScreen` expanded with additional feedback states.
- **Navigation:** `DeckBridgeNavHost` extended for update flow and new destinations.
- **Repository:** `DeckBridgeRepositoryImpl` expanded for Mac Bridge lifecycle, platform slot sync, LAN health merge, and update state management.
- **Strings:** EN + ES resources updated for Mac Bridge UI states, update flow, and platform slot labels.

### Removed

- `HidTransportDispatcher`, `HidGadgetSession`, `HidKeyboardSender`, `HidMediaSender`, `HidConsumerUsages`, `HidKeyboardUsages` — HID gadget transport removed pending hardware availability.
- `PrivilegedShellProbe` — privileged shell probe removed.
- `HidDebugLineFormatter`, `HidTransportUiMapper` — HID debug helpers removed with transport layer.

## [1.3.0] - 2026-04-11

### Added

- **Host platform & USB:** `HostOsDetector` for best-effort host OS awareness when tethered as a USB device; USB state probing integrated into repository refresh.
- **HID transport:** `HidGadgetSession` probe pipeline, `HidTransportUiMapper` / `HidDebugLineFormatter`, `HidTransportDispatcher` with keyboard/consumer writability, HID PC mode toggle, and privileged-shell availability checks surfaced in app state and settings.
- **Delivery routing:** `HostDeliveryRouter` and `HostDeliveryChannel` (USB HID vs LAN) with DataStore-backed preferences and LAN client wiring in `DeckBridgeRepositoryImpl`.
- **Pairing:** Pairing session models and QR-style payload coverage (`DeckbridgePairingPayloadTest`).
- **Onboarding:** First-run `OnboardingFlow` with themed components.
- **Deck editing:** `GridButtonEditScreen` / `GridButtonEditViewModel`; `KnobEditScreen` / `KnobEditViewModel` / `KnobEditBinding`.
- **Dashboard & mirror:** `DashboardBackground`, `DashboardChrome`, `DashboardEnergyPulses`, `DeviceChargingState`; expanded `HardwareMirrorPanel` with `MirrorLayoutDensity` and `MirrorPanelChrome`; `ic_platform_windows` drawable.
- **Strings:** Large EN + `values-es` updates for HID diagnostics, host detection, settings, and onboarding.

### Changed

- **Navigation:** `DeckBridgeNavHost` extended for onboarding, deck editors, and related destinations.
- **Home & settings:** `HomeScreen` and `SettingsScreen` refactored for host/HID/LAN controls and new chrome.
- **Repository:** `DeckBridgeRepository` / implementation expanded for host refresh, LAN health, pairing sync, and HID logging.

### Removed

- **Repository scope:** Dropped `pc-lan-server/`, `docs/QR_FLOW_QA.md`, and `.scripts/` so the repo is **Android-only**; host-side agents live in separate repositories.

## [1.2.0] - 2026-04-10

### Added

- **Navigation**: Settings and Hardware Calibration destinations in `DeckBridgeNavHost`.
- **Calibration UI**: `CalibrationScreen` wired to repository calibration session state.
- **Hardware mirror**: `HardwareMirrorPanel`, `MirrorPadSlot`, and live highlight feedback for pad/knob controls.
- **Settings**: `SettingsScreen` for device/keyboard refresh and related controls.
- **Logging**: `DeckBridgeLog` utility for structured app logging.
- **Knob mapping**: `KnobIntentMapper` for knob rotation/press intent resolution.
- **Per-app languages**: `locales_config.xml` and Spanish resources in `values-es/` (default remains English in `values/`).

### Changed

- **Repository**: Expanded `DeckBridgeRepository` / `DeckBridgeRepositoryImpl` for hardware calibration flow, diagnostics, and deck catalog integration.
- **Hardware bridge**: Refined motion/key handling and calibration JSON persistence.
- **Home**: `HomeScreen` refactor with entry points to calibration and settings.
- **Models**: `AppState` and activation/trigger types updated; removed legacy `InputPipeline` and `RecentInputEvent` in favor of repository-driven event history.
- **Build**: `buildConfig` enabled; Compose Material Icons Extended dependency.

### Fixed

- Action dispatcher logging aligned with new log helper.

## [1.1.1] - 2026-04-10

### Changed

- All in-app string resources and runtime status or hint copy moved to **English** (UI labels, diagnostics, mock data, repository messages, action summaries).

### Fixed

- `HardwareBridge`: persist calibration when the step index runs past the last step; use an explicit axis id for horizontal scroll where the SDK constant was not resolved for this toolchain.

## [1.1.0] - 2026-04-10

### Added

- `DeckBridgeApplication` and repository wiring for shared app state.
- Compose Navigation host and home screen scaffold.
- Domain models for deck actions, input diagnostics, keyboard snapshots, and profiles.
- Input pipeline: key capture rules, device snapshots, and forwarding of key events from `MainActivity`.
- DataStore-backed preferences and mock state factory for development.
- Action dispatcher layer for logging and future platform hooks.
- Material 3 theme updates (colors, typography) and expanded string resources.
- Dependencies: Navigation Compose, ViewModel Compose, DataStore Preferences.

### Changed

- `MainActivity` uses edge-to-edge layout, `NavHost`, and integrates with the repository on resume and key events.

## [1.0.0] - 2026-04-10

### Added

- Initial public release: Jetpack Compose shell, release signing via `keystore.properties`, and project tooling.

[1.6.0]: https://github.com/JUANES545/DeckBridge/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/JUANES545/DeckBridge/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/JUANES545/DeckBridge/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/JUANES545/DeckBridge/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/JUANES545/DeckBridge/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/JUANES545/DeckBridge/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/JUANES545/DeckBridge/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JUANES545/DeckBridge/releases/tag/v1.0.0
