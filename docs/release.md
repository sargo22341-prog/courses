# Release signée

La version de production est signée avec une clé Android conservée **hors du dépôt** : sur un
support USB qui reste débranché en dehors des signatures, et dans les secrets GitHub pour la
release automatique ([ADR 0021](adr/0021-release-automatique-github-actions.md)).

## Release automatique (GitHub Actions)

Le workflow `.github/workflows/ci.yml` :

- sur **chaque pull request et chaque push** :
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` et `compileDebugAndroidTestKotlin` (les
    rapports de tests et de Lint sont joints au run en cas d'échec) ;
  - en parallèle, sur un **émulateur Android 17** (`reactivecircus/android-emulator-runner`,
    image `google_apis` x86_64, KVM activé, 4 cœurs et 4 Go de RAM : avec les réglages par
    défaut l'image ne finit pas de démarrer et adb reste « device offline ») :
    `scripts/instrumented-tests.sh` (migrations Room, hors ligne, Room réel, parcours UI ; échoue
    si l'installation échoue ou si aucun test ne tourne), puis `scripts/release-smoke-test.sh`,
    qui signe l'APK release minifié avec la clé de debug, l'installe, le démarre et échoue s'il a
    planté ;
- sur **chaque push sur `main`**, si ces deux jobs passent :
  1. `scripts/bump-version.sh` incrémente le patch de `versionName` et `versionCode` dans
     `app/version.properties` ;
  2. `scripts/release-notes.sh` relève les notes de `RELEASE_NOTES.md` puis vide la liste ;
  3. compile `assembleRelease`, signe l'APK avec `apksigner` et vérifie la signature ;
  4. committe `Version X.Y.Z [skip ci]` (version montée **et** notes vidées), crée le tag
     `vX.Y.Z` et pousse les deux de façon atomique ;
  5. publie une GitHub Release `vX.Y.Z` avec `courses-X.Y.Z.apk` ; sa description est le texte
     relevé à l'étape 2, ou la liste des commits générée par GitHub s'il était vide.
- **Actions → CI → Run workflow** sur `main` permet de choisir `minor` ou `major` au lieu de
  `patch`.

### Notes de version

Écrire les changements dans `RELEASE_NOTES.md`, à la racine, **sous** la ligne `<!-- notes -->`
(Markdown libre, en pratique une ligne `- …` par changement), et les committer avec le travail
concerné. Tout ce qui suit le marqueur devient la description de la prochaine release ; ce qui le
précède (titre, mode d'emploi) est conservé. Si le marqueur est supprimé, le job de release échoue
avant toute publication.

Les notes ne sont vidées que dans le commit de version : si le push de ce commit est refusé,
elles restent en place et partent avec la release suivante. Après une release, `git pull` avant
d'ajouter de nouvelles notes, sinon la liste vidée par la CI entre en conflit avec l'ancienne.

### Exécution sur GitHub uniquement

Le workflow ne s'exécute que sur GitHub : un serveur Gitea ou Forgejo qui héberge une copie du
dépôt lit aussi `.github/workflows`, mais ses jobs y sont ignorés (`github.server_url`). Le commit
de version, poussé avec le jeton du workflow, ne relance pas la CI. Si `main` a avancé
pendant le build, le push est refusé et rien n'est publié : le push suivant produit la version.
Après une release, **récupérer `main`** (`git pull`) avant de travailler ou de lancer le script
local, sinon le `versionCode` local est en retard.

### Secrets à créer

Settings → Secrets and variables → Actions :

| Secret | Contenu |
| --- | --- |
| `COURSES_KEYSTORE_BASE64` | le fichier `courses.jks` encodé en base64 |
| `COURSES_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `COURSES_KEY_ALIAS` | alias de la clé (facultatif, `courses` par défaut) |
| `COURSES_KEY_PASSWORD` | mot de passe de la clé (facultatif, celui du keystore par défaut) |

Encodage sans écrire de fichier intermédiaire, puis coller le presse-papiers dans le secret :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("E:\courses-signing\courses.jks")) | Set-Clipboard
```

Sans `COURSES_KEYSTORE_BASE64` ou `COURSES_KEYSTORE_PASSWORD`, le job de release échoue avant
toute montée de version : aucune release non signée n'est publiée. Pour que les APK de la CI
s'installent par-dessus la version installée avec le script, il faut **la même clé** dans les
secrets. Si `main` est protégée, autoriser GitHub Actions à y pousser.

La clé est décodée dans le dossier temporaire du runner, puis supprimée à la fin du job.

## Release locale vers un téléphone

Le script
`scripts/release-to-phone.ps1` compile la variante `release` (R8 + réduction des ressources),
trouve la clé, fait demander le mot de passe par `apksigner`, vérifie la signature, installe la
mise à jour avec ADB et lance l'application.

### Création unique de la clé

La clé **doit** s'appeler `courses.jks` (ou `courses.keystore` / `courses.p12`) : c'est ce nom
que le script cherche. Remplacer `E:` par la lettre du support USB :

```powershell
New-Item -ItemType Directory -Force "E:\courses-signing" | Out-Null

& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" `
  -genkeypair -v `
  -keystore "E:\courses-signing\courses.jks" `
  -storetype PKCS12 `
  -alias courses -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` demande le mot de passe et les informations du certificat. Le mot de passe ne doit être
enregistré ni dans le dépôt, ni dans une tâche VS Code, ni dans une variable d'environnement.
**Perdre la clé ou son mot de passe empêche toute mise à jour de l'application installée** : en
garder une copie de sauvegarde sur un second support.

Vérifier la clé créée :

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "E:\courses-signing\courses.jks"
```

### Exécution

1. Brancher le support contenant `courses.jks`, puis le téléphone autorisé en débogage USB.
2. `Terminal > Run Task` → `courses: release signée → téléphone`
   (ou `powershell -ExecutionPolicy Bypass -File scripts\release-to-phone.ps1`).
3. Le script parcourt les lecteurs amovibles et disques USB (3 niveaux de dossiers) et prend
   l'unique fichier `courses.jks`/`.keystore`/`.p12`. S'il n'en trouve aucun, il demande le
   chemin du fichier ou du support ; s'il en trouve plusieurs, il s'arrête et les liste.
4. Saisir le mot de passe dans l'invite d'`apksigner`.
5. Débrancher le support.

Options : `-KeystorePath <fichier ou dossier>` pour désigner la clé,
`-KeyAlias <alias>` si l'alias n'est pas `courses`, `-Serial <numéro>` si plusieurs appareils sont
connectés. Le chemin (qui n'est pas un secret) peut aussi être mémorisé :

```powershell
[Environment]::SetEnvironmentVariable("COURSES_RELEASE_KEYSTORE", "E:\courses-signing\courses.jks", "User")
```

L'APK final reste un artefact local ignoré par Git :
`app/build/outputs/apk/release/courses-release-signed.apk`.

## Mises à jour

- Chaque nouvelle release installée par-dessus une précédente doit avoir un `versionCode`
  supérieur ou égal (`app/version.properties`, monté par la CI) ; sinon le script s'arrête avec un
  message clair.
- Une application installée avec la clé de **débogage** ne peut pas être mise à jour avec la clé
  de production : la première transition demande une désinstallation manuelle, qui **efface les
  données** de l'application. Les mises à jour suivantes fonctionnent avec `adb install -r` tant
  que la même clé est utilisée.

## Installer la version debug après la version de production

La version debug est signée avec la clé de débogage d'Android Studio, la production avec
`courses.jks` : Android refuse de remplacer l'une par l'autre
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE: … signatures do not match`), y compris quand l'application
semble désinstallée. Android conserve en effet le paquet, avec sa signature, tant qu'il reste dans
**un** profil de l'appareil :

- **Espace privé** ou **profil professionnel** : la désinstallation depuis l'écran d'accueil ne
  retire l'application que du profil principal ;
- **archivage** ou désinstallation « en conservant les données » : Android garde la signature pour
  une réinstallation.

Diagnostic et correction (le téléphone doit être visible par `adb devices`) :

```powershell
# Le paquet est-il encore connu, et pour quels profils ?
& C:\platform-tools\adb.exe shell pm list packages -U --user all org.opensources.courses
& C:\platform-tools\adb.exe shell pm list users

# Désinstallation complète, tous profils (efface les données de ces profils)
& C:\platform-tools\adb.exe uninstall org.opensources.courses

# Message exact en cas de nouvel échec
.\gradlew.bat :app:installDebug
```

Si l'espace privé est verrouillé, `pm` peut refuser d'y accéder : le déverrouiller, ou y
désinstaller l'application depuis ses propres paramètres, puis relancer l'installation.

## Limites de sécurité

Un programme exécuté sous le même compte Windows peut lire les fichiers accessibles à ce compte.
La séparation pratique consiste à garder la clé sur un support externe débranché, à ne jamais
enregistrer son mot de passe et à relire les changements du script avant une signature.

Côté GitHub, toute personne pouvant modifier le workflow sur `main` peut faire signer un APK : les
secrets ne sont pas exposés aux pull requests venant de forks, mais un collaborateur disposant du
droit d'écriture y a indirectement accès. Relire tout changement de `.github/workflows/`.
