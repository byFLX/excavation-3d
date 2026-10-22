# Shape Excavator

Standalone Minecraft 1.21.1 / NeoForge 21.1.x mod: a hand tool whose excavation shape is configured in game, with numeric fields and a rotatable 3D block preview. It does not depend on Create.

- Plan and acceptance criteria: [docs/SHAPE_EXCAVATOR_PLAN.md](docs/SHAPE_EXCAVATOR_PLAN.md)
- Gradle project: `neoforge-1.21.1/` (`./gradlew test build`)
- Status: Stage 1 (geometric core: configuration model, validation, all generators, anchors and transforms) is implemented in `com.byflx.shapeexcavator.shape` with unit tests. Stages 2–6 are not implemented yet.

The scaffold uses the same ModDevGradle, NeoForge and JUnit versions as the byFLX Create Stock Instant Craft mod, so the Agent-Team's offline Gradle sandbox can build it from its existing dependency seed.
