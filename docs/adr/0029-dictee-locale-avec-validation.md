# ADR 0029 — Dictée locale avec validation

- **Statut** : acceptée
- **Voir aussi** : [Interface](../interface.md), [Limites connues](../limites-connues.md)

## Contexte

La saisie vocale doit accepter plusieurs aliments et leurs quantités sans ajouter de données
mal reconnues, sans envoyer la voix ou les courses à un service externe et sans dépendance GMS.

## Décision

Embarquer Vosk et ses six petits modèles, sous licence Apache 2.0, pour que la dictée fonctionne
également sur un téléphone sans service vocal installé. L’utilisateur a explicitement accepté
le poids supplémentaire. Vosk nécessite JNA comme pont vers son moteur natif ; les deux
versions sont centralisées dans le catalogue Gradle. Le microphone est demandé via
`core/permission`, après une explication. Aucun moteur distant ni téléchargement de modèle
dans l’application.

Le maintien du bouton démarre la dictée ; le relâchement demande les résultats. La capture
AudioRecord est continue, non bloquante et limitée à 60 secondes. Les échantillons restent en
mémoire dans un tampon borné (environ 1,9 Mo), pour conserver aussi le début de la phrase lors
du premier chargement du modèle. Capture et décodage sont exécutés sur `IoDispatcher`, dans
des coroutines enfants du ViewModel, avec une limite totale de 90 secondes. Quitter l’écran ou
passer en arrière-plan annule l’écoute et libère le microphone. L’accessibilité permet de démarrer puis
d’arrêter par deux activations successives du bouton.

Les archives officielles sont livrées dans `assets/voice-models`, acquises hors build par
`scripts/prepare-voice-models.py`. Leur origine et leurs SHA-256 sont dans le manifeste livré.
La licence est fournie avec les modèles. Seul le modèle de la langue choisie est extrait à son
premier usage dans `noBackupFilesDir`, avec contrôle du checksum, limites de taille, nombre
d’entrées et temps, puis réutilisé. Le build n’effectue aucun téléchargement de modèle.

L’analyse des articles utilise des règles déterministes locales dans les six langues : mots introductifs,
séparateurs, nombres courants en lettres et analyseur de quantités existant. La transcription
est limitée à 4 000 caractères et 50 lignes. Les termes inconnus restent visibles et modifiables.
L’utilisateur valide noms, quantités et unités dans un récapitulatif avant tout ajout via
`AddItemUseCase` et le chemin Room/synchronisation existant. Aucun fichier de dictée n’est créé.
Les lignes ajoutées sont retirées du brouillon après chaque succès pour limiter les doublons
en cas d’échec partiel. Le brouillon est conservé en mémoire pendant la vie du ViewModel.

La reconnaissance acoustique est guidée par les mots du catalogue livré, des quantités et des
phrases introductives (au plus deux sources, 10 000 produits par source et 16 000 expressions). Elle
n’utilise ni habitudes d’achat ni données personnelles. Le marqueur Vosk `[unk]` est conservé
pour les sons inconnus, affiché comme « Mot non reconnu » et interdit la validation de la ligne
tant que son nom n’a pas été corrigé ou la ligne supprimée. Cette grammaire améliore le contexte
alimentaire sans rendre infaillible la reconnaissance des mots proches.
Les quantités et articles sont combinés avec les noms du catalogue de base en expressions
complètes, pour conserver le contexte (« et trois yaourts », « cinq cents grammes de pâtes »).

## Conséquences

Les six archives représentent environ 240 Mio dans le paquet ; leur chargement natif consomme
plus de mémoire que l’application seule. Seule une langue est chargée à la fois. Le premier
usage extrait son modèle (environ 52 à 92 Mio sur disque), sans bloquer la saisie clavier.
Les phrases ambiguës demandent une correction humaine. Aucune IA externe n’est
appelée pour corriger une ambiguïté. Les règles ne constituent pas un analyseur général du langage.

## Alternatives écartées

- Moteur Android par défaut : peut utiliser le réseau sans garantie de traitement local.
- IA distante : partage des données et configuration supplémentaire.
- Service local Android seul : absent sur le téléphone connecté, ne satisfait pas la dictée
  autonome demandée.
