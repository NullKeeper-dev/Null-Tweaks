<div align="center">
  <img src="https://raw.githubusercontent.com/NullKeeper-dev/Null-Tweaks/main/src/main/resources/assets/nulltweaks/icon.png" alt="Null Tweaks icon" width="128">
  <h1>Null Tweaks</h1>
  <p>A clean client-side Fabric mod for focused visual, utility, and automation tweaks.</p>
  <p>
    <img src="https://img.shields.io/badge/Loader-Fabric-dbd0b4" alt="Fabric loader">
    <img src="https://img.shields.io/badge/Minecraft-26.1%E2%80%9326.2-62b47a" alt="Minecraft 26.1–26.2">
    <img src="https://img.shields.io/badge/License-GPL--3.0--only-3da639" alt="GPL-3.0-only license">
  </p>
</div>

## Features

| Feature | Type | Highlight |
| --- | --- | --- |
| OuterLayer+ | Visual | Cleans up the player overlay and tab-list presentation. |
| Nametag Tweaks | Visual | Improves player-name visibility with configurable styling. |
| Freecam | Movement | Detaches the camera with configurable speed and hand visibility. |
| No Fishing Bobber | Visual | Hides your local hook sprite while keeping its fishing line visible. |
| No Fog | Visual | Controls lava, water, powder snow, effect, and world fog separately. |
| Autoclicker | Utility | Repeats left or right clicks at a fixed interval with an active indicator. |
| Quarry | Automation | Mines boxed or spherical selections with filtering, safety controls, and Baritone pathing. |
| Speed Nuker | Automation | Packet-mines nearby eligible blocks with reach, rate, protection, and list controls. |
| Raid Mob Highlight | World info | Outlines raid mobs and banner-carrying Pillager leaders through walls. |
| Librarian Trade Scanner | World info | Scans nearby librarians and displays their enchanted-book trades. |
| Max Enchant Indicator | World info | Marks maximum-level enchantments with a solid or animated chroma color. |

## See it in action

### Configure every tweak

Open one organized settings screen and adjust each feature without editing config files.

<p align="center">
  <img src="https://raw.githubusercontent.com/NullKeeper-dev/Null-Tweaks/main/assets/gallery/Settings.png" alt="Null Tweaks settings screen" width="760">
</p>

### Clear obstructive fog

Remove only the fog types you do not want, including water fog.

<p align="center">
  <img src="https://raw.githubusercontent.com/NullKeeper-dev/Null-Tweaks/main/assets/gallery/No%20Water%20Fog.png" alt="No Water Fog in action" width="760">
</p>

### Read nametags at a glance

Tune player nametags for clearer identification in busy scenes.

<p align="center">
  <img src="https://raw.githubusercontent.com/NullKeeper-dev/Null-Tweaks/main/assets/gallery/Nametags%20showcase.png" alt="Nametag Tweaks in action" width="760">
</p>

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for your Minecraft version.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api), [YetAnotherConfigLib](https://modrinth.com/mod/yacl), and [Mod Menu](https://modrinth.com/mod/modmenu) from Modrinth.
3. Download the matching Null Tweaks jar from [Modrinth](https://modrinth.com/mod/null-tweaks).
4. Place Null Tweaks and all required dependency jars in your Minecraft `mods` folder.
5. Launch the Fabric profile. To use Quarry, also install a compatible Fabric [Null-Baritone](https://github.com/NullKeeper-dev/Null-Baritone) build; other features do not require Baritone.

> [!NOTE]
> Null Tweaks supports Minecraft `26.1`, `26.1.1`, `26.1.2`, and `26.2`. Use the jar matching your exact game version. The mod is client-side only and does not need to be installed on the server.

## Use

1. Launch Minecraft with Fabric and open **Mods** from the main menu.
2. Select **Null Tweaks**, then choose **Configure**.
3. Open the **Visuals**, **World Info**, or **Movement & Automation** tab and enable the features you want.
4. Adjust each feature's options, then close the screen; changes are saved automatically.
5. Assign any desired Null Tweaks controls under **Options → Controls → Key Binds**. Quarry, Speed Nuker, and enchant search also expose in-game commands for their advanced workflows.

> **Tip:** Null Tweaks' config and automation keybinds are unbound by default. Speed Nuker sends mining packets without attempting to evade detection, so server anti-cheat may reject the reach or rate, disconnect you, or ban you.

<details>
<summary><strong>Build from source</strong></summary>

Requires JDK 25.

Build all configured Minecraft version targets with the Gradle wrapper:

```bash
./gradlew chiseledBuild
```

On Windows PowerShell, use:

```powershell
.\gradlew.bat chiseledBuild
```

The distributable jars are collected in `build/libs/<mod_version>/`.

</details>

<p align="center">
  <a href="https://spdx.org/licenses/GPL-3.0-only.html">GPL-3.0-only</a> · 2026 · <a href="https://github.com/NullKeeper-dev">NullKeeper-dev</a>
</p>
