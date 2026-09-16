# ADR 0004 — Pas de WorkManager

- **Statut** : acceptée ; la cadence de 2 minutes est précisée par
  [ADR 0023](0023-synchronisation-periodique-au-premier-plan.md)
- **Voir aussi** : [Synchronisation](../synchronisation.md#quand-synchroniser--synccoordinator), [Limites connues](../limites-connues.md#home-assistant)

## Contexte

Les opérations en attente doivent finir par partir vers Home Assistant. WorkManager permettrait de
les envoyer en arrière-plan, application fermée.

## Décision

Ne pas utiliser WorkManager. `SyncCoordinator` déclenche la synchronisation, tant que
l'application tourne :

- au démarrage et au retour au premier plan ;
- au retour du réseau ;
- à chaque nouvelle opération en attente ;
- toutes les 2 minutes ;
- sur action manuelle (bouton, tirer pour actualiser) et sur événement WebSocket.

Le catalogue OpenFoodFacts est lui aussi vérifié à l'ouverture de l'application.

## Conséquences

- La file est persistée dans Room ([ADR 0003](0003-file-de-synchronisation-transactionnelle.md)) :
  rien n'est perdu si l'application est fermée, les opérations partent au prochain déclencheur.
- Deux dépendances de moins, aucune tâche système planifiée.
- **Limite** : aucune modification ne part tant que l'application reste fermée.
- WorkManager ne sera introduit que si un besoin réel l'impose, en mettant cette ADR et la
  documentation à jour.

## Alternatives écartées

- **WorkManager** : envoi application fermée, au prix de deux dépendances et d'une planification
  système pour un bénéfice faible (on ouvre l'application pour faire ses courses).
