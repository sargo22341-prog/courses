# ADR 0012 — Catalogue retéléchargé dans la langue choisie

- **Statut** : remplacée par [ADR 0025](0025-catalogue-genere-a-la-compilation.md)
- **Voir aussi** : [Langues](../langues.md#changement-de-langue), [Catalogue](../catalogue.md)

## Contexte

La taxonomie OpenFoodFacts contient les noms de chaque catégorie dans jusqu'à 181 codes de langue.
Tout importer multiplierait la taille de la base et compliquerait chaque requête d'autocomplétion.

## Décision

- N'importer que les noms dans la **langue de l'application** ; mémoriser la langue importée
  (`catalog_language` dans DataStore).
- À un changement de langue : réimporter immédiatement le catalogue de base (embarqué, traduit),
  puis retélécharger en entier le catalogue OpenFoodFacts dès qu'un réseau est disponible, sans
  `ETag` (le fichier est le même pour toutes les langues).
- Rattacher ensuite les articles de toutes les listes au nouveau catalogue.

## Conséquences

- Base petite, requêtes inchangées.
- Le changement de langue fonctionne hors ligne pour l'interface et le catalogue de base ; la
  partie OpenFoodFacts reste dans l'ancienne langue jusqu'au retour du réseau, ce que les réglages
  indiquent.
- Un changement de langue coûte un téléchargement complet (~1,8 Mo compressé).

## Alternatives écartées

- **Stocker toutes les langues** : base bien plus lourde, filtre de langue dans chaque requête.
