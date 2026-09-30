# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Catalogue alimentaire mis à jour depuis Open Food Facts.
- Une erreur inattendue pendant la synchronisation ne fait plus planter l'application : elle est signalée et la synchronisation est réessayée.
- Le token Home Assistant n'est plus envoyé à une autre adresse que celle de son serveur : changer de serveur demande de le saisir à nouveau.
- Home Assistant qui redémarre derrière un proxy (erreurs 502, 503, 504, 429) ne fait plus abandonner de modifications.
- Un article ou une liste supprimé pendant sa création dans Home Assistant ne réapparaît plus.
- Un token devenu illisible est redemandé au lieu d'arrêter la synchronisation sans le dire.
- Les listes et l'historique d'achats ne partent plus dans les sauvegardes cloud ; ils suivent toujours un transfert direct vers un nouveau téléphone.
- Les adresses Home Assistant avec identifiants, requête ou fragment sont refusées, et la connexion temps réel ne suit plus les redirections.
- La file de synchronisation ne garde plus que la dernière modification de chaque article, et le « + » de quantité lit un seul article.

