# Entwicklungsplan: Excavation 3D

**Zielplattform:** Minecraft 1.21.1 / NeoForge 21.1.x  
**Status:** Etappen 1–5 umgesetzt (Lauf #25, 335b569). Korrektur 2026-10-22: Die Form hängt an normalen Werkzeugen, es gibt kein eigenes Item (siehe Abschnitt 2a).  
**Arbeitstitel:** Excavation 3D; die Verfügbarkeit des Namens wurde nicht geprüft.  
**Referenz:** Die vom Nutzer gezeigte, drehbare 3D-Vorschau seiner eigenen Mod. Deren Quellcode wurde für diesen Plan nicht eingesehen.

## 1. Ziel und verbindlicher Umfang

Eine eigenständige Mod, mit der normale Abbauwerkzeuge (Spitzhacke, Schaufel, Axt, auch Werkzeuge anderer Mods) eine im Spiel einstellbare Abbauform bekommen. Es gibt **kein eigenes Werkzeug-Item**. Der Spieler soll nicht erst eine Konfigurationsdatei bearbeiten oder sich eine Form anhand von Zahlen vorstellen müssen.

Der vollständige erste Funktionsumfang umfasst:

- Frei editierbare Zahlenfelder für Abmessungen und Formparameter; Plus-/Minus-Schaltflächen sind lediglich zusätzliche Bedienmöglichkeiten.
- Eine echte drehbare und zoombare 3D-Blockvorschau direkt im Einstellungsfenster.
- Quader, Flächen-/Linienvorlagen, Plus-Form, Stufenform und Treppengang.
- Einen Custom-Modus zum An- und Abwählen einzelner Blöcke über Ebenen.
- Klaren Startblock, Ausrichtung, Formrotation und Spiegelung.
- Speicherung der aktiven Konfiguration am Werkzeug, persönliche Vorlagen und Vorschau in der Spielwelt.
- Serverseitig geprüften Mehrfachabbau mit Tests für Singleplayer und einen separaten Server.

Die Mod soll ohne Create laufen. Der vorhandene Preview-Code ist zunächst nur eine mögliche Quelle wiederverwendbarer Darstellungskomponenten. Vor einer Übernahme müssen dessen tatsächliche Abhängigkeiten geprüft werden. Keine Controller-, Netzwerk- oder Produktionslogik aus der anderen Mod übernehmen.

Eine Quader-only-Version ist ein Zwischenstand, nicht die Erfüllung des gesamten Auftrags. Quarry-Maschinen, automatische Bauplatzierung, Erzader-Suche, Energieversorgung und viele Materialstufen gehören nicht zum ersten Funktionsumfang.

## 2. Bedienungsablauf

Der Spieler hält ein geeignetes Werkzeug (Abschnitt 2a) in der Haupthand. Eine frei belegbare Konfigurationstaste (Standard `V`) öffnet die Konfiguration, auch wenn ein Block anvisiert wird. Rechtsklicks werden **nicht** abgefangen: Fremde Werkzeuge haben oft eigene Rechtsklick-Fähigkeiten, und Schaufel/Axt nutzen Rechtsklick für Pfade und Entrinden. Gewöhnliche Interaktionen mit Kisten oder Maschinen bleiben unberührt.

Der Spieler wählt eine Form, gibt Zahlen ein und sieht unmittelbar die gültige neue Form. Anschließend speichert er mit **Übernehmen** oder **Übernehmen & in Welt zeigen**. Erst die Bestätigung des Servers aktiviert die neue Einstellung. Der zweite Knopf aktiviert zusätzlich die Weltvorschau und schließt nach erfolgreicher Bestätigung den Editor.

Das normale Abbauen des anvisierten Startblocks löst den Formabbau aus. Die Weltvorschau allein darf niemals etwas abbauen. Eine belegbare Einzelblock-Taste, standardmäßig Schleichen, umgeht die Form vorübergehend. Das muss im kleinen Werkzeug-Hinweis erkennbar sein.

Änderungen im Editor sind zunächst ein Entwurf. Abbrechen verändert das Werkzeug nicht. Beim Schließen mit ungespeicherten Änderungen erscheint eine kurze Verwerfen-Abfrage. Fenstergröße und GUI-Skalierung dürfen den Entwurf nicht zurücksetzen.

## 2a. Geeignete Werkzeuge (Korrektur 2026-10-22)

- Geeignet ist jedes Item im Tag `excavation3d:shape_tools`. Er enthält standardmäßig `#minecraft:pickaxes`, `#minecraft:shovels` und `#minecraft:axes`. Werkzeuge anderer Mods sind damit automatisch dabei, wenn sie in diesen Tags stehen; Datapacks und Modpacks können den Tag erweitern oder Werkzeuge entfernen.
- Ein Werkzeug ohne gespeicherte Form verhält sich exakt wie in Vanilla bzw. wie von seiner Mod vorgesehen. Erst eine im Editor übernommene Form (Data Component am ItemStack) aktiviert den Formabbau für genau dieses Werkzeug.
- Abbaueignung, Abbaustufe, Verzauberungen und Haltbarkeit kommen vom jeweiligen Werkzeug selbst. Zusatzblöcke, für die das Werkzeug nicht das richtige ist (z. B. Stein mit der Schaufel), werden übersprungen und gemeldet.
- Die Form bleibt beim Reparieren im Amboss, Verzaubern, Umbenennen und Schmieden (Netherit-Upgrade) erhalten, soweit Vanilla die Komponenten mitnimmt; das wird getestet. Im Editor gibt es „Form entfernen“, damit ein Werkzeug wieder normal abbaut.
- Der Werkzeug-Hinweis (Tooltip) erscheint nur bei Werkzeugen mit Form: aktive Form, Einzelblock-Taste, Editor-Taste.
- Entfernt werden das eigene Item `excavation3d:shape_excavator`, sein Rezept, der Eintrag im Kreativ-Inventar und die Einträge in den Verzauberbarkeits-Tags. Die Mod ist unveröffentlicht, eine Migration bestehender Items ist nicht nötig.
- Die Bindung der Editor-Sitzung an Spieler, Inventarplatz und ItemStack bleibt; geprüft wird zusätzlich, dass das Item noch im Tag `excavation3d:shape_tools` ist.

## 3. Bildschirmaufteilung

Links stehen die Formauswahl und die dazugehörigen Eingaben. Rechts befindet sich die große 3D-Vorschau. Unten stehen die Zusammenfassung, Vorlagenfunktionen und die Aktionen zum Übernehmen oder Abbrechen.

```text
Form: [Quader v]                  3D-Vorschau

Breite   [-] [  3 ] [+]          [drehbares Blockmodell]
Höhe     [-] [  3 ] [+]          [Startblock + Achsen]
Tiefe    [-] [  1 ] [+]

[Drehen 90°] [Spiegeln]          [Vorne] [Seite] [Oben]
                                 [Ansicht zurücksetzen]

Ausdehnung: 3 × 3 × 1             Markiert: 9 Blöcke

[Vorlage laden] [Vorlage speichern]
[Abbrechen] [Übernehmen] [Übernehmen & in Welt zeigen]
```

Das ist eine Layout-Skizze, keine pixelgenaue Vorgabe. Bei geringer verfügbarer GUI-Fläche wird der Parameterbereich scrollbar; Eingaben und Vorschau dürfen sich nicht überlagern. Die Oberfläche erhält deutsche und englische Übersetzungen.

Technische Grundlage: Ein eigenständiger Minecraft-`Screen` mit `EditBox`-Eingaben und eigener Vorschau-Komponente. Die NeoForge-Dokumentation für 1.21–1.21.1 beschreibt eigenständige Screens, Textfelder, Fokusbehandlung und GUI-Skalierung [S1].

## 4. Zahlenfelder: Pflichtanforderungen

Jede quantitative Einstellung erhält ein wirklich bearbeitbares Zahlenfeld. Das gilt ausdrücklich auch für Stufenanzahl, Stufentiefe, Stufenhöhe, Durchgangshöhe und Armstärke. Keine versteckten Regeln wie „nur ungerade Werte“ für allgemeine Breite, Höhe oder Tiefe.

Unterstützt werden Anklicken, Strg+A, Kopieren/Einfügen, Rücktaste, Entf und Tab-Navigation. Enter bestätigt den Feldwert, speichert aber nicht ungefragt die gesamte Werkzeugkonfiguration. Plus und Minus ändern den gültigen Wert um eins. Gedrückthalten oder größere Schrittweiten sind optionaler Komfort, kein Ersatz für Zahleneingabe.

Während des Tippens darf das Feld vorübergehend leer sein. Es darf nicht sofort auf 1 zurückspringen. Sobald die gesamte Konfiguration gültig ist, wird die Vorschau aktualisiert. Bei ungültiger Eingabe bleibt gegebenenfalls die letzte gültige Darstellung sichtbar, dann aber ausdrücklich mit dem Hinweis **Letzte gültige Vorschau**. Übernehmen ist in diesem Zustand deaktiviert.

Negative Zahlen, Dezimalzahlen, Überläufe und Werte außerhalb der Grenzen werden mit einer verständlichen Meldung abgelehnt. Keine stillen Korrekturen oder heimliche Verkleinerung der Form. Die Eingabelänge wird vor dem Parsen begrenzt.

**Vorgeschlagene Grenzen für Version 1:** höchstens 32 Blöcke je Achse der resultierenden Form und höchstens 4.096 ausgewählte Blöcke. Der Server darf diese Grenzen niedriger setzen. Eine Erhöhung der technischen Obergrenzen wird erst nach Leistungs- und Netzwerktests vorgesehen. Die Grenzen sind Designvorschläge, keine Minecraft-Vorgaben.

Abgeleitete Größen müssen ebenfalls geprüft werden: Beispielsweise darf eine Treppe nicht durch die Multiplikation zweier einzeln erlaubter Eingaben eine übergroße Form erzeugen. Für Größenberechnungen überlaufsichere Arithmetik verwenden und vor der Speicherreservierung validieren.

## 5. 3D-Vorschau und eindeutige Bedeutung

Die Vorschau zeigt standardmäßig **die Blöcke, die zum Abbau ausgewählt sind**. Sie darf nicht bloß den umschließenden Quader rendern. Ein Plus muss als Plus erkennbar sein; bei eigenen Formen müssen ausgesparte Stellen sichtbar bleiben.

Linkes Ziehen im 3D-Fenster dreht ausschließlich die Kamera, das Mausrad zoomt. Die Kamera verändert niemals die tatsächliche Abbauausrichtung. Dafür gibt es getrennte Schaltflächen **Form drehen** und **Form spiegeln**. Ansichten von vorne, oben und der Seite sowie ein Rücksetzknopf erleichtern die Kontrolle.

Ein Raster, Abmessungslinien und beschriftete lokale Achsen zeigen Breite, Höhe und Tiefe. Die Vorschau markiert den Startblock und die Richtung nach vorne beziehungsweise in den Block hinein. Kameraänderungen dürfen die Formdaten nicht ändern und keine neue Formberechnung auslösen.

Bei Treppen gibt es zusätzlich **Ergebnis ansehen**: Eine neutrale, klar als schematisch gekennzeichnete Umgebung zeigt, was stehen bleibt. Das darf nicht mit einem exakten Abbild des aktuellen Geländes verwechselt werden. Die Ansicht erzeugt und platziert keine echten Treppenblöcke.

## 6. Formdefinitionen

### 6.1 Quader, Fläche und Linie

Der Quader verwendet Breite B, Höhe H und Tiefe T. Alle Positionen in diesem Raster werden ausgewählt. Eine Fläche ist dieselbe Berechnung mit Tiefe 1; eine Linie ist eine passende Vorlage mit zwei Abmessungen auf 1. Dafür keine unnötigen separaten Geometriesysteme entwickeln.

Beispiele: 3 × 3 × 1 ergibt 9 ausgewählte Blöcke; 7 × 5 × 2 ergibt 70.

### 6.2 Plus-Form

Parameter: Breite, Höhe, Tiefe und Armstärke. Das Plus besteht aus einem senkrechten und einem waagerechten Balken in der Breiten-/Höhenebene und wird über die Tiefe fortgesetzt. Überschneidungen werden nur einmal gezählt.

Die Balken werden deterministisch im Raster zentriert. Bei geraden Abmessungen wird die zusätzliche Zelle eindeutig einer Seite zugeordnet und in der Vorschau sichtbar. Die Armstärke darf nicht größer als die kleinere der beiden Querschnittsabmessungen sein.

Beispiele bei Armstärke 1: 3 × 3 × 1 ergibt 5 Blöcke; 5 × 5 × 1 ergibt 9; 5 × 5 × 2 ergibt 18.

### 6.3 Stufenform

Diese Vorlage wählt eine **massive gestufte Abbaumenge** aus. Das ist nicht automatisch ein begehbarer Treppentunnel.

Parameter: Breite B, Stufenanzahl N, Tiefe pro Stufe L, Höhe pro Stufe R und aufsteigend/absteigend. Die resultierende Tiefe beträgt N × L; die maximale Höhe beträgt N × R.

Für eine aufsteigende Form gilt für jede Tiefenposition z: Stufenindex s = floor(z / L). Ausgewählt werden alle lokalen Höhen von 0 bis R × (s + 1) − 1, über die gesamte Breite. Die absteigende Variante kehrt die Stufenreihenfolge um.

Die Oberfläche zeigt sowohl die Parameter als auch die resultierenden Gesamtabmessungen. Sie benennt die Ansicht unmissverständlich als Abbauform.

### 6.4 Treppengang

Diese Vorlage soll einen schräg verlaufenden Gang freilegen, statt ein massives Treppenvolumen zu entfernen.

Parameter: Breite B, freie Durchgangshöhe C, Stufenanzahl N, Tiefe pro Stufe L, Höhe pro Stufe R und aufwärts/abwärts. Standardvorschlag: B = 3, C = 3, L = 1 und R = 1.

Für die Tiefe z gilt s = floor(z / L). Die unterste ausgewählte Höhe ist y0 = Richtung × s × R. Ausgewählt werden y0 bis y0 + C − 1. Der jeweilige Boden direkt darunter bleibt außerhalb der Auswahl.

Die Gesamthöhe beträgt C + (N − 1) × R und ist **nicht** identisch mit der freien Durchgangshöhe. Beispiel: Breite 3, Durchgangshöhe 3 und 6 Stufen mit jeweils einem Block Tiefe und Höhe ergeben die Ausdehnung 3 × 8 × 6 und 54 ausgewählte Blöcke.

Die Mod legt nur vorhandenes Gelände frei. Sie füllt keine Löcher, garantiert bei fehlendem Boden keinen Weg und setzt keine Treppenblöcke. Größere Stufenhöhen können unpraktisch zum Begehen sein; die Vorschau soll das erkennbar machen.

### 6.5 Custom-Form

Jede Vorlage kann mit **Als eigene Form bearbeiten** in eine Blockauswahl umgewandelt werden. Der Nutzer kann auch mit einem leeren Raster beginnen.

Für die Bearbeitung wird eine Tiefenebene ausgewählt. Ein 2D-Raster zeigt deren Breite und Höhe. Linkes Klicken/Ziehen setzt ausgewählte Zellen, rechtes Klicken/Ziehen entfernt sie. Ein Ziehvorgang darf dieselbe Zelle nicht mehrfach hin und her umschalten. Gleichzeitig aktualisiert sich die 3D-Vorschau.

Es gibt Ebenennummer als Zahlenfeld, vorige/nächste Ebene, Ebene kopieren/einfügen, Ebene füllen/leeren und Rückgängig/Wiederholen. Andere Ebenen werden bei Bedarf schwach eingeblendet. Im 3D-Fenster selbst wird in Version 1 nicht gemalt; damit kollidiert die Kamerabedienung nicht mit dem Bearbeiten.

Vergrößern des Rasters ergänzt leere Zellen und skaliert die bestehende Form nicht. Verkleinern fordert eine Bestätigung an, falls ausgewählte Zellen entfernt würden. Die Operation erhält den Anker oder wird abgelehnt. Ein leeres Raster ist als Entwurf erlaubt, aber nicht als aktive Abbaukonfiguration.

## 7. Startblock, lokale Achsen und Ausrichtung

Breite/Höhe/Tiefe sind lokale Formachsen, keine fest verdrahteten Weltkoordinaten.

Beim Quader und Plus liegt der Standardanker in der vorderen Tiefenebene, mit den Querschnittsindizes floor((B − 1) / 2) und floor((H − 1) / 2). So sind gerade Größen wie 4 × 2 × 1 eindeutig: In der Breite entstehen relativ zum Anker die Offsets −1, 0, +1, +2; in der Höhe 0 und +1.

Bei Treppen liegt der Standardanker vorne auf der untersten ausgewählten Gang-/Stufenposition. Im Custom-Modus kann ein ausgewählter Block der vordersten belegten Ebene als Anker gesetzt werden. Der Anker muss immer zur Auswahl gehören, damit ein regulär abgebauter Startblock niemals außerhalb der dargestellten Form liegt.

Bei normalen Formen bestimmt die getroffene Blockseite die Tiefenrichtung. Für Ober- und Unterseiten wird die horizontale Spielerorientierung zusätzlich verwendet, um Breite und Höhe deterministisch zuzuordnen. Alle sechs Seiten müssen getestet werden.

Treppen orientieren sich standardmäßig an Welt-oben und der horizontalen Blickrichtung. Die tatsächliche Richtung muss in der Weltvorschau erkennbar sein. Formrotation um 90° in der lokalen Querschnittsebene und Spiegelungen werden als explizite Transformationen gespeichert. Transformationen wenden dieselben Regeln auf Maske und Anker an.

Die Ausrichtung wird beim Beginn eines Abbauvorgangs festgehalten und serverseitig geprüft. Ein späterer Kamerawechsel darf einen laufenden Vorgang nicht umlenken.

## 8. Weltvorschau und tatsächliches Ergebnis

Der Renderer verwendet dieselbe Formberechnung und dieselbe Koordinatentransformation wie der Server. Er zeigt den geplanten Bereich am anvisierten Startblock als transparente Blockauswahl beziehungsweise Umrisse.

Die Anzeige unterscheidet zwischen geometrisch ausgewählten Positionen, aktuell vorhandenen Blöcken und erkennbar ausgeschlossenen Positionen. Hinweise zu Luft, fehlenden Chunks oder ausgeschlossenen Blocktypen verhindern falsche Erwartungen.

Ein Client kennt nicht zwingend sämtliche serverseitigen Schutzentscheidungen. Deshalb darf die Vorschau keine unbestätigte Abbauerlaubnis garantieren. Unbekannte Berechtigungen bleiben als unbekannt gekennzeichnet. Zur Vorschau keine echten Break-Events probeweise auslösen.

Die verbindliche Invariante lautet: **Editor, Weltvorschau und Server berechnen dieselbe geometrische Auswahl. Der tatsächlich erlaubte Abbau ist eine geprüfte Teilmenge davon.** Nach einem Vorgang kann beispielsweise „7 abgebaut, 2 übersprungen“ erscheinen. Keine Auslassungen außerhalb der Form hinzuerfinden, um eine Zielanzahl zu erreichen.

## 9. Speicher- und Netzwerkarchitektur

### Aktive Konfiguration am Werkzeug

Ein versionierter, unveränderlicher Konfigurationswert enthält Formtyp, Parameter, Custom-Maske, Anker, Transformationen und eine Konfigurationsrevision. Er wird als persistente und synchronisierte Data Component am jeweiligen ItemStack gespeichert. NeoForge dokumentiert dafür `DataComponentType`, Persistenz, Netzwerksynchronisierung und die Behandlung gespeicherter Komponentenwerte als unveränderlich [S2].

Zwei Werkzeuge können unterschiedliche aktive Formen besitzen. Ablegen, Aufheben, Übergeben und Weltneustart dürfen die gültige gespeicherte Konfiguration nicht verlieren. Veraltete oder beschädigte Konfigurationen führen zu einer sichtbaren Warnung und einem sicheren Einzelblock-Fallback, nicht zu einer riesigen Standardform.

Persönliche benannte Vorlagen werden separat clientseitig gespeichert. Beim Laden auf einem Server gelten dessen Grenzen erneut. Eine Vorlage ist kein Weg, Limits zu umgehen. Das Werkzeug muss seine aktive Form ohne Zugriff auf die persönliche Vorlagenbibliothek wiederherstellen können.

### Übernahme einer Änderung

Ein Editor-Entwurf bleibt lokal. Nur Übernehmen sendet eine begrenzte Anfrage mit Sitzung, erwarteter Revision und Konfiguration. NeoForge stellt für die Zielversion registrierbare Custom Payloads und deren Handler bereit [S3].

Die Serversitzung wird an den tatsächlichen Spieler, das konkrete Werkzeug und dessen aktuelle Inventarposition gebunden. Der Server prüft Besitz, Revision, sämtliche Parameter, Maskenlänge, Anker, effektive Grenzen und die erzeugte Form erneut. Bei Werkzeugwechsel, veralteter Sitzung oder ungültigen Daten erfolgt eine Ablehnung statt einer Änderung am falschen Werkzeug.

Erst eine erfolgreiche Serverantwort ersetzt die aktive Konfiguration im Client. Fehler bleiben im Editor sichtbar. Anfragen erhalten Größen- und Ratenlimits; keine unbeschränkten Listen aus Netzwerkdaten allozieren. Welt- und Inventaränderungen erfolgen auf dem Server-Hauptthread. Client-Renderer dürfen nicht aus gemeinsam geladenen Serverklassen referenziert werden.

### Vorgeschlagene Bausteine

| Baustein | Verantwortung |
|---|---|
| `ShapeConfig` | Versionierte, unveränderliche gespeicherte Einstellungen |
| `ShapeGenerator` | Reine Berechnung der ausgewählten lokalen Blockpositionen |
| `ShapeTransform` | Anker, Drehung, Spiegelung und Weltorientierung |
| `ShapeValidator` | Parameter-, Masken-, Größen- und Grenzprüfungen |
| `ShapeEditorScreen` | Entwurf, Formparameter, Vorlagen und Aktionen |
| `NumericField` | Einheitliche Zahlenbedienung und Validierungszustände |
| `ShapePreviewRenderer` | 3D-Darstellung im Editor |
| `CustomLayerEditor` | Bearbeitung einzelner Ebenen |
| `WorldPreviewRenderer` | Geometrische Auswahl am Weltziel |
| `ExcavationService` | Autorisierter und begrenzter Serverabbau |
| `PresetRepository` | Persönliche Vorlagen |

Keine getrennten Shape-Algorithmen in UI, Weltvorschau und Mining-Code. Custom-Masken müssen gegen unbeabsichtigte Mutation geschützt werden; ein Record mit einem intern veränderbaren Array genügt dafür allein nicht.

## 10. Regeln für den tatsächlichen Abbau

Der Flächenabbau startet erst, nachdem der Startblock wirklich erfolgreich serverseitig abgebaut wurde. Ein bloß ausgelöstes Break-Event ist kein Erfolgsnachweis. NeoForges `BlockEvent.BreakEvent` beschreibt einen abbrechbaren Abbauversuch; das Abbrechen verhindert den Blockabbau [S4]. Der konkrete Einhängepunkt muss anhand des Quellcodes der gewählten NeoForge-1.21.1-Version und durch Tests festgelegt werden.

Zusätzliche Blöcke durchlaufen den Abbaupfad des echten Spielers und die relevanten Prüfungen. Kein pauschales Setzen auf Luft, kein Un-Canceln fremder Schutzentscheidungen. Loot, Erfahrung und Werkzeugverschleiß dürfen nicht doppelt ausgelöst werden; der Startblock wird nicht erneut verarbeitet. Ein pro Vorgang/Spieler begrenzter Rekursionsschutz verhindert, dass Zusatzabbau wieder Zusatzabbau startet.

Der Startblock unterliegt der normalen Spielerreichweite. Zusätzliche Positionen sind die ausdrücklich erlaubte Flächenerweiterung innerhalb der serverseitig geprüften Formgrenzen. Der Client darf keine beliebigen Weltkoordinaten zum Löschen übermitteln. Diese Flächenerweiterung muss bewusst implementiert werden; ein unbeabsichtigter Reichweiten-Bypass ist keine Lösung.

Unzerstörbare Blöcke, reine Flüssigkeitsblöcke, Weltgrenzen, nicht geladene Chunks und geschützte Positionen werden nicht abgebaut. Der Vorgang lädt keine Chunks nach. Block Entities, insbesondere Lager und Maschinen, werden standardmäßig vom Zusatzabbau ausgeschlossen. Ein anvisierter solcher Block darf nicht nebenbei einen Flächenabbau starten.

Werkzeug-Eignung, Abbaustufe und Verzauberungen werden berücksichtigt. Vorgeschlagene einfache V1-Regel gegen das Ausnutzen eines weichen Startblocks: Zusätzliche Blöcke mit längerer effektiver Abbaudauer als der Startblock werden übersprungen und entsprechend gemeldet. Ein späterer Modus für heterogene Härten benötigt ein korrektes Zeitbudget; kein sofortiger Obsidianabbau durch Anklicken von Erde.

Der Server arbeitet große Auswahlen in begrenzten Portionen ab und setzt ein Budget für geprüfte Positionen pro Tick, zusätzlich ein globales Budget über alle Spieler. Auch Luft- und abgelehnte Positionen zählen zum Arbeitsbudget. Als Startwerte sind 32 geprüfte Positionen pro Spieler/Tick und 256 insgesamt vorgeschlagen; nach Lasttests anpassen.

Reihenfolge: nahe Tiefenebenen zuerst, innerhalb einer Ebene deterministisch. Ein Vorgang wird bei Werkzeugbruch, Werkzeugwechsel, Tod, Dimensionswechsel oder Verbindungsabbruch beendet. Später veränderte Blöcke müssen unmittelbar vor ihrem Abbau erneut geprüft werden. Vorschau-Caches sind keine Autorisierung.

## 11. Umsetzung in überprüfbaren Etappen

| Etappe | Lieferumfang | Abnahme |
|---|---|---|
| 1: Geometrischer Kern | Konfigurationsmodell, Validierung, alle Generatoren, Anker und Transformationen | Reine automatisierte Geometrietests bestehen |
| 2: Bedienbarer Editor | Screen, echte Zahlenfelder, 3D-Vorschau, Kamera, alle parametrischen Formen | Freie Zahlen und Formwechsel sind visuell korrekt; noch kein Weltabbau nötig |
| 3: Custom und Vorlagen | Ebeneneditor, Undo/Redo, Umwandlung aus Vorlagen, sichere Größenänderung | Eigene Formen sind zeichnbar, speicherbar und wiederherstellbar |
| 4: Werkzeug und Synchronisierung | Werkzeug-Tag (Abschnitt 2a), Data Component an normalen Werkzeugen, Editor-Sitzung, bestätigte Serverübernahme, Weltvorschau | Zwei Werkzeuge bleiben unabhängig; Werkzeuge ohne Form verhalten sich wie Vanilla; Server lehnt manipulierte Werte ab |
| 5: Sicherer Abbau | Erfolgreicher Startblock als Auslöser, Spieler-Abbaupfad, Schutz, Verschleiß, Budgets | Ausgewählte erlaubte Blöcke werden genau einmal verarbeitet |
| 6: Integration und Freigabe | Dedicated Server, Schutz-/Modpacktests, GUI-Skalierung, Shader-/Rendererprüfung | Dokumentierte Abnahmematrix ohne ungeklärte Fehler |

Neue fehleranfällige Regeln zuerst durch einen reproduzierbaren Test absichern. Bei einem gefundenen Fehler soll der Regressionstest vor dem Fix fehlschlagen. Weder vorhandene Vorschau-Wiederverwendbarkeit noch Kompatibilität mit einem bestimmten Shader oder Claim-Mod ohne tatsächlichen Test als erledigt markieren.

## 12. Verbindliche Abnahmetests

### Eingaben und Geometrie

- Breite 7, Höhe 5, Tiefe 2 lässt sich vollständig über Tastatur eingeben; Vorschau und Generator liefern 70 Positionen.
- 3 × 3 × 1 liefert 9 Positionen. Ein Plus 3 × 3 × 1 mit Armstärke 1 liefert genau 5 und nicht 9.
- Plus 5 × 5 × 2 liefert 18 Positionen. Stufenform und Treppengang besitzen getrennte, getestete Masken.
- Der beschriebene Treppengang 3 × 3 mit sechs Ein-Block-Stufen hat die Ausdehnung 3 × 8 × 6 und 54 Positionen.
- Gerade Größen, alle sechs Trefferseiten, vier Vierteldrehungen und zweimaliges Spiegeln sind deterministisch und erhalten die erwartete Form.
- Feld leeren, 0, negative Werte, Dezimalzahlen, überlange Eingaben und abgeleitete Übergrößen verursachen weder Crash noch stilles Speichern.

### Editor und Speicherung

- Kamera drehen verändert keine Formdaten. Form drehen verändert Maske und Anker konsistent.
- In Custom deaktivierte Zellen erscheinen weder in der Vorschau noch in der geometrischen Serverauswahl.
- Größenreduktion mit Datenverlust fordert Bestätigung; Undo stellt die vorherige Auswahl wieder her.
- GUI-Skalierung und Fensteränderung erhalten den Entwurf. Abbrechen erhält die vorherige aktive Konfiguration.
- Neustart, Ablegen/Aufheben und Übergabe erhalten die aktive Form am Werkzeug; zwei Werkzeuge bleiben unabhängig.
- Ungültige Revision, Werkzeugwechsel und zu große Netzwerkdaten werden abgelehnt.

### Abbau und Multiplayer

- Ein abgelehnter oder erfolgloser Startblock löst keinen Zusatzabbau aus.
- Geschützte Positionen, Maschinen, unpassende Werkzeugstufen und nicht geladene Chunks bleiben unangetastet.
- Startblock und Zusatzblöcke erzeugen jeweils höchstens einmal Loot, Erfahrung und Verschleiß.
- Zusatzabbau löst keine rekursive Flächenkaskade aus.
- Werkzeugbruch und Spieler-/Dimensionswechsel beenden laufende Vorgänge sicher.
- Zwei Spieler und mehrere gleichzeitige Vorgänge respektieren auch das globale Tickbudget.
- Editor, Weltvorschau und Server berechnen dieselbe geometrische Auswahl; erlaubte Abweichungen des tatsächlichen Abbaus werden als Gründe gemeldet.

Für Weltzustände und Abbauverhalten Game Tests einsetzen; NeoForge dokumentiert hierfür die Zielversion einschließlich Game-Test-Server [S5]. Die isolierte Geometrie mit normalen Unit-Tests prüfen. Rendering, Textfokus, GUI-Skalierung und Shaderdarstellung zusätzlich manuell abnehmen; ein erfolgreicher Build ersetzt diese Prüfungen nicht.

## 13. Maßstab für „fertig“

Ein Spieler kann ohne Config-Datei eine Form auswählen, eigene Zahlen eintippen, das Ergebnis als echte Blockform drehen und betrachten, daraus eine eigene Maske machen, sie speichern und am Weltziel sehen. Anschließend verarbeitet das Werkzeug nur die serverseitig erlaubten Blöcke dieser Auswahl.

Die visuelle Einstellbarkeit einschließlich direkter Zahleneingabe, Plus, Treppen und Custom-Ebenen ist der Kern des Projekts, kein optionaler Nachtrag.

## Technische Quellen

Die folgenden Quellen belegen die verwendeten NeoForge-Bausteine. Die konkrete Mod-Oberfläche, Limits, Formen und Arbeitsabläufe dieses Dokuments sind eigene Designvorschläge.

- [S1] NeoForge 1.21–1.21.1: Screens — https://docs.neoforged.net/docs/1.21.1/gui/screens/
- [S2] NeoForge 1.21–1.21.1: Data Components — https://docs.neoforged.net/docs/1.21.1/items/datacomponents/
- [S3] NeoForge 1.21–1.21.1: Registering Payloads — https://docs.neoforged.net/docs/1.21.1/networking/payload/
- [S4] NeoForge, Branch 1.21.1: BlockEvent.java — https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/event/level/BlockEvent.java
- [S5] NeoForge 1.21–1.21.1: Game Tests — https://docs.neoforged.net/docs/1.21.1/misc/gametest/
