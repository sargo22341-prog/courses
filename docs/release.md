# Release signée vers un téléphone

La version de production est signée avec une clé Android conservée **hors du dépôt**, sur un
support USB qui reste débranché en dehors des signatures. Le script
`scripts/release-to-phone.ps1` compile la variante `release` (R8 + réduction des ressources),
trouve la clé, fait demander le mot de passe par `apksigner`, vérifie la signature, installe la
mise à jour avec ADB et lance l'application.

## Création unique de la clé

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

## Exécution

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
  supérieur ou égal (`app/build.gradle.kts`) ; sinon le script s'arrête avec un message clair.
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

## Limite de sécurité

Un programme exécuté sous le même compte Windows peut lire les fichiers accessibles à ce compte.
La séparation pratique consiste à garder la clé sur un support externe débranché, à ne jamais
enregistrer son mot de passe et à relire les changements du script avant une signature.
