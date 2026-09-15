# ADR 0003 — File de synchronisation écrite dans la même transaction Room

- **Statut** : acceptée
- **Voir aussi** : [Synchronisation](../synchronisation.md)

## Contexte

Une modification faite hors ligne sur une liste liée à Home Assistant doit partir plus tard, même
si l'application est tuée entre-temps. Si l'article et l'opération à envoyer étaient écrits
séparément, un arrêt entre les deux perdrait la modification côté serveur ou enverrait une
opération sans article.

## Décision

- Toute modification d'une liste synchronisée écrit l'entité **et** sa `SyncOperationEntity`
  (`CREATE_ITEM`, `UPDATE_ITEM`, `DELETE_ITEM`, `CHECK_ITEM`, `UNCHECK_ITEM`, `CREATE_LIST`,
  `DELETE_LIST`, `UPDATE_LIST`) dans la **même transaction** Room (`TransactionRunner`).
- Une opération n'est supprimée qu'**après confirmation** de Home Assistant.
- Une suppression d'article synchronisé laisse une pierre tombale (`isDeleted`) jusqu'à
  confirmation.
- `core/sync` (`SyncQueue`, `SyncCoordinator`) ne connaît que l'interface `RemoteSyncEngine` ; les
  détails Home Assistant restent dans `feature/homeassistant`.

## Conséquences

- Aucune modification ne peut être perdue ; le nombre d'opérations en attente est affichable.
- Une opération qui échoue reste en file avec son nombre de tentatives et l'erreur.
- Les écritures issues du distant doivent revérifier, dans leur transaction, l'absence
  d'opération locale arrivée pendant la synchronisation.

## Alternatives écartées

- **Comparer l'état local et distant sans journal** : impossible de savoir quel côté a changé,
  Home Assistant ne fournissant pas de date de modification des articles.
