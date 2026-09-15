# ADR 0019 — DTOs réseau conservés par R8 dans les packages `data.remote`

- **Statut** : acceptée
- **Voir aussi** : [Développement](../developpement.md#release-et-r8)

## Contexte

La variante `release` est réduite par R8. Retrofit lit le type de réponse d'une méthode `suspend`
dans la signature générique de son paramètre `Continuation`, que R8 ne compte pas comme une
utilisation. Une classe de réponse jamais lue par l'application (`ApiStatusDto` de « Tester la
connexion ») a ainsi été supprimée : toute la release affichait « Adresse invalide », alors que la
version debug fonctionnait.

## Décision

- Les DTOs `@Serializable` des packages `data.remote` sont conservés par `app/proguard-rules.pro`
  (noms obfusqués autorisés).
- **Tout DTO réseau doit se trouver dans un package `data.remote`.**
- Règle vérifiée par `RetrofitKeepRulesTest` ; une erreur interne de Retrofit est affichée comme
  « Réponse inattendue de Home Assistant. » et non plus comme une adresse invalide.

## Conséquences

- La release se comporte comme la version debug pour les appels réseau.
- Une règle de keep par convention de package : un DTO placé ailleurs ne serait pas protégé.

## Alternatives écartées

- **Une règle `-keep` par classe** : facile à oublier à chaque nouveau DTO.
- **Désactiver R8** : APK plus lourd, pour contourner un seul problème.
