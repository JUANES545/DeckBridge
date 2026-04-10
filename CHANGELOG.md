# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

**Language:** Changelog entries and pull request descriptions are written in **English**.

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

[1.2.0]: https://github.com/JUANES545/DeckBridge/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/JUANES545/DeckBridge/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/JUANES545/DeckBridge/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JUANES545/DeckBridge/releases/tag/v1.0.0
