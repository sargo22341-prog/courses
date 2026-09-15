# AGENTS.md

Règles obligatoires pour toute session de travail sur **courses**. Ce fichier fait autorité ;
`README.md` décrit le fonctionnement détaillé (architecture, synchronisation, catalogue, conflits).
En cas de doute sur un comportement existant : lire le README et le code avant de modifier.

## 1. Produit — ce qui ne se négocie pas

- Application de courses **offline-first** : ouvrir → taper → sélectionner → cocher.
- Aucune fonction principale ne dépend du réseau : consulter, créer/renommer/supprimer une liste,
  ajouter/rechercher/cocher/décocher/supprimer un article, modifier une quantité, autocomplétion.
- Pas de compte, pas de configuration obligatoire, pas d'écran de chargement réseau, pas d'écran
  inutile, aucun analytics.
- Home Assistant est **facultatif** ; l'application doit fonctionner parfaitement sans.
- Le réseau sert uniquement à : synchroniser Home Assistant, mettre à jour le catalogue
  OpenFoodFacts, ou une action explicitement réseau demandée par l'utilisateur.
- Interface en français, anglais, allemand, espagnol, italien et portugais (langue de l'appareil
  par défaut, choix au premier lancement et dans les réglages), Material 3, thème clair
  chaud/calme, thème sombre sobre, mode système.
- Ne jamais afficher de stacktrace : toujours une phrase claire qui rappelle que les données
  locales restent disponibles.

## 2. Stack technique

- Kotlin uniquement, Android 17 (API 37) minimum, `compileSdk`/`targetSdk` 37, aucune
  rétrocompatibilité.
- Jetpack Compose exclusivement (Material 3), Navigation Compose avec routes typées.
- Coroutines, Flow/StateFlow, Kotlin Serialization.
- Room (seule source de vérité), DataStore pour les préférences, Hilt, Retrofit/OkHttp.
- Versions centralisées dans `gradle/libs.versions.toml` ; ne jamais écrire une version en dur
  dans un `build.gradle.kts`.
- **Pas de nouvelle dépendance sans nécessité démontrée** : vérifier d'abord si les API
  Android/Jetpack déjà présentes suffisent, et justifier l'ajout dans le rapport final.
- WorkManager n'est pas utilisé (choix documenté dans le README) ; ne l'introduire que si un besoin
  réel l'impose, en mettant le README à jour.
- **Aucune dépendance aux services Google Play** (GMS, ML Kit, Firebase, Play Integrity…) :
  l'application doit fonctionner à l'identique sur GrapheneOS et tout Android sans Google.
  Préférer AndroidX et des bibliothèques open source (ex. CameraX + ZXing pour les QR codes).
- Permissions d'exécution (caméra, `ACCESS_LOCAL_NETWORK` d'Android 17…) : via
  `core/permission`, demandées au moment où la fonctionnalité en a besoin, avec explication et
  accès aux paramètres après un refus définitif.
- Réseau : ne jamais retirer la confiance aux certificats CA installés par l'utilisateur ni ajouter
  de `domain-config` qui la supprimerait (Home Assistant auto-hébergé) ; garder
  `NetworkSecurityConfigTest` vert.
- Préférer les API modernes aux API dépréciées ; corriger tout avertissement de dépréciation
  introduit.

## 3. Architecture

Découpage **par fonctionnalité**, puis par couche. Structure de référence :

```
org.opensources.courses
├── CoursesApplication, AppInitializer, MainActivity, MainViewModel
├── navigation/                      routes typées + NavHost
├── core/
│   ├── common/  database/  designsystem/  model/  network/  security/  sync/
└── feature/<fonctionnalité>/
    ├── data/          Room, DataStore, Retrofit, implémentations des dépôts, modules Hilt
    ├── domain/        modèles, interfaces de dépôts, cas d'usage, règles pures
    └── presentation/  ViewModels, écrans, components/
```

Fonctionnalités existantes : `shopping`, `lists`, `catalog`, `homeassistant`, `settings`,
`onboarding`. Une nouvelle fonctionnalité = un nouveau package `feature/<nom>` avec seulement les
couches dont elle a besoin.

Règles de dépendance :

- `presentation → domain → data` : l'UI ne connaît que le domaine.
- Les composables ne font **ni** accès Room, **ni** appel réseau, **ni** logique métier ; ils
  reçoivent un état et émettent des événements.
- Les ViewModels n'utilisent que des interfaces du domaine et des cas d'usage ; ils exposent un
  `StateFlow` d'état immuable (`XxxUiState`).
- Le domaine ne dépend d'aucune classe Android (hors annotations) : il doit rester testable en
  JVM pur.
- Les détails Home Assistant ne sortent jamais de `feature/homeassistant` ; `core/sync` ne
  connaît que `RemoteSyncEngine`.
- `core/` ne contient que ce qui sert réellement à plusieurs fonctionnalités.

Flux obligatoire, jamais `UI → réseau → UI` :

```
UI → ViewModel → Repository → Room → Flow → UI
                     └─ SyncQueue (même transaction) → SyncCoordinator → moteur distant
```

## 4. Données, synchronisation, sécurité

- Toute modification d'une liste synchronisée écrit l'entité **et** sa `SyncOperationEntity`
  dans la **même transaction** Room. Une opération n'est supprimée qu'après confirmation distante.
- Stratégie de conflit : *last-write-wins, sauf qu'une modification locale non synchronisée n'est
  jamais écrasée* (`ConflictResolver`). Ne pas la modifier sans mettre à jour le code, les tests
  et le README.
- Les écritures issues du distant revérifient dans leur transaction l'absence d'opération locale
  en attente.
- Toute modification d'entité Room : incrémenter la version de `CoursesDatabase`, fournir une
  migration (ou `AutoMigration`), conserver le schéma exporté dans `app/schemas`, tester la
  migration. Jamais de `fallbackToDestructiveMigration` : les données utilisateur ne se perdent pas.
- Catalogue : jamais de requête OpenFoodFacts par frappe ; import hebdomadaire, nettoyé, stocké
  localement. Les habitudes (`product_usage`) restent séparées du catalogue.
- Secrets (token Home Assistant) : uniquement via `SecretStore` (Keystore). Jamais en clair dans
  DataStore/SharedPreferences, jamais dans un log, un `toString()`, un test ou un rapport.
- Ne pas envoyer de données personnelles (historique d'achats) à un service externe.

## 5. Qualité du code

- **Aucun fichier source ne dépasse 600 lignes** (Kotlin, Compose, tests compris). Découper dès
  ~400 lignes ; viser des fichiers de moins de 250 lignes.
- Fonctions courtes et spécialisées ; pas de God class, God ViewModel ou composable géant :
  extraire des composants dans `presentation/components/`.
- **Pas de dossier fourre-tout** (`utils/`, `helpers/`, `misc/`, `common/` générique, `managers/`) :
  chaque fichier va dans la fonctionnalité et la couche dont il relève.
- Un fichier = une responsabilité ; nommer selon le rôle (`XxxRepository`, `XxxUseCase`,
  `XxxViewModel`, `XxxScreen`, `XxxDao`, `XxxEntity`, `XxxDto`).
- Réutiliser l'existant avant de créer une abstraction ; pas d'abstraction prématurée, pas
  d'interface sans raison (test, frontière de couche ou implémentations multiples).
- Pas de duplication : factoriser dès la deuxième copie réelle.
- Supprimer code mort, imports inutilisés, `TODO` non traités, logs de debug et fichiers
  temporaires avant de terminer.
- Pas de mock silencieux ni de fonctionnalité factice : ce qui n'est pas terminé est documenté
  dans le README (« Limites connues ») et signalé dans le rapport.
- Pas de `!!` dans le code de production, pas de `GlobalScope`, pas de `runBlocking` hors tests,
  pas d'exception avalée sans raison écrite en commentaire ; toujours relancer
  `CancellationException`.
- Coroutines : `viewModelScope` dans les ViewModels, `@ApplicationScope` pour le travail qui
  survit à un écran, I/O bloquante sur `@IoDispatcher`.
- Compose : état remonté (state hoisting), écrans découpés en `XxxRoute` (branchement ViewModel)
  et `XxxScreen` (sans état, testable), `collectAsStateWithLifecycle`, clés stables dans les listes
  paresseuses, `stringResource` pour tout texte visible.
- Ressources : aucun texte visible en dur dans le code ; couleurs via le thème
  (`MaterialTheme.colorScheme`), jamais de couleur codée dans un écran sauf justification.
- Accessibilité : `contentDescription` pertinentes, zones tactiles d'au moins 48 dp, contraste
  suffisant dans les deux thèmes.
- Commentaires : expliquer le *pourquoi*, pas le *quoi* ; KDoc pour les règles métier non
  évidentes. Suivre le style et la densité de commentaires du code voisin.
- Ne pas modifier un comportement existant sans rapport avec la tâche. Toute modification
  d'architecture reste minimale et cohérente avec l'existant.

## 6. Tests

- Toute fonctionnalité métier ajoutée ou modifiée a des tests.
- Tests unitaires JVM (`src/test`) pour le domaine, les règles pures, les moteurs de sync et les
  clients réseau (MockWebServer). Utiliser des fakes en mémoire (`testing/`), pas de framework de
  mock supplémentaire.
- Tests instrumentés (`src/androidTest`) pour Room réel (requêtes, transactions, migrations), le
  fonctionnement hors ligne et les parcours UI Compose principaux.
- Les tests ne dépendent jamais du réseau réel.
- Un bug corrigé = un test qui le reproduit.

## 7. Vérifications avant de terminer une tâche

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

- Zéro erreur de compilation, zéro test en échec, aucun nouvel avertissement Kotlin/Compose.
- Vérifier la limite de 600 lignes sur les fichiers modifiés.
- Si Room ou le schéma change : migration écrite et testée.
- Si un comportement documenté change : mettre à jour `README.md`.
- Ne jamais annoncer comme vérifié ce qui ne l'a pas été (ex. synchronisation avec une vraie
  instance Home Assistant).

## 8. Téléphone et ADB

**ADB** : `C:\platform-tools\adb.exe`

- Avant tout test sur appareil : vérifier que l'appareil est visible (`adb devices`). Ne jamais
  supposer une connexion ni prétendre avoir testé si ADB ne le voit pas.
- Installer le build debug (`:app:installDebug`), lancer l'activité principale
  (`org.opensources.courses/.MainActivity`), consulter `logcat` filtré sur le package en cas de
  problème.
- Tests instrumentés sur le téléphone personnel : `installDebug installDebugAndroidTest` puis
  `adb shell am instrument -w org.opensources.courses.test/androidx.test.runner.AndroidJUnitRunner`.
  **Ne pas utiliser `connectedDebugAndroidTest`** : il désinstalle l'app et efface ses données.
- Ne pas effacer les données utilisateur sans autorisation, ne pas désinstaller inutilement.
- Si le réseau du téléphone est coupé pour un test (`svc wifi/data disable`), toujours le
  rétablir à la fin, même en cas d'échec.
- Jamais de secret dans les logs copiés.

## 9. Git et fichiers

- Modifier uniquement les fichiers liés à la tâche ; petits changements cohérents.
- Jamais `git reset --hard` ni suppression de travail existant pour résoudre un conflit.
- Pas de commit sans demande explicite.
- Ne pas versionner : binaires générés, `build/`, `local.properties`, secrets, keystores, fichiers
  temporaires, code généré massif inutilisé, police sans licence explicite.
- Fichiers temporaires de session : dans le scratchpad, jamais dans le dépôt.
- Release de production : uniquement via `scripts/release-to-phone.ps1` (procédure dans
  `docs/release.md`). La clé `courses.jks` reste sur un support USB, jamais dans le dépôt ; son mot
  de passe n'est jamais écrit ni demandé par l'agent.

## 10. Identité

- Nom public / `app_name` : **courses**
- `applicationId` et namespace : **`org.opensources.courses`** (ne jamais le changer sans demande)
- Icône : `docs/icon/courses_icon.svg` (source) et vector drawables `ic_launcher_*` ; aucun logo
  ou artwork d'une autre marque.

## 11. Rapport final

Rapport final : modifications, fichiers principaux, tests exécutés, résultats, vérifications non
effectuées, limites restantes, validation attendue.

Avant de donner le rapport final, tenter :
```powershell
kdeconnect-cli --device 9d3e0da7eb0e4cacb95ff4869f8f669b --ping-msg "text"
```
Ne pas tenir compte du retour de kdeconnect.

Dans le rapport final, ajouter un exemple de texte en français, copiable, pour un commit —
uniquement si des changements ont été apportés.
