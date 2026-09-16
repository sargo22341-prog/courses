# ADR 0023 — Synchronisation périodique au premier plan seulement

- **Statut** : acceptée ; la cadence au premier plan est précisée par
  [ADR 0024](0024-synchronisation-ciblee.md) (10 minutes quand le temps réel fonctionne)
- **Voir aussi** : [Synchronisation](../synchronisation.md#quand-synchroniser--synccoordinator), [ADR 0004](0004-pas-de-workmanager.md), [ADR 0017](0017-temps-reel-websocket-okhttp.md)

## Contexte

`SyncCoordinator` relançait une synchronisation toutes les 2 minutes tant que le processus vivait,
application visible ou non. Écran éteint, chaque cycle lisait toutes les listes de Home Assistant
et réveillait la radio, pour une liste de courses que personne ne regardait.

## Décision

- La boucle de 2 minutes vit avec le temps réel (WebSocket) : lancée par `onAppForeground`,
  arrêtée par `onAppBackground`.
- Restent actifs en arrière-plan : une nouvelle opération en file et le retour du réseau.
- Le retour au premier plan déclenche toujours une synchronisation immédiate.

## Conséquences

- Aucun réveil périodique quand l'application n'est pas visible.
- Une modification refusée pendant que l'application est en arrière-plan n'est réessayée qu'au
  retour dans l'application ; comme pour l'[ADR 0004](0004-pas-de-workmanager.md), rien ne part
  application fermée.

## Alternatives écartées

- **Intervalle allongé en arrière-plan** : moins de réveils, mais toujours des réveils inutiles.
- **WorkManager** : écarté par l'[ADR 0004](0004-pas-de-workmanager.md).
