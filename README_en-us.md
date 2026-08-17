# Sparse Vertical Expansion

[English](README_en-us.md) | [简体中文](README.md)

Sparse Vertical Expansion (SVE) is an experimental Minecraft mod currently supporting Minecraft 1.21.1 on NeoForge. It gives selected chunk regions on-demand access to heights far beyond vanilla without increasing the entire dimension's continuous build height. Each chunk can reach approximately ±8 million blocks, while the default limit is approximately ±2 million blocks.

> Current version: `0.1.0-beta.2`. **Back up your worlds first** and use this only in test worlds.  
> This version is compatible with Sodium `0.8.12/0.8.13` and Embeddium `1.0.x` (both beta-stage). Other versions are untested, and **other renderer-replacement mods are incompatible**.

- Design: xiaoshuaixia
- Development: gpt (AI), deepseek (AI)

## Core Features

- Normal chunks continue to use vanilla `Y=-64..319` and the vanilla contiguous section array.
- Extended sections use sparse storage keyed by `int sectionY`; empty heights between sections allocate no storage.
- An empty section is automatically reclaimed after its last non-air block is removed.
- Extended data is saved in chunk NBT and survives leaving and re-entering the world.
- Each chunk or chunk group can have an independent vertical build region; regions cannot overlap.
- The vanilla height range cannot be modified. All extended ranges are aligned outward to complete 16-block sections.
- The default save-wide maximum Y is `2,000,015`; the standard coordinate range is `-8,388,608..8,388,607`.

## Currently Available

- Place, break, and read ordinary blocks inside configured regions.
- Client synchronization, the vanilla rendering path, collision, movement, sounds, particles, and survival mining.
- The `/sve` height editor; right-click an existing region in the vertical bar to delete an empty region.
- Deleting a non-empty region is rejected and reports the first non-air block encountered.
- Extended-coordinate parsing and region-boundary validation for `setblock`, `fill`, and `clone`.
- WorldEdit 7.3.8 batch-write boundary validation; invalid operations do not leave partial fills.
- Save-wide maximum-Y, void-damage, and permission-level configuration.

## Usage

1. Stand in the target chunk and run `/sve` as a player with `sve.region.edit`.
2. Enter the minimum and maximum Y values. When an input loses focus, it is automatically expanded outward to complete sections.
3. After confirming the build region, run `/sve platform <x> <y> <z>` to create the first block, or continue building from an existing block surface.
4. Left-click an existing region in the vertical bar to select it; right-click to delete it. Deletion is rejected if the region contains any blocks.

## Permissions

- `sve.extended.build`: build at extended heights; granted to all players by default.
- `sve.region.edit`: edit chunk vertical regions.
- `sve.config.edit`: edit save-wide configuration.
- `sve.experimental.edit`: edit experimental settings.
- `sve.command.all`: access all SVE command permissions.

Without a permissions mod, permission nodes map to vanilla permission levels `0..4`; use `/sve permission` to change the mapping. With a permissions mod such as LuckPerms, player permissions can be managed externally.

## Save Configuration

Configuration can only be changed by the server or the owner of a single-player world and is stored per save:

```text
/sve config list
/sve config set default_extended_max_y <y>
/sve config set disable_void_damage false|player|entity
```

`disable_void_damage` defaults to `false`.

## Compatibility and Limitations

- Currently supports only Minecraft 1.21.1, NeoForge, and Java 21.
- The Sodium `0.8.12/0.8.13` and Embeddium `1.0.x` sparse-section compatibility layers are implemented (optional reflection bridge + optional mixins; these mods are never bundled, and the vanilla path is untouched when they are absent). Shaders have been verified (including shadows); Iris compatibility is still being tested.
- Block entities are not supported at extended heights yet.
- Sparse lighting is not implemented yet; high-altitude blocks currently use a full-bright fallback.
- Scheduled ticks, random ticks, fluid ticks, redstone, mob spawning, weather, and other segmented simulation rules are not implemented yet.
- The experimental double-coordinate mode is not implemented yet.
- Create, Valkyrien Skies, and Aeronautics compatibility is outside the guarantees of this beta.

## Installation and Building

Runtime requirements:

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21

Build with:

```powershell
.\gradlew.bat build
```

Build artifacts are written to `build/libs/`.

## Distribution

- [GitHub](https://github.com/xiaoshuaixia2012/sparse-vertical-expansion)
- Modrinth
- MC百科

## License

This project is licensed under [GNU GPLv3](LICENSE). Modpacks may include and distribute this mod; modified or derivative SVE code must continue to comply with GPLv3.
