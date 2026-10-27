# CurseForge upload - Excavation 3D

Everything needed for the upload. The operator does the upload (MOD_STANDARDS §10).

## Project (first time only)

| Field | Value |
|---|---|
| Name | Excavation 3D |
| Summary | Area mining for your normal tools - tunnels, floors, stairs and custom shapes with a 3D preview. |
| Project image / avatar | `docs/logo/logo-512.png` |
| Main category | Utility & QoL |
| Additional categories | Armor, Tools, and Weapons |
| License | MIT License |
| Source | https://github.com/byFLX/excavation-3d |
| Issues | https://github.com/byFLX/excavation-3d/issues |

Screenshots are worth adding: the quick wheel, the editor with the 3D preview, and the world outline in front of a
wall.

## File

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

### Changelog (paste as Markdown)

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

Give your **normal pickaxes, shovels and axes** an excavation shape - a tunnel, a floor, stairs or your own
block pattern - and set it up in game with a 3D preview and a quick wheel. No new tool item, no recipe.

## Features

- **Works with the tools you already have** - vanilla tools and tools from other mods (via the vanilla tool tags).
- **Ready-made shapes:** Tunnel 1×2, Tunnel 3×3, Floor 5×5 and Stairs.
- **Shape editor:** Box, Plus, Stairs, Corridor and Custom shapes with size buttons, a rotatable 3D preview, Undo/Redo
  and Reset. Click blocks in the preview to add or remove them, or build any pattern layer by layer.
- **Quick wheel:** hold the editor key, pick a favorite or switch the shape off - without opening a screen.
- **World preview:** an outline shows exactly which blocks the next swing will mine.
- **Bundled drops:** the drops of a shape go straight into your inventory or land as one pile at the start block -
  your choice in the editor (or vanilla scattering).
- **Fair:** a shape only mines blocks the tool is meant for (a pickaxe digs stone, not wood). Drops, durability,
  enchantments and block protection work like normal mining. Big shapes are spread over several ticks.

## How to use

1. Hold a pickaxe, shovel or axe.
2. **Hold V** to open the quick wheel, point at a shape, release V.
3. Mine - the whole shape is excavated.
4. **Tap V** to open the editor and make your own shape. **Done** puts it on the tool.

Hold **Shift** to mine a single block. More keys (toggle, next/previous favorite, favorite 1-5, world preview) can
be bound under Controls → Excavation 3D.

## Configuration

Server: `serverconfig/excavation3d-server.toml` - maximum shape size and per-tick work budgets.
Client: `config/excavation3d-client.toml` - world preview on/off.

## Requirements

Minecraft 1.21.1 · NeoForge 21.1.229 or newer · install on **client and server**.

## Issues

Please report problems on [GitHub](https://github.com/byFLX/excavation-3d/issues) with your mod, NeoForge and
Minecraft versions, your mod list and `logs/latest.log`.
```
