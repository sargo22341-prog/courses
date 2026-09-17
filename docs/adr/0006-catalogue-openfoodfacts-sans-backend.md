# ADR 0006 — Catalogue OpenFoodFacts importé localement, sans backend

- **Statut** : remplacée par [ADR 0025](0025-catalogue-genere-a-la-compilation.md)
- **Voir aussi** : [Catalogue et autocomplétion](../catalogue.md)

## Contexte

L'autocomplétion doit être instantanée et fonctionner hors ligne. OpenFoodFacts propose une API
de recherche et une base produits complète, mais aucune des deux ne convient telle quelle :
interroger l'API à chaque frappe dépend du réseau et divulgue la saisie ; la base produits pèse
plusieurs Go et contient surtout des références de marque.

## Décision

- Source : la **taxonomie des catégories** OpenFoodFacts
  (`static.openfoodfacts.org/data/taxonomies/categories.json`, fichier statique servi par CDN,
  licence ODbL). Les catégories (`Tomates cerise`, `Laits demi-écrémés`…) correspondent à ce qu'on
  écrit sur une liste de courses.
- **Aucune requête par frappe** : import complet, nettoyé (`TaxonomyCatalogMapper`) et stocké dans
  Room, dans la langue de l'application.
- Mise à jour **hebdomadaire** vérifiée à l'ouverture (`CatalogFreshnessPolicy`), avec
  `If-None-Match` / `ETag` ; import transactionnel. Pas de backend propre.
- Recherche et classement 100 % locaux (`SearchSuggestionsUseCase`, `SuggestionRanker`,
  `FuzzyMatcher`).

## Conséquences

- Autocomplétion hors ligne, sans latence, sans donnée personnelle envoyée.
- Quelques milliers de produits par langue : base petite, requêtes rapides.
- Pas de marques ni de produits précis ; couverture plus faible hors français et anglais.
- En cas d'échec d'import, le catalogue existant reste intact.

## Alternatives écartées

- **API de recherche OpenFoodFacts à la frappe** : réseau obligatoire, latence, saisie divulguée.
- **Base produits complète** : plusieurs Go, surtout des références de marque.
- **Backend intermédiaire** : infrastructure à héberger, contraire à « pas de compte, pas de
  configuration ».
