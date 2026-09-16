# ADR 0005 — Last-write-wins, sauf modification locale non synchronisée

- **Statut** : acceptée ; exception des modifications refusées 10 fois : [ADR 0022](0022-abandon-des-operations-refusees.md)
- **Voir aussi** : [Stratégie de conflit](../conflits.md)

## Contexte

Une même liste peut être modifiée sur le téléphone (parfois hors ligne) et dans Home Assistant.
Home Assistant ne fournit pas de date de modification pour les articles, seulement la date à
laquelle un article a été coché (`completed`).

## Décision

> **Last-write-wins, sauf qu'une modification locale non synchronisée n'est jamais écrasée.**

- Les opérations locales en attente sont envoyées d'abord, puis l'état distant est appliqué aux
  articles sans opération en attente.
- Seuls les champs modifiés localement sont envoyés ; les autres prennent la valeur distante.
- Un article coché dans Home Assistant **après** une coche/décoche locale garde l'état de Home
  Assistant.
- Une suppression locale en attente est annulée si l'article a été modifié dans Home Assistant
  depuis la dernière synchronisation.
- Règles implémentées dans `ConflictResolver`, pur et testé (`ConflictResolverTest`).

## Conséquences

- Aucun changement fait d'un côté n'est perdu à cause de l'autre, sauf le même champ du même
  article modifié des deux côtés pendant une coupure (le local l'emporte alors).
- La comparaison des dates de coche suppose des horloges à l'heure.
- Toute modification de cette stratégie impose de mettre à jour le code, les tests, la
  documentation et cette ADR.

## Alternatives écartées

- **Last-write-wins pur par horodatage** : impossible sans date de modification côté Home
  Assistant, et écraserait des modifications faites hors ligne.
- **Fusion manuelle par l'utilisateur** : un écran de conflit est contraire au principe
  « ouvrir → taper → cocher ».
