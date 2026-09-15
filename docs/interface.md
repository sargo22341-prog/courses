# Interface

[← Documentation](README.md)

Material 3, thème clair chaud et calme, thème sombre sobre, ou mode système. Tous les textes
visibles viennent des ressources (six langues, voir [Langues](langues.md)).

| Liste de courses | Autocomplétion | Rangement par catégorie |
| --- | --- | --- |
| ![Liste de courses](images/liste.png) | ![Autocomplétion](images/autocompletion.png) | ![Catégories](images/categories.png) |

| Listes | Réglages | Thème sombre |
| --- | --- | --- |
| ![Listes](images/listes.png) | ![Réglages](images/reglages.png) | ![Thème sombre](images/sombre.png) |

## Liste de courses

- Un champ en haut de la liste ajoute un article ; les suggestions du catalogue apparaissent dès
  la première lettre ([Catalogue et autocomplétion](catalogue.md)).
- Toucher un article le coche ; il passe dans « Achetés ». Le bas de l'écran indique le nombre
  d'articles achetés ; son menu « Plus d'actions » masque ou affiche les achetés et propose
  « Supprimer les articles achetés ».
- Appui long sur un article : modifier son nom et sa quantité.
- Glisser un article vers la gauche : le supprimer. Ces deux actions sont aussi proposées aux
  lecteurs d'écran comme actions d'accessibilité.
- **Articles achetés** : l'en-tête « Achetés » porte à droite un bouton corbeille qui supprime tous
  les articles cochés, **après confirmation** (la même boîte de dialogue que « Supprimer les
  articles achetés » dans le menu du bas de l'écran). Annuler ne supprime rien.
- **Rangement par catégorie** (désactivé par défaut) : voir [Catégories](categories.md).
- **Tirer pour actualiser** quand Home Assistant est activé.

## Réglages

- **Thème** : trois boutons compacts (48 dp de haut) sur une ligne, **Clair**, **Sombre** et
  **Système**, peints avec le fond de leur thème ; « Système » est coupé par une barre oblique
  nette (`[ clair / sombre ]`), sans fondu, et chaque partie de son libellé prend la couleur lisible
  sur son côté. Le choix courant est encadré et coché.
- **Langue** : puces portant chacune le nom de la langue dans cette langue.
- **Liste de courses** : ranger les articles par catégorie.
- **Catalogue alimentaire** : état du catalogue, « ↻ Synchroniser maintenant ».
- **Home Assistant** : état de la connexion, voir [Home Assistant](home-assistant.md).

## Transitions entre écrans

`NavigationTransitions` : glissement horizontal de 350 ms au lieu du fondu enchaîné par défaut.
Le nouvel écran entre par le bord de fin pendant que l'ancien sort par le bord de début, à la même
vitesse, tous deux opaques et bord à bord : rien n'est jamais transparent, donc aucun flash, quel
que soit le thème (le fond de la fenêtre suit le thème du système, pas celui choisi dans
l'application ; le `NavHost` est en plus peint avec le fond du thème de l'application). Le retour,
y compris le geste retour prédictif, joue le mouvement inverse. Le sens suit la direction de
lecture et l'échelle d'animation du système s'applique (`NavigationTransitionsTest`).

## Barres système

L'heure, le réseau, la batterie et la barre de navigation suivent le thème **choisi dans
l'application** (icônes sombres en thème clair, claires en thème sombre), même quand il diffère du
thème du téléphone (`SystemBarsAppearance`, appelé par `CoursesTheme`).

## Accessibilité

`contentDescription` sur les actions iconographiques, zones tactiles d'au moins 48 dp, contraste
vérifié dans les deux thèmes.
