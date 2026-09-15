# Synchronisation et file d'opérations

[← Documentation](README.md)

La synchronisation ne concerne que les listes liées à [Home Assistant](home-assistant.md). Elle ne
bloque jamais l'interface : l'utilisateur modifie Room, la file part plus tard.

```
UI → ViewModel → Repository ──┬─ écrit l'article
                              └─ écrit la SyncOperationEntity   (même transaction Room)
                                          ↓
                              SyncCoordinator (quand ?)
                                          ↓
                              RemoteSyncEngine = HomeAssistantSyncEngine (comment ?)
                                          ↓
                              Home Assistant → Room → Flow → UI
```

## File d'opérations

- Toute modification sur une liste synchronisée écrit l'article **et** une `SyncOperationEntity`
  (`CREATE_ITEM`, `UPDATE_ITEM`, `DELETE_ITEM`, `CHECK_ITEM`, `UNCHECK_ITEM`, `CREATE_LIST`,
  `DELETE_LIST`, `UPDATE_LIST`) **dans la même transaction Room**. Rien ne peut être perdu entre
  les deux, même application tuée ([ADR 0003](adr/0003-file-de-synchronisation-transactionnelle.md)).
- Supprimer une liste liée écrit un `DELETE_LIST`. Avec l'identifiant d'entrée de configuration
  (liste créée par l'application), la liste est supprimée dans Home Assistant ; sans (liste créée
  ailleurs), elle y reste et est seulement mémorisée comme ignorée. Tant que l'opération est en
  file, la liste n'est pas réimportée.
- Une suppression d'article synchronisé laisse une **pierre tombale** (`isDeleted`) masquée à
  l'UI, purgée quand Home Assistant a confirmé.

## Quand synchroniser : `SyncCoordinator`

- au démarrage et à chaque retour au premier plan ;
- au retour du réseau ;
- à chaque nouvelle opération en attente ;
- toutes les 2 minutes tant que l'application tourne ;
- sur « Synchroniser maintenant » et « tirer pour actualiser » ;
- à chaque changement annoncé par le WebSocket de Home Assistant.

Pas de WorkManager : la file est persistée, elle part au prochain de ces déclencheurs
([ADR 0004](adr/0004-pas-de-workmanager.md)).

## Comment synchroniser : `HomeAssistantSyncEngine`

Liste par liste :

1. créer la liste distante si un `CREATE_LIST` est en attente ;
2. lire les articles distants et associer par nom les articles locaux sans identifiant
   (liaison d'une liste existante sans doublons) ;
3. envoyer les opérations en attente, **regroupées par article** (l'état courant est envoyé une
   fois, [ADR 0009](adr/0009-operations-regroupees-par-article.md)). Seuls les champs modifiés
   localement partent : nom et quantité après une modification, état coché après une coche. Une
   création envoie tout ;
4. relire la liste distante et appliquer les changements selon la
   [stratégie de conflit](conflits.md).

## Confirmation et échecs

- Une opération n'est retirée de la file **qu'après confirmation** de Home Assistant.
- Échec non fatal : elle reste, avec son nombre de tentatives et l'erreur.
- Serveur injoignable ou token refusé : la synchronisation s'arrête, tout reste en file, nouvel
  essai plus tard.
- Les méthodes qui appliquent des données distantes revérifient **dans leur transaction**
  qu'aucune opération locale n'a été ajoutée pendant la synchronisation.
