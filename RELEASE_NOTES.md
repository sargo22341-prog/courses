# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Saisie plus fluide : les suggestions sont calculées hors de l'affichage et plus rapidement.
- Liste de courses plus fluide : elle n'est plus redessinée à chaque frappe ni à chaque synchronisation.
- Home Assistant : moins de données échangées ; un changement reçu en temps réel ne synchronise que sa liste, et la vérification périodique passe à 10 minutes quand le temps réel fonctionne.
- Démarrage un peu plus rapide et mise à jour du catalogue OpenFoodFacts moins gourmande en mémoire.
- Scanner de QR code plus léger.
- Base de données allégée (colonnes inutilisées supprimées), sans perte de données.

