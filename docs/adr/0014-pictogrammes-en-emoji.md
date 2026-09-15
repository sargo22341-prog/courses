# ADR 0014 — Pictogrammes des catégories en emoji

- **Statut** : acceptée
- **Voir aussi** : [Catégories](../categories.md#rayons)

## Contexte

Chaque rayon affiché dans la liste porte un pictogramme. Les icônes Material de base ne couvrent
pas les rayons alimentaires (fromage, baguette, surgelés…).

## Décision

- Utiliser des **emoji** (🥕 🥖 🧀 🥩 …), rendus par la police système.
- Chaque catégorie a une couleur d'accent fixe (vert pour les légumes, bleu pour les produits
  laitiers…, `CategoryStyle`), qui n'est pas un rôle du thème ; `CategoryHeader` l'adapte au thème
  courant. Chaque accent garde un contraste d'au moins 4,5:1 en texte sur le fond clair.

## Conséquences

- Aucune dépendance ni ressource supplémentaire ; rendu en couleur.
- Le dessin exact varie selon la police emoji de l'appareil.

## Alternatives écartées

- **Material Icons « extended »** : plusieurs Mo pour quelques icônes, et une couverture
  alimentaire incomplète.
- **Vector drawables dessinés à la main** : douze illustrations à créer et maintenir.
