# Sécurité et confidentialité

[← Documentation](README.md)

## Aucune collecte

- Aucun compte, aucun analytics, aucun historique d'achats envoyé : les statistiques d'usage
  restent dans Room et peuvent être effacées (Réglages → Historique → « Vider l'historique »).
- La base (listes, articles, historique) est **exclue des sauvegardes cloud** (Google, Seedvault…)
  mais suit l'utilisateur lors d'un **transfert direct** vers un nouveau téléphone. Une restauration
  depuis une sauvegarde cloud repart donc de listes vides
  ([ADR 0028](adr/0028-base-exclue-des-sauvegardes-cloud.md)).
  Les catégories sont calculées localement.
- Le catalogue alimentaire est livré avec l'application : aucune requête n'est faite pour le
  consulter ni pour le tenir à jour, et la saisie n'est jamais divulguée
  ([ADR 0025](adr/0025-catalogue-genere-a-la-compilation.md)).
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
- **Lié à son serveur** : le token enregistré n'est envoyé qu'au serveur pour lequel il a été
  saisi (même hôte, quel que soit le schéma ou le port). Tester ou enregistrer l'adresse d'un autre
  serveur demande de saisir à nouveau le token : une adresse erronée ou dictée ne le reçoit jamais.
- Une adresse contenant des identifiants (`utilisateur:motdepasse@`), une requête (`?…`) ou un
  fragment (`#…`) est refusée : ils seraient enregistrés en clair.
- La connexion temps réel (WebSocket) ne suit **aucune redirection** : le token y circule dans le
  message d'authentification, qu'une redirection ne peut pas retirer comme un en-tête. Les
  requêtes REST suivent les redirections, mais OkHttp retire l'en-tête `Authorization` dès que
  l'hôte, le port ou le schéma change.
- Un token devenu illisible (clé Keystore perdue, données corrompues) est effacé et Home Assistant
  s'affiche « non configuré » : le token est redemandé au lieu d'une synchronisation qui ne ferait
  plus rien sans le dire.
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
- Home Assistant est le **seul** serveur que l'application contacte : il n'y a plus qu'une
  configuration réseau, celle de base.
- **Certificats installés par l'utilisateur** : `network_security_config.xml` fait confiance aux
  autorités système **et** utilisateur pour tous les domaines, et aucun `domain-config` ne vient les
  remplacer ([ADR 0018](adr/0018-confiance-aux-certificats-utilisateur.md)). Un Home Assistant en
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
