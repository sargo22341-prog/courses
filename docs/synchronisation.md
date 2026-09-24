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
- toutes les 2 minutes, **seulement au premier plan** : en arrière-plan, rien ne réveille la radio
  périodiquement ([ADR 0023](adr/0023-synchronisation-periodique-au-premier-plan.md)). Tant que la
  connexion temps réel fonctionne, toutes les 10 minutes seulement ;
- sur « Synchroniser maintenant » et « tirer pour actualiser » ;
- à chaque changement annoncé par le WebSocket de Home Assistant (au premier plan).

Les demandes faites pendant l'attente de 1,5 s sont fusionnées en une seule `SyncRequest`, qui dit
s'il faut relire les listes de Home Assistant et lesquelles synchroniser
([ADR 0024](adr/0024-synchronisation-ciblee.md)) :

| Déclencheur | Listes Home Assistant (`/api/states`) | Listes synchronisées |
| --- | --- | --- |
| Démarrage, premier plan, retour du réseau, nouvel essai périodique, action de l'utilisateur | relues | toutes |
| Nouvelle opération en attente | reprises de la dernière synchronisation réussie | toutes |
| Événement WebSocket | reprises de la dernière synchronisation réussie | celle de l'événement |

Pas de WorkManager : la file est persistée, elle part au prochain de ces déclencheurs
([ADR 0004](adr/0004-pas-de-workmanager.md)).

« En ligne » signifie qu'Android a un réseau par défaut ; qu'Internet y réponde n'est pas exigé, si
bien qu'un Wi-Fi domestique privé d'Internet laisse Home Assistant local joignable
([Hors ligne](hors-ligne.md#indicateurs)).

## Comment synchroniser : `HomeAssistantSyncEngine`

La file est lue **une seule fois** par synchronisation ; les opérations ajoutées pendant ce temps
partent à la suivante, que leur insertion déclenche de toute façon. Les listes Home Assistant sont
lues ensuite, ce qui vérifie aussi le token avant que quoi que ce soit compte comme refusé.

Quand la demande ne l'exige pas, les listes lues par la dernière synchronisation réussie sont
reprises (`RemoteListsCache`, en mémoire, même adresse et même token), sauf si une opération de
liste est en attente ou si une liste liée n'y figure pas. La première requête est alors la lecture
des articles, qui vérifie le token tout autant. Des listes reprises n'importent ni ne retirent
jamais de liste.

Les articles de chaque liste sont lus et écrits dans le format de celle-ci (`HaItemFormat`) :
quantité dans la description (Local To-do), quantité en tête du texte (listes Mealie, reconnues
par le registre des entités de Home Assistant, [ADR 0026](adr/0026-listes-mealie.md)) ou nom seul.

Liste par liste :

1. créer la liste distante si un `CREATE_LIST` est en attente ;
2. lire les articles distants et associer par nom les articles locaux sans identifiant
   (liaison d'une liste existante sans doublons) ;
3. envoyer les opérations en attente, **regroupées par article** (l'état courant est envoyé une
   fois, [ADR 0009](adr/0009-operations-regroupees-par-article.md)). Seuls les champs modifiés
   localement partent : nom et quantité après une modification, état coché après une coche. Une
   création envoie tout ;
4. appliquer la liste distante selon la [stratégie de conflit](conflits.md), après l'avoir relue
   si quelque chose a été envoyé (sinon la première lecture suffit). Toute la liste est appliquée
   dans **une seule transaction** Room (l'écran n'est mis à jour qu'une fois) ; les articles déjà
   synchronisés et identiques des deux côtés ne sont pas réécrits.

## Confirmation et échecs

- Une opération n'est retirée de la file **qu'après confirmation** de Home Assistant.
- Refus (requête rejetée, élément introuvable, réponse inattendue) : elle reste, avec son nombre
  de tentatives. Un refus ne concerne que son article : les autres partent quand même.
- **Abandon** : une opération refusée `SyncQueue.MAX_ATTEMPTS` fois (10) n'est plus envoyée
  ([ADR 0022](adr/0022-abandon-des-operations-refusees.md)). Dans la même transaction, l'article
  est remis en cohérence : une suppression refusée est annulée (l'article réapparaît), un article
  lié reprend l'état de Home Assistant, un article jamais accepté reste sur le téléphone seulement,
  une liste jamais créée est déliée, une liste dont la suppression est refusée n'est plus importée.
  La synchronisation l'indique une fois (« Modifications refusées »).
- **Texte modifié par Home Assistant** à la création d'un article (l'identifiant n'est pas
  retrouvé par nom) : l'article n'est pas renvoyé, ce qui créerait un doublon ; la copie de Home
  Assistant le remplace localement à la réconciliation qui suit.
- Serveur injoignable ou token refusé : la synchronisation s'arrête, tout reste en file sans que
  la tentative compte, nouvel essai plus tard.
- Un token valide mais **non administrateur** ne peut pas créer ni supprimer de liste : Home
  Assistant répond 401, ce qui n'est traité comme « token refusé » que si `/api/` le refuse aussi ;
  sinon seule la demande est refusée.
- Les méthodes qui appliquent des données distantes revérifient **dans leur transaction**
  qu'aucune opération locale n'a été ajoutée pendant la synchronisation.
