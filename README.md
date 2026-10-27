# Excavation 3D

Give your normal pickaxes, shovels and axes an excavation shape - a tunnel, a floor, stairs or your own block
pattern - and set it up in game with a 3D preview and a quick wheel.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.229** or newer
- Java 21
- Required on both the server and the client. No other mods needed.

## What it adds

- **Shapes on the tools you already have.** Every pickaxe, shovel and axe (also from other mods, via the item tags)
  can carry an excavation shape. There is no new tool item and no crafting recipe.
- **Ready-made shapes:** Tunnel 1×2, Tunnel 3×3, Floor 5×5 and Stairs are there from the start.
- **Shape editor** with Box, Plus, Stairs, Corridor and Custom shapes, size buttons, a rotatable 3D preview and full
  Undo/Redo. Click blocks in the preview to add or remove them; build any pattern layer by layer in Custom mode.
- **Bundled drops:** send a whole excavation to your inventory (default), collect a pile at the start, or keep
  vanilla drops. Overflow lands at your feet, and XP stays a pickup orb.
- **Quick wheel:** hold the editor key to pick a favorite shape or switch the shape off, without opening a screen.
- **World preview:** an outline in the world shows exactly which blocks the next swing will mine.
- **Fair mining:** a shape only mines blocks the held tool is meant for (a pickaxe digs stone, not wood). Drops,
  durability, enchantments and protection work like normal mining, and the server spreads big excavations over
  several ticks.

## Getting started

1. Hold a pickaxe, shovel or axe.
2. **Hold V**: the quick wheel opens. Move the mouse to a shape (e.g. **Tunnel 3×3**) and release V.
3. Mine a block: the whole shape is excavated. The outline in the world shows it before you mine.
4. **Tap V** to open the editor and change the shape. Press **Done** to put it on the tool. **Escape** closes
   without shape changes. Drop-mode preferences save immediately.

More controls:

| Key | What it does |
|---|---|
| Hold **V** | Quick wheel: pick a favorite, **Off** pauses the shape (it stays on the tool) |
| Tap **V** | Shape editor |
| Hold **Shift** while mining | Mine a single block only |
| In the wheel: right-click a favorite twice | Delete that favorite |
| In the editor: **Favorites ▾** | Save the current shape, load, edit or delete favorites |
| In the editor: **Drops: Inventory / Pile at start / Vanilla** | Cycle drop delivery; saved immediately without Done |
| Optional keys (Controls → Excavation 3D) | Toggle shape, next/previous favorite, favorite 1-5, toggle world preview, cycle drop mode (unbound by default) |

In creative mode, tools mine single blocks.

## Configuration

**Server** - `serverconfig/excavation3d-server.toml` in the world folder. No restart needed: edits are picked up
while the server runs, and players get new size limits the next time they join.

| Option | Default | Meaning |
|---|---|---|
| `limits.maxAxis` | 32 | Largest size of a shape along any axis, in blocks (1-32) |
| `limits.maxSelected` | 4096 | Largest number of blocks in one shape (1-4096) |
| `budget.perPlayerPerTick` | 32 | Positions checked per player and tick (1-4096) |
| `drops.forceMode` | `PLAYER_CHOICE` | Let players choose, or force `INVENTORY`, `START_PILE` or `VANILLA`; applies to new excavations immediately |
| `budget.globalPerTick` | 256 | Positions checked per tick across all players (1-65536) |

**Client** - `config/excavation3d-client.toml`:

| Option | Default | Meaning |
|---|---|---|
| `dropMode` | `INVENTORY` | `INVENTORY`, `START_PILE` or `VANILLA`; saved per player on this computer, sent on join/change, no restart needed |
| `worldPreview` | true | Show the held tool's shape as an outline in the world |

The server override greys out the drop button with **Set by the server**. Drop choice is independent of shape
edits: Escape keeps the selected drop mode. Bundles include the start block; they deliver when excavation ends
or is interrupted. A 256-stack memory cap triggers early item deliveries for unusually large loot. XP uses one
orb per flush, preserving vanilla pickup and Mending; exceptionally large modded XP rewards flush early
at 32,767 XP and split into safe-sized orbs. On logout, death, dimension change or server stop,
collected drops go to the original start (last known position in that dimension if its chunk is unloaded).

Favorites are stored on your computer in `config/excavation3d-presets.json`, so they work on every server.

**Tags:** `excavation3d:shape_tools` (item tag) decides which tools can carry a shape. It contains
`#minecraft:pickaxes`, `#minecraft:shovels` and `#minecraft:axes`, and can be changed with a datapack.

## Compatibility

- Version **1.1.0** uses network protocol **6**; client and server must both use 1.1.0. The saved shape format stays version 1.
- The following runtime evidence is for **1.0.0**; 1.1.0 still needs a full build and in-game checks.
- Tested with NeoForge **21.1.229** and **21.1.265** on Minecraft 1.21.1 (build, unit tests, game tests, dedicated
  server and client start). Details: [docs/TESTING.md](docs/TESTING.md).
- Tools from other mods work when they are in the vanilla pickaxe, shovel or axe tags.
- Block protection mods that cancel block breaking also stop the excavation of protected blocks.
- No known conflicts. Other area-mining mods (e.g. vein miners) may react to the same block break; use one at a
  time on the same tool.

## Reporting issues

Please open an issue at <https://github.com/byFLX/excavation-3d/issues> with:

- Excavation 3D version, NeoForge version and Minecraft version
- the other mods in your game or modpack
- your log file (`logs/latest.log`, and the crash report if there is one)
- the steps to reproduce the problem

## Building from source

```bash
cd neoforge-1.21.1
./gradlew build
```

The jar is `neoforge-1.21.1/build/libs/excavation3d-neoforge-1.21.1-<version>.jar`. Unit tests: `./gradlew test`,
game tests: `./gradlew runGameTestServer`. Development notes: [docs/BEHAVIOUR_REFERENCE.md](docs/BEHAVIOUR_REFERENCE.md),
[docs/EXCAVATION_3D_PLAN.md](docs/EXCAVATION_3D_PLAN.md).

## License

MIT, Copyright (c) 2026 byFLX. See [LICENSE](LICENSE).
