# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Ajout "Get it on GitHub"
- Ajout "Get it on Obtainium"
- README.md passe en anglais, le français est déplacé dans README.fr.md
- ajout de README.de.md, README.es.md, README.it.md, README.pt.md
- barre de choix de la langue en haut de chaque README
- AGENTS.md : les traductions du README doivent suivre ses modifications