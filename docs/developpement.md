# Développement

[← Documentation](README.md)

Les règles obligatoires pour toute modification sont dans [`AGENTS.md`](../AGENTS.md).

## Prérequis

- Android Studio récent (JDK 21 embarqué) ;
- SDK Android 37 installé ;
- un appareil ou un émulateur **Android 17 (API 37)**.

Le projet s'ouvre directement dans Android Studio (`File → Open` sur la racine).

## Compiler et installer

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

# Compiler
.\gradlew.bat :app:assembleDebug

# Installer sur un appareil Android 17 connecté
.\gradlew.bat :app:installDebug
```

`installDebug` installe sur **tous** les appareils visibles par ADB. Pour viser un seul appareil :
`adb -s <numéro> install -r app\build\outputs\apk\debug\app-debug.apk`.

## Vérifications avant de terminer une tâche

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

Zéro erreur, zéro test en échec, Lint au vert (ses avertissements sont des erreurs, sauf les
annonces de nouvelles versions de dépendances, vues avec `dependencyUpdates`), aucun nouvel
avertissement Kotlin ou Compose, aucun fichier source de plus de 600 lignes. Détail des suites : [Tests](tests.md).

## Vérifier les mises à jour des dépendances

```powershell
.\gradlew.bat dependencyUpdates
```

Le plugin [Gradle Versions](https://github.com/ben-manes/gradle-versions-plugin) interroge les
dépôts Maven et liste les bibliothèques, plugins et la version de Gradle pour lesquels une version
**stable** plus récente existe (alpha, bêta et RC ignorées). Le rapport s'affiche dans la console
et est écrit dans `build/dependencyUpdates/report.txt`. La commande ne modifie rien : les versions
se montent à la main dans `gradle/libs.versions.toml`. Elle nécessite le réseau et n'est jamais
mise en cache par le cache de configuration.

## Base Room

Toute modification d'entité incrémente la version de `CoursesDatabase`, fournit une migration (ou
une `AutoMigration`), conserve le schéma exporté dans `app/schemas` et teste la migration
([ADR 0020](adr/0020-migrations-room-sans-perte.md)).

## Release et R8

La variante `release` est réduite par R8. Retrofit lit le type de réponse d'une méthode `suspend`
dans la signature générique de son paramètre `Continuation`, que R8 ne compte pas comme une
utilisation : une classe de réponse jamais lue par l'application (`ApiStatusDto` de « Tester la
connexion ») était supprimée et toute la release affichait « Adresse invalide ». Les DTOs
`@Serializable` des packages `data.remote` sont donc conservés par `app/proguard-rules.pro` (noms
obfusqués autorisés), règle vérifiée par `RetrofitKeepRulesTest`. **Tout nouveau DTO réseau doit
rester dans un package `data.remote`** ([ADR 0019](adr/0019-dto-reseau-conserves-par-r8.md)).

La version de production signée est produite par la CI GitHub Actions à chaque push sur `main`
(montée de version automatique dans `app/version.properties`), ou localement avec
`scripts/release-to-phone.ps1` : voir [Release signée](release.md). Les mêmes vérifications que
ci-dessus tournent en CI sur chaque pull request. La version debug et la version de production ne sont pas
signées avec la même clé : pour passer de l'une à l'autre sur un téléphone, voir « Installer la
version debug après la version de production » dans ce même document.

## Captures d'écran de la documentation

Les images de `docs/images` sont prises sur un émulateur Android 17 avec des données fictives,
jamais sur un téléphone personnel. Barre d'état propre avec le mode démo de System UI :

```powershell
adb -s emulator-5554 shell settings put global sysui_demo_allowed 1
adb -s emulator-5554 shell am broadcast -a com.android.systemui.demo -e command enter
adb -s emulator-5554 shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb -s emulator-5554 shell screencap -p /sdcard/capture.png
adb -s emulator-5554 pull /sdcard/capture.png
```

Sous Windows PowerShell 5.1, ne pas rediriger `exec-out screencap` vers un fichier avec `>` : la
redirection réencode la sortie et corrompt le PNG.
