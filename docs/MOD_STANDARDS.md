# byFLX Mod-Grunddesign

Stand: 1.0 · 2026-10-23 · Änderungen: `CHANGELOG.md` in `D:\OpenCode-Projekt\_mod-standards\`

Verbindliche Vorgaben für alle Minecraft-Mods von byFLX, egal ob die Arbeit im Chat, in T3 Code, über Codex oder
über die AgentAI-Pipeline läuft.

**Rangfolge:**
1. Ausdrückliche Anweisung des Betreibers (byFLX).
2. Die `AGENTS.md` des Repos: Ergänzungen und vom Betreiber genehmigte, begründete Abweichungen.
3. Dieses Dokument.

Agenten heben keine Regel eigenmächtig auf. Wer eine Regel für falsch hält, meldet das dem Betreiber.

**Verteilung:**
- Die Quelle liegt in `D:\OpenCode-Projekt\_mod-standards\`. Jedes Mod-Repo enthält eine Kopie unter
  `docs/MOD_STANDARDS.md`, weil Sandbox-Agenten nur ihr Repo sehen.
- Geändert wird nur die Quelle: Version anheben, Eintrag im CHANGELOG, dann alle Kopien mit
  `sync-standards.ps1` abgleichen (wird beim Ausrollen geschrieben). Das Skript prüft zuerst und überschreibt nur
  `docs/MOD_STANDARDS.md`, nie die `AGENTS.md`.
- Ein Agent, der die Quelle nicht erreicht, schlägt eine Änderung in seiner Antwort vor und ändert die Kopie
  nicht.

## 1. Identität

- **Gradle-Gruppe:** neue Mods `com.byflx`. **Java-Basispaket:** `com.byflx.<modid>`.
- **Bestehende Werte bleiben,** umbenannt wird nur auf Wunsch (siehe Abschnitt 14).
- **`mod_id`:** nur Kleinbuchstaben, Ziffern und Unterstriche. Nach der ersten Veröffentlichung nie mehr ändern.
- **Registry-IDs** (Blöcke, Items, Data Components, Menüs, Entities, Rezepttypen, Payloads) sind ab der ersten
  Veröffentlichung ebenso stabil. Umbenennen geht nur mit Migration (Abschnitt 8).
- **Anzeigename:**
  - Eine Mod mit Create als harter Abhängigkeit heißt `Create: <Name>`. Ihre neue `mod_id` beginnt mit
    `create_`.
  - Eine eigenständige Mod trägt nur ihren Namen ohne Präfix (`Shape Excavator`), auch wenn sie optional mit Create
    zusammenspielt.
- **Autor:** `byFLX` in den Metadaten, im README und als einziger Git-Autor.
- **Lizenz:** neue Mods **MIT**, mit `Copyright (c) <Jahr> byFLX`. `LICENSE`-Datei, Lizenzfeld in den Metadaten und README
  stimmen immer überein. Lizenzen übernommener oder gebündelter Fremdquellen stehen getrennt in
  `THIRD_PARTY_NOTICES.md`. Diese Datei wird nie entfernt.

## 2. Repo-Struktur

```
<repo>/
  README.md              Spielerseite (Abschnitt 11)
  CHANGELOG.md
  LICENSE
  THIRD_PARTY_NOTICES.md   nur wenn Fremdcode oder Fremdassets enthalten sind
  AGENTS.md              Repo-Vorgaben (Vorlage: templates/AGENTS.md)
  CLAUDE.md              enthält nur "@AGENTS.md"
  docs/MOD_STANDARDS.md  Kopie dieses Dokuments
  docs/                  Pläne, Specs, Designnotizen, Testnachweise
  <loader>-<mc>/         ein Gradle-Projekt pro Loader und Minecraft-Version, z. B. neoforge-1.21.1/
```

- Ein Port bekommt einen eigenen Unterordner neben den vorhandenen. Bestehende Projekte im Wurzelordner werden nur
  auf Wunsch umgezogen.
- Neue Pläne und Specs kommen nach `docs/`.
- Nicht ins Repo gehören erzeugte Mod-Jars, `build/`, `run/` und `.gradle/`. **Ausnahme:**
  `gradle/wrapper/gradle-wrapper.jar` wird versioniert.

## 3. Plattform und Build-Werkzeuge

- **Loader und Minecraft-Version wählt der Betreiber pro Mod,** es gibt keinen Standard. Beides steht in
  `gradle.properties`, in der `AGENTS.md` und im README unter „Requirements“.
- **Minecraft-Bereich:** Nur getestete Minecraft-Versionen freigeben. Die offene Obergrenze für Mod-Abhängigkeiten
  (Abschnitt 4) gilt **nicht** für Minecraft. Bestehende Projekte deklarieren `[1.21.1,1.22)`. Neue Projekte
  nennen genau die getesteten Versionen, z. B. `[1.21.1]`. Mehrere 1.21-Unterversionen sind nur nach Test
  erlaubt.
- **Java:** was die Minecraft-Version verlangt (1.21.x: Java 21). Gesetzt über die Gradle-Toolchain.
- **Reproduzierbar bauen:**
  - Gradle-Wrapper, Plugin-Version (NeoForge: ModDevGradle, Fabric: Loom), Mappings (Mojang mit oder ohne Parchment,
    Fabric: Yarn) und Build-Versionen der Abhängigkeiten sind **konkrete** Versionen. Sie stehen in
    `gradle.properties` und werden in der `AGENTS.md` genannt.
  - Snapshot-Werkzeuge nur als Ausnahme mit Grund in der `AGENTS.md`.
  - Neue NeoForge-Projekte nutzen ModDevGradle wie die bestehenden. Bestehende Builds wechseln das Werkzeug nicht
    ohne Grund.
- **NeoForge 1.21.1:**
  - *Bauen:* gegen den neuesten veröffentlichten stabilen 21.1-Build, Stand 2026-10-22 **21.1.264**
    (maven.neoforged.net). Vor jedem Release erneut prüfen.
  - *Untergrenze* (`neo_version_range`): pro Mod die niedrigste getestete Version, die die genutzten APIs und die
    Abhängigkeiten wirklich brauchen, **aber nicht unter `21.1.229`**. Dieser Build schließt eine
    Server-Absturzlücke über präparierte Pakete (NeoForge-Mitteilung vom 2026-05-11).
  - Darüber wird nur mit echtem Bedarf angehoben, mit Grund im CHANGELOG. Modpacks hängen oft hinterher, und eine
    unnötig hohe Untergrenze sperrt Spieler aus.
  - Wichtig: Der Build darf keine APIs nutzen, die es unterhalb der Untergrenze noch nicht gab. Bei Zweifel gegen
    die Untergrenze testen.
- **Fabric:** gleiche Logik mit der Syntax von `fabric.mod.json`: Untergrenze `">=0.16.9"`, keine Maven-Bereiche.
- **Neue Mods auf 1.21.1:** NeoForge entwickelt inzwischen die 26.x-Linie, 1.21.1 bekommt aber weiter
  Fehlerbehebungen. Wird eine neue Mod auf 1.21.1 gebaut, steht der Grund im README (z. B. weil dort die meisten
  Modpacks sind).

## 4. Abhängigkeiten

Das ist die wichtigste Regel. Exakte Pins sind der häufigste Grund für Meldungen wie „Can you update this for the
current version of …?“

- **Laufzeit-Metadaten** (`neoforge.mods.toml`, `fabric.mod.json`): Untergrenze statt Pin.
  - NeoForge: `versionRange = "[<getestet>,)"`.
  - Fabric: `">=<getestet>"`.
  - Exakte Pins (`[1.1.1]`, `[6.0.10]`) und vorsorgliche Obergrenzen (`[6.0.10,7.0)`) sind verboten.
  - Ausnahme: Eine bestimmte Version funktioniert nachweislich nicht. Dann wird genau sie ausgeschlossen, mit
    Kommentar und Eintrag im CHANGELOG.
  - Das Verbot gilt **nicht** für Build-Versionen (Abschnitt 3). Die bleiben konkret.
- **Warum auch optionale Abhängigkeiten:** Bei NeoForge verhindert eine *installierte* optionale Abhängigkeit
  außerhalb ihres Bereichs das Laden des Spiels. Fehlt sie ganz, ist das kein Problem. Bei Fabric haben
  `recommends` und `suggests` eine andere Wirkung, und in beiden Fällen wird Integrationscode nicht automatisch
  abgeschaltet.
- **Versionsstrings:** Manche Mods melden sich mit Zusatz, etwa `1.1.1-neoforge-1.21.1`, was die Sortierung
  verändert. Die Untergrenze muss die echte Form einschließen. Mit der echten Jar testen.
- **Create-Addons:** Create ist `required` mit Untergrenze, z. B. `[6.0.10,)`. Gebaut wird gegen die neueste
  *veröffentlichte stabile* Create-Version für die Ziel-MC, nicht gegen Maven-Entwicklungsbuilds. Stand
  2026-10-22: 6.0.10 für 1.21.1.
- **Optionale Integrationen** sicher bauen:
  - Code für eine fremde Mod liegt in `compat/<modid>/`. Er wird nur über eine Einstiegsklasse geladen, die man erst
    aufruft, nachdem die Anwesenheit geprüft ist: NeoForge `ModList.get().isLoaded(...)`, Fabric
    `FabricLoader.getInstance().isModLoaded(...)`. Keine fremden Klassen in statischen Feldern, Basisklassen oder
    automatisch registrierten Event-Handlern außerhalb von `compat/`.
  - Die Anwesenheit sagt nichts über die API-Version. Gibt es mehrere Versionen der fremden API, wird die Version
    geprüft oder abgesichert, z. B. durch Abfangen von `LinkageError` beim ersten Aufruf.
- **Mixins in fremde Mods:**
  - Eigene Mixin-Config pro Integration.
  - Ein Mixin-Plugin (`IMixinConfigPlugin.shouldApplyMixin`) prüft, ob die Ziel-Mod da ist. Dort ist `ModList`
    noch nicht sicher verfügbar, deshalb nimmt das Plugin den frühen Weg des Loaders (z. B. `LoadingModList`
    unter NeoForge).
  - `@Pseudo` erlaubt nur fehlende Zielklassen. Es prüft keine Kompatibilität. `"required": false` mildert nur
    manche Anwendungsfehler ab.
  - Pflicht sind deshalb: der kleinste nötige Eingriff, ein Kommentar mit Zielversion und Zweck, ein Test gegen die
    neueste Version der Ziel-Mod und eine Log-Warnung mit Abschaltung des Features, wo das technisch geht.
- **Öffentliche APIs und Events gehen vor Mixins,** Access Transformers oder Access Widener. Jeder Eingriff in
  fremden Code steht mit Grund in der `AGENTS.md`.
- **Vor jedem Release:** gegen die Untergrenze und gegen die neueste stabile Version jeder Abhängigkeit testen.
  Die getesteten Versionen stehen im README unter „Compatibility“.

## 5. Netzwerk

- Jede Payload hat eine feste ID unter dem eigenen Namespace. Das Protokoll trägt eine Versionsnummer.
- Bei unterschiedlichen Versionen zwischen Client und Server wird die Verbindung mit einer verständlichen Meldung
  abgelehnt.
- Für jede Payload ist festgelegt, welche Seite sie empfangen darf. Verarbeitet wird auf dem Haupt-Thread.
- Der **Server ist die Autorität:** Er prüft Berechtigung, Reichweite, Zugehörigkeit zu Menü und Block sowie
  Eingabegrößen. Welt- und Inventaränderungen passieren nur serverseitig.
- Listen und Maps aus Paketen haben eine Obergrenze. Pakete vom Client zum Server bleiben klein (Lehre aus der
  NeoForge-Lücke vom Mai 2026).

## 6. Texte

- **Nur Englisch:** `en_us.json`. Weitere Sprachen nur, wenn jemand eine Übersetzung beisteuert. Vorhandene
  `de_de.json` dürfen bleiben, werden aber nicht mehr gepflegt.
- Alle Spielertexte, auch Befehlsmeldungen, laufen über Lang-Keys (`Component.translatable`). Kein
  `Component.literal` für Text, den Spieler sehen. Keys haben die Form `<typ>.<modid>.<name>`.
- Dinge heißen so, wie sie im Spiel heißen, z. B. „Factory Gauge“. Der Mod-Name steht in README und Metadaten
  überall gleich. Kurzformen wie „Stock Crafter“ stehen im Mod-Verzeichnis (Abschnitt 13).
- Tooltips sind kurz, Details gibt es auf Shift. Create-Addons folgen dem Create-Stil.

## 7. Spielgefühl und Design

Grundsätze für alle Mods (vom Betreiber bestätigt 2026-10-23, Ergänzungen folgen bei Bedarf):

- **Funktioniert ohne Config:** spielbare, faire Standardwerte. Die Werte werden geprüft (Bereiche), ungültige
  Werte fallen mit Log-Hinweis auf den Standard zurück.
- **Config-Aufteilung (NeoForge):**
  - Spielwerte stehen in der `SERVER`-Config. Sie wird beim Verbinden synchronisiert, ist aber nicht früh in der
    Initialisierung verfügbar.
  - Darstellung steht in der `CLIENT`-Config.
  - Die `COMMON`-Config wird nicht synchronisiert und ist nur für Werte gedacht, die beide Seiten lokal brauchen.
  - Für jeden Wert ist dokumentiert, ob er einen Neustart braucht.
  - Fabric: Config-Bibliothek pro Mod festlegen und in der `AGENTS.md` nennen.
- **Passt zur Host-Mod:** Create-Addons nutzen Create-Rezepttypen, -Optik und -Begriffe. Eigenständige Mods sehen
  aus wie Vanilla.
- **Rezepte:** über Datapacks überschreibbar. JEI/EMI-Integration, wenn die Mod Rezepte oder Rezepttypen hat. Tags
  im gemeinsamen Namespace (`c:`), wo vorhanden.
- **Keine Überraschungen:** Ein starkes Feature lässt sich in der Config abschalten. Die Mod ändert die Welt nur
  durch Spieleraktionen oder durch Automatisierung, die ein Spieler gebaut oder eingeschaltet hat.
- **Icon:** jede Mod hat ein Icon (`logo.png`, Quelldatei unter `docs/`), eingetragen in den Metadaten.
- **GUIs:** lesbar auf jeder GUI-Skalierung, Tooltips an allen Bedienelementen.

## 8. Daten und Kompatibilität

- Gespeicherte Daten (Data Components, NBT, Block-Entities, Weltdaten, Configs) bleiben über Updates lesbar.
- Jedes eigene Format trägt eine Formatversion. Beim Laden wird das alte Format gelesen oder migriert.
- Unbekannte oder entfernte IDs führen nicht zum Absturz. Sie werden geloggt und übersprungen oder ersetzt.
- Jede Formatänderung bekommt mindestens einen Test mit einem gespeicherten Stand der Vorversion.
- **Downgrades** auf eine ältere Mod-Version werden nicht unterstützt. Das steht im CHANGELOG, wenn sich ein Format
  ändert.
- Ein MAJOR-Release darf die Lesbarkeit nur brechen, wenn das im CHANGELOG deutlich angekündigt ist und es einen
  Migrationsweg oder eine Anleitung gibt.

## 9. Code, Tests und Nachweise

- **Build:** `./gradlew build` im Projektordner muss grün sein, bevor etwas als fertig gilt.
- **Tests:** Logik ohne Minecraft bekommt Unit-Tests (JUnit). Verhalten in der Welt bekommt GameTests, wo es
  sinnvoll ist.
- **Seiten getrennt:** keine Client-Klassen im gemeinsamen Code (physische Seite). Spiellogik läuft auf dem Server
  (logische Seite).
- **Server-Test vor Releases:**
  1. Die *fertige* Jar auf einem dedizierten Server laden.
  2. Mit einem Client verbinden.
  3. Die Kernfunktion ausführen, auch Menüs und Payloads.
  4. Die Welt neu laden.
- **Leistung:**
  - Kein schweres Scannen pro Tick.
  - Große Arbeit wird über mehrere Ticks budgetiert.
  - Caches werden invalidiert statt neu aufgebaut.
- **Logging:** Logger mit der `mod_id`, kein Spam pro Tick. Warnungen nennen Ursache und Abhilfe.
- **Datagen:** pro Projekt in der `AGENTS.md` festlegen, ob sie genutzt wird und wie man sie startet. Eine
  Ressource stammt entweder aus der Datagen oder ist von Hand geschrieben, nie beides.
- **Testnachweis:** für jede Änderung, die an Spieler geht, eine Zeile in `docs/TESTING.md` mit Datum,
  Mod-Version/Commit, Loader, MC, tatsächlichen Versionen der Abhängigkeiten, ob die optionalen Integrationen
  dabei waren, Ergebnis und dem, was **nicht** getestet wurde.
- **Testwelten:** nur Kopien. Test-Jars nie ungefragt in persönliche Spielstände oder Instanzen außer „test“
  legen.

## 10. Versionierung und Release

- **SemVer `MAJOR.MINOR.PATCH`:**
  - PATCH: Fehlerbehebung oder Kompatibilität, z. B. eine neue Version einer Abhängigkeit.
  - MINOR: neue Features.
  - MAJOR: Brüche (siehe Abschnitt 8).
- **Jar-Name:** `<mod_id>-<loader>-<mc>-<version>.jar`, z. B. `shapeexcavator-neoforge-1.21.1-0.2.0.jar`.
  Installierbar ist genau diese Jar: remappt, mit gebündelten Bibliotheken, keine `-sources` und keine
  Dev-Artefakte. Gesetzt über `base.archivesName`/`jar` in Gradle.
- **Tags:** `v<version>`, wenn alle Ports dieselbe Version tragen. Sonst `v<version>-<loader>-<mc>`.
- **CHANGELOG:** im Stil „Keep a Changelog“ mit `Added`, `Changed`, `Fixed`, `Compatibility`, für Spieler
  verständlich.
- **Release-Reihenfolge:**
  1. Abhängigkeiten und den NeoForge- bzw. Fabric-Build prüfen (Abschnitte 3 und 4).
  2. Version anheben, CHANGELOG und README-Kompatibilität aktualisieren.
  3. Final bauen (`./gradlew build`), Tests grün.
  4. **Genau diese Jar** testen: Client, dedizierter Server (Abschnitt 9) und die CurseForge-Instanz „test“ mit den
     echten Nachbar-Mods. Den Nachweis in `docs/TESTING.md` eintragen.
  5. Commit und Tag. **Pushen und Veröffentlichen nur nach OK des Betreibers.**
  6. Den Upload auf CurseForge (und gegebenenfalls Modrinth) macht der Betreiber. Loader, MC-Versionen,
     Pflicht- und optionale Abhängigkeiten und Release-Kanal müssen zur Jar passen.

## 11. README-Aufbau

`# <Anzeigename>`, ein Satz, worum es geht, dann diese Abschnitte:

- `## Requirements`: Loader, Minecraft, Untergrenzen der harten Abhängigkeiten.
- `## What it adds`
- `## Getting started`
- `## Configuration`
- `## Compatibility`: getestete Versionen, optionale Integrationen, bekannte Konflikte.
- `## Reporting issues`: Mod-, Loader- und MC-Version, andere Mods, Log (`latest.log`), Schritte zum Nachstellen.
- `## Building from source`: mit dem richtigen Projektordner.
- `## License`

## 12. Git

- Gearbeitet wird auf Branches, `main` bleibt baubar. Commit-Präfixe: `feat`, `fix`, `chore`, `docs`, `refactor`,
  `test`.
- **Keine KI als Autor oder Committer, kein KI-Vermerk** in Commits, PRs, Issues oder Kommentaren (keine
  `Co-Authored-By`- oder „Generated with“-Zeilen). Der globale Hook prüft nur Commit-Nachrichten. Alles andere ist
  Pflicht des Agenten. Der Hook wird nie umgangen.
- Vorhandene Änderungen bleiben erhalten: kein ungefragtes `reset`, `clean`, `checkout --` oder Überschreiben.
  Vor einem Zusammenführen erst sichern.
- Kein Force-Push ohne ausdrückliches OK des Betreibers.

## 13. Arbeitsweise für Agenten

- **Kleine Anfragen** (Spielermeldung, Kompatibilitäts-Update, Screenshot aus Kommentaren):
  1. Mod im Verzeichnis unten finden.
  2. Fehlende Angaben (Versionen, Log) nachfragen oder selbst prüfen.
  3. Nachvollziehen, kleinste passende Änderung, bauen, mit der echten Nachbar-Mod testen.
  4. Ergebnis, Jar-Pfad und Testnachweis melden.
  - Nichts Unbeteiligtes umbauen.
- **Große Aufträge** (neue Mod, neue Etappe): Plan in `docs/`, nach Freigabe in Etappen umsetzen.
- **Bestand:** Die ⚠-Punkte in Abschnitt 14 werden als eigene Aufträge behoben. Die übrigen Abweichungen einer
  Mod gleicht der Agent mit an, wenn er die Mod ohnehin bearbeitet. Das läuft als eigener Commit und nur für
  risikoarme Punkte: README, CHANGELOG, Lang-Keys statt `Component.literal`, Jar-Name, Versionsformat,
  NeoForge-Untergrenze. Nie dazu gehören `mod_id`, Gruppe, Paket oder der Umzug von Ordnern. Diese ändert der
  Agent nur auf ausdrücklichen Auftrag.
- **Sprache:** Antworten an den Betreiber auf Deutsch. Spielertexte und Antworten an Spieler auf Englisch.

### Mod-Verzeichnis

| Mod (Kurzform) | Repo `D:\OpenCode-Projekt\…` | Projektordner | mod_id | Gruppe | Status |
|---|---|---|---|---|---|
| Create: Factory Routing | `create-factory-routing` | Wurzel (NeoForge 1.21.1) | `create_factory_routing` | `dev.failzockt.factoryrouting` | veröffentlicht |
| Create: Production Network | `create-production-network` | Wurzel (NeoForge 1.21.1) | `create_production_network` | `dev.failzockt.productionnetwork` | in Entwicklung |
| Create: Instant Stock Crafting („Stock Crafter“) | `create-stock-instant-craft` | `neoforge-1.21.1/` | `stockinstantcraft` | `com.byflx.stockinstantcraft` | veröffentlicht (1.1.3) |
| Shape Excavator | `minecraft_shape-excavator` | `neoforge-1.21.1/` | `shapeexcavator` | `com.byflx.shapeexcavator` | in Entwicklung (Etappen 1–5 von 6) |
| Easy Quarry | `easy_quarry` | `fabric-1.21.1/`, `fabric-1.21.10/`, `neoforge-1.21.1/` | `easyquerrie` | `com.easyquerrie` | veröffentlicht |

Den Status pflegt der Betreiber. Links zu CurseForge und Modrinth sowie zum Issue-Tracker stehen in der jeweiligen
`AGENTS.md`.

## 14. Bekannte Abweichungen im Bestand (Stand 2026-10-22)

⚠ = verursacht wahrscheinlich Probleme bei Spielern, wird als eigener Auftrag behoben. Der Rest wird nebenbei
angeglichen (Abschnitt 13).

- **Factory Routing:**
  - ⚠ Laufzeit-Pins: Create `[6.0.10]` und das *optionale* Factory Controller, seit 0.2.0 (2026-10-23, Commit
    3c82a79) `[1.2.1-neoforge-1.21.1,1.2.3-neoforge-1.21.1]`. Die geschlossene Obergrenze heißt: Mit der nächsten
    Factory-Controller-Version lädt das Spiel wieder nicht. Bis 0.1.0 war es `[1.1.1-neoforge-1.21.1]`, was zur
    Spielermeldung vom ca. 2026-10-18 passte.
  - Liegt im Wurzelordner, `SPEC.md` im Wurzelordner.
  - Spielertexte über `Component.literal` in `command/FactoryRoutingCommands.java`.
  - README ohne „Configuration“ und „Compatibility“. Jar-Name ohne Loader.
  - NeoForge-Untergrenze `[21.1.227,)` liegt unter `21.1.229`.
- **Production Network:**
  - ⚠ Laufzeit-Pins: Create `[6.0.10]` und ApricityUI `[1.2.4-hotfix]` (gebündelt, siehe
    `THIRD_PARTY_NOTICES.md`).
  - Wurzelordner, Pläne und Specs im Wurzelordner, README anders gegliedert, Jar-Name ohne Loader.
  - NeoForge-Untergrenze `[21.1.227,)`. Der optionale JEI-Stack braucht laut Repo 21.1.238.
  - Die bestehende `AGENTS.md` verbietet Worktrees und andere Arbeitskopien. Diese Vorgabe bleibt.
- **Instant Stock Crafting:**
  - Create mit Obergrenze `[6.0.10,7.0)`.
  - NeoForge-Untergrenze `[21.1.219,)` liegt unter `21.1.229`.
  - `mod_id` ohne `create_`-Präfix (bleibt so). README ohne „Configuration“. Jar-Name ohne MC-Version.
- **Shape Excavator:**
  - README noch kurz.
  - NeoForge-Untergrenze `[21.1.219,)`.
- **Easy Quarry:**
  - NeoForge-Untergrenze `[21.1.0,)`.
  - Version `1.1` statt `1.1.0`. Die `mod_id` `easyquerrie` hat einen Tippfehler, bleibt aber.
  - Das README nennt Fabric 1.21.1 nicht. Die Fabric-Bauanleitung zeigt auf den Wurzelordner, wo es kein
    Gradle-Projekt gibt.
  - Jar `fabric-1.21.10` heißt `easyquerrie-1.1.jar`. Fabric nutzt Loom `1.14-SNAPSHOT`.
- **Alle:**
  - `de_de.json` vorhanden bei Factory Routing, Production Network, Stock Crafting und Easy Quarry.
  - Ein CHANGELOG gibt es nur bei Production Network. `docs/TESTING.md` gibt es noch nirgends.
  - Gradle: 8.14.3 bzw. 9.2.1 gemischt (ModDevGradle 2.0.141 überall bei NeoForge).

## 15. Entscheidungen des Betreibers

- 2026-10-22: Gruppe `com.byflx` für neue Mods; ein Unterordner pro Loader und Version; `Create:`-Präfix nur
  für Mods, die Create brauchen; Loader und Version pro Mod; Abhängigkeiten mit Untergrenze statt Pin; nur
  Englisch; Lizenz MIT für alle Mods (Easy Quarry und Shape Excavator umgestellt).
- 2026-10-23: Abschnitt 7 (Spielgefühl) verbindlich; nur die ⚠-Punkte werden als eigene Aufträge behoben, der
  Rest nebenbei; vorerst keine CI (lokaler Build vor jedem Release reicht).

- 2026-10-23: Ausgerollt in alle fünf Repos (`docs/MOD_STANDARDS.md`, `AGENTS.md`, `CLAUDE.md`), Abgleich über
  `sync-standards.ps1`.
