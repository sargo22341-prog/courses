# ADR 0028 — Base exclue des sauvegardes cloud

- **Statut** : acceptée
- **Voir aussi** : [Sécurité et confidentialité](../securite.md#aucune-collecte),
  [ADR 0015](0015-token-chiffre-par-le-keystore.md)

## Contexte

`android:allowBackup` est actif et les règles de sauvegarde n'excluaient que la configuration Home
Assistant. La base `courses.db`, qui contient les listes, les articles et l'historique d'achats
(`product_usage`), partait donc dans la sauvegarde cloud du téléphone (Google Drive sur un
téléphone avec les services Google). Cela contredisait la règle « aucun historique d'achats envoyé
à un service externe ».

Une restauration ramenait aussi des listes marquées synchronisées sans la configuration Home
Assistant, exclue, et leurs modifications s'accumulaient dans la file sans jamais partir.

## Décision

- `courses.db` et ses fichiers de journal sont exclus de `cloud-backup`
  (`data_extraction_rules.xml`).
- Ils restent dans `device-transfer` : un transfert direct vers un nouveau téléphone, choisi par
  l'utilisateur, ne passe par aucun service.
- `DataExtractionRulesTest` vérifie ces règles.

## Conséquences

- Une restauration depuis une sauvegarde cloud (Google, Seedvault…) repart de listes vides ; le
  catalogue est réimporté depuis l'application au premier lancement.
- Les réglages d'affichage (langue, thème) restent sauvegardés : ils ne disent rien des achats.

## Alternatives écartées

- **Garder la base dans la sauvegarde cloud** et le documenter : la sauvegarde Google est chiffrée
  de bout en bout avec un verrouillage d'écran, mais l'historique quitterait quand même le
  téléphone pour un service externe.
- **Exclure aussi du transfert d'appareil** : perdre ses listes en changeant de téléphone, sans
  gain de confidentialité.
