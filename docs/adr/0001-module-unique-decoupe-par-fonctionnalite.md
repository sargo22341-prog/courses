# ADR 0001 — Un seul module Gradle, découpé par fonctionnalité

- **Statut** : acceptée
- **Voir aussi** : [Architecture](../architecture.md)

## Contexte

L'application est petite (une activité, quelques écrans) et maintenue par une seule personne. Il
faut pourtant garder des frontières nettes entre l'interface, les règles métier et les sources de
données, et pouvoir tester le domaine sans Android.

## Décision

- Un **seul module Gradle** `:app`.
- Découpage en packages **par fonctionnalité** (`feature/shopping`, `lists`, `catalog`,
  `homeassistant`, `settings`, `language`, `onboarding`), puis par couche (`data`, `domain`,
  `presentation`).
- `core/` ne contient que ce qui sert à plusieurs fonctionnalités.
- Règle de dépendance `presentation → domain → data` ; le domaine ne dépend d'aucune classe
  Android.

## Conséquences

- Configuration de build minimale, compilation simple.
- Les frontières reposent sur la discipline et la revue, pas sur le compilateur : un import
  interdit (`presentation` qui utilise un DAO) n'est pas bloqué automatiquement.
- Passer à plusieurs modules resterait mécanique, les packages suivant déjà les futures frontières.

## Alternatives écartées

- **Modules Gradle par fonctionnalité** : frontières vérifiées à la compilation, mais beaucoup de
  configuration pour un bénéfice faible à cette taille.
- **Découpage par couche** (`ui/`, `data/`, `domain/` globaux) : disperse chaque fonctionnalité dans
  tout le projet.
