# ADR 0007 — Catalogue de base embarqué dans l'application

- **Statut** : acceptée
- **Voir aussi** : [Catalogue et autocomplétion](../catalogue.md), [Fonctionnement hors ligne](../hors-ligne.md#premier-lancement)

## Contexte

La taxonomie OpenFoodFacts ne couvre que l'alimentaire et nomme les catégories comme une base de
données (« Laits demi-écrémés »), pas comme une liste de courses. Certains rayons n'y existent pas
du tout.

## Décision

- Embarquer un catalogue de base (`assets/catalog/seed.json`) : 266 produits et variantes
  courants, rangés par rayon, traduits dans les six langues de l'interface.
- L'importer dans Room au premier démarrage et de nouveau à chaque changement de langue.
- Il couvre les rayons absents de la taxonomie (hygiène et maison, bébé et animaux).
- Quand un nom existe dans les deux catalogues, le catalogue de base l'emporte pour le rayon.

## Conséquences

- Les produits du quotidien sont nommés et rangés à la main, mieux que la taxonomie générique ne
  sait le faire ([ADR 0025](0025-catalogue-genere-a-la-compilation.md) l'embarque aussi).
- Le fichier est à maintenir à la main dans les six langues (`SeedCatalogMapperTest` vérifie
  qu'aucune traduction ne manque).
- Identifiants indépendants de la langue (`seed:lait`) : habitudes et liens des articles survivent
  aux changements de langue.
