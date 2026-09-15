# Fonctionnement hors ligne

[← Documentation](README.md)

Room est la **seule source de vérité** pour l'interface
([ADR 0002](adr/0002-room-seule-source-de-verite.md)). Sans aucun réseau, on peut :

- ouvrir l'application ;
- consulter, créer, renommer et supprimer des listes ;
- ajouter, rechercher, cocher, décocher, modifier la quantité et supprimer des articles ;
- utiliser l'autocomplétion.

Le réseau ne sert qu'à synchroniser Home Assistant, à mettre à jour le catalogue OpenFoodFacts,
ou à une action explicitement réseau demandée par l'utilisateur (« Tester la connexion »,
« Synchroniser maintenant »).

## Premier lancement

- L'écran d'accueil propose le choix de la langue, **Commencer** et, facultativement,
  **Connecter Home Assistant**.
- La liste par défaut est créée, nommée dans la langue affichée (« Courses », « Groceries »,
  « Einkaufsliste », « Compra », « Spesa », « Compras »), et ouverte immédiatement.
- Un **catalogue de base** (266 produits et variantes courants, rangés par catégorie, traduits
  dans les six langues, `assets/catalog/seed.json`) est importé dans Room au premier démarrage,
  et de nouveau, hors ligne, à chaque changement de langue : l'autocomplétion fonctionne avant
  tout téléchargement ([ADR 0007](adr/0007-catalogue-de-base-embarque.md)).

## Indicateurs

L'état réseau (`ConnectivityObserver`) est affiché discrètement sous le titre de la liste :
`Hors connexion` et, si Home Assistant est activé, `Synchronisé`, `Synchronisation…` ou
`Synchronisation impossible`, avec le nombre de modifications en attente.

## Erreurs

Aucune erreur réseau n'est bloquante et aucune trace technique n'est affichée : seulement des
phrases comme « Impossible de contacter Home Assistant. Votre liste locale reste disponible. »

Les modifications faites hors ligne sur une liste synchronisée restent dans la
[file de synchronisation](synchronisation.md) et partent au retour du réseau.
