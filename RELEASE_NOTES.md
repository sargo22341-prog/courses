# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Le catalogue alimentaire est désormais **livré avec l'application** : plus aucun téléchargement,
  plus d'attente du réseau. Les 6 800 produits en français, et l'équivalent dans les cinq autres
  langues, sont disponibles dès la première ouverture, même hors connexion, et un changement de
  langue est immédiat.
- **Recherche enrichie** : environ 3 400 synonymes de la taxonomie Open Food Facts ont été ajoutés
  en français. « patates » trouve « Pommes de terre », « alimentation infantile » trouve
  « Aliments pour bébé ».
- Rangement par rayon un peu plus complet : 98,8 % des produits français contre 97,7 %.
- Réglages → Catalogue alimentaire n'affiche plus que le nombre de produits disponibles hors
  connexion : il n'y a plus rien à synchroniser.
