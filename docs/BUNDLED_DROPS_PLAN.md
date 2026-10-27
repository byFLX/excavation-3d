# Bundled drops: 1.1.0

Implementation of the operator's bundled-drops brief. No branch switches, commits, deletions or Gradle runs.

1. Capture the confirmed start and each exact synchronous extra target through BlockDropsEvent. Retain the
   existing destroyBlock path, protection, loot modifiers, enchantments, durability and spawnAfterBreak effects.
2. Snapshot the effective per-player delivery mode at start. Pack equal item/component stacks within item
   limits. Deliver at completion/interruption; flush items early before exceeding 256 retained stacks. Keep XP
   as a single pickup orb for normal operations so vanilla pickup and Mending still run. A 32,767 XP
   threshold flushes/splits exceptionally large modded rewards to avoid overflowing vanilla Mending math.
3. Keep captured starts independently until the existing end-of-tick start gate resolves. Flush even if a late
   bypass or restored block prevents expansion. Flush lifecycle interruptions in the original dimension before
   server saves, falling back to the last player position and loading that one chunk if necessary.
4. Add client dropMode and server drops.forceMode, validated one-byte preference/policy payloads, protocol 6,
   an independent editor footer control and an optional unbound shortcut. Sync joins, changes and server reloads.
5. Add pure packing/overflow tests and world GameTests, update player documentation and version metadata.

Validation performed: direct Java 21 compilation against cached NeoForge 21.1.265; 9/9 pure JUnit tests passed.
Gradle, the GameTest server and manual in-game checks remain unrun. See [TESTING.md](TESTING.md).

## Changed files

- [CHANGELOG.md](../CHANGELOG.md)
- [README.md](../README.md)
- [docs/BEHAVIOUR_REFERENCE.md](../docs/BEHAVIOUR_REFERENCE.md)
- [docs/BUNDLED_DROPS_PLAN.md](../docs/BUNDLED_DROPS_PLAN.md)
- [docs/TESTING.md](../docs/TESTING.md)
- [neoforge-1.21.1/gradle.properties](../neoforge-1.21.1/gradle.properties)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/Excavation3D.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/Excavation3D.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/ClientPayloadHandler.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/ClientPayloadHandler.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/ClientState.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/ClientState.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/DropModeChoice.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/DropModeChoice.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/Excavation3DClient.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/Excavation3DClient.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/screen/ShapeEditorScreen.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/client/screen/ShapeEditorScreen.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/config/ClientConfig.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/config/ClientConfig.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/config/ServerConfig.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/config/ServerConfig.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropBuffer.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropBuffer.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropDelivery.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropDelivery.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropMode.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropMode.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropPolicy.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropPolicy.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropPreferences.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/DropPreferences.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/ExcavationService.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/ExcavationService.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/StartGate.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/excavation/StartGate.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/gametest/ExcavationGameTests.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/gametest/ExcavationGameTests.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/gametest/GameTestSupport.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/gametest/GameTestSupport.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/net/DropModePayload.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/net/DropModePayload.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/net/DropPolicyPayload.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/net/DropPolicyPayload.java)
- [neoforge-1.21.1/src/main/java/com/byflx/excavation3d/net/ModNetwork.java](../neoforge-1.21.1/src/main/java/com/byflx/excavation3d/net/ModNetwork.java)
- [neoforge-1.21.1/src/main/resources/assets/excavation3d/lang/en_us.json](../neoforge-1.21.1/src/main/resources/assets/excavation3d/lang/en_us.json)
- [neoforge-1.21.1/src/test/java/com/byflx/excavation3d/excavation/DropBufferTest.java](../neoforge-1.21.1/src/test/java/com/byflx/excavation3d/excavation/DropBufferTest.java)

Files to delete: **none**. Generated direct-compiler/JUnit check files remain under the ignored
`neoforge-1.21.1/build/bundled-drops-check/` directory.
