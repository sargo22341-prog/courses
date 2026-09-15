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
5. [Interface](#interface)
6. [Catalogue OpenFoodFacts et autocomplétion](#catalogue-openfoodfacts-et-autocomplétion)
7. [Catégories](#catégories)
8. [Home Assistant](#home-assistant)
9. [Synchronisation et file d'opérations](#synchronisation-et-file-dopérations)
10. [Stratégie de conflit](#stratégie-de-conflit)
11. [Sécurité et confidentialité](#sécurité-et-confidentialité)
12. [Lancer le projet](#lancer-le-projet)
13. [Tests](#tests)
14. [Choix d'architecture](#choix-darchitecture)
15. [Limites connues](#limites-connues)

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
  doublons, catégories…) vit dans des classes pures du domaine, testées sans Android.

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
│   ├── designsystem/  thème clair/sombre, barres système, composants de réglages
│   ├── model/         SyncStatus
│   ├── network/       OkHttp, JSON, User-Agent, ConnectivityObserver
│   ├── security/      SecretStore chiffré par le Keystore
│   └── sync/          SyncQueue, SyncOperation, SyncCoordinator, RemoteSyncEngine
└── feature/
    ├── shopping/      articles : CRUD, ajout intelligent, rangement par catégorie, écran principal
    ├── lists/         listes : créer, renommer, supprimer, liste par défaut
    ├── catalog/       catalogue local, autocomplétion, catégories, import OpenFoodFacts
    ├── homeassistant/ configuration, client REST, liaison des listes, moteur de sync
    ├── settings/      préférences (thème, masquage des achetés, rangement par catégorie)
    └── onboarding/    premier lancement
```

Chaque fonctionnalité contient `data/`, `domain/` et `presentation/` quand elle en a besoin.
Aucun fichier source ne dépasse 600 lignes (le plus long en fait environ 260).

## Fonctionnement hors ligne

Room est la **seule source de vérité** pour l'interface. Sans aucun réseau, on peut : ouvrir
l'application, consulter, créer, renommer et supprimer des listes, ajouter, rechercher, cocher,
décocher, modifier la quantité et supprimer des articles, et utiliser l'autocomplétion.

- Au premier lancement, l'écran d'accueil propose **Commencer** (et, facultativement,
  **Connecter Home Assistant**). La liste « Courses » est créée et ouverte immédiatement.
- Un **catalogue de base** (266 produits et variantes courants en français, rangés par catégorie,
  `assets/catalog/seed_fr.json`)
  est importé dans Room au premier démarrage : l'autocomplétion fonctionne avant tout
  téléchargement.
- L'état réseau (`ConnectivityObserver`) est affiché discrètement sous le titre de la liste :
  `Hors connexion`, et, si Home Assistant est activé, `Synchronisé`, `Synchronisation…` ou
  `Synchronisation impossible`, avec le nombre de modifications en attente.
- Aucune erreur réseau n'est bloquante et aucune trace technique n'est affichée : seulement des
  phrases comme « Impossible de contacter Home Assistant. Votre liste locale reste disponible. »

## Interface

- **Articles achetés** : l'en-tête « Achetés » porte à droite un bouton corbeille qui supprime tous
  les articles cochés, **après confirmation** (la même boîte de dialogue que « Supprimer les
  articles achetés » dans le menu du bas de l'écran). Annuler ne supprime rien.
- **Thème** (Réglages) : trois boutons compacts (48 dp de haut) sur une ligne, **Clair**, **Sombre**
  et **Système**, peints avec le fond de leur thème ; « Système » est coupé par une barre oblique
  nette (`[ clair / sombre ]`), sans fondu, et chaque partie de son libellé prend la couleur lisible
  sur son côté. Le choix courant est encadré et coché.
- **Barres système** : l'heure, le réseau, la batterie et la barre de navigation suivent le thème
  **choisi dans l'application** (icônes sombres en thème clair, claires en thème sombre), même
  quand il diffère du thème du téléphone (`SystemBarsAppearance`, appelé par `CoursesTheme`).

## Catalogue OpenFoodFacts et autocomplétion

### Source et import

- Source : la taxonomie publique des **catégories** OpenFoodFacts
  (`https://static.openfoodfacts.org/data/taxonomies/categories.json`, ~4,6 Mo, ~1,8 Mo compressé,
  licence ODbL). C'est un fichier statique servi par CDN : **aucun appel API par frappe**, aucune
  donnée personnelle envoyée (seulement un `User-Agent` identifiant l'application et un `ETag`).
- Pourquoi les catégories plutôt que les produits : la base produits complète pèse plusieurs Go et
  contient surtout des références de marque. Les catégories (`Laits demi-écrémés`,
  `Tomates cerise`…) correspondent à ce qu'on écrit sur une liste de courses.
- Langues : en septembre 2026, le fichier compte ~14 700 entrées nommées dans 181 codes de langue
  (168 langues réelles, dont le français pour ~10 700 entrées) ; seuls les noms français sont
  importés.
- Nettoyage (`TaxonomyCatalogMapper`) : nom français obligatoire ;
  exclusion des appellations protégées (AOP/IGP) et des entrées liées à une origine ; au plus
  4 mots et 40 caractères ; aucun chiffre ; dédoublonnage sur le nom normalisé en gardant l'entrée
  la plus générique. Il reste quelques milliers de produits : la base reste petite.
- Stockage : `catalog_products` (nom, nom normalisé indexé, catégorie, rayon, parent, source, score
  de base, version d'import), `catalog_aliases`, et `product_usage` (habitudes, **séparées** du
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

**Version de format** : quand l'import extrait de nouvelles données (`TaxonomyCatalogMapper.FORMAT_VERSION`,
1 = rayons), un catalogue importé par une version précédente de l'application est retéléchargé
une fois en entier au prochain démarrage avec réseau, même s'il a moins de 7 jours.

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

## Catégories

Réglage **Réglages → Liste de courses → Ranger les articles par catégorie**, **désactivé par
défaut**. Activé, les articles à acheter sont regroupés sous un en-tête par rayon : pictogramme
(emoji), nom et couleur propres à la catégorie. Les articles achetés restent dans « Achetés ».

| Ordre | Catégorie | Source OpenFoodFacts (exemples d'identifiants) |
| --- | --- | --- |
| 1 | 🥕 Fruits et légumes | `en:fruits`, `en:vegetables`, `en:nuts`, `en:potatoes` |
| 2 | 🥖 Boulangerie | `en:breads`, `en:viennoiseries`, `en:pastries` |
| 3 | 🧀 Produits laitiers et œufs | `en:dairies`, `en:eggs`, `en:cheeses` |
| 4 | 🥩 Viandes et poissons | `en:meats-and-their-products`, `en:seafood`, `en:meat-alternatives` |
| 5 | 🍱 Traiteur et plats préparés | `en:meals`, `en:sandwiches`, `en:pizzas-pies-and-quiches` |
| 6 | 🥫 Épicerie salée | `en:condiments`, `en:canned-foods`, `en:cereals-and-potatoes` |
| 7 | 🍫 Épicerie sucrée | `en:sweet-snacks`, `en:confectioneries`, `en:jams` |
| 8 | 🧊 Surgelés | `en:frozen-foods`, `en:ice-creams-and-sorbets` |
| 9 | 🥤 Boissons | `en:beverages-and-beverages-preparations` |
| 10 | 🧴 Hygiène et maison | catalogue de base uniquement (OpenFoodFacts ne couvre que l'alimentaire) |
| 11 | 🍼 Bébé et animaux | `en:baby-foods` ; croquettes, litière… du catalogue de base |
| 12 | 🛒 **Autres** | tout article non reconnu, notamment les produits créés à la main |

- **Import** : chaque produit OpenFoodFacts reçoit le rayon du premier identifiant connu sur sa
  chaîne de parents, du plus précis au plus général (`OpenFoodFactsGroceryCategories`) : `en:breads`
  l'emporte sur la racine `en:plant-based-foods-and-beverages`. Le catalogue de base déclare le rayon
  de chaque section (`seed_fr.json`, version 2). Stockage : colonne `catalog_products.groceryCategory`.
- **Rangement par nom** (`GroupItemsByCategoryUseCase`) : un article prend le rayon du produit du
  catalogue qui porte **son nom**, au singulier ou au pluriel (`CategoryNameKeys` : `Tomate` trouve
  `Tomates`, `gâteau` trouve `Gâteaux`). Le rangement suit donc les renommages et s'applique de la
  même façon aux articles tapés à la main et aux **articles créés dans Home Assistant**, dès
  qu'OpenFoodFacts ou le catalogue de base connaît ce nom. Si les deux le connaissent, le catalogue
  de base l'emporte. Sinon : « Autres ».
- Le rangement se met à jour tout seul après un import du catalogue (requête Room observée).

## Home Assistant

Entièrement **facultatif** : l'application fonctionne sans. Dans **Réglages**, l'entrée
« Home Assistant » affiche « Non connecté — facultatif » tant qu'aucune adresse et aucun token ne
sont enregistrés. Dès qu'ils le sont, elle affiche « Connecté », l'adresse et l'état de la
synchronisation (« Synchronisé », « Synchronisation… », « Hors connexion », « Synchronisation
impossible », modifications en attente), ou « Synchronisation désactivée » si l'interrupteur est
coupé : désactiver la synchronisation ne déconnecte pas Home Assistant.

Écran **Réglages → Home Assistant** :

- activer / désactiver la synchronisation ;
- **connexion repliée** : dès qu'une adresse et un token sont enregistrés, la carte « Connexion »
  n'affiche que « Connecté » (avec l'adresse) et l'interrupteur de synchronisation ; toucher
  « Connecté » déplie l'adresse, le token, le scan et les boutons. Une première configuration reste
  dépliée après « Enregistrer » pour pouvoir tester la connexion ;
- adresse (`http://homeassistant.local:8123`, `https://ha.nas.home`…, le schéma est ajouté si
  absent ; la correction automatique du clavier est désactivée sur ce champ) ;
- token d'accès longue durée (stocké chiffré, jamais réaffiché), saisi à la main ou **scanné** :
  Home Assistant affiche le token en QR code (Profil → Sécurité → Jetons d'accès longue durée →
  Générer un QR code). « Scanner le QR code du token » ouvre un scanner intégré (CameraX +
  ZXing, sans services Google) qui demande la permission Appareil photo au premier usage ; les
  images sont analysées en mémoire, jamais enregistrées ni envoyées. Seul un token valide (JWT)
  est accepté, puis « Enregistrer » le stocke ;
- tester la connexion. « Adresse invalide. » n'est affiché que pour une adresse réellement
  malformée, vérifiée avant tout appel ; une erreur interne de Retrofit est signalée comme
  « Réponse inattendue de Home Assistant. » ;
- mode d'affichage des listes : **Toutes les listes** ou **Uniquement les listes créées par cette
  application** ;
- **Créer automatiquement les nouvelles listes** (activé par défaut) : toute liste créée dans
  l'application est aussitôt marquée à synchroniser (`CREATE_LIST` écrit dans la même transaction
  que la liste) et créée dans Home Assistant à la synchronisation suivante. Désactivé, une nouvelle
  liste reste locale jusqu'à ce que l'utilisateur la lie avec « Choisir » ;
- **premier paramétrage** : dès que Home Assistant est activé et configuré, chaque liste qui existait
  déjà et n'est pas synchronisée est proposée tour à tour (créer dans Home Assistant, lier à une
  liste existante, ne pas synchroniser, ou « Plus tard »). Cette question n'est posée qu'une fois
  (`lists_setup_done` dans DataStore) ;
- pour chaque liste locale : lier à une liste `todo.*` existante, **créer** la liste dans Home
  Assistant (intégration *Local To-do*, créée par l'application et mémorisée dans
  `ha_tracked_lists`), ou ne pas synchroniser ;
- **noms déjà pris** : si une liste `todo.*` de Home Assistant porte déjà ce nom (casse, accents et
  ponctuation ignorés), un numéro est ajouté côté Home Assistant (« Courses 2 », « Courses 3 »…,
  `HaListNameAllocator`) ; le nom local ne change pas. Si *Local To-do* refuse quand même le nom
  (`already_configured`, liste masquée), le numéro suivant est essayé ;
- synchronisation automatique et « Synchroniser maintenant ».

Suivi des changements faits dans Home Assistant :

- **à l'ouverture** : chaque retour de l'application au premier plan déclenche une synchronisation ;
- **temps réel** : tant que l'application est au premier plan (synchronisation automatique activée,
  réseau disponible), une connexion WebSocket (`/api/websocket`, commande `todo/item/subscribe`)
  suit toutes les listes liées. Chaque changement annoncé déclenche une synchronisation normale
  (regroupée sur 1,5 s) : il n'y a qu'un seul chemin de fusion. Connexion perdue : nouvel essai
  après 5 s, puis un délai croissant jusqu'à 5 min. Elle est fermée quand l'application passe en
  arrière-plan. WebSocket d'OkHttp, sans nouvelle dépendance ;
- **tirer pour actualiser** sur la liste de courses (quand Home Assistant est activé) ;
- **articles créés dans Home Assistant** : rattachés au catalogue alimentaire (produit existant de
  même nom, sinon produit personnalisé créé), donc proposés ensuite dans l'autocomplétion, et rangés
  dans leur catégorie selon leur nom (voir [Catégories](#catégories)) ;
- **liste indisponible** (intégration arrêtée, état `unavailable`, pour laquelle Home Assistant
  répond HTTP 500) : grisée et non sélectionnable dans le choix des listes. Déjà liée, elle est
  ignorée à la synchronisation, ses modifications restent en file et l'indicateur affiche
  « Liste Home Assistant indisponible ».

API utilisées (REST) : `GET /api/`, `GET /api/states`,
`POST /api/services/todo/get_items?return_response`, `todo.add_item`, `todo.update_item`,
`todo.remove_item`, `POST /api/config/config_entries/flow` (création *Local To-do*) et
`DELETE /api/config/config_entries/entry/{id}`. WebSocket : `auth`, `todo/item/subscribe`.

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
     fois). Seuls les champs modifiés localement partent : nom et quantité après une modification,
     état coché après une coche. Une création envoie tout ;
  4. relire la liste distante et appliquer les changements selon la stratégie de conflit.
- Une opération n'est retirée de la file **qu'après confirmation**. Échec non fatal : elle reste,
  avec son nombre de tentatives et l'erreur. Serveur injoignable ou token refusé : la
  synchronisation s'arrête, tout reste en file, nouvel essai plus tard.

## Stratégie de conflit

Documentée dans `ConflictResolver` :

> **Last-write-wins, sauf qu'une modification locale non synchronisée n'est jamais écrasée.**

Home Assistant ne fournit pas de date de modification pour les articles de listes, seulement la
date à laquelle un article a été coché (`completed`). L'ordre est donc donné par la
synchronisation : les opérations locales en attente sont envoyées d'abord (la modification locale
est la plus récente), puis l'état distant est appliqué à tous les articles sans opération en
attente (l'état distant est le plus récent). Pour qu'aucun changement fait d'un côté ne soit perdu
à cause de l'autre (par exemple application hors ligne pendant qu'on modifie la liste dans Home
Assistant) :

| Local | Distant | Résultat |
| --- | --- | --- |
| synchronisé | identique | rien |
| synchronisé | modifié | appliquer le distant |
| synchronisé | absent | supprimer localement |
| modification en attente | présent | envoyer **seulement les champs modifiés localement** ; les autres prennent la valeur distante (cocher hors ligne n'annule pas un renommage fait dans Home Assistant) |
| coche / décoche en attente | coché dans Home Assistant **après** la modification locale | garder l'état de Home Assistant |
| modification en attente | absent | recréer à distance |
| supprimé en attente, sans autre modification | modifié dans Home Assistant depuis la dernière synchronisation | **garder l'article** (suppression annulée) |
| supprimé en attente | inchangé / absent | supprimer à distance / purger |
| — | nouvel article | créer localement, rattaché au catalogue |

Seul cas où un changement cède : le même champ du même article modifié des deux côtés pendant une
coupure. La modification locale l'emporte alors, sauf pour l'état coché si Home Assistant l'a
coché plus tard. La comparaison des dates suppose que les horloges du téléphone et du serveur sont
à l'heure.

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
  restent dans Room. Les catégories sont calculées localement.

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
`courses: release signée → téléphone`) : voir [docs/release.md](docs/release.md). La version debug
et la version de production ne sont pas signées avec la même clé : pour passer de l'une à l'autre
sur un téléphone, voir « Installer la version debug après la version de production » dans ce même
document.

**Release et R8** : la variante `release` est réduite par R8. Retrofit lit le type de réponse d'une
méthode `suspend` dans la signature générique de son paramètre `Continuation`, que R8 ne compte pas
comme une utilisation : une classe de réponse jamais lue par l'application (`ApiStatusDto` de
« Tester la connexion ») était supprimée et toute la release affichait « Adresse invalide ». Les DTOs
`@Serializable` des packages `data.remote` sont donc conservés par `app/proguard-rules.pro`
(noms obfusqués autorisés), règle vérifiée par `RetrofitKeepRulesTest`. Tout nouveau DTO réseau doit
rester dans un package `data.remote`.

## Tests

```powershell
# Tests unitaires JVM
.\gradlew.bat :app:testDebugUnitTest

# Tests instrumentés (Room réel, migrations, scénario hors ligne, UI Compose) sur un appareil
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
| Migrations Room (données de la version 1 conservées) | `CoursesDatabaseMigrationTest` |
| File de synchronisation | `SyncQueueTest` |
| Conflits | `ConflictResolverTest` |
| Synchronisation Home Assistant | `HomeAssistantSyncEngineTest`, `HomeAssistantClientTest` (MockWebServer), `ItemDescriptionCodecTest` |
| Adresse Home Assistant, erreur Retrofit de la release | `HaUrlNormalizerTest`, `HomeAssistantClientTest`, `RetrofitKeepRulesTest` |
| Création automatique des listes, noms déjà pris, premier paramétrage | `HaListNameAllocatorTest`, `HomeAssistantClientTest`, `HomeAssistantSyncEngineTest`, `RoomRepositoriesTest`, `HaListPickerDialogTest` |
| Changements des deux côtés, catalogue, liste indisponible | `HomeAssistantBidirectionalSyncTest`, `ConflictResolverTest`, `HaListPickerDialogTest` |
| Temps réel, synchronisation à l'ouverture, tirer pour actualiser | `HomeAssistantWebSocketClientTest` (MockWebServer), `SyncCoordinatorTest`, `ShoppingScreenTest` |
| Âge et format du catalogue, synchronisation forcée | `CatalogFreshnessPolicyTest`, `CatalogSyncManagerTest`, `TaxonomyCatalogMapperTest` |
| Catégories (import, singulier/pluriel, rangement, priorité du catalogue de base) | `TaxonomyCatalogMapperTest`, `SeedCatalogMapperTest`, `CategoryNameKeysTest`, `GroupItemsByCategoryUseCaseTest`, `RoomRepositoriesTest`, `ShoppingScreenTest` |
| Fonctionnement hors ligne (redémarrages) | `OfflineScenarioTest` |
| Parcours UI | `ShoppingScreenTest` (dont suppression des achetés avec confirmation), `WelcomeScreenTest`, `HaConnectionCardTest` (connexion repliée), `ThemeModeSelectorTest` |
| Icônes des barres système selon le thème choisi | `SystemBarsAppearanceTest` |
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
- **Catégorie calculée par nom plutôt que stockée sur l'article** : aucune colonne à maintenir sur
  `shopping_items`, aucun conflit de synchronisation possible, et un article renommé ou venu de
  Home Assistant est rangé correctement sans traitement particulier.
- **Pictogrammes en emoji** : aucune bibliothèque d'icônes supplémentaire (le jeu d'icônes Material
  « extended » pèse plusieurs Mo), rendus en couleur par la police système.
- **Room avec schéma exporté** : version 2 (ajout de `catalog_products.groceryCategory` par
  `AutoMigration`). Toute évolution du schéma incrémente la version et fournit une migration testée
  contre `app/schemas` (`CoursesDatabaseMigrationTest`).

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
- **Pas d'envoi en arrière-plan application fermée** (voir WorkManager ci-dessus). Le temps réel
  (WebSocket) ne fonctionne que tant que l'application est au premier plan.
- **Articles Mealie** : l'intégration Mealie écrit des libellés comme « 1 Salade Salade » ; ils sont
  repris tels quels (et ajoutés au catalogue comme produits personnalisés), sans interprétation de
  la quantité ni catégorie reconnue.
- **Catégories** : la reconnaissance se fait sur le nom exact (au singulier ou au pluriel près). Un
  nom plus précis que le catalogue (« Lait bio de la ferme ») ou avec une faute de frappe va dans
  « Autres ». Le rayon d'une catégorie OpenFoodFacts suit une table écrite à la main : quelques
  produits peuvent être rangés dans un rayon voisin (ex. coulis de fruits en épicerie sucrée).
  « Hygiène et maison » et « Bébé et animaux » ne sont alimentés que par le catalogue de base.
  Tant que le catalogue OpenFoodFacts n'a pas été retéléchargé après la mise à jour, seuls les
  produits du catalogue de base sont rangés.
- **Certificats utilisateur** : leur prise en compte est vérifiée automatiquement
  (`NetworkSecurityConfigTest`), mais le test complet n'est effectif que sur un appareil où une
  autorité de certification utilisateur est installée ; il est ignoré sinon.
- **Synchronisation Home Assistant vérifiée surtout par tests automatisés** (moteur avec un Home
  Assistant simulé en mémoire, client HTTP contre MockWebServer). Les requêtes brutes ont été
  vérifiées contre une instance réelle (création *Local To-do*, refus `already_configured` d'un nom
  déjà pris, `add_item` avec description, `get_items`, suppression de l'entrée), mais le parcours
  complet de l'application contre une vraie instance reste à valider.
- **Création automatique** : elle ne s'applique qu'aux listes créées après l'activation du
  réglage ; une liste passée en « Ne pas synchroniser » n'est jamais recréée automatiquement.
