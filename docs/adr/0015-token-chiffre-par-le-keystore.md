# ADR 0015 — Token Home Assistant chiffré par le Keystore

- **Statut** : acceptée
- **Voir aussi** : [Sécurité](../securite.md#token-home-assistant)

## Contexte

Le token d'accès longue durée Home Assistant donne un accès complet à l'instance (souvent la
domotique de la maison). Il doit être conservé sur l'appareil pour synchroniser sans le redemander.

## Décision

- Stockage **uniquement** via l'interface `SecretStore`, implémentée par `KeystoreSecretStore` :
  AES-256-GCM avec une clé **non exportable** du Keystore Android ; seul le texte chiffré est
  écrit (DataStore `secrets`).
- Jamais en clair dans DataStore ou SharedPreferences, jamais dans un log, un `toString()`
  (`HaCredentials` le masque), un test ou un rapport ; jamais réaffiché dans l'interface.
- Configuration Home Assistant exclue des sauvegardes cloud et des transferts d'appareil.

## Conséquences

- Le token ne peut pas être extrait d'une copie des fichiers de l'application.
- Après une restauration ou un changement d'appareil, il faut ressaisir ou rescanner le token.

## Alternatives écartées

- **`EncryptedSharedPreferences` (Jetpack Security)** : bibliothèque dépréciée, dépendance
  supplémentaire pour ce que le Keystore fait directement.
