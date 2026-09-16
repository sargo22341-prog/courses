# ADR 0024 — Synchronisation ciblée : listes relues seulement si nécessaire

- **Statut** : acceptée
- **Précise** : [ADR 0017](0017-temps-reel-websocket-okhttp.md), [ADR 0023](0023-synchronisation-periodique-au-premier-plan.md)
- **Voir aussi** : [Synchronisation](../synchronisation.md#quand-synchroniser--synccoordinator), [Home Assistant](../home-assistant.md#suivi-des-changements-faits-dans-home-assistant)

## Contexte

Chaque synchronisation commençait par `GET /api/states`, qui renvoie l'état de **toutes** les
entités de l'installation (souvent plusieurs centaines de Ko, parfois plusieurs Mo) pour n'en garder
que les listes `todo.*`, puis relisait les articles de toutes les listes liées. Or une
synchronisation part à chaque modification locale, à chaque événement WebSocket et toutes les
2 minutes au premier plan : données mobiles, batterie et processeur étaient consommés pour des
listes qui, le plus souvent, n'avaient pas changé.

## Décision

- `SyncCoordinator` fusionne les demandes faites pendant l'attente (1,5 s) en une `SyncRequest` :
  relire ou non les listes, toutes les listes ou seulement certaines.
  - **Tout relire** : démarrage, retour au premier plan, retour du réseau, nouvel essai
    périodique, action de l'utilisateur (« Synchroniser maintenant », tirer pour actualiser,
    réglages Home Assistant), connexion temps réel refusée.
  - **Modification locale** : toutes les listes, sans relire `/api/states`.
  - **Événement WebSocket** : seulement la liste de l'abonnement qui l'a émis, sans relire
    `/api/states`.
- `HomeAssistantSyncEngine` réutilise les listes lues par la dernière synchronisation **réussie**
  (`RemoteListsCache`, en mémoire), et seulement si :
  - elles viennent de la même adresse et du même token (`tokenVersion`) ;
  - aucune opération de liste (création, suppression, renommage) n'est en attente ;
  - toutes les listes liées y figurent (une liste liée entre-temps impose une relecture).
- Des listes réutilisées **n'importent ni ne retirent jamais** de liste : seules des listes lues à
  l'instant disent ce qui est apparu ou a disparu dans Home Assistant.
- Au premier plan, le nouvel essai périodique a lieu toutes les 2 minutes, et toutes les
  **10 minutes** tant que la connexion temps réel fonctionne (des événements arrivent). Il repasse
  à 2 minutes dès qu'elle est perdue.

## Conséquences

- `/api/states` n'est plus téléchargé à chaque case cochée ni à chaque événement ; un événement ne
  relit qu'une liste.
- Toujours un seul chemin de fusion ([ADR 0017](0017-temps-reel-websocket-okhttp.md)) : la
  synchronisation ciblée applique les mêmes règles.
- Pendant que le temps réel fonctionne, un changement **de liste** fait dans Home Assistant
  (nouvelle liste en mode « Toutes les listes », renommage, suppression, intégration arrêtée) et
  le nouvel essai d'une modification refusée peuvent attendre jusqu'à 10 minutes, ou le prochain
  retour dans l'application ou « tirer pour actualiser ».
- Le cache ne vit qu'en mémoire : rien de plus n'est stocké.

## Alternatives écartées

- **`POST /api/template`** pour ne recevoir que les entités `todo.*` : format et droits à valider
  sur une vraie instance, et nouvelle dépendance à un langage de modèle côté serveur.
- **Supprimer la synchronisation périodique quand le temps réel fonctionne** : les changements de
  listes et les nouveaux essais ne seraient plus vus avant un retour dans l'application.
- **Cache à durée fixe** : relirait encore les listes sans raison, ou garderait des listes
  périmées après une création ou une suppression.
