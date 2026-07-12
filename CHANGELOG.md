# Changelog

All notable changes to Null Tweaks will be documented in this file.

## [1.0.1] - 2026-07-12

### Fixed

- Fixed Freecam so enabling it mid-air lets the physical player continue normal vanilla physics with movement input cleared instead of freezing in place.
- Fixed Freecam toggles while movement keys are held by clearing stale movement flags, jump state, sprint/sneak state, travel input, and player velocity at the toggle instant.
- Fixed Freecam camera coordinates for F3 and camera-aware mods by exposing the detached camera entity while Freecam is active.
- Removed the OuterLayer+ HUD editor button from Minecraft's pause menu while keeping the feature and editor available from Null Tweaks settings.

## [1.0.0] - 2026-07-09

### Removed

- Removed Clear Lava & Water and its water/lava opacity controls.

### Fixed

- Fixed GitHub Actions builds on Linux by marking the Gradle wrapper executable.

## [0.5.4] - 2026-07-09

### Added

- Added the Null Tweaks cover art as the packaged mod icon.
- Added namespaced Modrinth dependency metadata hints to the packaged Fabric metadata.

## [0.5.3] - 2026-07-09

### Fixed

- Fixed README image paths so the cover and selected gallery screenshots render from the repository assets.

## [0.5.2] - 2026-07-09

### Added

- Added a dependency-mod reference file for the required runtime mods.

### Changed

- Refreshed the README with a polished cover layout, feature overview, and gallery placeholders for users.

## [0.5.1] - 2026-07-09

### Changed

- Removed the unreleased optional seed integration category and entrypoint.

### Fixed

- Fixed Clear Lava & Water so opacity and tint changes refresh already-loaded terrain immediately.
- Fixed No Fishing Bobber so the local player's fishing line remains visible while only the hook sprite is hidden.

## [0.5.0] - 2026-07-09

### Added

- Added No Fishing Bobber to hide the local player's fishing hook sprite client-side.
- Added No Fog with independent toggles for lava, water, powder snow, Blindness, Darkness, and atmospheric/world fog.
- Added Clear Lava & Water with separate per-fluid tint colors and opacity controls.
- Added Autoclicker with a fixed-interval numeric input, left/right click selection, unbound toggle keybind, and active HUD indicator.

## [0.4.0] - 2026-07-09

### Changed

- Changed build collection to output only launchable mod jars instead of source jars.

## [0.3.0] - 2026-07-09

### Added

- Added an unbound keybind to open the Null Tweaks settings screen directly.
- Added descriptions to Null Tweaks config options.
- Added config-screen category memory so reopening settings returns to the last selected feature category.

### Fixed

- Fixed Freecam cave visibility by disabling Minecraft's global smart-cull state while Freecam is active.
- Removed the Freecam HUD text indicator.
- Fixed the Null Tweaks keybind category label in Minecraft's Controls screen.

## [0.2.1] - 2026-07-09

### Fixed

- Smoothed Freecam camera movement between client ticks.
- Fixed Freecam forward and backward movement so looking up or down no longer changes height.
- Fixed Freecam local body rendering while staying in first-person perspective.
- Fixed Freecam cave and underground visibility by disabling smart chunk culling while active.

## [0.2.0] - 2026-07-09

### Added

- Added a Freecam setting to show or hide the first-person hand while freecam is active.
- Added an OuterLayer+ overlay background opacity setting.

### Fixed

- Fixed Freecam so it no longer replaces Minecraft's camera entity, preserving the vanilla HUD, hand rendering, and player identity checks.
- Fixed Freecam block-overlay and lava-fog handling while the free camera is inside terrain or lava.
- Fixed OuterLayer+ tab-list rings to align with vanilla player head icons.
- Fixed OuterLayer+ tab-list rings to show a white tracked-player outline when distance coloring is disabled.
- Fixed OuterLayer+ overlay face icons to include the skin hat layer.
- Improved OuterLayer+ tab-list name recoloring so it applies later in the render path while preserving server-provided display text.
