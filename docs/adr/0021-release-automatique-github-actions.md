# ADR 0021 — Release signée et montée de version par GitHub Actions

- **Statut** : acceptée
- **Voir aussi** : [Release signée](../release.md), [Développement](../developpement.md#release-et-r8)

## Contexte

La release de production n'était produite que par `scripts/release-to-phone.ps1`, sur le poste
qui a accès au support USB de la clé, avec un `versionCode` à incrémenter à la main dans
`app/build.gradle.kts`. Aucune vérification ne tournait automatiquement sur les pull requests, et
aucun APK n'était publié.

## Décision

- Un workflow GitHub Actions (`.github/workflows/ci.yml`) compile et lance les tests JVM sur chaque
  pull request et chaque push.
- À chaque push sur `main` validé, il monte le patch de la version, compile l'APK release, le
  signe, committe la version, pose le tag `vX.Y.Z` et publie une GitHub Release avec l'APK.
- La version vit dans `app/version.properties`, lu par Gradle et modifié par
  `scripts/bump-version.sh` ; `versionCode` augmente de 1 à chaque release.
- La clé de production est aussi stockée dans les secrets GitHub (base64 + mots de passe). La
  signature se fait avec `apksigner` après `assembleRelease`, comme le script local, sans
  configuration de signature dans Gradle : la compilation locale reste inchangée.

## Conséquences

- Chaque push sur `main` produit une version installable ; `versionCode` ne régresse jamais.
- La clé n'est plus uniquement sur un support débranché : sa confidentialité dépend aussi du
  compte GitHub et des personnes ayant le droit d'écriture sur le workflow.
- Le dépôt reçoit un commit `Version X.Y.Z [skip ci]` par release : il faut récupérer `main`
  avant de continuer à travailler.
- Aucune dépendance ajoutée à l'application ; seules des actions officielles (`actions/*`,
  `gradle/actions`) et la CLI `gh` du runner sont utilisées.
- Le script local reste disponible pour installer directement sur un téléphone par ADB.

## Alternatives écartées

- **Pas de signature en CI** : la clé resterait sur le support USB, mais l'APK publié ne serait
  pas installable.
- **Montée de version manuelle ou par tag poussé** : pas de commit automatique, mais une étape
  à ne pas oublier avant chaque release.
- **Version dérivée du nombre de commits** : aucun fichier à modifier, mais `versionName` perd
  son sens et un historique réécrit fait régresser `versionCode`.
