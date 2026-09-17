# ADR 0025 — Catalogue OpenFoodFacts généré avant la compilation

- **Statut** : acceptée
- **Remplace** : [ADR 0006](0006-catalogue-openfoodfacts-sans-backend.md),
  [ADR 0012](0012-catalogue-retelecharge-dans-la-langue.md)
- **Voir aussi** : [Catalogue et autocomplétion](../catalogue.md),
  [Fonctionnement hors ligne](../hors-ligne.md), [Langues](../langues.md)

## Contexte

L'[ADR 0006](0006-catalogue-openfoodfacts-sans-backend.md) faisait télécharger la taxonomie des
catégories OpenFoodFacts (4,6 Mo) par l'application, une fois par semaine, puis la nettoyait et
l'importait sur le téléphone. Trois constats après usage :

- la taxonomie bouge très peu — quelques catégories par mois, sans effet sur une liste de courses —
  alors que le code de téléchargement, de fraîcheur, d'`ETag`, de version de format et de
  réimport au changement de langue est permanent ;
- un utilisateur hors ligne à l'installation n'a que les 266 produits du catalogue de base, et un
  changement de langue le laisse avec un catalogue dans l'ancienne langue jusqu'au retour du
  réseau ;
- le fichier JSON publié sur le CDN **perd les synonymes** de la taxonomie (`fr: Laits, lait`), qui
  n'existent que dans son fichier source `categories.txt`, trop gros et trop coûteux à analyser sur
  un téléphone. L'autocomplétion se privait ainsi de milliers d'alias.

## Décision

- Le catalogue est **généré avant la compilation** par `scripts/generate-catalog.py`, lancé à la
  main sur un poste de développement, jamais par le build ni par l'application.
- Le script lit les deux sources publiques (`categories.json` du CDN et `categories.txt` du dépôt
  `openfoodfacts-server`), applique le nettoyage, calcule le rayon sur **tout le graphe de parents**
  et écrit un fichier par langue dans `app/src/main/assets/catalog/taxonomy-<langue>.json`, commité
  au dépôt.
- L'application ne fait **plus aucun appel à OpenFoodFacts**. `AssetTaxonomyCatalogSource` lit le
  fichier de la langue courante exactement comme `AssetSeedCatalogSource` lit `seed.json` : les deux
  sont des `BundledCatalogSource`, importées par `CatalogImporter` quand leur version ou la langue
  change.
- La mise à jour du catalogue passe donc par une mise à jour de l'application.

## Conséquences

- Catalogue complet dès la première ouverture, hors ligne, dans les six langues ; un changement de
  langue est immédiat et n'attend plus le réseau.
- Environ 3 400 alias en français (2 100 produits) issus des synonymes de la taxonomie, que
  l'ancien import ne pouvait pas connaître : « patates » trouve « Pommes de terre ».
- Rayon connu pour 98,8 % des produits français contre 97,7 % (le parcours du graphe complet place
  les produits qu'une seule branche de parents ne plaçait pas).
- Catalogue **identique pour tous** : un défaut de recherche est reproductible et testable sur les
  fichiers réels (`AssetTaxonomyCatalogTest`), et la liste générée peut être relue.
- Environ 570 Ko ajoutés au paquet (3,3 Mo de JSON, compressés dans l'APK), et autant dans le
  dépôt.
- Code supprimé : client Retrofit OpenFoodFacts, lecture en flux de la taxonomie, politique de
  fraîcheur, version de format, état de synchronisation, bouton « Synchroniser maintenant » et la
  `domain-config` réseau associée.
- Redistribuer une base dérivée engage la licence **ODbL** : l'attribution reste affichée dans
  Réglages → Catalogue alimentaire et le script qui produit la base est dans le dépôt.

## Alternatives écartées

- **Garder le téléchargement hebdomadaire** : tout le code réseau du catalogue pour une donnée qui
  ne change pas d'une semaine à l'autre, et un premier lancement hors ligne appauvri.
- **Générer par une tâche Gradle** : le build deviendrait dépendant du réseau et non reproductible.
- **Ajouter les noms des autres langues comme alias** (« milk » trouverait « Lait ») : cinq fois
  plus de lignes dans `catalog_aliases`, parcourues à chaque frappe, pour un besoin marginal.
