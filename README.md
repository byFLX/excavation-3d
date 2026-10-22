# Shape Excavator

Standalone Minecraft 1.21.1 / NeoForge 21.1.x mod: normal mining tools (pickaxes, shovels, axes, also from other mods) get an excavation shape that is configured in game, with numeric fields and a rotatable 3D block preview. There is no own tool item. It does not depend on Create.

- Plan and acceptance criteria: [docs/SHAPE_EXCAVATOR_PLAN.md](docs/SHAPE_EXCAVATOR_PLAN.md)
- Acceptance matrix (automated evidence and open in-game checks): [docs/ACCEPTANCE_MATRIX.md](docs/ACCEPTANCE_MATRIX.md)
- Gradle project: `neoforge-1.21.1/` (`./gradlew test build`; world-level Game Tests: `./gradlew runGameTestServer`)
- Status: stages 1–5 of the plan are implemented (geometric core, editor with 3D preview, custom layer editor and presets, tool data component with server-confirmed apply and world preview, server-side excavation), with the correction of plan §2a (shapes on normal tools, no own item). The in-game and multiplayer checks of stage 6 are listed as open in the acceptance matrix; they have not been run.

## Using the tools

- Eligible tools: every item in the item tag `shapeexcavator:shape_tools`, which contains `#minecraft:pickaxes`, `#minecraft:shovels` and `#minecraft:axes` (`data/shapeexcavator/tags/item/shape_tools.json`). Tools of other mods in these tags are included; datapacks can add or remove tools.
- Hold an eligible tool in the main hand and press the editor key (default `V`) to open the editor. Right-clicks are never intercepted, so shovels, axes and other mods' tools keep their own right-click actions. Apply sends the shape to the server; only its confirmation stores the shape on that tool.
- A tool without a shape behaves exactly like vanilla: it mines single blocks and shows no hint. Opening and cancelling the editor does not change it. "Remove shape" in the editor's Presets menu deletes the shape, so the tool mines normally again.
- Mining the targeted block with a tool that carries a shape excavates the shape in survival mode, once the tool has really mined and harvested that start block. Suitability, tier, enchantments and durability come from the tool itself; extra blocks the tool is not correct for (e.g. stone with a shovel) are skipped and reported. In creative mode it mines single blocks. Hold the single-block key (default Shift) to mine one block only.
- The shape stays on the tool through anvil repair and renaming, enchanting and the netherite upgrade, as far as vanilla keeps the item's components.
- Server limits and work budgets: `serverconfig/shapeexcavator-server.toml` in the world folder. Personal presets: `config/shapeexcavator-presets.json`.

## Editor and preview controls

- **Shape type:** one row of icons – cuboid, plus, stairs, stair corridor, custom. The hovered icon names its type; the selected one has a gold frame. Surfaces and lines are cuboids with depth 1 or height and depth 1. The custom icon turns a valid template into a custom shape with the same selection; Undo brings back the previous custom shape. If the template is invalid, the icon shows the existing custom shape and "Fix the shape first".
- **Sizes:** Width, Height, Depth (and the type's own fields such as arm thickness or steps) are typed with the keyboard or changed with `-`/`+`. Errors show in red at the field, in its tooltip and in the status line; Apply stays disabled while anything is invalid. Stairs and corridors have one Direction toggle.
- **Custom shapes:** layer `<`/field/`>`, "Layer ▾" (copy, paste, fill, clear), "Set start", Undo and Redo. In the grid, left-click selects and right-click removes. Shrinking that would delete selected blocks asks first ("Remove N blocks" / "Keep size").
- **3D preview:** drag to rotate, mouse wheel to zoom, double-click to reset the view. The block scale stays fixed while you change sizes, so a wider shape looks wider against the 1-block floor grid; it only zooms out when the shape no longer fits. After a size change, added blocks flash green and removed blocks red. The icons in the preview's top right corner rotate (↻) and mirror (⇆) the shape; stairs and corridors also have a schematic result view (▤).
- **Presets ▾** (top right): type a name and press Save or Enter; click a preset to load it as a draft; × deletes it. "Remove shape" at the end of the menu removes the shape from the tool. Escape or a click outside closes the menu.
- **Bottom:** the "World preview" checkbox, Cancel and Apply. Apply sends the draft to the server; the editor stays open and shows the server's answer. Cancel and Escape never change the tool and ask before discarding unsaved changes.
- **World preview in the game:** with the checkbox on, looking at a block shows the outer outline of the selection, a light fill on the blocks that will be mined and the start block in yellow. Air, excluded and unloaded positions are not drawn. The hint above the hotbar shows "N blocks"; hold Shift for the details (present, air, excluded, not loaded; protection is decided by the server). Holding the single-block key hides the drawing.

## Code layout (`com.byflx.shapeexcavator`)

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
