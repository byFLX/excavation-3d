# Test evidence

One line per change that reaches players (see `docs/MOD_STANDARDS.md` §9). Minecraft 1.21.1, Java 21 (Temurin 21)
unless stated. Excavation 3D has no other mod dependencies and no optional integrations.

| Date | Version / commit | Loader | Test | Result | Not tested |
|---|---|---|---|---|---|
| 2026-10-27 | 1.1.0 working tree, bundled drops | NeoForge 21.1.265 cached compile dependencies; Minecraft 1.21.1; Temurin 21.0.11; no optional integrations | Direct `javac --release 21` of all main sources, including GameTests; direct JUnit 5.11.4 run of 9 pure DropBuffer tests; JSON and diff checks | Compilation and 9/9 pure tests passed; Gradle deliberately not run | Packaged build, bootstrapped full test suite, added/existing GameTests, 21.1.229 compatibility, client/server startup, UI, payload round trips, manual mining and lifecycle/save checks |
| 2026-10-25 | 1.0.0, release branch (final jar sha256 `c019390e…d246`, with the final icon) | NeoForge 21.1.229 | Build + 309 unit tests + 17 GameTests against 21.1.229; dev client to title screen and clean close; **final jar** on a real dedicated server (official NeoForge installer) to `Done`, clean `stop` | Passed | Client joining that server; manual mining in the world |
| 2026-10-25 | 1.0.0, release branch (same jar) | NeoForge 21.1.265 (build version) | Build + 309 unit tests + 17 GameTests; dev client to title screen and clean close; **final jar** on a real dedicated server (official installer) to `Done`, clean `stop` | Passed | as above |
| 2026-10-25 | main `26f745d` (0.1.0) | NeoForge 21.1.219 and 21.1.265 | Modloader version test: build, unit tests, GameTests, dev dedicated server to `Done` + clean stop, dev client to title screen | Passed | manual gameplay |
| 2026-10-25 | 0.1.0 dev builds (editor v2, rounds 7-11) | NeoForge 21.1.219 | Operator: CurseForge instance "test" (single player) and the operator's own multiplayer server - editor, wheel, favorites, world preview, tool suitability | Passed (operator report: "geht soweit bei uns aufn server") | Not re-run with the exact 1.0.0 jar yet |

Open before or right after upload: the exact 1.0.0 jar in the CurseForge instance "test" and on a server with a
client connected (core function, menus/payloads, reload the world) - the operator does this check.

For **1.1.0**, run the full build, unit tests and GameTests when authorized. Then check Inventory, Pile at start
and Vanilla with Fortune/Silk Touch and a Mending tool; partial/full inventories; two players mining together;
config reload and reconnect; the disabled forced-mode tooltip; Escape after a drop-mode change; the unbound
shortcut and tool hint; small GUI sizes; multi-tick interruption by logout/death/dimension change/tool break;
server stop and world reload; and fallback delivery when the start chunk is unloaded. Check the exact packaged
jar on a dedicated server with a connected client. The newly added GameTests are compiled, not executed.
