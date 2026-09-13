# AGENTS.md

- Garder le projet propre, modulaire et facile à maintenir.
- Respecter l'architecture et les conventions déjà présentes dans le projet.
- Organiser le code par fonctionnalité et responsabilité ; éviter les dossiers fourre-tout.
- Séparer clairement l'UI, la logique métier et l'accès aux données.
- Préférer des classes et fonctions petites et spécialisées.
- Aucun fichier source ne doit dépasser 600 lignes.
- Réutiliser le code existant avant de créer de nouvelles abstractions ou dépendances.
- Supprimer le code, les imports et les fichiers temporaires inutilisés.
- Ne pas mettre de logique métier dans les Composables.
- Ne pas accéder directement au réseau ou à la base de données depuis l'UI.
- Ne pas ajouter de dépendances sans nécessité.
- Ajouter des tests pour les fonctionnalités métier importantes.
- Avant de terminer une tâche, compiler le projet et corriger les erreurs de compilation ou de tests.
- Ne pas modifier un comportement existant sans rapport avec la tâche en cours.
- Si une modification d'architecture est nécessaire, la garder minimale et cohérente avec le projet.

## Téléphone, ADB, Git et identité

**ADB** : `C:\platform-tools\adb.exe`

Avant test : vérifier l'appareil visible, ne jamais supposer une connexion ni prétendre avoir testé si ADB ne le voit pas. Ne pas effacer les données utilisateur sans autorisation ni désinstaller inutilement. Installer le build debug, lancer l'activité principale, consulter `logcat` filtré sur le package en cas de problème. Jamais de secret dans les logs copiés.

**Git et fichiers** : modifier uniquement les fichiers liés à la tâche ; jamais `git reset --hard` ni suppression de travail existant pour résoudre un conflit ; pas de commit sans demande explicite ; petits changements cohérents. Pas de binaire généré, secret, fichier temporaire, code généré massif inutilisé, ou police sans licence explicite.

**Identité** : nom public/`app_name` = **courses** ·

`applicationId` manquant → demander sa valeur avant init définitive, jamais en inventer un.

## rapport final
Rapport final : modifications, fichiers principaux, tests exécutés, résultats, vérifications non effectuées, limites restantes, validation attendue.

Avant de donner le rapport final, tenter :
```powershell
kdeconnect-cli --device 9d3e0da7eb0e4cacb95ff4869f8f669b --ping-msg "text"
```
Ne pas tenir compte du retour de kdeconnect.

Dans le rapport final rajouter un exemple de text en francais et copiable pour un commit uniquement si des changment on etais ajouter