# Catalogue OpenFoodFacts et autocomplétion

[← Documentation](README.md)

L'autocomplétion est **100 % locale** : aucune requête n'est faite pendant la frappe. Le catalogue
combine trois sources, toutes stockées dans Room :

| Source | Origine | Mise à jour |
| --- | --- | --- |
| Catalogue de base | `assets/catalog/seed.json`, 266 produits traduits dans les six langues | au premier démarrage et à chaque changement de langue, hors ligne |
| OpenFoodFacts | taxonomie publique des catégories | hebdomadaire, dès qu'un réseau est disponible |
| Produits personnalisés | créés par l'utilisateur ou venus de Home Assistant | à la saisie |

## Source et import

- Source : la taxonomie publique des **catégories** OpenFoodFacts
  (`https://static.openfoodfacts.org/data/taxonomies/categories.json`, ~4,6 Mo, ~1,8 Mo compressé,
  licence ODbL). C'est un fichier statique servi par CDN : **aucun appel API par frappe**, aucune
  donnée personnelle envoyée (seulement un `User-Agent` identifiant l'application et un `ETag`).
- Pourquoi les catégories plutôt que les produits : la base produits complète pèse plusieurs Go et
  contient surtout des références de marque. Les catégories (`Laits demi-écrémés`,
  `Tomates cerise`…) correspondent à ce qu'on écrit sur une liste de courses
  ([ADR 0006](adr/0006-catalogue-openfoodfacts-sans-backend.md)).
- Langues : en septembre 2026, le fichier compte ~14 700 entrées nommées dans 181 codes de langue.
  Seuls les noms dans la langue de l'application sont importés. Entrées nommées / produits gardés
  après nettoyage (estimation) : français ~10 700 / ~6 500, anglais ~9 200 / ~7 600, allemand
  ~3 600 / ~3 200, espagnol ~3 600 / ~2 600, italien ~3 700 / ~2 300, portugais ~1 300 / ~900.
- Nettoyage (`TaxonomyCatalogMapper`) : nom obligatoire dans la langue de l'application ;
  exclusion des appellations protégées (AOP/IGP) et des entrées liées à une origine ; au plus
  4 mots et 40 caractères ; aucun chiffre ; dédoublonnage sur le nom normalisé en gardant l'entrée
  la plus générique. Il reste quelques milliers de produits : la base reste petite.
- Stockage : `catalog_products` (nom, nom normalisé indexé, catégorie, rayon, parent, source, score
  de base, version d'import), `catalog_aliases`, et `product_usage` (habitudes, **séparées** du
  catalogue pour ne jamais être effacées par une mise à jour,
  [ADR 0008](adr/0008-habitudes-separees-du-catalogue.md)).

## Mise à jour hebdomadaire, sans backend

`CatalogSyncManager` + `CatalogFreshnessPolicy` :

```
ouverture de l'app → date du dernier téléchargement (DataStore)
   → plus de 7 jours ? → réseau disponible ? → téléchargement (If-None-Match: ETag)
        304 → seule la date est mise à jour
        200 → import transactionnel (upsert + suppression des lignes d'une ancienne version)
```

- Si le réseau est absent à l'ouverture, la vérification a lieu dès qu'il revient tant que
  l'application est ouverte.
- **Réglages → Catalogue alimentaire** : « ↻ Synchroniser maintenant » force un téléchargement
  complet (sans `ETag`) et affiche `Synchronisation…`, puis `✓ Catalogue mis à jour` ou un message
  clair (hors connexion, échec). En cas d'échec, le catalogue existant reste intact.
- **Version de format** : quand l'import extrait de nouvelles données
  (`TaxonomyCatalogMapper.FORMAT_VERSION`, 1 = rayons), un catalogue importé par une version
  précédente de l'application est retéléchargé une fois en entier au prochain démarrage avec
  réseau, même s'il a moins de 7 jours.
- **Langue** : un catalogue importé dans une autre langue que celle de l'application
  (`catalog_language` dans DataStore) est retéléchargé en entier dès qu'un réseau est disponible
  ([Langues](langues.md)).

## Recherche et classement

`SearchSuggestionsUseCase` (100 % local) :

1. Normalisation (`TextNormalizer`) : minuscules, sans accents, `œ → oe`, `ß → ss`, ponctuation →
   espaces, identique dans toutes les langues.
2. Présélection SQL : nom ou alias contenant la saisie.
3. Classement (`SuggestionRanker`), par paliers de 1 000 points que les bonus ne peuvent pas
   franchir : **exact > préfixe > début de mot > partiel > approximatif**. À palier égal :
   fréquence d'utilisation (+20 par ajout, plafonné), récence (+150 sur 3 jours, +100 sur
   14 jours, +40 sur 60 jours), score de base du catalogue, puis nom le plus court.
4. Si les résultats sont insuffisants : recherche tolérante aux fautes (`FuzzyMatcher`, distance
   d'édition avec transpositions sur les préfixes de mots : `lati → Lait`, `tomatos → Tomates`).

## Ajout d'un article

- Si rien ne correspond exactement, `+ Ajouter « … »` crée un **produit personnalisé** local,
  proposé ensuite comme les autres.
- Chaque ajout alimente les statistiques d'usage.
- Ajouter un produit déjà présent incrémente sa quantité ; ajouter un produit déjà acheté le remet
  « à acheter » (`AddItemUseCase`).
