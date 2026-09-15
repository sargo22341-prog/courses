# ADR 0018 — Confiance aux autorités de certification installées par l'utilisateur

- **Statut** : acceptée
- **Voir aussi** : [Sécurité](../securite.md#trafic-réseau)

## Contexte

Home Assistant est souvent auto-hébergé, en HTTPS derrière un certificat signé par une autorité
privée (`https://ha.nas.home`), ou en `http://` sur le réseau local. Par défaut, depuis Android 7,
les applications ne font confiance qu'aux autorités système.

## Décision

- `network_security_config.xml` fait confiance aux autorités **système et utilisateur** pour tous
  les domaines, sans `domain-config` qui retirerait les autorités utilisateur.
- Le trafic en clair reste autorisé (Home Assistant local en `http://`) ; OpenFoodFacts est
  toujours appelé en HTTPS.
- Règle protégée par `NetworkSecurityConfigTest`, qui doit rester vert.

## Conséquences

- Un Home Assistant signé par une autorité privée fonctionne dès qu'elle est installée dans
  Android ; le nom du certificat serveur doit correspondre à l'adresse saisie.
- Une autorité utilisateur malveillante installée sur l'appareil pourrait intercepter le trafic de
  l'application ; c'est le choix de l'utilisateur qui l'a installée.
- Le test complet n'est effectif que sur un appareil où une autorité utilisateur est installée.

## Alternatives écartées

- **Autorités système seulement** : Home Assistant auto-hébergé en HTTPS privé inutilisable.
- **Épinglage d'un certificat saisi dans l'application** : écran et configuration supplémentaires.
