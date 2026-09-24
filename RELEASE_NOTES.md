# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Catalogue alimentaire mis à jour depuis Open Food Facts.
- Listes Mealie reconnues : quantités et unités lues dans le texte (« 250 grammes Pâtes » → Pâtes 250 g), articles rangés avec le bon produit du catalogue
- Mealie préservé : cocher un article n'envoie que son état, l'aliment et la quantité restent intacts dans Mealie
- Anciens articles Mealie relus automatiquement, anciens produits « texte brut » retirés de l'autocomplétion
- Nouvelle liste : option « Importer une liste de Home Assistant » (liste liée et synchronisée)
- Mises à jour : Gradle 9.8.0, core-ktx 1.19.1, navigation-compose 2.10.2
