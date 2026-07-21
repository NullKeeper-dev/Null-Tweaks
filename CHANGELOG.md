# Changelog

All notable changes to Null Tweaks will be documented in this file.

## [1.4.0] - 2026-07-21

### Added

- Added an absolute Quarry block-list mode toggle: blacklist mode prevents Quarry and Baritone from mining listed blocks, while whitelist mode prevents them from mining unlisted blocks.
- Added `/quarry mode blacklist`, `/quarry mode whitelist`, and neutral `/quarry blocklist` commands for managing Quarry block-list behavior.
- Added `/quarry <radius>` to create a spherical Quarry selection centered on the player before starting it with `/quarry start`.
- Added a Quarry Baritone chat-log toggle, including `/quarry chatlogs on` and `/quarry chatlogs off`, to hide Baritone messages globally when disabled.
- Added Speed Nuker as an independent opt-in module with `/speednuker` commands, a default three-block reach, configurable 2–64 block attempt limit, absolute standalone blacklist or whitelist filtering, and a clear server-ban/disconnect warning without anti-detection behavior.
- Added optional Quarry integration for Speed Nuker. When enabled alongside Quarry, it shares Quarry's block list and replaces Quarry's normal mining action inside the active selection; otherwise it uses its separate standalone lists.
- Added Full, Protect Below, and Protect Y Level Speed Nuker mining modes, an unbound on/off hotkey, and a configurable `1–6` block reach in `0.5`-block increments.
- Added a Quarry player-proximity behavior option so another player entering render distance pauses Quarry by default or can be ignored.

### Fixed

- Fixed `/quarry clear` so it remains usable for clearing saved Quarry state even when Quarry is disabled in settings.
- Fixed Quarry startup to begin at the nearest non-skipped block inside the selected box instead of always starting from the first traversal corner.
- Fixed Quarry mining stability on slower blocks such as deepslate by cancelling active Baritone pathing once the target is within reach.
- Fixed Quarry automation state around client screens by cancelling its active mining and Baritone path as soon as chat, inventory, Mod Menu, Escape, or another screen opens, then resuming after the screen closes.
- Fixed Quarry navigation so Baritone paths to reachable standing positions near target blocks and skips targets that stop making pathing progress instead of repeatedly jumping in place.

### Changed

- Reduced Quarry's mining reach from 4.5 blocks to 3 blocks and made reach checks use the player's eyes and the nearest target face.

## [1.3.0] - 2026-07-16

### Added

- Added Quarry automation with `/quarry` commands, keybinds, configurable overlay rendering, top-down task progress persistence, whitelist controls, safety pause options, and optional Baritone integration.
- Added a join warning when Quarry cannot find a compatible Baritone install, with a Null-Baritone download button and persistent "Don't show again" control.
- Added `/quarry clear` to stop Quarry and clear the saved selection and task progress.

### Changed

- Updated Quarry documentation to point at compatible Null-Baritone builds and clarify that current builds still require Baritone to be loaded separately until single-jar packaging is implemented.

### Fixed

- Fixed Quarry's selected-region overlay so choosing both positions no longer crashes the client during world rendering, including on the 26.2 line vertex format.
- Fixed Quarry's Baritone detection and command dispatch so compatible Baritone forks with nonstandard Fabric mod ids, such as Null-Baritone, work even when they expose only the chat-command integration.
- Fixed Quarry block breaking so it continues one break action per target instead of restarting the same block every tick.
- Changed Quarry target selection to automatically skip vegetation-like blocks, including leaves, flowers, crops, vines, bamboo, and replaceable plants.
- Changed Quarry traversal to snake across rows and layers continuously, dropping at the layer end instead of returning to the first corner before moving down.
- Isolated client tick, HUD render, and world-render feature failures so an exception disables the failing feature for the session instead of crashing the game.

## [1.2.1] - 2026-07-13

### Fixed

- Fixed Freecam so toggling during a jump lets the player land naturally before freezing, then keeps the frozen player reported as grounded to prevent false server flying flags.

## [1.2.0] - 2026-07-13

### Added

- Added Max Enchant Indicator to color max-level enchantments in item tooltips, enchanting table previews, and Librarian Trade Scanner labels with solid or chroma colors.

### Changed

- Grouped the Null Tweaks config screen into Visuals, World Info, and Movement & Automation tabs with each feature collapsed by default.
- Added descriptions to each feature group in the Null Tweaks config screen.

## [1.1.1] - 2026-07-12

### Fixed

- Fixed Autoclicker so it keeps clicking while menus, inventories, or other screens are open.

## [1.1.0] - 2026-07-12

### Added

- Added Librarian Trade Scanner to background-scan nearby librarians for enchanted book trades and render configurable floating trade labels.
- Added Enchant Search Highlight commands and config controls to glow known librarians offering a searched enchanted book.
- Added Raid Mob Highlight with separate through-wall colors for banner-carrying Pillager leaders and other raid mobs.

### Changed

- Persisted Librarian Trade Scanner results per world/server using villager UUIDs so cached labels can reappear after relog without reopening trades.
- Marked Librarian Trade Scanner as experimental and rewrote scanning around a closest-first single-flight queue with strict merchant window correlation.

### Fixed

- Fixed Librarian Trade Scanner prices to cache base emerald costs and apply Hero of the Village discounts live from the player's current effect level.
- Fixed Librarian Trade Scanner manual interactions so real librarian clicks preempt queued or in-flight background scans instead of being delayed or auto-closed.
- Fixed raid mob highlighting to route qualifying mobs through Minecraft's vanilla outline-pass decision so local outlines can render through walls.

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
