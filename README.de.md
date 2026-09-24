<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.fr.md">Français</a> ·
  <strong>Deutsch</strong> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a>
</p>

<p align="center">
  <img src="./docs/icon/courses_icon.svg" width="120" alt="courses-Logo" />
</p>

<h1 align="center">courses</h1>

<p align="center">
  Eine einfache, schnelle Android-Einkaufsliste, die <strong>offline</strong> funktioniert,<br />
  mit optionaler Synchronisierung mit Home Assistant.
</p>

<p align="center">
  <a href="https://github.com/sargo22341-prog/courses/releases/latest"><img src="./docs/images/badges/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sargo22341-prog/courses"><img src="./docs/images/badges/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Hinweis

> [!WARNING]
> Diese Anwendung wurde **mit Hilfe künstlicher Intelligenz entwickelt**.
> Code, Tests und Dokumentation wurden größtenteils von einer KI erstellt und anschließend durch
> automatisierte Tests geprüft, aber nicht alles wurde Zeile für Zeile gelesen oder in allen
> realen Situationen validiert. Nutzung auf eigene Verantwortung; siehe auch die
> [bekannten Einschränkungen](docs/limites-connues.md).

## Überblick

**courses** folgt einer einfachen Idee: *öffnen → tippen → auswählen → abhaken*. Die App startet
direkt mit der Liste, ohne Konto, ohne Einrichtung und ohne Ladebildschirm. Alles wird auf dem
Telefon gespeichert: Listen lassen sich auch im hintersten Winkel eines Supermarkts ohne Netz
ansehen und bearbeiten.

Um eine Liste mit der Familie zu teilen oder auf einem Dashboard anzuzeigen, kann sie mit den
`todo`-Listen einer selbst gehosteten **Home Assistant**-Instanz synchronisiert werden. Das ist
vollständig optional.

Wichtigste Funktionen:

- **mehrere Listen**, mit Mengen und Einheiten (`12`, `1,5 kg`);
- **sofortige Offline-Autovervollständigung**, tolerant gegenüber Tippfehlern, die die am
  häufigsten gekauften Produkte lernt;
- **Verlauf** der am häufigsten hinzugefügten Produkte, angeboten beim Antippen des leeren
  Suchfelds (in den Einstellungen abschaltbar und löschbar);
- **vollständig integrierter Lebensmittelkatalog**: ein handgeschriebener Basiskatalog und
  mehrere tausend Produkte, erzeugt aus der Taxonomie von
  [OpenFoodFacts](https://world.openfoodfacts.org/), mit der App ausgeliefert — nichts wird
  heruntergeladen, nichts Eingetipptes wird gesendet;
- **Sortierung nach Abteilung** (Obst und Gemüse, Bäckerei, Milchprodukte…), optional;
- **sechs Sprachen**: Französisch, Englisch, Deutsch, Spanisch, Italienisch, Portugiesisch;
- **Home Assistant (optional)**: Synchronisierung in beide Richtungen, in Echtzeit bei geöffneter
  App, ohne Verlust offline vorgenommener Änderungen, auch mit **Mealie**-Einkaufslisten (Mengen
  und Produkte aus dem Mealie-Text gelesen, ohne die Liste in Mealie zu beschädigen);
- helles, dunkles oder System-Design, Material 3;
- **kein Konto, keine Analyse, keine Abhängigkeit von Google-Play-Diensten**: funktioniert unter
  GrapheneOS.

### Vorschau

| Erster Start | Einkaufsliste | Autovervollständigung |
| --- | --- | --- |
| ![Startbildschirm und Sprachauswahl](docs/images/accueil.png) | ![Einkaufsliste](docs/images/liste.png) | ![Vorschläge während der Eingabe](docs/images/autocompletion.png) |

| Sortierung nach Abteilung | Dunkles Design | Einstellungen |
| --- | --- | --- |
| ![Nach Kategorie sortierte Artikel](docs/images/categories.png) | ![Liste im dunklen Design](docs/images/sombre.png) | ![Einstellungen](docs/images/reglages.png) |

Screenshots auf einem Emulator mit fiktiven Daten.

## Installation

Es gibt keine Veröffentlichung in einem App-Store: Die signierte APK jeder Version liegt den
GitHub-Releases des Repositorys bei, oder die App wird aus den Quellen gebaut.

Voraussetzungen: aktuelles Android Studio (mitgeliefertes JDK 21), Android SDK 37 und ein Telefon
oder Emulator mit mindestens **Android 17 (API 37)**.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Details, Prüfungen und signiertes Release: [Entwicklung](docs/developpement.md).

## Home Assistant in Kürze

1. In Home Assistant: Profil → Sicherheit → Langlebige Zugangstoken → Token erstellen (die App
   kann ihn als QR-Code scannen).
2. In der App: Einstellungen → Home Assistant → Adresse, Token, **Verbindung testen**.
3. Für jede Liste wählen: in Home Assistant anlegen, mit einer bestehenden Liste verknüpfen oder
   lokal behalten. **Meine Listen → Neue Liste → Home-Assistant-Liste importieren** holt eine
   bestehende Liste (zum Beispiel eine Mealie-Liste) verknüpft in die App.

Der Token wird vom Android Keystore verschlüsselt und nie wieder angezeigt. Vollständige
Anleitung: [Home Assistant](docs/home-assistant.md).

## Datenschutz

- Listen, Verlauf und Einstellungen bleiben auf dem Telefon.
- Ohne Aktion des Nutzers wird kein externer Dienst kontaktiert: Der Lebensmittelkatalog wird mit
  der App ausgeliefert.
- Home Assistant wird nur kontaktiert, wenn es eingerichtet wurde; es ist der einzige Server, den
  die App erreichen kann.

## Technologie

| Schicht | Technologie |
| --- | --- |
| Sprache | Kotlin, Coroutines, Flow |
| Oberfläche | Jetpack Compose, Material 3, Navigation Compose |
| Daten | Room (einzige Quelle der Wahrheit), DataStore |
| Injektion | Hilt |
| Netzwerk | Retrofit, OkHttp (REST und WebSocket), Kotlin Serialization |
| Sicherheit | Android Keystore (AES-256-GCM) |
| QR-Scan | CameraX + ZXing |
| Plattform | mindestens Android 17 (API 37) |

## Dokumentation

Die gesamte Dokumentation (auf Französisch) befindet sich in [`docs/`](docs/README.md):

- [Oberfläche](docs/interface.md) · [Offline-Betrieb](docs/hors-ligne.md) ·
  [Sprachen](docs/langues.md)
- [Katalog und Autovervollständigung](docs/catalogue.md) · [Kategorien](docs/categories.md)
- [Home Assistant](docs/home-assistant.md) · [Synchronisierung](docs/synchronisation.md) ·
  [Konfliktstrategie](docs/conflits.md)
- [Architektur](docs/architecture.md) · [Architekturentscheidungen (ADR)](docs/adr/README.md)
- [Sicherheit und Datenschutz](docs/securite.md) · [Bekannte Einschränkungen](docs/limites-connues.md)
- [Entwicklung](docs/developpement.md) · [Tests](docs/tests.md) ·
  [Signiertes Release](docs/release.md)

Beitragsregeln (Menschen und Agenten): [`AGENTS.md`](AGENTS.md).

## Lizenz

Copyright © 2026 sargo.

courses ist freie Software unter der **GNU General Public License v3.0**: Sie darf gemäß deren
Bedingungen weitergegeben und verändert werden; jede veränderte und weitergegebene Version muss
unter derselben Lizenz bleiben. Volltext: [`LICENSE`](LICENSE).

Die Katalogdaten stammen von [Open Food Facts](https://world.openfoodfacts.org) und stehen
weiterhin unter der [ODbL](https://opendatacommons.org/licenses/odbl/1-0/). Die Dateien in
`app/src/main/assets/catalog/` sind eine abgeleitete Datenbank, erzeugt durch
`scripts/generate-catalog.py`.
