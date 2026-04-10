# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

**Language:** Changelog entries and pull request descriptions are written in **English**.

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

[1.1.1]: https://github.com/JUANES545/DeckBridge/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/JUANES545/DeckBridge/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JUANES545/DeckBridge/releases/tag/v1.0.0
