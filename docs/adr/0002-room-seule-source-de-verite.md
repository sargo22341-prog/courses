# ADR 0002 — Room, seule source de vérité (offline-first)

- **Statut** : acceptée
- **Voir aussi** : [Fonctionnement hors ligne](../hors-ligne.md), [Architecture](../architecture.md#flux-de-données)

## Contexte

Une liste de courses se consulte dans un magasin, souvent au sous-sol, avec un réseau faible ou
absent. Ouvrir → taper → sélectionner → cocher doit fonctionner instantanément, sans compte ni
chargement.

## Décision

- Room est la **seule source de vérité** de l'interface.
- Flux obligatoire : `UI → ViewModel → Repository → Room → Flow → UI`. Jamais
  `UI → réseau → UI`.
- Le réseau n'écrit que dans Room (synchronisation Home Assistant, import du catalogue) ; l'écran
  se met à jour par les `Flow` observés.
- Aucune fonction principale ne dépend du réseau.

## Conséquences

- L'application démarre et fonctionne à l'identique hors ligne.
- Aucun écran de chargement réseau, aucune erreur réseau bloquante.
- Les données distantes doivent être fusionnées avec les données locales : c'est le rôle de la
  [file de synchronisation](0003-file-de-synchronisation-transactionnelle.md) et de la
  [stratégie de conflit](0005-strategie-de-conflit.md).

## Alternatives écartées

- **Serveur comme source de vérité avec cache local** : dépend du réseau pour toute écriture et
  impose un compte ou un backend.
