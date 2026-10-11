# ADR 0030 — Recoupement local des quantités vocales

- **Statut** : acceptée
- **Complète** : [ADR 0029](0029-dictee-locale-avec-validation.md)
- **Voir aussi** : [Interface](../interface.md), [Tests](../tests.md)

## Contexte

Le vocabulaire alimentaire guidé transcrit parfois « deux sacs poubelle » comme
« de sacs poubelle ». Un échantillon synthétique français reproduit cette confusion
sur le moteur Vosk embarqué ; le modèle avec son contexte linguistique original
reconnaît « deux sacs poubelle » pour le même audio. Modifier seulement l’analyseur
de nombres ne peut pas récupérer une quantité absente de la transcription.

## Décision

Conserver le décodage guidé pour les noms alimentaires. Lorsqu’une ligne reconnue
ne possède pas de quantité et ne contient pas de mot inconnu, effectuer au plus
une seconde passe avec le modèle linguistique original. Réutiliser le même tampon
audio borné en mémoire et le même modèle chargé, sur `IoDispatcher`, dans la
coroutine et le délai total de 90 secondes de la session. L’annulation reste effective
entre les blocs de 1 600 échantillons et entre les deux passes.

Ne reprendre une quantité de la seconde transcription que pour un produit correspondant
sans ambiguïté au nom guidé : normalisation et formes singulier/pluriel existantes,
sans recherche floue. Préserver les quantités déjà reconnues et les mots inconnus.
Ne jamais déduire « deux » d’un simple « de » : le nombre doit être reconnu dans
la seconde passe. La transcription présentée reflète les quantités recoupées ;
la validation humaine du récapitulatif reste obligatoire.

## Conséquences

La seconde passe peut allonger le traitement des phrases sans quantités explicites.
Elle n’ajoute ni dépendance, ni fichier audio, ni connexion réseau, ni modèle supplémentaire.
Les erreurs qui subsistent dans les deux passes restent à corriger dans le récapitulatif.
Un test acoustique optionnel utilise une voix synthétique locale et le code de décodage
de production pour vérifier le cas « deux sacs poubelle », puis les quantités sont testées
en JVM avec des produits différents et des doublons ambigus.
