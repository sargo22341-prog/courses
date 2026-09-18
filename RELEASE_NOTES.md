# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Quantité tapée avec le nom : « 2 pain », « 500g de pâtes », « 1 kg d'oranges » ou « lait x2 » ajoutent directement le produit avec sa quantité, sans passer par l'édition ; les suggestions affichent la quantité détectée et « 2 pains » retrouve « Pain » du catalogue.
- Boutons − et + sur les articles à acheter, avec un pas adapté à l'unité (1, 100 g, 0,5 kg…).
- Listes réordonnables par glisser-déposer (poignée à gauche) ou avec « Monter » / « Descendre » dans leur menu ; l'ordre reste sur le téléphone.
- Animations : cochage (trait qui barre le nom, rebond de la case, retour haptique), fondu entre historique, suggestions et liste, article ajouté mis en valeur, seuil de suppression visible et ressenti en glissant, compteurs qui défilent, états « liste vide » et « tout est acheté », indicateur de synchronisation, transition douce entre thèmes.
- Mise à jour des outils de compilation : Android Gradle Plugin 9.4.0 → 9.4.1, plugin Gradle Versions 0.63.1 → 0.64.0.
