# Tests

[← Documentation](README.md)

```powershell
# Tests unitaires JVM
.\gradlew.bat :app:testDebugUnitTest

# Android Lint (avertissements traités comme des erreurs)
.\gradlew.bat :app:lintDebug

# Compilation des tests instrumentés
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

La CI exécute aussi les tests instrumentés sur un émulateur Android 17 jetable, puis démarre l'APK
release minifié (`scripts/release-smoke-test.sh`) : un problème R8 qui fait planter l'application
au démarrage bloque la release. Les tests JVM n'utilisent pas `isReturnDefaultValues` : un appel
involontaire à `android.jar` échoue au lieu de renvoyer une valeur vide.

- **JVM** (`src/test`) : domaine, règles pures, moteurs de synchronisation, clients réseau contre
  MockWebServer, ViewModels (`MainDispatcherRule`), DataStore réel sur un dossier temporaire. Des
  fakes en mémoire (`testing/`), aucun framework de mock.
- **Instrumentés** (`src/androidTest`) : Room réel (requêtes, transactions, migrations),
  fonctionnement hors ligne, parcours UI Compose principaux.
- Aucun test ne dépend du réseau réel. Un bug corrigé = un test qui le reproduit.

## Tests instrumentés sans perdre ses données

`connectedDebugAndroidTest` désinstalle l'application à la fin, ce qui **efface ses données**. Sur
un téléphone personnel :

```powershell
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w org.opensources.courses.test/androidx.test.runner.AndroidJUnitRunner
```

Sur un émulateur jetable, `connectedDebugAndroidTest` convient.

## Couverture par sujet

| Sujet | Tests |
| --- | --- |
| Autocomplétion, ranking, recherche floue | `SuggestionRankerTest`, `SearchSuggestionsUseCaseTest`, `FuzzyMatcherTest`, `TextNormalizerTest` |
| Création, doublons, produit personnalisé | `AddItemUseCaseTest` |
| Création, coche/décoche, suppression, file (Room réel) | `RoomRepositoriesTest` |
| Migrations Room (données de la version 1 conservées) | `CoursesDatabaseMigrationTest` |
| File de synchronisation | `SyncQueueTest` |
| Refus, abandon des opérations, lecture unique de la file, texte modifié par Home Assistant, token non administrateur | `HomeAssistantRefusedChangesTest`, `HomeAssistantClientTest`, `HaGivenUpChangesRoomTest` (Room réel) |
| Synchronisation périodique au premier plan seulement, exécution exclusive | `SyncCoordinatorTest`, `ForgetHomeAssistantUseCaseTest` |
| Oublier la connexion, adresse invalide refusée, version du token | `HaConfigRepositoryImplTest` (DataStore réel), `ForgetHomeAssistantUseCaseTest`, `HaGivenUpChangesRoomTest`, `HaConnectionCardTest` |
| ViewModels (premier paramétrage, suggestion exacte, tirer pour actualiser, suppression annulable, liste créée, accueil) | `HomeAssistantSettingsViewModelTest`, `ShoppingViewModelTest`, `ListsViewModelTest`, `WelcomeViewModelTest`, `ListsRouteTest` |
| Conflits | `ConflictResolverTest` |
| Synchronisation Home Assistant | `HomeAssistantSyncEngineTest`, `HomeAssistantClientTest` (MockWebServer), `ItemDescriptionCodecTest` |
| Adresse Home Assistant, erreur Retrofit de la release | `HaUrlNormalizerTest`, `HomeAssistantClientTest`, `RetrofitKeepRulesTest` |
| Création automatique des listes, noms déjà pris, premier paramétrage | `HaListNameAllocatorTest`, `HomeAssistantClientTest`, `HomeAssistantSyncEngineTest`, `RoomRepositoriesTest`, `HaListPickerDialogTest` |
| Modes des listes (import, noms, renommage, suppression, listes ignorées, retour au mode par défaut) | `HomeAssistantListImportTest`, `HaListImportRoomTest` (Room réel), `HaListModeCardTest`, `HomeAssistantClientTest`, `CoursesDatabaseMigrationTest` |
| Changements des deux côtés, catalogue, liste indisponible | `HomeAssistantBidirectionalSyncTest`, `ConflictResolverTest`, `HaListPickerDialogTest` |
| Temps réel (dont token refusé sans nouvel essai), synchronisation à l'ouverture, tirer pour actualiser | `HomeAssistantWebSocketClientTest` (MockWebServer), `SyncCoordinatorTest`, `ShoppingScreenTest` |
| Âge et format du catalogue, synchronisation forcée | `CatalogFreshnessPolicyTest`, `CatalogSyncManagerTest`, `TaxonomyCatalogMapperTest` |
| Réimport du catalogue avec le même `ETag` dans une autre langue (aucun produit de l'ancienne langue ne reste) | `RoomRepositoriesTest` (Room réel) |
| Catégories (import, singulier/pluriel, rangement, priorité du catalogue de base, pas de requête en cochant) | `TaxonomyCatalogMapperTest`, `SeedCatalogMapperTest`, `CategoryNameKeysTest`, `GroupItemsByCategoryUseCaseTest`, `RoomRepositoriesTest`, `ShoppingScreenTest` |
| Langues (langue de l'appareil, traductions complètes, catalogue de base dans les six langues, retéléchargement, installations existantes, nom de la liste par défaut, formats) | `AppLanguageTest`, `StringResourcesTest`, `SeedCatalogMapperTest`, `TaxonomyCatalogMapperTest`, `CatalogSyncManagerTest`, `KeepFrenchForExistingInstallUseCaseTest`, `CompleteOnboardingUseCaseTest`, `CategoryNameKeysTest`, `TextNormalizerTest`, `QuantityFormatterTest`, `ItemDescriptionCodecTest`, `WelcomeScreenTest` |
| Rattachement des articles au catalogue après un import ou un changement de langue | `ItemCatalogLinkResolverTest`, `LinkItemsToCatalogUseCaseTest`, `GroupItemsByCategoryUseCaseTest`, `ItemCatalogLinkRoomTest` (Room réel) |
| Fonctionnement hors ligne (redémarrages) | `OfflineScenarioTest` |
| Parcours UI (affichés en français quelle que soit la langue du téléphone, `FrenchCoursesTheme`) | `ShoppingScreenTest` (dont suppression des achetés avec confirmation, glissement et « Annuler »), `WelcomeScreenTest` (choix de la langue), `HaConnectionCardTest` (connexion repliée, oubli, avertissement `http://`), `HaListPickerDialogTest` (zones de 48 dp), `ListsRouteTest`, `ThemeModeSelectorTest` |
| Transitions entre écrans | `NavigationTransitionsTest` |
| Icônes des barres système selon le thème choisi | `SystemBarsAppearanceTest` |
| QR code du token (décodage ZXing, validation) | `QrCodeDecoderTest`, `HaTokenParserTest` |
| Certificats CA utilisateur, trafic local, clair refusé vers OpenFoodFacts | `NetworkSecurityConfigTest` (le test CA ne s'exécute que si une CA utilisateur est installée) |
| Traductions (placeholders, pluriels `many`) | `StringResourcesTest` |
| Connexion réelle au réseau local | `LocalNetworkConnectionTest` : ignoré sans arguments (voir ci-dessous) |

Connexion réelle à un Home Assistant du réseau local :

```powershell
adb shell am instrument -w `
  -e class org.opensources.courses.core.network.LocalNetworkConnectionTest `
  -e haLocalUrl http://<ip>:8123 -e expectReachable true `
  org.opensources.courses.test/androidx.test.runner.AndroidJUnitRunner
```
