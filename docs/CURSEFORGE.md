# CurseForge upload - Excavation 3D

Everything needed for the upload. The operator does the upload (MOD_STANDARDS §10).

## Project (first time only)

| Field | Value |
|---|---|
| Name | Excavation 3D |
| Summary | Area mining with your normal pickaxes, shovels and axes: tunnels, floors, stairs or custom 3D shapes with preview, quick wheel and bundled drops. |
| Project image / avatar | `docs/logo/logo-512.png` |
| Main category | Utility & QoL |
| Additional categories | Armor, Tools, and Weapons |
| License | MIT License |
| Source | https://github.com/byFLX/excavation-3d |
| Issues | https://github.com/byFLX/excavation-3d/issues |

Screenshots are worth adding: the quick wheel, the editor with the 3D preview, and the world outline in front of a
wall.

## File (latest: 1.1.0)

| Field | Value |
|---|---|
| File | `neoforge-1.21.1/build/libs/excavation3d-neoforge-1.21.1-1.1.0.jar` |
| Display name | Excavation 3D 1.1.0 (NeoForge 1.21.1) |
| Release type | Release |
| Game version | 1.21.1 |
| Mod loader | NeoForge |
| Java | Java 21 |
| Environment | Client and Server (required on both) |
| Relations | none (no required or optional mod dependencies) |

### Changelog 1.1.0 (paste as Markdown)

```markdown
**New: bundled drops.** The drops of a shape no longer lie scattered around:

- **Inventory** (default): everything goes straight into your inventory as full stacks; what does not fit drops
  as one pile at your feet. XP comes as one orb.
- **Pile at start**: everything drops as one pile where you started mining.
- **Vanilla**: drops stay where each block was, like before.

Switch it in the editor (tap V, bottom left: "Drops: ...") or bind the optional "Cycle drop mode" key. Servers can
force one mode with `drops.forceMode` in `excavation3d-server.toml`.

Client and server must both use 1.1.0. Requires NeoForge 21.1.229 or newer on Minecraft 1.21.1.
```

### Changelog 1.0.0 (for reference)

```markdown
First public release.

- Excavation shapes on your normal pickaxes, shovels and axes (also tools from other mods) - no new item.
- Ready-made favorites: Tunnel 1×2, Tunnel 3×3, Floor 5×5, Stairs.
- Shape editor (tap V): Box, Plus, Stairs, Corridor and Custom shapes, 3D preview, click blocks to add/remove,
  Undo/Redo, Reset to defaults, Favorites panel.
- Quick wheel (hold V): pick a favorite or switch the shape off.
- World outline shows what the next swing will mine.
- A shape only mines blocks the tool is meant for; drops, durability, enchantments and protection work as normal.
- Shift mines a single block.

Requires NeoForge 21.1.229 or newer on Minecraft 1.21.1.
```

## Description (paste as Markdown)

```markdown
# Excavation 3D

Area mining for the tools you already have: give any pickaxe, shovel or axe a shape and mine a whole tunnel,
floor or staircase with one swing. No new item, no recipe.

## Features

- **Works with normal tools** - vanilla and modded pickaxes, shovels and axes.
- **Ready-made shapes:** Tunnel 1×2, Tunnel 3×3, Floor 5×5, Stairs.
- **Shape editor** (tap **V**): Box, Plus, Stairs, Corridor and Custom shapes, 3D preview, click blocks to
  add/remove, Undo/Redo, Reset, Rotate/Mirror.
- **Favorites:** save, load, edit and delete your own shapes.
- **Quick wheel** (hold **V**): switch shape or turn it off instantly.
- **World preview:** an outline shows exactly what the next swing mines.
- **Bundled drops:** straight into your inventory, as one pile at the start block, or vanilla - your choice.
- **Fair mining:** only blocks the tool is meant for; Fortune, Silk Touch, durability and protection work as
  normal. Hold **Shift** for a single block.
- **Custom key bindings:** every key can be changed under Controls → Excavation 3D, plus optional keys for
  toggle shape, next/previous favorite, favorite 1-5, world preview and drop mode.
- **Server-friendly:** size limits and per-tick budgets in the server config; drop mode can be forced.

## Requirements

Minecraft 1.21.1 · NeoForge 21.1.229+ · install on **client and server**.

Issues: [GitHub](https://github.com/byFLX/excavation-3d/issues)
```
