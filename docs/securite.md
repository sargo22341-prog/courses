# Sécurité et confidentialité

[← Documentation](README.md)

## Aucune collecte

- Aucun compte, aucun analytics, aucun historique d'achats envoyé : les statistiques d'usage
  restent dans Room. Les catégories sont calculées localement.
- Le catalogue OpenFoodFacts est un fichier statique téléchargé en HTTPS : seuls un `User-Agent`
  identifiant l'application et un `ETag` sont envoyés.
- Aucune dépendance aux services Google Play
  ([ADR 0016](adr/0016-aucune-dependance-google-play.md)).

## Token Home Assistant

- Chiffré en AES-256-GCM avec une clé **non exportable** du Keystore Android (`SecretStore`,
  `KeystoreSecretStore`) ; seul le texte chiffré est écrit (DataStore `secrets`)
  ([ADR 0015](adr/0015-token-chiffre-par-le-keystore.md)).
- Jamais en clair, jamais dans les logs (`HaCredentials.toString()` le masque), jamais réaffiché
  dans l'interface.
- Les fichiers de configuration Home Assistant sont exclus des sauvegardes cloud et des transferts
  d'appareil.
- **Oublier la connexion** (Réglages → Home Assistant, connexion dépliée, après confirmation) :
  efface le token du Keystore et tous les réglages Home Assistant, délie toutes les listes (elles
  restent sur le téléphone) et vide la file. Rien n'est supprimé dans Home Assistant. Pensé pour un
  téléphone prêté ou revendu, ou un token révoqué.

## Trafic réseau

- Le trafic en clair reste autorisé car Home Assistant est souvent joignable en `http://` sur le
  réseau local. Le token circule alors en clair : l'écran Home Assistant **l'indique** sous
  « Connecté » tant que l'adresse enregistrée commence par `http://`. Une adresse saisie sans
  schéma reste complétée en `http://` : c'est ce que sert une installation Home Assistant par
  défaut (port 8123), et essayer `https://` d'abord demanderait un appel réseau à l'enregistrement.
- **OpenFoodFacts** (`openfoodfacts.org` et ses sous-domaines) : le clair y est **refusé** par un
  `domain-config`, même après une redirection.
- **Certificats installés par l'utilisateur** : `network_security_config.xml` fait confiance aux
  autorités système **et** utilisateur pour tous les domaines : le `domain-config` d'OpenFoodFacts
  redéclare les deux ([ADR 0018](adr/0018-confiance-aux-certificats-utilisateur.md)). Un Home Assistant en
  `https://ha.nas.home` signé par une autorité privée fonctionne donc dès que cette autorité est
  installée dans Android (Paramètres → Sécurité → Chiffrement et identifiants → Installer un
  certificat → Certificat CA). Le nom du certificat serveur doit correspondre à l'adresse saisie
  (SAN `ha.nas.home`). Vérifié par `NetworkSecurityConfigTest`.

## Permissions

Toutes passent par `core/permission` : demandées au moment où la fonctionnalité en a besoin, avec
une explication, et un accès aux paramètres Android après un refus définitif.

### Accès au réseau local

Depuis Android 17, joindre une adresse locale (`*.local`, `ha.nas.home` résolu en 192.168.x.x…)
exige la permission d'exécution `ACCESS_LOCAL_NETWORK`, indépendamment du certificat. L'écran Home
Assistant affiche une carte pour l'accorder tant qu'elle manque (ou ouvre les paramètres après un
refus définitif).

### Appareil photo

Demandée uniquement à l'ouverture du scanner de QR code du token. Les images sont analysées en
mémoire, jamais enregistrées ni envoyées.
