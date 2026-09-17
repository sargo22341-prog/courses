# Décisions d'architecture (ADR)

[← Documentation](../README.md)

Chaque ADR (*Architecture Decision Record*) décrit un choix structurant : son contexte, la
décision, ses conséquences et les alternatives écartées.

## Règles

- Une décision par fichier, numérotée `nnnn-titre-court.md`.
- Une ADR acceptée n'est pas réécrite : une nouvelle décision qui la remplace porte le numéro
  suivant, et l'ancienne passe au statut « remplacée par ADR nnnn ».
- Statuts : proposée, acceptée, remplacée, abandonnée.

## Index

| N° | Décision | Statut |
| --- | --- | --- |
| [0001](0001-module-unique-decoupe-par-fonctionnalite.md) | Un seul module Gradle, découpé par fonctionnalité | acceptée |
| [0002](0002-room-seule-source-de-verite.md) | Room, seule source de vérité (offline-first) | acceptée |
| [0003](0003-file-de-synchronisation-transactionnelle.md) | File de synchronisation écrite dans la même transaction Room | acceptée |
| [0004](0004-pas-de-workmanager.md) | Pas de WorkManager | acceptée |
| [0005](0005-strategie-de-conflit.md) | Last-write-wins, sauf modification locale non synchronisée | acceptée |
| [0006](0006-catalogue-openfoodfacts-sans-backend.md) | Catalogue OpenFoodFacts importé localement, sans backend | remplacée par 0025 |
| [0007](0007-catalogue-de-base-embarque.md) | Catalogue de base embarqué dans l'application | acceptée |
| [0008](0008-habitudes-separees-du-catalogue.md) | Habitudes d'achat séparées du catalogue | acceptée |
| [0009](0009-operations-regroupees-par-article.md) | Opérations envoyées regroupées par article | acceptée |
| [0010](0010-categorie-calculee-par-nom.md) | Catégorie d'un article calculée par son nom | acceptée |
| [0011](0011-langue-par-application-android.md) | Langue par application d'Android plutôt qu'une préférence | acceptée |
| [0012](0012-catalogue-retelecharge-dans-la-langue.md) | Catalogue retéléchargé dans la langue choisie | remplacée par 0025 |
| [0013](0013-articles-jamais-traduits.md) | Les articles des listes ne sont jamais traduits | acceptée |
| [0014](0014-pictogrammes-en-emoji.md) | Pictogrammes des catégories en emoji | acceptée |
| [0015](0015-token-chiffre-par-le-keystore.md) | Token Home Assistant chiffré par le Keystore | acceptée |
| [0016](0016-aucune-dependance-google-play.md) | Aucune dépendance aux services Google Play | acceptée |
| [0017](0017-temps-reel-websocket-okhttp.md) | Temps réel Home Assistant par WebSocket OkHttp | acceptée |
| [0018](0018-confiance-aux-certificats-utilisateur.md) | Confiance aux autorités de certification de l'utilisateur | acceptée |
| [0019](0019-dto-reseau-conserves-par-r8.md) | DTOs réseau conservés par R8 dans `data.remote` | acceptée |
| [0020](0020-migrations-room-sans-perte.md) | Schéma Room exporté et migrations sans perte | acceptée |
| [0021](0021-release-automatique-github-actions.md) | Release signée et montée de version par GitHub Actions | acceptée |
| [0022](0022-abandon-des-operations-refusees.md) | Abandon des opérations refusées 10 fois | acceptée |
| [0023](0023-synchronisation-periodique-au-premier-plan.md) | Synchronisation périodique au premier plan seulement | acceptée |
| [0024](0024-synchronisation-ciblee.md) | Synchronisation ciblée : listes relues seulement si nécessaire | acceptée |
| [0025](0025-catalogue-genere-a-la-compilation.md) | Catalogue OpenFoodFacts généré avant la compilation | acceptée |

## Modèle

```markdown
# ADR nnnn — Titre

- **Statut** : proposée | acceptée | remplacée par ADR nnnn | abandonnée
- **Voir aussi** : liens vers les pages de documentation concernées

## Contexte
## Décision
## Conséquences
## Alternatives écartées
```
