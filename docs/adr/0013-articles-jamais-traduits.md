# ADR 0013 — Les articles des listes ne sont jamais traduits

- **Statut** : acceptée
- **Voir aussi** : [Langues](../langues.md#changement-de-langue)

## Contexte

Après un changement de langue, un article « Lait » pourrait être renommé « Milk » grâce à son
produit associé.

## Décision

- Le nom d'un article est une **donnée saisie** : il n'est jamais traduit, renommé ni supprimé par
  un changement de langue.
- Seuls le produit associé et donc le rayon sont mis à jour
  ([ADR 0010](0010-categorie-calculee-par-nom.md)).

## Conséquences

- Aucun renommage non demandé n'est envoyé à Home Assistant ni imposé aux autres personnes qui
  partagent la liste.
- Un article écrit dans l'ancienne langue garde son rayon grâce au produit associé, dont
  l'identifiant ne dépend pas de la langue (`seed:lait`).
