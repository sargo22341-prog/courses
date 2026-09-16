# Stratégie de conflit

[← Documentation](README.md)

Documentée dans `ConflictResolver` ([ADR 0005](adr/0005-strategie-de-conflit.md)) :

> **Last-write-wins, sauf qu'une modification locale non synchronisée n'est jamais écrasée.**

## Pourquoi cet ordre

Home Assistant ne fournit pas de date de modification pour les articles de listes, seulement la
date à laquelle un article a été coché (`completed`). L'ordre est donc donné par la
synchronisation :

1. les opérations locales en attente sont envoyées d'abord (la modification locale est la plus
   récente) ;
2. puis l'état distant est appliqué à tous les articles sans opération en attente (l'état distant
   est le plus récent).

## Table de décision

Pour qu'aucun changement fait d'un côté ne soit perdu à cause de l'autre (par exemple application
hors ligne pendant qu'on modifie la liste dans Home Assistant) :

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

## Garde-fous

- Les écritures issues du distant revérifient **dans leur transaction** l'absence d'opération
  locale en attente.
- Chaque article porte `localId`, `remoteId`, `updatedAt` et `syncStatus`.
- Modifier cette stratégie impose de mettre à jour `ConflictResolver`, `ConflictResolverTest`,
  cette page et l'ADR.
- **Seule exception** : une modification locale que Home Assistant a **refusée 10 fois** est
  abandonnée ; l'article redevient synchronisé et reprend l'état distant à la réconciliation
  ([ADR 0022](adr/0022-abandon-des-operations-refusees.md)). `ConflictResolver` n'est pas modifié :
  l'abandon retire l'opération en attente, et la règle s'applique ensuite normalement.
