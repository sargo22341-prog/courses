<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.es.md">Español</a> ·
  <strong>Italiano</strong> ·
  <a href="README.pt.md">Português</a>
</p>

<p align="center">
  <img src="./docs/icon/courses_icon.svg" width="120" alt="Logo di courses" />
</p>

<h1 align="center">courses</h1>

<p align="center">
  Una lista della spesa Android semplice, veloce e <strong>offline</strong>,<br />
  con sincronizzazione facoltativa con Home Assistant.
</p>

<p align="center">
  <a href="https://github.com/sargo22341-prog/courses/releases/latest"><img src="./docs/images/badges/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sargo22341-prog/courses"><img src="./docs/images/badges/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Avvertenza

> [!WARNING]
> Questa applicazione è stata **sviluppata con l'aiuto dell'intelligenza artificiale**.
> Il codice, i test e la documentazione sono stati prodotti in gran parte dall'IA e poi verificati
> da test automatici, ma non tutto è stato riletto riga per riga né convalidato in ogni situazione
> reale. Usala con consapevolezza e consulta i [limiti noti](docs/limites-connues.md).

## Presentazione

**courses** nasce da un'idea semplice: *apri → digita → seleziona → spunta*. L'app si apre
direttamente sulla lista, senza account, senza configurazione e senza schermate di caricamento.
Tutto è salvato sul telefono: puoi consultare e modificare le tue liste in fondo a un negozio,
senza rete.

Per condividere una lista con la famiglia o mostrarla su una dashboard, può sincronizzarsi con le
liste `todo` di un'istanza **Home Assistant** self-hosted. È del tutto facoltativo.

Funzioni principali:

- **più liste**, con quantità e unità (`12`, `1,5 kg`);
- **completamento automatico istantaneo e offline**, tollerante agli errori di battitura, che
  impara i prodotti acquistati più spesso;
- **cronologia** dei prodotti aggiunti più spesso, proposta toccando il campo di ricerca vuoto
  (disattivabile e cancellabile nelle impostazioni);
- **catalogo alimentare interamente integrato**: un catalogo di base scritto a mano e diverse
  migliaia di prodotti generati dalla tassonomia di
  [OpenFoodFacts](https://world.openfoodfacts.org/), inclusi nell'app — non viene scaricato nulla
  e nulla di ciò che digiti viene inviato;
- **ordinamento per reparto** (frutta e verdura, panetteria, latticini…), facoltativo;
- **sei lingue**: francese, inglese, tedesco, spagnolo, italiano, portoghese;
- **Home Assistant (facoltativo)**: sincronizzazione bidirezionale, in tempo reale quando l'app è
  aperta, senza perdere le modifiche fatte offline;
- tema chiaro, scuro o di sistema, Material 3;
- **nessun account, nessuna analisi, nessuna dipendenza dai servizi Google Play**: funziona su
  GrapheneOS.

### Anteprima

| Primo avvio | Lista della spesa | Completamento automatico |
| --- | --- | --- |
| ![Schermata di benvenuto e scelta della lingua](docs/images/accueil.png) | ![Lista della spesa](docs/images/liste.png) | ![Suggerimenti durante la digitazione](docs/images/autocompletion.png) |

| Ordinamento per reparto | Tema scuro | Impostazioni |
| --- | --- | --- |
| ![Articoli ordinati per categoria](docs/images/categories.png) | ![Lista in tema scuro](docs/images/sombre.png) | ![Impostazioni](docs/images/reglages.png) |

Schermate acquisite su un emulatore con dati fittizi.

## Installazione

Non esiste una versione pubblicata su uno store: l'APK firmato di ogni versione è allegato alle
release GitHub del repository, oppure l'app può essere compilata dai sorgenti.

Requisiti: Android Studio recente (JDK 21 incluso), SDK Android 37 e un telefono o emulatore con
almeno **Android 17 (API 37)**.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Dettagli, verifiche e release firmata: [Sviluppo](docs/developpement.md).

## Home Assistant in breve

1. In Home Assistant: Profilo → Sicurezza → Token di accesso a lunga durata → crea un token
   (l'app può scansionarlo come codice QR).
2. Nell'app: Impostazioni → Home Assistant → indirizzo, token, **Verifica connessione**.
3. Per ogni lista scegli: crearla in Home Assistant, collegarla a una lista esistente o tenerla
   locale.

Il token è cifrato dal Keystore di Android e non viene mai più mostrato. Guida completa:
[Home Assistant](docs/home-assistant.md).

## Privacy

- Le liste, la cronologia e le preferenze restano sul telefono.
- Nessun servizio esterno viene contattato senza un'azione dell'utente: il catalogo alimentare è
  incluso nell'app.
- Home Assistant viene contattato solo se è stato configurato; è l'unico server che l'app sa
  raggiungere.

## Tecnologie

| Livello | Tecnologia |
| --- | --- |
| Linguaggio | Kotlin, coroutine, Flow |
| Interfaccia | Jetpack Compose, Material 3, Navigation Compose |
| Dati | Room (unica fonte di verità), DataStore |
| Iniezione | Hilt |
| Rete | Retrofit, OkHttp (REST e WebSocket), Kotlin Serialization |
| Sicurezza | Android Keystore (AES-256-GCM) |
| Scansione QR | CameraX + ZXing |
| Piattaforma | almeno Android 17 (API 37) |

## Documentazione

Tutta la documentazione (in francese) si trova in [`docs/`](docs/README.md):

- [Interfaccia](docs/interface.md) · [Funzionamento offline](docs/hors-ligne.md) ·
  [Lingue](docs/langues.md)
- [Catalogo e completamento automatico](docs/catalogue.md) · [Categorie](docs/categories.md)
- [Home Assistant](docs/home-assistant.md) · [Sincronizzazione](docs/synchronisation.md) ·
  [Strategia dei conflitti](docs/conflits.md)
- [Architettura](docs/architecture.md) · [Decisioni di architettura (ADR)](docs/adr/README.md)
- [Sicurezza e privacy](docs/securite.md) · [Limiti noti](docs/limites-connues.md)
- [Sviluppo](docs/developpement.md) · [Test](docs/tests.md) ·
  [Release firmata](docs/release.md)

Regole di contribuzione (persone e agenti): [`AGENTS.md`](AGENTS.md).

## Licenza

Copyright © 2026 sargo.

courses è software libero distribuito con licenza **GNU General Public License v3.0**: puoi
ridistribuirlo e modificarlo secondo i suoi termini; ogni versione modificata e ridistribuita deve
restare sotto la stessa licenza. Testo completo: [`LICENSE`](LICENSE).

I dati del catalogo provengono da [Open Food Facts](https://world.openfoodfacts.org) e restano
sotto licenza [ODbL](https://opendatacommons.org/licenses/odbl/1-0/). I file in
`app/src/main/assets/catalog/` sono un database derivato prodotto da `scripts/generate-catalog.py`.
