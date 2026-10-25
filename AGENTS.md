# Excavation 3D

Diese Mod folgt dem byFLX Mod-Grunddesign in `docs/MOD_STANDARDS.md`. Lies es vor jeder Änderung. Hier stehen
nur repo-spezifische Angaben und vom Betreiber genehmigte Abweichungen.

## Projekt

- Arbeitsverzeichnis: `D:\OpenCode-Projekt\minecraft_shape-excavator` (Repo-Ordner trägt noch den alten Namen)
- Status: 1.0.0 zur Veröffentlichung vorbereitet. Die Form hängt an normalen Werkzeugen, es gibt kein eigenes Item.
  Plan: `docs/EXCAVATION_3D_PLAN.md`, Abnahme: `docs/ACCEPTANCE_MATRIX.md`, Bedienung im Detail:
  `docs/BEHAVIOUR_REFERENCE.md`.
- Projektordner: `neoforge-1.21.1/` · Build: `./gradlew build` dort · Tests: JUnit 5 (`./gradlew test`) und
  GameTests (`./gradlew runGameTestServer`, Namespace `excavation3d`) · Datagen: keine, alle Ressourcen von Hand
- Minecraft 1.21.1 (Laufzeitbereich `[1.21.1]`) · Loader NeoForge · Java 21
- Werkzeuge: Gradle 9.2.1 · ModDevGradle 2.0.141 · Mappings Mojang
- mod_id `excavation3d` · Gruppe `com.byflx` · Paket `com.byflx.excavation3d` · Version 1.0.0
- Eigenständige Mod, kein `Create:`-Präfix.
- Icon: `neoforge-1.21.1/src/main/resources/logo.png` (256×256). Quelle `docs/logo/make-logo.py` (nur
  Python-Standardbibliothek, schreibt `logo-256.png` und `logo-512.png` neben sich; die 256er wird nach
  `src/main/resources/logo.png` kopiert, die 512er ist das CurseForge-Projektbild).

## Abhängigkeiten

| Mod | Art | Build-Version | Laufzeitbereich |
|---|---|---|---|
| NeoForge | required | 21.1.265 | `[21.1.229,)` |

Eingriffe in fremden Code: ein Access Transformer (`META-INF/accesstransformer.cfg`) macht
`net.minecraft.client.gui.Gui.toolHighlightTimer` öffentlich, damit der Werkzeughinweis wartet, bis der Vanilla-
Itemname ausgeblendet ist. Keine Mixins.

## Artefakt und Test

- Installierbare Jar: `neoforge-1.21.1/build/libs/excavation3d-neoforge-1.21.1-<version>.jar`
- Testinstanz: `C:\Users\MLo\curseforge\minecraft\Instances\test\mods\`
- Testnachweise: `docs/TESTING.md`
- Netzwerkprotokoll: Version 5 (`net/ModNetwork.java`). Gespeichertes Formformat: Version 1.
- Die frühen Entwicklungs-IDs `shapeexcavator` werden bewusst nicht migriert (nie veröffentlicht).

## Veröffentlichung

- GitHub: <https://github.com/byFLX/excavation-3d> (öffentlich) · Issues: <https://github.com/byFLX/excavation-3d/issues>
- CurseForge: Upload-Texte in `docs/CURSEFORGE.md`. Den Upload macht der Betreiber.

## Abweichungen vom Grunddesign (vom Betreiber genehmigt)

- keine
