# Langues

[← Documentation](README.md)

Langues de l'interface : **allemand, anglais, espagnol, français, italien, portugais**. Les textes
anglais sont les ressources par défaut (`values/`, `res/resources.properties`) ; chaque texte existe
dans `values-de`, `values-es`, `values-fr`, `values-it` et `values-pt` (`StringResourcesTest`).

## Choix de la langue

- Écran d'accueil et **Réglages → Langue**, par des puces portant chacune le nom de la langue dans
  cette langue (« Deutsch », « English »…, `LanguageSelector`).
- Au premier lancement, la langue de l'appareil est présélectionnée si elle est proposée
  (`pt-BR` → portugais), sinon l'anglais (`AppLanguage.resolve`). Toucher une langue traduit
  l'écran aussitôt.

## Stockage

- La langue par application d'Android (`LocaleManager`, `LocaleManagerAppLanguageRepository`),
  sans préférence dupliquée ([ADR 0011](adr/0011-langue-par-application-android.md)).
- Android la conserve, recrée les écrans dans la nouvelle langue et la propose aussi dans la page
  de l'application de ses paramètres (`generateLocaleConfig`) ; un changement fait là est relu au
  retour dans l'application.
- Tant qu'aucune autre langue n'est choisie, l'application suit la langue de l'appareil.
- **Installations existantes** : une application configurée avant l'arrivée des langues reste en
  français (la langue de son catalogue), même sur un téléphone dans une autre langue ; une seule
  fois (`KeepFrenchForExistingInstallUseCase`, `language_confirmed` dans DataStore).

## Changement de langue

Hors ligne comme en ligne, et sans rien attendre du réseau :

1. l'interface est traduite immédiatement ;
2. tout le catalogue est réimporté dans la nouvelle langue, hors ligne : le catalogue de base et le
   fichier OpenFoodFacts de la nouvelle langue sont livrés avec l'application
   ([ADR 0025](adr/0025-catalogue-genere-a-la-compilation.md)) ;
3. après chaque import, **les articles de toutes les listes sont rattachés au catalogue**
   (`LinkItemsToCatalogUseCase`, voir [Catégories](categories.md#rattachement-des-articles)).

**Rien n'est supprimé ni renommé dans les listes** : les articles gardent le nom saisi (« Lait »
reste « Lait » en anglais), seuls leur produit associé et donc leur rayon sont mis à jour
([ADR 0013](adr/0013-articles-jamais-traduits.md)). Les identifiants des produits ne dépendent pas
de la langue (`en:milks` pour la taxonomie, `seed:lait` construit sur le nom français pour le
catalogue de base) : habitudes d'usage et liens des articles sont conservés, les produits
personnalisés aussi.

## Formats

Dates et séparateur décimal des quantités suivent la langue (`1,5 kg`, `1.5 kg`), y compris dans
la description envoyée à Home Assistant ; les deux séparateurs sont relus.
