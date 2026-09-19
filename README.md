<p align="center">
  <strong>English</strong> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a>
</p>

<p align="center">
  <img src="./docs/icon/courses_icon.svg" width="120" alt="courses logo" />
</p>

<h1 align="center">courses</h1>

<p align="center">
  A simple, fast, <strong>offline</strong> Android shopping list,<br />
  with optional Home Assistant synchronization.
</p>

<p align="center">
  <a href="https://github.com/sargo22341-prog/courses/releases/latest"><img src="./docs/images/badges/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sargo22341-prog/courses"><img src="./docs/images/badges/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Disclaimer

> [!WARNING]
> This application was **developed with the help of artificial intelligence**.
> The code, tests and documentation were largely produced by AI and then checked by automated
> tests, but not everything has been reviewed line by line or validated in every real-world
> situation. Use it knowingly and see the [known limitations](docs/limites-connues.md).

## Overview

**courses** is built on a simple idea: *open → type → select → check off*. The app opens
straight on the list, with no account, no setup and no loading screen. Everything is stored on
the phone: you can view and edit your lists at the back of a store, with no network.

To share a list with your family or show it on a dashboard, it can sync with the `todo` lists of
a self-hosted **Home Assistant** instance. This is entirely optional.

Main features:

- **multiple lists**, with quantities and units (`12`, `1.5 kg`);
- **instant, offline autocomplete**, tolerant of typos, that learns the products you buy most
  often;
- **history** of the most frequently added products, offered when you tap the empty search field
  (can be disabled and cleared in the settings);
- **fully built-in food catalog**: a hand-written base catalog plus several thousand products
  generated from the [OpenFoodFacts](https://world.openfoodfacts.org/) taxonomy, shipped with the
  app — nothing is downloaded, nothing you type is sent anywhere;
- **sorting by aisle** (fruit and vegetables, bakery, dairy…), optional;
- **six languages**: French, English, German, Spanish, Italian, Portuguese;
- **Home Assistant (optional)**: two-way sync, in real time while the app is open, without losing
  changes made offline;
- light, dark or system theme, Material 3;
- **no account, no analytics, no dependency on Google Play services**: works on GrapheneOS.

### Preview

| First launch | Shopping list | Autocomplete |
| --- | --- | --- |
| ![Welcome screen and language choice](docs/images/accueil.png) | ![Shopping list](docs/images/liste.png) | ![Suggestions while typing](docs/images/autocompletion.png) |

| Sorting by aisle | Dark theme | Settings |
| --- | --- | --- |
| ![Items sorted by category](docs/images/categories.png) | ![List in dark theme](docs/images/sombre.png) | ![Settings](docs/images/reglages.png) |

Screenshots taken on an emulator with fictitious data.

## Installation

There is no release on an app store: the signed APK of each version is attached to the
repository's GitHub releases, or the app can be built from source.

Requirements: a recent Android Studio (bundled JDK 21), Android SDK 37, and a phone or emulator
running **Android 17 (API 37)** or later.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Details, checks and signed release: [Development](docs/developpement.md).

## Home Assistant in short

1. In Home Assistant: Profile → Security → Long-lived access tokens → create a token (the app can
   scan it as a QR code).
2. In the app: Settings → Home Assistant → address, token, **Test connection**.
3. For each list, choose: create it in Home Assistant, link it to an existing list, or keep it
   local.

The token is encrypted by the Android Keystore and is never shown again. Full guide:
[Home Assistant](docs/home-assistant.md).

## Privacy

- Lists, add history and preferences stay on the phone.
- No external service is contacted without a user action: the food catalog ships with the app.
- Home Assistant is only contacted if it has been configured; it is the only server the app can
  reach.

## Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin, coroutines, Flow |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Data | Room (single source of truth), DataStore |
| Injection | Hilt |
| Network | Retrofit, OkHttp (REST and WebSocket), Kotlin Serialization |
| Security | Android Keystore (AES-256-GCM) |
| QR scanning | CameraX + ZXing |
| Platform | Android 17 (API 37) minimum |

## Documentation

All documentation (in French) lives in [`docs/`](docs/README.md):

- [User interface](docs/interface.md) · [Offline operation](docs/hors-ligne.md) ·
  [Languages](docs/langues.md)
- [Catalog and autocomplete](docs/catalogue.md) · [Categories](docs/categories.md)
- [Home Assistant](docs/home-assistant.md) · [Synchronization](docs/synchronisation.md) ·
  [Conflict strategy](docs/conflits.md)
- [Architecture](docs/architecture.md) · [Architecture decisions (ADR)](docs/adr/README.md)
- [Security and privacy](docs/securite.md) · [Known limitations](docs/limites-connues.md)
- [Development](docs/developpement.md) · [Tests](docs/tests.md) ·
  [Signed release](docs/release.md)

Contribution rules (humans and agents): [`AGENTS.md`](AGENTS.md).

## License

Copyright © 2026 sargo.

courses is free software distributed under the **GNU General Public License v3.0**: you may
redistribute and modify it under its terms; any modified version you redistribute must remain
under the same license. Full text: [`LICENSE`](LICENSE).

The catalog data comes from [Open Food Facts](https://world.openfoodfacts.org) and remains under
the [ODbL](https://opendatacommons.org/licenses/odbl/1-0/) license. The files in
`app/src/main/assets/catalog/` are a derived database produced by `scripts/generate-catalog.py`.
