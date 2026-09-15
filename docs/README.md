# Documentation de courses

Documentation technique et fonctionnelle de l'application. La présentation générale est dans le
[README du dépôt](../README.md) ; les règles obligatoires pour toute modification sont dans
[`AGENTS.md`](../AGENTS.md).

## Utiliser l'application

| Page | Contenu |
| --- | --- |
| [Interface](interface.md) | écrans, gestes, réglages, thèmes, transitions, barres système |
| [Fonctionnement hors ligne](hors-ligne.md) | ce qui marche sans réseau, premier lancement, indicateurs |
| [Langues](langues.md) | six langues, choix, changement de langue et catalogue |
| [Catalogue et autocomplétion](catalogue.md) | sources, import OpenFoodFacts, mise à jour, classement des suggestions |
| [Catégories](categories.md) | rangement par rayon, rattachement des articles au catalogue |
| [Home Assistant](home-assistant.md) | connexion, modes des listes, liaison, temps réel, API utilisées |

## Comprendre le fonctionnement

| Page | Contenu |
| --- | --- |
| [Architecture](architecture.md) | technologies, couches, flux de données, packages, base Room |
| [Synchronisation](synchronisation.md) | file d'opérations, déclencheurs, moteur Home Assistant |
| [Stratégie de conflit](conflits.md) | règle de fusion et table de décision |
| [Sécurité et confidentialité](securite.md) | token, certificats, permissions, données envoyées |
| [Décisions d'architecture (ADR)](adr/README.md) | pourquoi les choix structurants ont été faits |
| [Limites connues](limites-connues.md) | ce qui n'est pas possible, pas terminé ou pas vérifié |

## Développer

| Page | Contenu |
| --- | --- |
| [Développement](developpement.md) | prérequis, compilation, vérifications, R8, captures d'écran |
| [Tests](tests.md) | suites JVM et instrumentées, couverture par sujet |
| [Release signée](release.md) | clé de production, script de release, passage debug ↔ production |

## Ressources

- [`icon/courses_icon.svg`](icon/courses_icon.svg) : source de l'icône de l'application.
- [`images/`](images) : captures d'écran, prises sur émulateur avec des données fictives.

## Maintenir cette documentation

- Un comportement documenté qui change : mettre à jour la page concernée dans le même changement.
- Un choix structurant (dépendance, stockage, stratégie de synchronisation…) : ajouter une ADR
  numérotée dans [`adr/`](adr/README.md), sans réécrire les anciennes ; une décision remplacée
  passe au statut « remplacée par ADR nnnn ».
- Une fonctionnalité incomplète ou non vérifiée : l'ajouter aux [limites connues](limites-connues.md).
