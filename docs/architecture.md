# Architecture

[← Documentation](README.md)

## Technologies

| Besoin | Choix |
| --- | --- |
| Langage | Kotlin 2.4, coroutines, Flow / StateFlow |
| UI | Jetpack Compose, Material 3, Navigation Compose (routes typées) |
| Injection | Hilt |
| Base locale | Room 2.8 (schéma exporté dans `app/schemas`, migrations testées avec `room-testing`) |
| Préférences | DataStore Preferences |
| Réseau | Retrofit 3 + OkHttp 5 + Kotlin Serialization |
| Sécurité | Android Keystore (AES-256-GCM) |
| Scan QR du token | CameraX (AndroidX) + ZXing (open source), sans services Google Play |
| Build | AGP 9.4, Gradle 9.7, KSP |

Aucune autre bibliothèque : pas d'analytics, pas de WorkManager
([ADR 0004](adr/0004-pas-de-workmanager.md)), pas de bibliothèque d'images ni de police.
**Aucune dépendance aux services Google Play** : l'application fonctionne sur GrapheneOS et
tout Android sans Google ([ADR 0016](adr/0016-aucune-dependance-google-play.md)).

Versions centralisées dans `gradle/libs.versions.toml`. Android 17 (API 37) minimum,
`compileSdk` et `targetSdk` 37, aucune rétrocompatibilité. `applicationId` et namespace :
`org.opensources.courses`.

## Couches

Découpage **par fonctionnalité**, puis par couche
([ADR 0001](adr/0001-module-unique-decoupe-par-fonctionnalite.md)) :

```
Presentation (Compose + ViewModel)
        ↓
Domain (modèles, cas d'usage, interfaces de dépôts, règles pures)
        ↓
Data (Room, DataStore, Retrofit, implémentations des dépôts)
```

- L'UI ne lit **que** des `StateFlow` exposés par les ViewModels.
- Les ViewModels n'appellent que des interfaces du domaine (dépôts, cas d'usage).
- Aucun composable n'accède à Room, à Retrofit ou aux détails Home Assistant.
- La logique métier (classement des suggestions, conflits, fraîcheur du catalogue, fusion des
  doublons, catégories…) vit dans des classes pures du domaine, testées sans Android.
- Les détails Home Assistant ne sortent pas de `feature/homeassistant` : `core/sync` ne connaît
  que l'interface `RemoteSyncEngine`.

## Flux de données

Jamais `UI → réseau → UI` ([ADR 0002](adr/0002-room-seule-source-de-verite.md)) :

```
UI → ViewModel → Repository → Room → (Flow) → UI
                     │
                     └─ SyncQueue (même transaction Room)
                              ↓
                     SyncCoordinator → HomeAssistantSyncEngine → Home Assistant
```

Le détail de la file et du moteur est dans [Synchronisation](synchronisation.md).

## Structure des packages

```
org.opensources.courses
├── CoursesApplication.kt / AppInitializer.kt   démarrage (catalogue de base, fraîcheur, sync)
├── MainActivity.kt / MainViewModel.kt          thème + destination de départ
├── navigation/                                 routes typées, NavHost, transitions
├── core/
│   ├── common/        qualifiers coroutines, horloge
│   ├── database/      CoursesDatabase, TransactionRunner, module Room
│   ├── designsystem/  thème clair/sombre, barres système, composants de réglages
│   ├── model/         SyncStatus
│   ├── network/       OkHttp, JSON, User-Agent, ConnectivityObserver
│   ├── permission/    permissions d'exécution (explication, renvoi vers les paramètres)
│   ├── scanner/       scanner de QR code (CameraX + ZXing)
│   ├── security/      SecretStore chiffré par le Keystore
│   └── sync/          SyncQueue, SyncOperation, SyncCoordinator, RemoteSyncEngine
└── feature/
    ├── shopping/      articles : CRUD, ajout intelligent, rangement par catégorie, écran principal
    ├── lists/         listes : créer, renommer, supprimer, liste par défaut
    ├── catalog/       catalogue local, autocomplétion, catégories, import OpenFoodFacts
    ├── homeassistant/ configuration, client REST et WebSocket, liaison des listes, moteur de sync
    ├── settings/      écran des réglages et préférences (thème, masquage des achetés, rangement)
    ├── language/      langue de l'application (langue par application d'Android), sélecteur
    └── onboarding/    premier lancement (choix de la langue)
```

Chaque fonctionnalité contient `data/`, `domain/` et `presentation/` quand elle en a besoin.
Aucun fichier source ne dépasse 600 lignes.

## Portée des objets Hilt

- `@Singleton` pour ce qui porte un état, un verrou ou une ressource partagée : `SyncCoordinator`,
  `SyncQueue`, `HomeAssistantSyncEngine` (et le `RoomSyncLocalStore` qu'il utilise),
  `CatalogSyncManager`, `KeystoreSecretStore`, `AndroidConnectivityObserver`, la langue, les
  clients réseau et les DataStore.
- Les dépôts et cas d'usage sans état restent non scopés : une instance par point d'injection ne
  coûte rien et ne peut rien désynchroniser.
- Les ViewModels n'appellent jamais de callback de navigation : ils exposent un événement dans
  leur état (liste créée, sortie de l'accueil) et l'écran navigue dans un `LaunchedEffect`.

## Base Room

- `CoursesDatabase` est la **seule source de vérité** de l'interface.
- Schéma exporté dans `app/schemas`, version 4 (2 et 3 par `AutoMigration` :
  `catalog_products.groceryCategory` ; `shopping_lists.importedFromRemote`,
  `shopping_lists.remoteName` et table `ha_ignored_lists`. 4 par `CoursesDatabaseMigrations` :
  suppression sur place des colonnes jamais lues).
- Toute évolution du schéma incrémente la version et fournit une migration testée contre
  `app/schemas` (`CoursesDatabaseMigrationTest`). Jamais de migration destructive
  ([ADR 0020](adr/0020-migrations-room-sans-perte.md)).

| Table | Contenu |
| --- | --- |
| `shopping_lists` | listes, liaison Home Assistant, liste importée et dernier nom distant appliqué |
| `shopping_items` | articles, quantité, état coché, produit associé, champs de synchronisation |
| `sync_operations` | file des opérations locales en attente d'envoi |
| `catalog_products`, `catalog_aliases` | catalogue alimentaire (base + OpenFoodFacts + personnalisés) |
| `product_usage` | habitudes d'ajout, séparées du catalogue |
| `ha_tracked_lists`, `ha_ignored_lists` | listes créées par l'application / listes à ne plus importer |

Les préférences (thème, réglages de la liste, état du catalogue, configuration Home Assistant)
sont dans DataStore ; le token Home Assistant est chiffré ([Sécurité](securite.md)).
