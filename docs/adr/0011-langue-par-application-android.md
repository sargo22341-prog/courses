# ADR 0011 — Langue par application d'Android plutôt qu'une préférence

- **Statut** : acceptée
- **Voir aussi** : [Langues](../langues.md)

## Contexte

L'interface existe en six langues. L'utilisateur doit pouvoir choisir une langue différente de
celle du téléphone, au premier lancement et dans les réglages.

## Décision

- Utiliser la **langue par application** d'Android (`LocaleManager`,
  `LocaleManagerAppLanguageRepository`), sans préférence DataStore dupliquée.
- `generateLocaleConfig` déclare les langues proposées ; Android les affiche aussi dans la page de
  l'application de ses paramètres.
- Tant qu'aucune langue n'est choisie, l'application suit celle de l'appareil ; au premier
  lancement, la langue de l'appareil est présélectionnée si elle est proposée, sinon l'anglais.

## Conséquences

- Une seule source de vérité, cohérente avec les paramètres Android ; un changement fait dans les
  paramètres est relu au retour dans l'application.
- Aucune dépendance : AppCompat n'est pas nécessaire en API 37.
- Aucun changement de schéma Room : la langue des imports du catalogue est mémorisée dans le
  DataStore du catalogue.

## Alternatives écartées

- **Préférence DataStore + `AppCompatDelegate`** : dépendance supplémentaire et deux sources de
  vérité à synchroniser avec les paramètres Android.
