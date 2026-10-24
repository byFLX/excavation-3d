# Excavation 3D

Standalone Minecraft 1.21.1 / NeoForge 21.1.x mod: normal mining tools (pickaxes, shovels, axes, also from other mods) get an excavation shape that is configured in game, with size controls and a rotatable 3D block preview. There is no own tool item. It does not depend on Create.

- Plan and acceptance criteria: [docs/EXCAVATION_3D_PLAN.md](docs/EXCAVATION_3D_PLAN.md)
- Acceptance matrix (automated evidence and open in-game checks): [docs/ACCEPTANCE_MATRIX.md](docs/ACCEPTANCE_MATRIX.md)
- Gradle project: `neoforge-1.21.1/` (`./gradlew test build`; world-level Game Tests: `./gradlew runGameTestServer`)
- Status: stages 1–5 of the plan are implemented (geometric core, editor with 3D preview, custom layer editor and presets, tool data component with server-confirmed apply and world preview, server-side excavation), with the correction of plan §2a (shapes on normal tools, no own item). The in-game and multiplayer checks of stage 6 are listed as open in the acceptance matrix; they have not been run.

## Using the tools

- Hold a pickaxe, shovel or axe and tap **V** to edit its shape. **Done** applies and enables it after server confirmation. Rejections stay visible in the editor. **Escape** closes without applying and asks before discarding edits.
- Hold **V** for 250 ms to open the wheel. Move to an entry and release V, click, or use 1–8 on the current page; scroll to change pages. Release in the centre or press Escape to cancel.
- The wheel pins **Off/On** at the top of every page, followed by favorites in save order. **Tunnel 1×2**, **Tunnel 3×3**, **Floor 5×5** and **Stairs** are seeded once as ordinary favorites (see below). Off pauses the shape on this tool. While paused the first entry becomes **On**, with the stored shape's thumbnail; select it to resume. Selecting any shape enables it. A tool without a shape marks Off. The gold dot marks Off when paused, otherwise the first matching shape in wheel order; unmatched shapes have no dot.
- Right-click any favorite, including a seeded default, then right-click the same segment again to delete it. Moving away cancels. After a right-click, releasing V closes without applying; a left-click still selects. With no favorites, a disabled segment says **Save favorites in the editor (tap V)**.
- **Shift** mines a single block temporarily. Paused tools also mine single blocks and show **Off · <shape summary>** in grey. Shapes stay on their individual tool items.
- In **Controls → Excavation 3D**, optional bindings toggle shape on/off, cycle Next/Previous preset, apply preset 1–5, or toggle world preview. Shape shortcuts use the same favorite order as the wheel (excluding Off and the empty hint); cycling wraps and starts at the first/last entry for paused or unmatched shapes. Empty slots do nothing. Shape changes use server confirmation.
- World preview is a client preference: `worldPreview = true` in `config/excavation3d-client.toml`. The preview appears for an enabled held shape. Its shortcut saves the client setting immediately and shows “World preview on/off” in the tool hint. Old per-tool preview values are ignored.
- Eligible tools are controlled by `excavation3d:shape_tools`, including vanilla pickaxe, shovel and axe tags. Excavation still respects tool suitability, protection and server limits.

## Editor and preview controls

- Choose a type, then use **− / +** or the wheel over a size row. Each changes the value by 1; hold **Shift** for 5. Values clamp to the server's axis range. Validation errors remain beside the affected row and disable Done.
- In every type's 3D preview, left-click a cube face to add its neighbour, click a ghost/floor cell to add it, or right-click a cube to remove it. The start block stays selected. A movement of at least 4 GUI pixels rotates instead of editing; wheel zooms and double-click resets the view. A single line shows dimensions and block count; size edits briefly flash added/removed blocks.
- The first preview edit of **Box, Plus, Stairs or Corridor** converts the selection to **Custom** and briefly shows **Switched to Custom**. The canvas grows by one cell on the sides needed for the clicked neighbour, within server limits. Rejected edits show the limit message; invalid templates show **Fix the shape first**. One **Undo** restores the exact template type and settings; **Redo** remains available on that template to reapply the edit.
- **Custom** shows layer navigation, Layer tools, Set start, Undo/Redo, the layer grid and Rotate/Mirror. Stairs and corridors use their Direction row.
- **Save as favorite** opens a name field. Press Save or Enter; an existing name requires Save/Enter again to confirm overwrite. Escape dismisses the field. Favorites live in `config/excavation3d-presets.json`: new names append, overwrites retain their slots. Legacy files migrate once to alphabetical order. Load and delete favorites in the wheel.
- On first load, a missing or false `defaultsSeeded` flag adds **Tunnel 1×2**, **Tunnel 3×3**, **Floor 5×5** and **Stairs** to the front in that order, then writes `defaultsSeeded: true`. Existing favorites keep their order; a matching English name keeps the user's configuration and slot. Files without an `order` array sort their existing favorites alphabetically first. Seeded favorites use these English names in every language. Deleting any or all favorites never restores the defaults. Migration preserves a full file even if the four seeds take it above the 64-favorite save limit; new names require fewer than 64 favorites.
- **Floor 5×5 orientation:** the existing hit-face mapping sends local width across the player, local height along horizontal player facing, and local depth downward when hitting the ground. Its cuboid is therefore local **5×5×1**, yielding a level 25-block patch, rather than local 5×1×5 which would dig five blocks down. Stairs use their existing defaults and horizontal player orientation.

## Code layout (`com.byflx.excavation3d`)

| Package | Content | Side |
|---|---|---|
| `shape` | Pure geometry: config, generator, validator, transforms, orientation, encoding | both |
| `editor` | Editor draft and custom layer draft (undo/redo, resize) | both, pure |
| `preview` | Preview geometry: world-preview contour and fill, editor block scale, size-change diff, label placement | both, pure |
| `data`, `registry`, `config` | Data component and codecs, component registration and the `shape_tools` tag key, server config | both |
| `net` | Payloads and server-side editor sessions | both (handlers per side) |
| `excavation` | Server-side excavation and tick budgets | server logic |
| `client` | Editor screen, 3D preview, world preview, hint, keys, presets | client only |
| `gametest` | Game Tests for excavation in the world; only active on the Game Test server | server |

The scaffold uses the same ModDevGradle, NeoForge and JUnit versions as the byFLX Create Stock Instant Craft mod, so the Agent-Team's offline Gradle sandbox can build it from its existing dependency seed.
