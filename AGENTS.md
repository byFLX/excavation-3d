# Shape Excavator

Diese Mod folgt dem byFLX Mod-Grunddesign in `docs/MOD_STANDARDS.md`. Lies es vor jeder Änderung. Hier stehen
nur repo-spezifische Angaben und vom Betreiber genehmigte Abweichungen.

## Projekt

- Arbeitsverzeichnis: `D:\OpenCode-Projekt\minecraft_shape-excavator`
- Status: in Entwicklung. Etappen 1–5 von 6 sind umgesetzt (siehe `docs/SHAPE_EXCAVATOR_PLAN.md`). Die Form hängt
  an normalen Werkzeugen, es gibt kein eigenes Item. Die Abnahme steht in `docs/ACCEPTANCE_MATRIX.md`. Pro Auftrag
  genau eine Etappe.
- Projektordner: `neoforge-1.21.1/` · Build: `./gradlew build` dort · Tests: JUnit 5 (`./gradlew test`) und GameTests
  (`./gradlew runGameTestServer`; `build` kompiliert sie nur, ausgeführt wurden sie noch nicht) · Datagen: keine
- Minecraft 1.21.1 (Laufzeitbereich `[1.21.1,1.22)`) · Loader NeoForge · Java 21
- Werkzeuge: Gradle 9.2.1 · ModDevGradle 2.0.141 · Mappings Mojang
- mod_id `shapeexcavator` · Gruppe/Paket `com.byflx.shapeexcavator` · Version 0.1.0
- Eigenständige Mod, kein `Create:`-Präfix.

## Abhängigkeiten

| Mod | Art | Build-Version | Laufzeitbereich (Ist) |
|---|---|---|---|
| NeoForge | required | 21.1.219 | `[21.1.219,)` (unter 21.1.229) |

Eingriffe in fremden Code: keine.

## Artefakt und Test

- Installierbare Jar: `neoforge-1.21.1/build/libs/shapeexcavator-neoforge-<version>.jar` (Name noch ohne MC)
- Testinstanz: `C:\Users\MLo\curseforge\minecraft\Instances\test\mods\`
- Testnachweise: `docs/TESTING.md` (noch anzulegen)

## Veröffentlichung

- Noch nicht veröffentlicht, kein Git-Remote.

## Abweichungen vom Grunddesign (vom Betreiber genehmigt)

- keine
