# Shape Excavator

Standalone Minecraft 1.21.1 / NeoForge 21.1.x mod: a hand tool whose excavation shape is configured in game, with numeric fields and a rotatable 3D block preview. It does not depend on Create.

- Plan and acceptance criteria: [docs/SHAPE_EXCAVATOR_PLAN.md](docs/SHAPE_EXCAVATOR_PLAN.md)
- Acceptance matrix (automated evidence and open in-game checks): [docs/ACCEPTANCE_MATRIX.md](docs/ACCEPTANCE_MATRIX.md)
- Gradle project: `neoforge-1.21.1/` (`./gradlew test build`)
- Status: stages 1–5 of the plan are implemented (geometric core, editor with 3D preview, custom layer editor and presets, tool data component with server-confirmed apply and world preview, server-side excavation). The in-game and multiplayer checks of stage 6 are listed as open in the acceptance matrix; they have not been run.

## Using the tool

- Craft: iron ingot, redstone, iron ingot over two sticks (`data/shapeexcavator/recipe/shape_excavator.json`). Also in the Tools creative tab.
- Right-click into the air, or press the editor key (default `V`), to open the editor. Apply sends the shape to the server; only its confirmation changes the tool.
- Mining the targeted block with the tool excavates the shape in survival mode, once the tool has really mined and harvested that start block. In creative mode the tool mines single blocks. Hold the single-block key (default Shift) to mine one block only.
- Server limits and work budgets: `serverconfig/shapeexcavator-server.toml` in the world folder. Personal presets: `config/shapeexcavator-presets.json`.

## Code layout (`com.byflx.shapeexcavator`)

| Package | Content | Side |
|---|---|---|
| `shape` | Pure geometry: config, generator, validator, transforms, orientation, encoding | both |
| `editor` | Editor draft and custom layer draft (undo/redo, resize) | both, pure |
| `data`, `registry`, `item`, `config` | Data component and codecs, registration, the tool, server config | both |
| `net` | Payloads and server-side editor sessions | both (handlers per side) |
| `excavation` | Server-side excavation and tick budgets | server logic |
| `client` | Editor screen, 3D preview, world preview, hint, keys, presets | client only |

The scaffold uses the same ModDevGradle, NeoForge and JUnit versions as the byFLX Create Stock Instant Craft mod, so the Agent-Team's offline Gradle sandbox can build it from its existing dependency seed.
