# ADR 0008 — Habitudes d'achat séparées du catalogue

- **Statut** : acceptée
- **Voir aussi** : [Catalogue et autocomplétion](../catalogue.md#recherche-et-classement)

## Contexte

Le classement des suggestions tient compte de la fréquence et de la récence des ajouts. Le
catalogue, lui, est remplacé à chaque import OpenFoodFacts ou changement de langue.

## Décision

- Stocker les habitudes dans une table dédiée, `product_usage`, séparée de `catalog_products`.
- Les imports ne touchent jamais `product_usage`.
- Ces statistiques restent sur l'appareil : elles ne sont envoyées à aucun service.

## Conséquences

- Un import ou un changement de langue ne réinitialise jamais le classement personnel.
- Les identifiants de produits doivent rester stables d'un import à l'autre (identifiants de
  taxonomie `en:…`, `seed:…`), sinon les habitudes perdraient leur produit.
