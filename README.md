<p align="center">
  <img src="assets/cover.png" alt="Null Tweaks cover" width="520">
</p>

<h1 align="center">Null Tweaks</h1>

<p align="center">
  A clean client-side Fabric mod for small visual and quality-of-life tweaks.
</p>

<p align="center">
  <a href="LICENSE">GPL-3.0-only</a> | Minecraft 26.1-26.2 | Fabric Client
</p>

## Overview

Null Tweaks keeps a focused set of client-side options in one tidy settings
screen. It is built for players who want small visual cleanup, better HUD
control, and a few practical utility toggles without changing server-side
gameplay.

## Features

- OuterLayer+ for a cleaner player overlay and tab-list presentation.
- Nametag Tweaks for easier player-name visibility.
- Freecam with configurable movement and first-person hand visibility.
- No Fishing Bobber to hide only your local hook sprite while keeping the line.
- No Fog with separate toggles for lava, water, powder snow, effects, and world fog.
- Autoclicker for fixed-interval left or right clicking with a visible active indicator.
- Quarry automation for selected boxed regions, with `/quarry clear` selection reset, a wireframe selection overlay, layer-continuous serpentine traversal, automatic vegetation skipping, and optional Baritone integration for pathing.

## Gallery

<p align="center">
  <img src="assets/gallery/Settings.png" alt="Null Tweaks settings screen" width="760">
</p>

<p align="center">
  <sub>Settings are grouped by feature so each tweak stays easy to find.</sub>
</p>

<p align="center">
  <img src="assets/gallery/No%20Water%20Fog.png" alt="No water fog showcase" width="760">
</p>

<p align="center">
  <sub>No Fog can remove specific fog types without changing gameplay.</sub>
</p>

<p align="center">
  <img src="assets/gallery/Nametags%20showcase.png" alt="Nametag tweaks showcase" width="760">
</p>

<p align="center">
  <sub>Nametag Tweaks keeps player names easier to read at a glance.</sub>
</p>

## Requirements

- Required: [Fabric Loader](https://fabricmc.net/use/installer/)
- Required: [Fabric API](https://modrinth.com/mod/fabric-api)
- Required: [YetAnotherConfigLib](https://modrinth.com/mod/yacl)
- Required: [Mod Menu](https://modrinth.com/mod/modmenu)
- Optional for Quarry: Quarry currently uses Baritone for pathing and movement.
  For local testing, install a compatible Fabric Baritone jar, or a fork such
  as [Null-Baritone](https://github.com/NullKeeper-dev/Null-Baritone), beside
  Null Tweaks. If no compatible Baritone is loaded, Null Tweaks shows a join
  warning with a Null-Baritone download link and a persistent "Don't show
  again" option. A single-jar release would require bundling or replacing the
  Baritone integration; until then, Quarry will not start unless a compatible
  Baritone API or command-capable Baritone fork is loaded.

See [DEPENDENCY_MODS.md](DEPENDENCY_MODS.md) for the exact dependency versions
used by each supported Minecraft release.

## Download

Use the Null Tweaks jar that matches your Minecraft version:

- `26.1`
- `26.1.1`
- `26.1.2`
- `26.2`

Install it in your `mods` folder with the required dependency mods, then open
the Mod Menu entry to configure each feature.
