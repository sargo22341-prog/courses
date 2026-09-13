# courses

Application Android de liste de courses **offline-first**, avec autocomplétion locale et
synchronisation **facultative** avec Home Assistant.

> Ouvrir → taper → sélectionner → cocher. Pas de compte, pas de connexion obligatoire, pas de
> configuration obligatoire, pas de chargement réseau pour afficher la liste.

- `applicationId` : `org.opensources.courses`
- Android 17 (API 37) minimum, aucune rétrocompatibilité.
- Interface en français.

## Sommaire

1. [Technologies](#technologies)
2. [Architecture](#architecture)
3. [Structure des packages](#structure-des-packages)
4. [Fonctionnement hors ligne](#fonctionnement-hors-ligne)
5. [Catalogue OpenFoodFacts et autocomplétion](#catalogue-openfoodfacts-et-autocomplétion)
6. [Home Assistant](#home-assistant)
7. [Synchronisation et file d'opérations](#synchronisation-et-file-dopérations)
8. [Stratégie de conflit](#stratégie-de-conflit)
9. [Sécurité et confidentialité](#sécurité-et-confidentialité)
10. [Lancer le projet](#lancer-le-projet)
11. [Tests](#tests)
12. [Choix d'architecture](#choix-darchitecture)
13. [Limites connues](#limites-connues)

## Technologies

| Besoin | Choix |
| --- | --- |
| Langage | Kotlin 2.4, coroutines, Flow / StateFlow |
| UI | Jetpack Compose, Material 3, Navigation Compose (routes typées) |
| Injection | Hilt |
| Base locale | Room 2.8 (schéma exporté dans `app/schemas`) |
| Préférences | DataStore Preferences |
| Réseau | Retrofit 3 + OkHttp 5 + Kotlin Serialization |
| Sécurité | Android Keystore (AES-256-GCM) |
| Scan QR du token | CameraX (AndroidX) + ZXing (open source), sans services Google Play |
| Build | AGP 9.4, Gradle 9.7, KSP |

Aucune autre bibliothèque : pas d'analytics, pas de WorkManager (voir
[Choix d'architecture](#choix-darchitecture)), pas de bibliothèque d'images ni de police.
**Aucune dépendance aux services Google Play** : l'application fonctionne sur GrapheneOS et
tout Android sans Google.

## Architecture

Découpage **par fonctionnalité**, puis par couche :

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
  doublons…) vit dans des classes pures du domaine, testées sans Android.

Flux de données, jamais `UI → réseau → UI` :

```
UI → ViewModel → Repository → Room → (Flow) → UI
                     │
                     └─ SyncQueue (même transaction Room)
                              ↓
                     SyncCoordinator → HomeAssistantSyncEngine → Home Assistant
```

## Structure des packages

```
org.opensources.courses
├── CoursesApplication.kt / AppInitializer.kt   démarrage (catalogue de base, fraîcheur, sync)
├── MainActivity.kt / MainViewModel.kt          thème + destination de départ
├── navigation/                                 routes typées et NavHost
├── core/
│   ├── common/        qualifiers coroutines, horloge
│   ├── database/      CoursesDatabase, TransactionRunner, module Room
│   ├── designsystem/  thème clair/sombre, composants de réglages
│   ├── model/         SyncStatus
│   ├── network/       OkHttp, JSON, User-Agent, ConnectivityObserver
│   ├── security/      SecretStore chiffré par le Keystore
│   └── sync/          SyncQueue, SyncOperation, SyncCoordinator, RemoteSyncEngine
└── feature/
    ├── shopping/      articles : CRUD, ajout intelligent, écran principal
    ├── lists/         listes : créer, renommer, supprimer, liste par défaut
    ├── catalog/       catalogue local, autocomplétion, import OpenFoodFacts
    ├── homeassistant/ configuration, client REST, liaison des listes, moteur de sync
    ├── settings/      préférences (thème, masquage des achetés)
    └── onboarding/    premier lancement
```

Chaque fonctionnalité contient `data/`, `domain/` et `presentation/` quand elle en a besoin.
Aucun fichier source ne dépasse 600 lignes (le plus long en fait environ 230).

## Fonctionnement hors ligne

Room est la **seule source de vérité** pour l'interface. Sans aucun réseau, on peut : ouvrir
l'application, consulter, créer, renommer et supprimer des listes, ajouter, rechercher, cocher,
décocher, modifier la quantité et supprimer des articles, et utiliser l'autocomplétion.

- Au premier lancement, l'écran d'accueil propose **Commencer** (et, facultativement,
  **Connecter Home Assistant**). La liste « Courses » est créée et ouverte immédiatement.
- Un **catalogue de base** (266 produits et variantes courants en français,
  `assets/catalog/seed_fr.json`)
  est importé dans Room au premier démarrage : l'autocomplétion fonctionne avant tout
  téléchargement.
- L'état réseau (`ConnectivityObserver`) est affiché discrètement sous le titre de la liste :
  `Hors connexion`, et, si Home Assistant est activé, `Synchronisé`, `Synchronisation…` ou
  `Synchronisation impossible`, avec le nombre de modifications en attente.
- Aucune erreur réseau n'est bloquante et aucune trace technique n'est affichée : seulement des
  phrases comme « Impossible de contacter Home Assistant. Votre liste locale reste disponible. »

## Catalogue OpenFoodFacts et autocomplétion

### Source et import

- Source : la taxonomie publique des **catégories** OpenFoodFacts
  (`https://static.openfoodfacts.org/data/taxonomies/categories.json`, ~4,6 Mo, ~1,8 Mo compressé,
  licence ODbL). C'est un fichier statique servi par CDN : **aucun appel API par frappe**, aucune
  donnée personnelle envoyée (seulement un `User-Agent` identifiant l'application et un `ETag`).
- Pourquoi les catégories plutôt que les produits : la base produits complète pèse plusieurs Go et
  contient surtout des références de marque. Les catégories (`Laits demi-écrémés`,
  `Tomates cerise`…) correspondent à ce qu'on écrit sur une liste de courses.
- Nettoyage (`TaxonomyCatalogMapper`) sur les ~15 000 entrées : nom français obligatoire ;
  exclusion des appellations protégées (AOP/IGP) et des entrées liées à une origine ; au plus
  4 mots et 40 caractères ; aucun chiffre ; dédoublonnage sur le nom normalisé en gardant l'entrée
  la plus générique. Il reste quelques milliers de produits : la base reste petite.
- Stockage : `catalog_products` (nom, nom normalisé indexé, catégorie, parent, source, score de
  base, version d'import), `catalog_aliases`, et `product_usage` (habitudes, **séparées** du
  catalogue pour ne jamais être effacées par une mise à jour).

### Mise à jour hebdomadaire, sans backend

`CatalogSyncManager` + `CatalogFreshnessPolicy` :

```
ouverture de l'app → date du dernier téléchargement (DataStore)
   → plus de 7 jours ? → réseau disponible ? → téléchargement (If-None-Match: ETag)
        304 → seule la date est mise à jour
        200 → import transactionnel (upsert + suppression des lignes d'une ancienne version)
```

Si le réseau est absent à l'ouverture, la vérification a lieu dès qu'il revient tant que
l'application est ouverte. Dans **Réglages → Catalogue alimentaire**, « ↻ Synchroniser
maintenant » force un téléchargement complet (sans `ETag`) et affiche `Synchronisation…`, puis
`✓ Catalogue mis à jour` ou un message clair (hors connexion, échec). En cas d'échec, le catalogue
existant reste intact.

### Recherche et classement

`SearchSuggestionsUseCase` (100 % local) :

1. Normalisation (`TextNormalizer`) : minuscules, sans accents, `œ → oe`, ponctuation → espaces.
2. Présélection SQL : nom ou alias contenant la saisie.
3. Classement (`SuggestionRanker`), par paliers de 1 000 points que les bonus ne peuvent pas
   franchir : **exact > préfixe > début de mot > partiel > approximatif**. À palier égal :
   fréquence d'utilisation (+20 par ajout, plafonné), récence (+150 sur 3 jours, +100 sur
   14 jours, +40 sur 60 jours), score de base du catalogue, puis nom le plus court.
4. Si les résultats sont insuffisants : recherche tolérante aux fautes (`FuzzyMatcher`, distance
   d'édition avec transpositions sur les préfixes de mots : `lati → Lait`, `tomatos → Tomates`).

Si rien ne correspond exactement, `+ Ajouter « … »` crée un **produit personnalisé** local, proposé
ensuite comme les autres. Chaque ajout alimente les statistiques d'usage. Ajouter un produit déjà
présent incrémente sa quantité ; ajouter un produit déjà acheté le remet « à acheter ».

## Home Assistant

Entièrement **facultatif** : l'application fonctionne sans. Écran **Réglages → Home Assistant** :

- activer / désactiver la synchronisation ;
- adresse (`http://homeassistant.local:8123`, le schéma est ajouté si absent) ;
- token d'accès longue durée (stocké chiffré, jamais réaffiché), saisi à la main ou **scanné** :
  Home Assistant affiche le token en QR code (Profil → Sécurité → Jetons d'accès longue durée →
  Générer un QR code). « Scanner le QR code du token » ouvre un scanner intégré (CameraX +
  ZXing, sans services Google) qui demande la permission Appareil photo au premier usage ; les
  images sont analysées en mémoire, jamais enregistrées ni envoyées. Seul un token valide (JWT)
  est accepté, puis « Enregistrer » le stocke ;
- tester la connexion ;
- mode d'affichage des listes : **Toutes les listes** ou **Uniquement les listes créées par cette
  application** ;
- pour chaque liste locale : lier à une liste `todo.*` existante, **créer** la liste dans Home
  Assistant (intégration *Local To-do*, créée par l'application et mémorisée dans
  `ha_tracked_lists`), ou ne pas synchroniser ;
- synchronisation automatique et « Synchroniser maintenant ».

API utilisées (REST) : `GET /api/`, `GET /api/states`,
`POST /api/services/todo/get_items?return_response`, `todo.add_item`, `todo.update_item`,
`todo.remove_item`, `POST /api/config/config_entries/flow` (création *Local To-do*) et
`DELETE /api/config/config_entries/entry/{id}`.

Quantités : Home Assistant n'a pas de champ quantité. Pour les listes qui acceptent une
description (Local To-do), la quantité y est écrite (`2`, `1,5 kg`) ; une quantité de 1 sans unité
laisse la description vide.

## Synchronisation et file d'opérations

- Toute modification sur une liste synchronisée écrit l'article **et** une `SyncOperationEntity`
  (`CREATE_ITEM`, `UPDATE_ITEM`, `DELETE_ITEM`, `CHECK_ITEM`, `UNCHECK_ITEM`, `CREATE_LIST`,
  `DELETE_LIST`, `UPDATE_LIST`) **dans la même transaction Room**. Rien ne peut être perdu entre
  les deux, même application tuée.
- Une suppression d'article synchronisé laisse une **pierre tombale** (`isDeleted`) masquée à
  l'UI, purgée quand Home Assistant a confirmé.
- `SyncCoordinator` décide **quand** synchroniser (démarrage, retour du réseau, nouvelle opération
  en attente, relance toutes les 2 minutes tant que l'application tourne, bouton manuel) ;
  `HomeAssistantSyncEngine` décide **comment**, liste par liste :
  1. créer la liste distante si un `CREATE_LIST` est en attente ;
  2. lire les articles distants et associer par nom les articles locaux sans identifiant
     (liaison d'une liste existante sans doublons) ;
  3. envoyer les opérations en attente, **regroupées par article** (l'état courant est envoyé une
     fois) ;
  4. relire la liste distante et appliquer les changements selon la stratégie de conflit.
- Une opération n'est retirée de la file **qu'après confirmation**. Échec non fatal : elle reste,
  avec son nombre de tentatives et l'erreur. Serveur injoignable ou token refusé : la
  synchronisation s'arrête, tout reste en file, nouvel essai plus tard.

## Stratégie de conflit

Documentée dans `ConflictResolver` :

> **Last-write-wins, sauf qu'une modification locale non synchronisée n'est jamais écrasée.**

Home Assistant ne fournit pas de date de modification pour les articles de listes. L'ordre est
donc donné par la synchronisation : les opérations locales en attente sont envoyées d'abord (la
modification locale est la plus récente), puis l'état distant est appliqué à tous les articles
sans opération en attente (l'état distant est le plus récent).

| Local | Distant | Résultat |
| --- | --- | --- |
| synchronisé | identique | rien |
| synchronisé | modifié | appliquer le distant |
| synchronisé | absent | supprimer localement |
| modification en attente | présent | garder le local (il sera envoyé) |
| modification en attente | absent | recréer à distance |
| supprimé en attente | présent / absent | attendre / purger |
| — | nouvel article | créer localement |

Les méthodes qui appliquent des données distantes revérifient **dans leur transaction** qu'aucune
opération locale n'a été ajoutée pendant la synchronisation. Chaque article porte `localId`,
`remoteId`, `updatedAt`, `version` et `syncStatus`.

## Sécurité et confidentialité

- Le token Home Assistant est chiffré en AES-256-GCM avec une clé **non exportable** du Keystore
  Android ; seul le texte chiffré est écrit (DataStore `secrets`). Jamais en clair, jamais dans les
  logs (`HaCredentials.toString()` le masque).
- Les fichiers de configuration Home Assistant sont exclus des sauvegardes cloud et des
  transferts d'appareil.
- Le trafic en clair reste autorisé car Home Assistant est souvent joignable en `http://` sur le
  réseau local ; OpenFoodFacts est toujours appelé en HTTPS.
- **Certificats installés par l'utilisateur** : `network_security_config.xml` fait confiance aux
  autorités système **et** utilisateur pour tous les domaines (aucun `domain-config` qui les
  retirerait). Un Home Assistant en `https://ha.nas.home` signé par une autorité privée
  fonctionne donc dès que cette autorité est installée dans Android (Paramètres → Sécurité →
  Chiffrement et identifiants → Installer un certificat → Certificat CA). Le nom du certificat
  serveur doit correspondre à l'adresse saisie (SAN `ha.nas.home`).
- **Accès au réseau local** : depuis Android 17, joindre une adresse locale (`*.local`,
  `ha.nas.home` résolu en 192.168.x.x…) exige la permission d'exécution `ACCESS_LOCAL_NETWORK`,
  indépendamment du certificat. L'écran Home Assistant affiche une carte pour l'accorder tant
  qu'elle manque (ou ouvre les paramètres après un refus définitif).
- **Appareil photo** : permission demandée uniquement à l'ouverture du scanner de QR code.
- Aucun compte, aucun analytics, aucun historique d'achats envoyé : les statistiques d'usage
  restent dans Room.

## Lancer le projet

Prérequis : Android Studio récent (JDK 21 embarqué), SDK Android 37 installé.

```powershell
# Compiler
.\gradlew.bat :app:assembleDebug

# Installer sur un appareil Android 17 connecté
.\gradlew.bat :app:installDebug
```

Le projet s'ouvre directement dans Android Studio (`File → Open` sur la racine).

Version de production signée (clé `courses.jks` sur support USB, tâche VS Code
`courses: release signée → téléphone`) : voir [docs/release.md](docs/release.md).

## Tests

```powershell
# Tests unitaires JVM
.\gradlew.bat :app:testDebugUnitTest

# Tests instrumentés (Room réel, scénario hors ligne, UI Compose) sur un appareil
.\gradlew.bat :app:connectedDebugAndroidTest
```

> `connectedDebugAndroidTest` désinstalle l'application à la fin, ce qui efface ses données. Pour
> conserver les données d'un téléphone personnel : `installDebug installDebugAndroidTest`, puis
> `adb shell am instrument -w org.opensources.courses.test/androidx.test.runner.AndroidJUnitRunner`.

| Sujet | Tests |
| --- | --- |
| Autocomplétion, ranking, recherche floue | `SuggestionRankerTest`, `SearchSuggestionsUseCaseTest`, `FuzzyMatcherTest`, `TextNormalizerTest` |
| Création, doublons, produit personnalisé | `AddItemUseCaseTest` |
| Création, coche/décoche, suppression, file (Room réel) | `RoomRepositoriesTest` |
| File de synchronisation | `SyncQueueTest` |
| Conflits | `ConflictResolverTest` |
| Synchronisation Home Assistant | `HomeAssistantSyncEngineTest`, `HomeAssistantClientTest` (MockWebServer), `ItemDescriptionCodecTest` |
| Âge du catalogue, synchronisation forcée | `CatalogFreshnessPolicyTest`, `CatalogSyncManagerTest`, `TaxonomyCatalogMapperTest` |
| Fonctionnement hors ligne (redémarrages) | `OfflineScenarioTest` |
| Parcours UI | `ShoppingScreenTest`, `WelcomeScreenTest`, `HaConnectionCardTest` |
| QR code du token (décodage ZXing, validation) | `QrCodeDecoderTest`, `HaTokenParserTest` |
| Certificats CA utilisateur, trafic local | `NetworkSecurityConfigTest` (le test CA ne s'exécute que si une CA utilisateur est installée) |
| Connexion réelle au réseau local | `LocalNetworkConnectionTest` : ignoré sans arguments ; `adb shell am instrument -w -e class org.opensources.courses.core.network.LocalNetworkConnectionTest -e haLocalUrl http://<ip>:8123 -e expectReachable true org.opensources.courses.test/androidx.test.runner.AndroidJUnitRunner` |

## Choix d'architecture

- **Un seul module Gradle**, découpé en packages par fonctionnalité : le projet est petit, des
  modules Gradle ajouteraient de la configuration sans bénéfice réel aujourd'hui.
- **Pas de WorkManager** : la file est persistée dans Room, donc rien n'est perdu si l'application
  est fermée. Les opérations en attente partent au prochain démarrage, au retour du réseau ou
  périodiquement tant que l'application tourne. WorkManager n'apporterait que l'envoi en
  arrière-plan application fermée, au prix de deux dépendances.
- **Catalogue sans backend** : fichier statique OpenFoodFacts + ETag, vérification d'âge au
  démarrage.
- **Catalogue de base embarqué** : garantit l'autocomplétion hors ligne dès l'installation.
- **Habitudes séparées du catalogue** (`product_usage`) : un import ne réinitialise jamais le
  classement personnel.
- **Opérations regroupées par article** à l'envoi : moins de requêtes, et c'est toujours l'état
  réel de l'article qui part, jamais un état intermédiaire périmé.
- **Room avec schéma exporté** : version 1, pas de migration à ce stade ; toute évolution du schéma
  devra incrémenter la version et fournir une migration vérifiée contre `app/schemas`.

## Limites connues

- **Renommer une liste liée ne change pas son nom dans Home Assistant** : l'API REST ne permet pas
  de renommer une entité `todo`. L'opération `UPDATE_LIST` est retirée de la file sans appel
  (le nom local change) ; l'écran Home Assistant le signale.
- **Quantités** : synchronisées seulement pour les listes qui acceptent une description (Local
  To-do). Pour l'intégration « Shopping list » historique, elles restent locales. Écrire la
  quantité dans la description remplace une description saisie à la main dans Home Assistant.
- **Identifiant après création** : `todo.add_item` ne renvoie pas l'identifiant du nouvel article ;
  il est retrouvé par nom. Si Home Assistant modifie le texte, l'opération reste en file et est
  réessayée (risque de doublon distant dans ce cas rare).
- **Créer ou supprimer une liste dans Home Assistant** nécessite un token d'administrateur (flux
  de configuration *Local To-do*).
- **Pas d'envoi en arrière-plan application fermée** (voir WorkManager ci-dessus).
- **Certificats utilisateur** : leur prise en compte est vérifiée automatiquement
  (`NetworkSecurityConfigTest`), mais le test complet n'est effectif que sur un appareil où une
  autorité de certification utilisateur est installée ; il est ignoré sinon.
- **Synchronisation Home Assistant vérifiée par tests automatisés uniquement** (moteur avec un Home
  Assistant simulé en mémoire, client HTTP contre MockWebServer) : pas encore validée contre une
  instance réelle.
