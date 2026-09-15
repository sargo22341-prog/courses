# ADR 0007 — Catalogue de base embarqué dans l'application

- **Statut** : acceptée
- **Voir aussi** : [Catalogue et autocomplétion](../catalogue.md), [Fonctionnement hors ligne](../hors-ligne.md#premier-lancement)

## Contexte

Au premier lancement, le catalogue OpenFoodFacts n'est pas encore téléchargé, et peut ne jamais
l'être si l'appareil reste hors ligne. OpenFoodFacts ne couvre par ailleurs que l'alimentaire.

## Décision

- Embarquer un catalogue de base (`assets/catalog/seed.json`) : 266 produits et variantes
  courants, rangés par rayon, traduits dans les six langues de l'interface.
- L'importer dans Room au premier démarrage et de nouveau, hors ligne, à chaque changement de
  langue.
- Il couvre aussi les rayons absents d'OpenFoodFacts (hygiène et maison, bébé et animaux).
- Quand un nom existe dans les deux catalogues, le catalogue de base l'emporte pour le rayon.

## Conséquences

- Autocomplétion et rangement par catégorie fonctionnent dès l'installation, sans réseau.
- Le fichier est à maintenir à la main dans les six langues (`SeedCatalogMapperTest` vérifie
  qu'aucune traduction ne manque).
- Identifiants indépendants de la langue (`seed:lait`) : habitudes et liens des articles survivent
  aux changements de langue.
