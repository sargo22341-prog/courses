# ADR 0026 — Listes Mealie : texte lu comme quantité + aliment, intégration demandée au registre

- **Statut** : acceptée
- **Précise** : [ADR 0005](0005-strategie-de-conflit.md), [ADR 0009](0009-operations-regroupees-par-article.md), [ADR 0010](0010-categorie-calculee-par-nom.md)
- **Voir aussi** : [Home Assistant](../home-assistant.md#listes-mealie), [Catégories](../categories.md#rattachement-des-articles), [Limites connues](../limites-connues.md#home-assistant)

## Contexte

L'intégration Mealie de Home Assistant expose chaque liste de courses Mealie comme une entité
`todo.*` sans description. Le texte d'un article est l'affichage calculé par Mealie : quantité,
unité, aliment puis note (« 250 grammes Pâtes », « 1 gousse ail », « graines de sésame ou selon le
goût »). Lu tel quel, chaque article devenait un produit personnalisé « 250 grammes Pâtes », rangé
dans « Autres », avec une quantité de 1.

Surtout, l'intégration transforme en **simple note** tout article dont on renvoie un texte différent
de son affichage : Mealie perd l'aliment, l'unité et la quantité. Renvoyer le nom lu par
l'application (« Pâtes ») à chaque modification casserait donc la liste dans Mealie.

L'API REST ne dit pas quelle intégration fournit une entité, et rien dans `/api/states` ne distingue
une liste Mealie de la liste de courses historique (mêmes fonctionnalités, pas de description).

## Décision

- **Format par liste** (`HaItemFormat`) : `DESCRIPTION` (Local To-do, quantité dans la
  description), `NAME_ONLY` (autres listes sans description, quantité locale), `MEALIE`. Le moteur,
  l'envoi, le rapprochement et la résolution des conflits lisent et écrivent les articles dans ce
  format ; la stratégie de conflit ne change pas, elle compare le nom, la quantité et l'unité lus.
- **Détection par le registre des entités** : pour une liste sans description, la commande
  WebSocket `config/entity_registry/get_entries` (sans droits d'administrateur) donne la
  `platform` ; `mealie` choisit le format `MEALIE`. La réponse est gardée dans Room
  (`ha_list_integrations`, base version 6) : une entité garde son intégration, la question n'est
  posée qu'une fois par liste. Un Home Assistant qui ne sait pas répondre (commande inconnue) n'est
  plus interrogé ; un registre injoignable est interrogé de nouveau à la synchronisation suivante,
  la liste étant lue entre-temps comme avant (`NAME_ONLY`).
- **Lecture** (`MealieItemText`) : mêmes règles que le champ d'ajout (`ItemEntryParser`), après
  conversion des fractions de Mealie (« 1/2 », « 1 ½ ») ; un mot de liaison laissé en fin de texte
  est retiré. Un texte sans quantité en tête garde la quantité locale. Le texte que l'application a
  écrit pour l'article lié se relit comme cet article, même avec une unité libre.
- **Écriture** : quantité en tête du texte (« 500 g Pâtes », « 2 Pain »), jamais de description.
  Le texte n'est envoyé qu'à la création ou après une modification du nom ou de la quantité **et**
  s'il diffère de ce que Home Assistant contient déjà ; cocher n'envoie que l'état : Mealie garde
  l'aliment et la quantité.
- **Catalogue** : l'article est rattaché au produit de base ou OpenFoodFacts nommé dans son texte
  (`FindProductInTextUseCase` : plus longue suite de mots, noms puis alias, singulier ou pluriel),
  sinon à un produit personnalisé. Les articles lus avant ce support sont relus ; le produit
  personnalisé qui ne portait que l'ancien texte est supprimé s'il ne sert plus à rien
  (ni article, ni historique d'ajout).

## Conséquences

- Les articles Mealie ont leur vraie quantité et leur rayon ; les ingrédients de recettes trouvent
  leur produit sans requête réseau (catalogue embarqué).
- Mealie reste utilisable : aucun article n'est réécrit par la synchronisation elle-même.
- Une connexion WebSocket de plus, courte, une seule fois par liste sans description.
- Modifier le nom ou la quantité d'un article Mealie dans l'application en fait une note dans
  Mealie : c'est le comportement de l'intégration, documenté dans les limites connues.

## Alternatives écartées

- **Deviner Mealie d'après le texte ou l'identifiant d'entité** : une liste de courses ordinaire
  peut contenir « 2 Pain », et l'identifiant d'entité est choisi par l'utilisateur.
- **Service `mealie.get_shopping_list_items`** : données structurées (aliment, unité), mais
  seulement depuis Home Assistant 2026.3, et un appel sur une entité d'une autre intégration répond
  une erreur indistincte d'une panne. L'affichage reste de toute façon ce que voit l'utilisateur.
- **`POST /api/template` avec `integration_entities('mealie')`** : réservé aux administrateurs.
- **Lire tous les textes de toutes les listes sans description comme Mealie** : aurait changé le
  comportement de la liste de courses historique, où « 2 Pain » peut être un nom voulu.
