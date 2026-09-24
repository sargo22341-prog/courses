# ADR 0020 — Schéma Room exporté et migrations sans perte

- **Statut** : acceptée
- **Voir aussi** : [Architecture](../architecture.md#base-room)

## Contexte

Room est la seule source de vérité ([ADR 0002](0002-room-seule-source-de-verite.md)) : les listes
et les habitudes ne sont souvent sauvegardées nulle part ailleurs, surtout sans Home Assistant.

## Décision

- Schéma exporté dans `app/schemas` et versionné.
- Toute modification d'entité incrémente la version de `CoursesDatabase` et fournit une migration
  ou une `AutoMigration`, testée contre le schéma exporté (`CoursesDatabaseMigrationTest`).
- **Jamais** de `fallbackToDestructiveMigration`.

Historique :

| Version | Changement |
| --- | --- |
| 1 | schéma initial |
| 2 | `catalog_products.groceryCategory` (rayons) |
| 3 | `shopping_lists.importedFromRemote`, `shopping_lists.remoteName`, table `ha_ignored_lists` |
| 4 | colonnes jamais lues supprimées sur place (`ALTER TABLE … DROP COLUMN`) : `catalog_products.brand` et `parentId`, `shopping_items.version`, `sync_operations.lastError`, `ha_tracked_lists.name` et `createdAt` |
| 5 | `shopping_lists.position` (ordre choisi par l'utilisateur) |
| 6 | table `ha_list_integrations` (intégration de chaque liste Home Assistant sans description, [ADR 0026](0026-listes-mealie.md)) |

## Conséquences

- Les données utilisateur survivent à chaque mise à jour de l'application.
- Chaque changement de schéma coûte une migration et un test.
- Les préférences qui ne concernent pas les données relationnelles (langue des imports, état du
  catalogue) vont dans DataStore, ce qui évite des migrations inutiles.
