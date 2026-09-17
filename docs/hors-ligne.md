# Fonctionnement hors ligne

[← Documentation](README.md)

Room est la **seule source de vérité** pour l'interface
([ADR 0002](adr/0002-room-seule-source-de-verite.md)). Sans aucun réseau, on peut :

- ouvrir l'application ;
- consulter, créer, renommer et supprimer des listes ;
- ajouter, rechercher, cocher, décocher, modifier la quantité et supprimer des articles ;
- utiliser l'autocomplétion.

Le réseau ne sert qu'à synchroniser Home Assistant ou à une action explicitement réseau demandée
par l'utilisateur (« Tester la connexion »). Le catalogue alimentaire, lui, est livré avec
l'application et n'est jamais téléchargé
([ADR 0025](adr/0025-catalogue-genere-a-la-compilation.md)).

## Premier lancement

- L'écran d'accueil propose le choix de la langue, **Commencer** et, facultativement,
  **Connecter Home Assistant**.
- La liste par défaut est créée, nommée dans la langue affichée (« Courses », « Groceries »,
  « Einkaufsliste », « Compra », « Spesa », « Compras »), et ouverte immédiatement.
- Le **catalogue complet** est importé dans Room au premier démarrage, hors ligne : les 266
  produits et variantes du catalogue de base écrit à la main
  ([ADR 0007](adr/0007-catalogue-de-base-embarque.md)) et les milliers de produits générés depuis la
  taxonomie OpenFoodFacts (6 554 en français), tous livrés dans `assets/catalog/`. L'autocomplétion
  et le rangement par rayon sont complets dès l'installation, sans le moindre téléchargement
  ([Catalogue](catalogue.md)).

## Indicateurs

L'état réseau (`ConnectivityObserver`) est affiché discrètement sous le titre de la liste :
`Hors connexion` et, si Home Assistant est activé, `Synchronisé`, `Synchronisation…`,
`Synchronisation impossible` ou `Modifications refusées`, avec le nombre de modifications en
attente.

« Hors connexion » signifie qu'Android n'a **aucun réseau par défaut**. La validation d'Internet
n'est pas exigée : un Wi-Fi domestique dont la box a perdu Internet reste « en ligne » et Home
Assistant local continue d'être synchronisé.

## Erreurs

Aucune erreur réseau n'est bloquante et aucune trace technique n'est affichée : seulement des
phrases comme « Impossible de contacter Home Assistant. Votre liste locale reste disponible. »

Les modifications faites hors ligne sur une liste synchronisée restent dans la
[file de synchronisation](synchronisation.md) et partent au retour du réseau.
