# ADR 0009 — Opérations envoyées regroupées par article

- **Statut** : acceptée
- **Voir aussi** : [Synchronisation](../synchronisation.md#comment-synchroniser--homeassistantsyncengine)

## Contexte

Hors ligne, un même article peut accumuler plusieurs opérations (ajout, coche, décoche, changement
de quantité). Les rejouer une à une multiplie les requêtes et peut envoyer des états
intermédiaires déjà périmés.

## Décision

- `HomeAssistantSyncEngine` regroupe les opérations en attente **par article** et envoie une seule
  fois l'**état courant** de l'article.
- Seuls les champs modifiés localement partent : nom et quantité après une modification, état
  coché après une coche. Une création envoie tout.
- Toutes les opérations regroupées sont retirées de la file après confirmation.

## Conséquences

- Moins de requêtes, et jamais d'état intermédiaire périmé envoyé.
- Les champs non modifiés localement prennent la valeur distante
  ([ADR 0005](0005-strategie-de-conflit.md)).
