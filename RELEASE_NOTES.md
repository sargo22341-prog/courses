# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Suppression d'un article annulable : un message « Annuler » s'affiche quelques secondes et rien n'est supprimé (ni envoyé à Home Assistant) si on l'utilise.
- Ré-ajouter un article qui a une unité (« 500 g ») ne change plus sa quantité ; sans unité, elle augmente toujours de 1.
- Home Assistant : nouveau bouton « Oublier la connexion » qui efface le token et les réglages ; les listes restent sur le téléphone.
- Home Assistant : avertissement quand l'adresse est en `http://` (le token circule en clair sur le réseau).
- Home Assistant : une modification refusée 10 fois n'est plus renvoyée sans fin ; elle est abandonnée, l'article reprend l'état de Home Assistant et « Modifications refusées » est affiché.
- Home Assistant : un token refusé n'est plus réessayé en boucle en temps réel ; l'erreur est affichée et la connexion reprend dès qu'un nouveau token est enregistré.
- Home Assistant : un token non administrateur ne bloque plus toute la synchronisation quand une liste doit être créée ou supprimée.
- Home Assistant : un article dont Home Assistant modifie le texte n'est plus recréé en double à chaque synchronisation.
- Home Assistant : une erreur sur un article nouvellement créé n'interrompt plus l'envoi des autres.
- Batterie : plus de synchronisation toutes les 2 minutes quand l'application est en arrière-plan.
- Performances : cocher un article avec « Ranger par catégorie » ne relance plus de requête sur le catalogue ; la file de synchronisation n'est plus relue article par article.
- Accessibilité : le bouton « Nouvelle liste » est annoncé par les lecteurs d'écran ; les choix du sélecteur de liste Home Assistant font au moins 48 dp.
- Couleurs : pastille « Hors connexion » plus lisible en thème clair ; message d'annulation aux couleurs de l'application.
- Réseau : OpenFoodFacts n'est plus jamais appelé en clair, même après une redirection.
- Qualité : Android Lint est exécuté par la CI.
