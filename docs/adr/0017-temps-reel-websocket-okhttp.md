# ADR 0017 — Temps réel Home Assistant par WebSocket OkHttp, un seul chemin de fusion

- **Statut** : acceptée ; la synchronisation déclenchée par un événement est ciblée sur sa liste
  ([ADR 0024](0024-synchronisation-ciblee.md))
- **Voir aussi** : [Home Assistant](../home-assistant.md#suivi-des-changements-faits-dans-home-assistant)

## Contexte

Quand quelqu'un modifie la liste dans Home Assistant, le téléphone ouvert doit le voir rapidement.
Relire les listes toutes les quelques secondes coûterait des requêtes et de la batterie. Home
Assistant expose une API WebSocket avec la commande `todo/item/subscribe`.

## Décision

- Tant que l'application est au premier plan (synchronisation automatique activée, réseau
  disponible), `HomeAssistantWebSocketClient` s'abonne à toutes les listes liées via
  `/api/websocket`.
- Un changement annoncé **ne modifie pas Room directement** : il déclenche une synchronisation
  normale, regroupée sur 1,5 s. Il n'existe qu'un seul chemin de fusion
  ([ADR 0005](0005-strategie-de-conflit.md)).
- Reconnexion : 5 s, puis délai croissant jusqu'à 5 min. Fermeture en arrière-plan.
- WebSocket d'OkHttp, déjà présent : aucune nouvelle dépendance.

## Conséquences

- Mises à jour quasi immédiates sans sondage fréquent.
- La stratégie de conflit est appliquée de la même façon, quelle que soit l'origine du
  déclenchement.
- Rien n'est suivi application fermée ([ADR 0004](0004-pas-de-workmanager.md)).

## Alternatives écartées

- **Appliquer le contenu des événements WebSocket** : second chemin de fusion à maintenir et à
  tester, risque d'écraser une modification locale en attente.
- **Sondage REST fréquent** : requêtes et consommation inutiles.
