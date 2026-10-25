# Changelog

All notable changes to Excavation 3D. The format follows [Keep a Changelog](https://keepachangelog.com/), versions
follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-25

First public release.

### Added

- Excavation shapes on normal pickaxes, shovels and axes (item tag `excavation3d:shape_tools`, includes tools from
  other mods in the vanilla tool tags). No own tool item.
- Shape editor (tap V): Box, Plus, Stairs, Corridor and Custom shapes, size buttons, Reset to defaults, rotatable and
  zoomable 3D preview, click-to-add/remove blocks in every mode, Custom layer editor with Rotate/Mirror, full
  Undo/Redo, and a Favorites panel to save, load, edit and delete shapes.
- Quick wheel (hold V): Off on top, favorites with thumbnails, number keys 1-8, Resume for a paused shape,
  two-step delete.
- Ready-made favorites: Tunnel 1×2, Tunnel 3×3, Floor 5×5, Stairs (deletable).
- World preview outline of the blocks the next swing will mine, switchable in the client config or with an
  optional key.
- Short tool hint above the hotbar that fades out; Shift mines a single block.
- Optional key bindings: toggle shape, next/previous favorite, favorite 1-5, toggle world preview.
- Server config with shape size limits and per-tick work budgets.

### Compatibility

- Minecraft 1.21.1, NeoForge 21.1.229 or newer (21.1.229 closes a server crash exploit via crafted packets).
  Tested with NeoForge 21.1.229 and 21.1.265.
- Client and server must both have the mod (network protocol version 5).
