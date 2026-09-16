# ADR 0022 — Abandon des opérations refusées 10 fois

- **Statut** : acceptée
- **Voir aussi** : [Synchronisation](../synchronisation.md#confirmation-et-échecs), [Conflits](../conflits.md), [ADR 0003](0003-file-de-synchronisation-transactionnelle.md)

## Contexte

Une opération n'est retirée de la file qu'après confirmation de Home Assistant. Certaines ne le
seront jamais : liste en lecture seule (`400`), élément disparu, token non administrateur pour
créer ou supprimer une liste. Sans limite, elles étaient renvoyées à chaque synchronisation,
l'indicateur restait en erreur, le compteur « en attente » ne redescendait jamais, et
l'utilisateur n'avait d'autre recours que d'effacer les données de l'application.

## Décision

- Seuls les **refus** comptent (`SyncQueue.fail` incrémente `attemptCount`) : un serveur
  injoignable ou un token refusé arrête la synchronisation avant tout envoi, sans rien compter.
  Les listes Home Assistant sont lues en premier pour vérifier le token.
- Au **10ᵉ refus** (`SyncQueue.MAX_ATTEMPTS`), l'opération est retirée et son effet local est remis
  en cohérence, dans la même transaction pour un article :
  - suppression d'article refusée : l'article réapparaît ;
  - article lié : il reprend l'état de Home Assistant à la réconciliation ;
  - article jamais accepté : il reste sur le téléphone seulement (`LOCAL_ONLY`) ;
  - création de liste refusée : la liste est déliée et reste sur le téléphone ;
  - suppression de liste refusée : la liste reste dans Home Assistant et n'est plus importée.
- La synchronisation qui abandonne rend `SyncFailure.REJECTED` (« Modifications refusées ») ; la
  suivante, sans refus, efface le message.
- Un article dont Home Assistant a modifié le texte à la création n'est jamais renvoyé : sa copie
  distante le remplace (le renvoyer créait un doublon à chaque synchronisation).

## Conséquences

- Plus de boucle infinie ni de radio sollicitée pour rien ; la file finit toujours par se vider.
- Une modification abandonnée est perdue pour Home Assistant, ce qui est signalé. C'est une
  exception assumée à « une modification locale non synchronisée n'est jamais écrasée »
  ([ADR 0005](0005-strategie-de-conflit.md)) : elle ne s'applique qu'après 10 refus explicites.
- Une erreur passagère renvoyée comme réponse (502 d'un proxy) compte comme un refus ; le plafond
  de 10 laisse une large marge.

## Alternatives écartées

- **Nouvel essai espacé (backoff) sans abandon** : moins de requêtes, mais la file ne se vide
  jamais et l'erreur reste affichée.
- **File de rebut visible dans l'interface** : écran supplémentaire pour un cas rare.
- **Abandon au premier refus** : une erreur passagère ferait perdre une modification.
