# Home Assistant

[← Documentation](README.md)

Entièrement **facultatif** : l'application fonctionne sans. Home Assistant sert de serveur de
synchronisation pour partager une liste entre plusieurs téléphones ou avec un tableau de bord, via
ses entités `todo.*`.

![Réglages Home Assistant](images/home-assistant.png)

## État dans les réglages

Dans **Réglages**, l'entrée « Home Assistant » affiche « Non connecté — facultatif » tant qu'aucune
adresse et aucun token ne sont enregistrés. Dès qu'ils le sont, elle affiche « Connecté », l'adresse
et l'état de la synchronisation (« Synchronisé », « Synchronisation… », « Hors connexion »,
« Synchronisation impossible », modifications en attente), ou « Synchronisation désactivée » si
l'interrupteur est coupé : désactiver la synchronisation ne déconnecte pas Home Assistant.

## Connexion

Écran **Réglages → Home Assistant** :

- activer / désactiver la synchronisation ;
- **connexion repliée** : dès qu'une adresse et un token sont enregistrés, la carte « Connexion »
  n'affiche que « Connecté » (avec l'adresse) et l'interrupteur de synchronisation ; toucher
  « Connecté » déplie l'adresse, le token, le scan et les boutons. Une première configuration reste
  dépliée après « Enregistrer » pour pouvoir tester la connexion ;
- **adresse** (`http://homeassistant.local:8123`, `https://ha.nas.home`…, le schéma est ajouté si
  absent ; la correction automatique du clavier est désactivée sur ce champ) ;
- **token d'accès longue durée** (stocké chiffré, jamais réaffiché), saisi à la main ou **scanné** :
  Home Assistant affiche le token en QR code (Profil → Sécurité → Jetons d'accès longue durée →
  Générer un QR code). « Scanner le QR code du token » ouvre un scanner intégré (CameraX + ZXing,
  sans services Google) qui demande la permission Appareil photo au premier usage ; les images sont
  analysées en mémoire, jamais enregistrées ni envoyées. Seul un token valide (JWT) est accepté,
  puis « Enregistrer » le stocke ;
- **tester la connexion**. « Adresse invalide. » n'est affiché que pour une adresse réellement
  malformée, vérifiée avant tout appel ; une erreur interne de Retrofit est signalée comme
  « Réponse inattendue de Home Assistant. » ;
- **avertissement `http://`** : une adresse enregistrée en clair affiche, sous « Connecté », que le
  token circule en clair sur le réseau ([Sécurité](securite.md#trafic-réseau)) ;
- **oublier la connexion** (connexion dépliée, après confirmation) : efface le token et les
  réglages, délie les listes sans rien supprimer dans Home Assistant ; une nouvelle connexion
  repropose le premier paramétrage des listes ;
- **accès au réseau local** : une carte demande la permission `ACCESS_LOCAL_NETWORK` d'Android 17
  tant qu'elle manque ([Sécurité](securite.md#accès-au-réseau-local)).

## Modes des listes

`HaListMode` :

- **Uniquement les listes créées par cette application** (par défaut) : seules les listes créées
  depuis l'application sont synchronisées ; une liste qui existait déjà se lie à la main avec
  « Choisir ». Une liste supprimée dans Home Assistant est déliée et reste sur le téléphone.
- **Toutes les listes** : à l'activation puis à chaque synchronisation, chaque liste `todo.*`
  **modifiable** (ajout, modification et suppression d'articles) et disponible, qui n'est liée à
  aucune liste de l'application, y est **importée** avec ses articles
  (`shopping_lists.importedFromRemote`).
  - Elle prend le nom de Home Assistant ; si ce nom existe déjà dans l'application, un numéro est
    ajouté (« Courses 2 », `HaListNameAllocator`).
  - Un renommage fait ensuite dans Home Assistant renomme la liste importée (`remoteName` retient
    le dernier nom appliqué, donc un renommage fait dans l'application n'est pas écrasé tant que le
    nom ne change pas dans Home Assistant).
  - Une liste **supprimée dans Home Assistant** est supprimée de l'application, sauf si elle
    contient des modifications pas encore envoyées ou si c'est la dernière liste : elle est alors
    seulement déliée.
- **Listes ignorées** : supprimer dans l'application une liste liée créée ailleurs, la passer en
  « Ne pas synchroniser » ou la lier à une autre liste la mémorise dans `ha_ignored_lists` : elle
  reste dans Home Assistant et n'est plus importée. La lier de nouveau avec « Choisir » annule ce
  choix.
- **Repasser en « Uniquement les listes créées »** supprime du téléphone les listes importées,
  après confirmation (elles restent dans Home Assistant). La dernière liste de l'application est
  gardée, déliée. Le moteur refait ce ménage à la synchronisation suivante, au cas où une
  synchronisation en cours aurait importé une liste entre-temps.

## Liaison des listes

- **Créer automatiquement les nouvelles listes** (activé par défaut) : toute liste créée dans
  l'application est aussitôt marquée à synchroniser (`CREATE_LIST` écrit dans la même transaction
  que la liste) et créée dans Home Assistant à la synchronisation suivante. Désactivé, une nouvelle
  liste reste locale jusqu'à ce que l'utilisateur la lie avec « Choisir ».
- **Premier paramétrage** : dès que Home Assistant est activé et configuré, chaque liste qui
  existait déjà et n'est pas synchronisée est proposée tour à tour (créer dans Home Assistant, lier
  à une liste existante, ne pas synchroniser, ou « Plus tard »). Cette question n'est posée qu'une
  fois (`lists_setup_done` dans DataStore).
- **Importer une liste de Home Assistant** (mode « Uniquement les listes créées par cette
  application ») : dans **Mes listes → Nouvelle liste**, « Importer une liste de Home Assistant »
  propose, au lieu d'un nom, les listes `todo.*` modifiables, disponibles et pas encore liées (une
  liste Mealie par exemple). La liste choisie est ajoutée **liée**, sous son nom Home Assistant (ou
  « Mealie 2 » si ce nom est déjà pris dans l'application), s'ouvre aussitôt et ses articles
  arrivent à la synchronisation demandée dans la foulée ; elle se synchronise ensuite dans les deux
  sens comme toute liste liée. Ce n'est pas une copie du mode « Toutes les listes » : elle reste si
  l'on quitte ce mode, son nom ne suit pas les renommages faits dans Home Assistant, et la supprimer
  dans l'application la laisse dans Home Assistant (sauf si l'application l'y avait créée). Une
  liste ignorée ou dont la suppression n'était pas encore envoyée redevient synchronisée. Seule
  étape réseau de l'écran : si Home Assistant ne répond pas, un message le dit et les listes locales
  restent utilisables ; « Réessayer » relit les listes. Absent sans Home Assistant configuré et
  activé, et en mode « Toutes les listes » (qui importe déjà tout).
- **Pour chaque liste locale** : lier à une liste `todo.*` existante (toutes les listes de Home
  Assistant sont proposées, quel que soit le mode ; une copie importée de cette liste est alors
  remplacée), **créer** la liste dans Home Assistant (intégration *Local To-do*, créée par
  l'application et mémorisée dans `ha_tracked_lists`), ou ne pas synchroniser.
- **Noms déjà pris** : si une liste `todo.*` de Home Assistant porte déjà ce nom (casse, accents et
  ponctuation ignorés), un numéro est ajouté côté Home Assistant (« Courses 2 », « Courses 3 »…,
  `HaListNameAllocator`) ; le nom local ne change pas. Si *Local To-do* refuse quand même le nom
  (`already_configured`, liste masquée), le numéro suivant est essayé.
- Synchronisation automatique et « Synchroniser maintenant ».

## Listes Mealie

L'intégration [Mealie](https://www.home-assistant.io/integrations/mealie/) expose chaque liste de
courses Mealie comme une liste `todo.*`, sans description, dont chaque article est le texte affiché
par Mealie : quantité, unité, aliment et note (« 250 grammes Pâtes », « 1 gousse ail », « ½
cuillère à café sel »). Elle est reconnue et lue autrement
([ADR 0026](adr/0026-listes-mealie.md)) :

- **Reconnaissance** : pour une liste sans description, l'application demande une fois au registre
  des entités de Home Assistant (WebSocket, `config/entity_registry/get_entries`, sans droits
  d'administrateur) l'intégration qui la fournit, et la garde (`ha_list_integrations`). Si le
  registre ne répond pas, la liste est lue comme une liste ordinaire pour cette fois et la question
  est reposée à la synchronisation suivante ; un Home Assistant trop ancien pour répondre n'est plus
  interrogé.
- **Lecture** : mêmes règles que le champ d'ajout ([Interface](interface.md)) : « 250 grammes
  Pâtes » devient « Pâtes », 250 g ; « 1 mangue » devient « mangue » × 1 ; les fractions de Mealie
  (« 1/2 », « 1 ½ », « ½ ») sont comprises ; un mot de liaison laissé en fin de texte (« crevettes
  décortiquées de ») est retiré. Un texte sans quantité en tête (« graines de sésame ou selon le
  goût ») garde la quantité du téléphone. Un article modifié dans Mealie est relu.
- **Produits du catalogue** : l'article est rattaché au produit du catalogue de base ou
  OpenFoodFacts que nomme son texte, par son nom ou un alias, au singulier ou au pluriel, la plus
  longue suite de mots gagnant (« gousse ail » → Ail, « oignon rouge » → Oignons rouges, « graines
  de sésame ou selon le goût » → Sésame), sinon à un produit personnalisé. Il est ainsi rangé dans
  son rayon ([Catégories](categories.md#rattachement-des-articles)). Tout est fait hors ligne, dans
  le catalogue embarqué.
- **Articles lus avant ce support** : ils sont relus à la première synchronisation (« 250 grammes
  Pâtes » × 1 devient « Pâtes » 250 g) et rattachés à leur produit ; les produits personnalisés qui
  ne portaient que l'ancien texte disparaissent de l'autocomplétion s'ils ne servent plus (aucun
  article, aucun ajout dans l'historique). Rien n'est renvoyé à Mealie.
- **Ne pas casser Mealie** : Mealie transforme en simple note tout article dont on change le texte
  (il perd l'aliment et la quantité). L'application n'envoie donc que l'état coché quand on coche
  ou décoche, et n'envoie le texte qu'à la création d'un article ou après une modification de son
  nom ou de sa quantité, et seulement s'il diffère de ce que Mealie affiche déjà. Le texte envoyé met
  la quantité en tête (« 500 g Pâtes », « 2 Pain », rien pour une quantité de 1 sans unité), pour
  être relu tel quel.

## Suivi des changements faits dans Home Assistant

- **À l'ouverture** : chaque retour de l'application au premier plan déclenche une synchronisation.
- **Temps réel** : tant que l'application est au premier plan (synchronisation automatique activée,
  réseau disponible), une connexion WebSocket (`/api/websocket`, commande `todo/item/subscribe`)
  suit toutes les listes liées. Chaque changement annoncé déclenche la synchronisation de **sa**
  liste (regroupée sur 1,5 s, sans relire `/api/states`) : il n'y a qu'un seul chemin de fusion.
  Tant que la connexion fonctionne, le nouvel essai périodique passe de 2 à 10 minutes
  ([ADR 0024](adr/0024-synchronisation-ciblee.md)). Connexion perdue : nouvel essai
  après 5 s, puis un délai croissant jusqu'à 5 min. **Token refusé** (ou adresse invalide) : aucun
  nouvel essai ; une dernière synchronisation est demandée, qui affiche l'erreur, et la connexion
  est rouverte dès qu'un autre token ou une autre adresse est enregistré. Elle est fermée quand
  l'application passe en arrière-plan ([ADR 0017](adr/0017-temps-reel-websocket-okhttp.md)).
- **Tirer pour actualiser** sur la liste de courses.
- **Articles créés dans Home Assistant** : rattachés au catalogue alimentaire (produit existant de
  même nom, sinon produit personnalisé créé), donc proposés ensuite dans l'autocomplétion, et rangés
  dans leur catégorie selon leur nom ([Catégories](categories.md)).
- **Liste indisponible** (intégration arrêtée, état `unavailable`, pour laquelle Home Assistant
  répond HTTP 500) : grisée et non sélectionnable dans le choix des listes. Déjà liée, elle est
  ignorée à la synchronisation, ses modifications restent en file et l'indicateur affiche
  « Liste Home Assistant indisponible ».

## API utilisées

| Type | Appels |
| --- | --- |
| REST | `GET /api/`, `GET /api/states` (seulement quand les listes doivent être relues), `POST /api/services/todo/get_items?return_response`, `todo.add_item`, `todo.update_item`, `todo.remove_item` (réponse ignorée) |
| Configuration | `POST /api/config/config_entries/flow` (création *Local To-do*), `DELETE /api/config/config_entries/entry/{id}` |
| WebSocket | `auth`, `todo/item/subscribe`, `config/entity_registry/get_entries` (intégration d'une liste sans description, une fois par liste) |

**Quantités** : Home Assistant n'a pas de champ quantité. Pour les listes qui acceptent une
description (Local To-do), la quantité y est écrite (`2`, `1,5 kg`) ; une quantité de 1 sans unité
laisse la description vide. Pour les listes Mealie, elle est en tête du texte de l'article
([Listes Mealie](#listes-mealie)). Pour les autres listes sans description, elle reste sur le
téléphone.

Voir aussi : [Synchronisation](synchronisation.md), [Stratégie de conflit](conflits.md),
[Limites connues](limites-connues.md#home-assistant).
