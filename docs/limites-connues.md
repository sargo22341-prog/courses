# Limites connues

[← Documentation](README.md)

Ce qui n'est pas terminé, pas possible ou pas vérifié. Toute nouvelle limite est ajoutée ici.

## Home Assistant

- **Renommer une liste liée ne change pas son nom dans Home Assistant** : l'API REST ne permet pas
  de renommer une entité `todo`. L'opération `UPDATE_LIST` est retirée de la file sans appel
  (le nom local change) ; l'écran Home Assistant le signale.
- **Quantités** : synchronisées seulement pour les listes qui acceptent une description (Local
  To-do). Pour l'intégration « Shopping list » historique, elles restent locales. Écrire la
  quantité dans la description remplace une description saisie à la main dans Home Assistant.
- **Identifiant après création** : `todo.add_item` ne renvoie pas l'identifiant du nouvel article ;
  il est retrouvé par nom. Si Home Assistant modifie le texte, l'article n'est pas renvoyé : la
  copie de Home Assistant le remplace localement, sans son état coché. Une intégration qui
  n'ajouterait l'article qu'après la réponse le ferait disparaître jusqu'à la synchronisation
  suivante.
- **Abandon après 10 refus** : une modification que Home Assistant refuse 10 fois est abandonnée ;
  l'article reprend l'état de Home Assistant. Une panne passagère renvoyée comme erreur (502 d'un
  proxy pendant un redémarrage) compte aussi comme refus, d'où un plafond large.
- **Création de liste interrompue** : si Home Assistant crée l'entrée *Local To-do* mais que son
  entité n'apparaît pas dans les 3 s, la création est réessayée et une seconde liste (« Courses 2 »)
  peut être créée.
- **Créer ou supprimer une liste dans Home Assistant** nécessite un token d'administrateur (flux
  de configuration *Local To-do*).
- **Pas d'envoi en arrière-plan application fermée** ([ADR 0004](adr/0004-pas-de-workmanager.md)).
  Le temps réel (WebSocket) et le nouvel essai toutes les 2 minutes ne fonctionnent que tant que
  l'application est au premier plan ([ADR 0023](adr/0023-synchronisation-periodique-au-premier-plan.md)) :
  une modification refusée en arrière-plan est réessayée au retour dans l'application.
- **Wi-Fi sans Internet et données mobiles** : si Android bascule alors son réseau par défaut sur
  les données mobiles, l'application l'utilise aussi et un Home Assistant joignable seulement sur le
  réseau local peut ne plus répondre. Non vérifié sur appareil.
- **Suppression annulable** : pendant les quelques secondes du message « Annuler », la suppression
  n'est pas encore écrite ; si le processus est tué à ce moment, l'article reste dans la liste.
- **Création automatique** : elle ne s'applique qu'aux listes créées après l'activation du
  réglage ; une liste passée en « Ne pas synchroniser » n'est jamais recréée automatiquement.
- **Mode « Toutes les listes »** : une liste que Home Assistant ne renvoie plus du tout dans
  `/api/states` (intégration supprimée ou pas encore rechargée) est considérée comme supprimée et
  retirée de l'application ; une intégration simplement arrêtée (`unavailable`) ne retire rien. Un
  renommage fait dans Home Assistant remplace un renommage local antérieur. Une liste ignorée ne
  réapparaît qu'en la liant avec « Choisir » ; il n'y a pas d'écran listant les listes ignorées.
- **Vérifiée surtout par tests automatisés** (moteur avec un Home Assistant simulé en mémoire,
  client HTTP contre MockWebServer). Les requêtes brutes ont été vérifiées contre une instance
  réelle (création *Local To-do*, refus `already_configured` d'un nom déjà pris, `add_item` avec
  description, `get_items`, suppression de l'entrée), mais le parcours complet de l'application
  contre une vraie instance reste à valider.

## Catégories

- La reconnaissance se fait sur le nom exact (au singulier ou au pluriel près). Un nom plus précis
  que le catalogue (« Lait bio de la ferme ») ou avec une faute de frappe va dans « Autres ».
- Le rayon d'une catégorie OpenFoodFacts suit une table écrite à la main : quelques produits
  peuvent être rangés dans un rayon voisin (ex. coulis de fruits en épicerie sucrée).
- « Hygiène et maison » et « Bébé et animaux » ne sont alimentés que par le catalogue de base.
- Tant que le catalogue OpenFoodFacts n'a pas été retéléchargé après une mise à jour de
  l'application, seuls les produits du catalogue de base sont rangés.

## Langues

- La taxonomie OpenFoodFacts est bien moins fournie hors du français et de l'anglais (environ
  900 produits en portugais contre 6 500 en français) : autocomplétion et rangement y reposent
  davantage sur le catalogue de base.
- Singulier et pluriel ne sont reconnus que par les terminaisons régulières (`WordForms`) : les
  pluriels irréguliers (« Mann/Männer ») ne sont pas retrouvés.
- Un seul portugais (plutôt européen, quelques alias brésiliens : « suco », « abacaxi ») et un
  seul espagnol (d'Espagne, alias « jugo »).
- Après un changement de langue hors ligne, la partie OpenFoodFacts reste dans l'ancienne langue
  jusqu'au retour du réseau (Réglages → Catalogue alimentaire l'indique).
- Les traductions ont été écrites sans relecture par des locuteurs natifs.
- Le changement de langue depuis les paramètres Android est relu au retour au premier plan de
  l'application ; il n'a pas été vérifié sur toutes les versions de GrapheneOS.

## Release automatique

- Le workflow GitHub Actions n'a pas encore été exécuté sur GitHub : SDK Android 37 sur le runner,
  push sur `main` protégée et publication de la release restent à valider au premier push.
- Le job émulateur a été vérifié localement avec la même image (`google_apis` x86_64 r6) et le même
  émulateur (37.1.11) que la CI. Sur GitHub, une installation y a échoué dans le système
  (`NullPointerException` dans `StorageManagerService`, non reproduite en local) :
  `scripts/instrumented-tests.sh` attend le gestionnaire de paquets et retente l'installation.
  Il bloque la release s'il échoue.
- `connectedDebugAndroidTest` (AGP 9.4) peut réussir sans exécuter de test si l'installation
  échoue ; la CI ne l'utilise plus.
- Le démarrage de la release ne couvre que le lancement : un problème R8 sur un écran ou un appel
  Home Assistant n'est détecté que par `RetrofitKeepRulesTest` (règle connue) ou à l'usage.
- Un push sur `main` pendant qu'une release compile fait échouer le push de version de cette
  release ; la version sort au push suivant.

## Réseau

- **Certificats utilisateur** : leur prise en compte est vérifiée automatiquement
  (`NetworkSecurityConfigTest`), mais le test complet n'est effectif que sur un appareil où une
  autorité de certification utilisateur est installée ; il est ignoré sinon.
