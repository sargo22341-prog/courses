# ADR 0010 — Catégorie d'un article calculée par son nom

- **Statut** : acceptée
- **Voir aussi** : [Catégories](../categories.md)

## Contexte

Le rangement par rayon doit fonctionner pour les articles tapés dans l'application, ceux créés
dans Home Assistant, ceux renommés, et survivre à un changement de langue ou à un import du
catalogue.

## Décision

- La catégorie **n'est pas stockée** sur l'article : `GroupItemsByCategoryUseCase` la calcule.
- Ordre : rayon du produit du catalogue qui porte le nom de l'article (singulier ou pluriel,
  `CategoryNameKeys`, `WordForms`) ; sinon rayon du produit associé
  (`shopping_items.catalogProductId`, colonne existante) ; sinon « Autres ».
- Après chaque import, `LinkItemsToCatalogUseCase` rattache les articles au catalogue ; cette
  écriture ne concerne que le lien et n'est pas synchronisée.

## Conséquences

- Aucune colonne de catégorie à maintenir sur `shopping_items`, aucun conflit de synchronisation
  possible sur la catégorie.
- Un article renommé ou venu de Home Assistant est rangé correctement sans traitement particulier.
- Le rangement se met à jour tout seul après un import (requête Room observée).
- Un nom plus précis que le catalogue ou mal orthographié va dans « Autres »
  ([Limites connues](../limites-connues.md#catégories)).

## Alternatives écartées

- **Catégorie stockée et choisie à l'ajout** : à migrer, à synchroniser, fausse après un
  renommage, et absente pour les articles créés dans Home Assistant.
