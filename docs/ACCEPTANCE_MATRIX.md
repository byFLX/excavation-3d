# Acceptance matrix: Shape Excavator

Maps every acceptance test of [plan §12](SHAPE_EXCAVATOR_PLAN.md#12-verbindliche-abnahmetests) to its evidence.

- **Automated** – a JUnit test in `neoforge-1.21.1/src/test/java` (run by `./gradlew test build`).
- **Game Test – not run yet** – a Game Test in `neoforge-1.21.1/src/main/java/com/byflx/shapeexcavator/gametest/ExcavationGameTests.java`, run by `./gradlew runGameTestServer`. The build gate (`test build`) only compiles these tests; it does not run them.
- **In game – open** – needs a client, a dedicated server or two players. Not run yet. A passing build does not replace these checks (plan §12, last paragraph).

Test paths are relative to `neoforge-1.21.1/src/test/java/com/byflx/shapeexcavator/`. Game Test names are methods of `gametest/ExcavationGameTests`.

## Eligible tools (plan §2a, correction 2026-10-22)

| Plan §2a requirement | Evidence | Status |
|---|---|---|
| Item tag `shapeexcavator:shape_tools` with `#minecraft:pickaxes`, `#minecraft:shovels`, `#minecraft:axes`; every item in it can carry the shape | `data/shapeexcavator/tags/item/shape_tools.json`, `registry/ModTags.SHAPE_TOOLS`; checked by `EditSessions`, `ExcavationService.onBreak` and `validate`, `ClientAccess.openEditor`. Every mining Game Test checks that its iron pickaxe, shovel or axe is in the tag (`shapedTool`). Game Tests per tool type: `pickaxeShapeMinesStone`, `shovelShapeSkipsStoneAndReportsIt`, `axeShapeMinesPlanks` | Game Tests not run yet |
| The editor opens only with the editor key while holding an eligible tool; right-clicks are not intercepted | `ShapeExcavatorClient.onClientTick` → `ClientAccess.openEditor` (tag check). No `Item.use` override or right-click listener exists any more | Code review; key press in game – open |
| A tool without a shape behaves like vanilla; "Remove shape" | Opening the editor writes nothing (`EditSessions.begin`); the tool id is created by the first accepted Apply. `ExcavationService.onBreak` returns before any message when there is no component. `RemoveShapePayload` → `EditSessions.remove` deletes the whole component. Game Test `unconfiguredToolMinesOneBlock` (iron pickaxe: one block, damage 1, no component, no message) | Game Test not run yet; Remove button in game – open |
| Suitability, tier, enchantments and durability come from the tool; extra blocks it is not correct for are skipped and reported | `ExcavationService.check` uses the held stack (`requiresCorrectToolForDrops` and `isCorrectToolForDrops`, destroy progress); loot, enchantments and wear come from `destroyBlock`. Game Tests `shovelShapeSkipsStoneAndReportsIt` (2 stone skipped as "wrong tool or tier", 7 dirt, 7 wear), `machinesAndWrongTierAreSkipped` (obsidian with an iron pickaxe) | Game Tests not run yet |
| The shape survives anvil repair and rename, enchanting and the netherite upgrade | Game Tests `shapeSurvivesAnvilRepairAndRename`, `shapeSurvivesEnchanting`, `shapeSurvivesNetheriteUpgrade` (result component equals the input component) | Game Tests not run yet. Mods that replace these results (anvil events, own `applyEnchantments`) are not covered |
| The tooltip hint only on tools that carry a shape | `ShapeExcavatorClient.onTooltip`, `ToolHud`, `WorldPreview` all use `ModTags.shapeOf` (in the tag and a shape or a damaged one) | Code review; in game – open |
| Own item, recipe, creative-tab entry, enchantable tag entries and lang keys removed | No `ModItems`, `ShapeExcavatorItem`, item registration or creative-tab listener; lang key `item.shapeexcavator.shape_excavator` removed | Code review |
| The session binds player, slot and stack, and checks that the item is still in the tag | `net/SessionBinding` decides; `EditSessions.boundTool` gathers slot, stack instance, tag, tool id and revision. `net/SessionBindingTest` | Automated |
| "Remove shape" does not overlap other widgets at GUI scale 4 | `ShapeEditorScreen.initActionRow`: five equal buttons in one row | In game – open |

Mod tools with their own area mining (e.g. hammers in `#minecraft:pickaxes`) may react to the extra breaks; this interplay is not tested.

## Inputs and geometry

| Plan §12 check | Evidence | Status |
|---|---|---|
| 7 × 5 × 2 entered as text: preview and generator give 70 | `editor/EditorDraftTest.typedFieldsDriveTheSameGeneratorAsTheServer`, `shape/ShapeGeneratorTest.cuboid7x5x2Selects70` | Automated. Typing with the keyboard in the real `EditBox`: in game – open |
| 3 × 3 × 1 gives 9; plus 3 × 3 × 1 arm 1 gives exactly 5 | `shape/ShapeGeneratorTest.cuboid3x3x1Selects9`, `plus3x3x1Arm1IsExactlyFiveCells` | Automated |
| Plus 5 × 5 × 2 gives 18; stair and corridor have separate tested masks | `shape/ShapeGeneratorTest.plus5x5Counts`, `stairColumnHeights`, `corridorAndStairHaveDifferentMasks` | Automated |
| Corridor 3 × 3 with six 1-block steps: 3 × 8 × 6 and 54 | `shape/ShapeGeneratorTest.corridorExampleFromPlan` | Automated |
| Even sizes, all six hit faces, four quarter turns, mirroring twice | `shape/AnchorTest`, `shape/OrientationTest`, `shape/ShapeTransformTest`, `editor/EditorDraftTest.transformButtonsChangeMaskAndAnchorTogether` | Automated |
| Empty field, 0, negative, decimal, over-long, derived oversize: no crash, no silent save | `shape/NumericInputTest`, `shape/ShapeGeneratorTest.extremeValuesProduceIssuesNotExceptions`, `editor/EditorDraftTest.fieldsMayBeEmptyWhileTypingAndAreNeverCorrected`, `derivedOversizeIsAnIssueNotAFieldError` | Automated (Apply is disabled whenever `Evaluation.isValid()` is false). Visual state in game – open |

## Editor and storage

| Plan §12 check | Evidence | Status |
|---|---|---|
| Camera rotation changes no shape data; shape rotation changes mask and anchor consistently | `PreviewCamera` holds only view state and is never passed to the generator; `editor/EditorDraftTest.transformButtonsChangeMaskAndAnchorTogether` | Automated for the data; dragging the 3D view in game – open |
| Deselected custom cells appear neither in the preview nor in the server selection | `shape/CustomMaskTest.deselectedCellsAreNeverSelected`, `editor/CustomMaskDraftTest.deselectingRemovesCellsFromTheMask` | Automated (preview, world preview and server all use `ShapeGenerator`) |
| Shrinking with data loss asks for confirmation; undo restores | `editor/CustomMaskDraftTest.shrinkingReportsLossAndUndoRestores`, `editor/EditorDraftTest.customSizeFieldsRequestResizeInsteadOfApplyingIt` | Automated for the model; confirmation button in game – open |
| GUI scale and window resize keep the draft; Cancel keeps the active configuration | Widgets are rebuilt from `EditorDraft` in `ShapeEditorScreen.init()`; Cancel never sends a payload. Edits made while an Apply is waiting for the server stay unsaved (discard confirmation, no auto-close): `editor/DraftSaveStateTest.editsMadeWhileAwaitingTheReplyStayUnsaved` | Save state automated; in game – open |
| Restart, drop/pick up and hand-over keep the shape; two tools stay independent | `data/ShapeCodecsTest.componentSurvivesPersistence`, `componentSurvivesNetworkSync`, `twoToolsStayIndependent`, `brokenStoredShapeIsDroppedNotTheItem` | Automated for the codecs; in game – open |
| Invalid revision, tool change and oversized network data are rejected | `net/EditSessions.apply` and `remove` (session, rate, then `net/SessionBinding`: slot, stack instance, `shape_tools` tag, tool id, revision; `net/SessionBindingTest`; Apply also regenerates the shape fully); `net/EditSessions.begin` drops begin requests above 4 per 40 ticks per player before creating a session or replying (`net/RequestThrottleTest`). The editor resends an unanswered begin request after 50 ticks, at most 4 requests in total, then shows "no answer" (`editor/SessionHandshakeTest.droppedRequestIsSentAgainAfterTheWait`, `retriesAreBoundedAndEndInAVisibleState`, `net/BeginRetryTest.fifthQuickOpenIsDroppedAndItsRetryGetsASession`). Each begin request carries a request id that the reply echoes; only the reply to the editor's latest request is accepted (`editor/SessionHandshakeTest.lateReplyForAClosedEditorNeverReachesTheNextOne`, `replyToAnEarlierAttemptIsIgnored`); `data/ShapeCodecsTest.oversizedNetworkMaskIsRejectedBeforeAllocation`, `malformedNetworkShapesAreRejected` | Decoding, rate limit and session handshake automated; session checks with real latency need a server – open |

## Mining and multiplayer

| Plan §12 check | Evidence | Status |
|---|---|---|
| A refused or failed start block triggers no extra mining | `ExcavationService.onBreak` records an attempt. Only `ExcavationService.onBlockDrops` confirms it: the player's own survival `destroyBlock` handles drops only after the block was removed, and the drops name that player as breaker. `StartGate.drain` starts confirmed attempts whose block is still gone (`ExcavationService.startBlockRemoved`). Creative breaks have no post-removal signal and mine a single block. `excavation/StartGateTest.refusedRemovalFollowedByAnotherPlayersRemovalStartsNothing`, `anotherPlayersSuccessfulBreakConfirmsOnlyThatPlayer`, `unconfirmedAttemptNeverStartsEvenIfTheBlockIsGone`. Game Tests: `successfulStartMinesTheShapeExactlyOnce`, `protectedStartStartsNothing`, `machineAsStartBlockStartsNothing`, `unconfiguredToolMinesOneBlock` | Start decision automated; Game Tests written, not run yet. A block that refuses its removal is covered only by `StartGateTest` |
| The single-block key keeps a break a single block | Client: `ShapeExcavatorClient.onInteractionKey` sends the key state before the attack's mining action. Server: `StartGate.setBypass` blocks attempts and drops a pending one. `excavation/StartGateTest.bypassArrivingAfterTheBreakInTheSameTickStartsNothing`, `heldBypassBlocksAttemptsUntilReleased`. Game Test: `singleBlockKeyMinesOnlyTheStart` | Server rule automated; Game Test not run yet; key press plus instant break in game – open |
| The hit face is checked on the server (plan §7) | `HitFaces.choose`: the server's ray first; the client's face only if the ray missed and the eye is in front of that face. `excavation/HitFacesTest` | Automated; in game – open |
| Protected positions, machines, wrong tool or tier and unloaded chunks stay untouched | `ExcavationService.check` (+ the real `destroyBlock` path, which fires the break event for protection mods). Game Tests: `protectedExtraBlockIsSkipped` (a test listener cancels the break event like a claim mod), `machinesAndWrongTierAreSkipped` (furnace, obsidian with an iron pickaxe), `shovelShapeSkipsStoneAndReportsIt` (stone with an iron shovel) | Game Tests not run yet. Unloaded chunks: no Game Test, because a test cannot unload a chunk inside its own loaded area – in game – open |
| Start and extra blocks give loot, XP and wear at most once | Extra blocks use `ServerPlayerGameMode.destroyBlock` once each; the start block is excluded from the targets. Game Test `successfulStartMinesTheShapeExactlyOnce`: drops handled exactly once per mined position (experience is dropped in the same `destroyBlock` call), 9 dirt items and 9 points of wear for a 3 × 3 × 1 shape | Game Test not run yet. XP amounts are not counted separately |
| Extra mining starts no recursive cascade | `BREAKING_EXTRA` guard in `ExcavationService`. Game Test `successfulStartMinesTheShapeExactlyOnce`: the rest of a 5 × 5 × 2 wall stays | Game Test not run yet |
| Tool breaking, player or dimension change end running operations | `ExcavationService.validate`, `onDeath`, `onChangedDimension`, `onLogout` | In game – open |
| Two players and parallel operations respect the global tick budget | `excavation/BudgetSchedulerTest` | Scheduler automated; two-player run – open |
| Editor, world preview and server compute the same selection; deviations are reported with reasons | One `ShapeGenerator` + `Orientation` + `GeneratedShape.worldOffsets` for all three; skip reasons in the result message | Shared code; visual comparison in game – open |

## Stage 6 checks still open

None of these have been run:

1. Dedicated server start (`./gradlew runServer`) with the mod, joining with a client: no client class loading on the server.
2. Two clients on one server: independent tools, parallel excavation, budget.
3. A claim/protection mod: protected blocks are skipped and reported.
4. GUI scale 1–4 and small windows: no overlap, scrolling parameter area, draft kept.
5. A shader pack: editor preview and world preview outlines still visible.
6. Game Tests (plan §12, last paragraph): run `./gradlew runGameTestServer` in `neoforge-1.21.1/`. It should finish with 13 passed tests in the `shapeexcavator` namespace. The tests are in `gametest/ExcavationGameTests`. They use an in-memory empty template, a survival test player from `gametest/GameTestSupport` and vanilla iron tools (a diamond pickaxe for the netherite upgrade). They cover successful and refused starts, protection, machines, wrong tool or tier, recursion, loot and wear, the result count, one shape test per tool type (pickaxe, shovel, axe), a tool without a shape, and the shape surviving anvil repair and rename, enchanting and the netherite upgrade. Unloaded chunks and a block that refuses its removal are not covered by a Game Test. The build gate only compiles the Game Tests; it has not run them.
7. Plan §2a in a client (survival): an iron pickaxe, a shovel and an axe each get a shape via `V` and mine it once; a tool without a shape mines a single block and shows no tooltip hint or HUD line; "Remove shape" makes a tool mine single blocks again.
